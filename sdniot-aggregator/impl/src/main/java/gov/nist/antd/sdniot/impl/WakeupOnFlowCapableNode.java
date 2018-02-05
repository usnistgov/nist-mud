package gov.nist.antd.sdniot.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WakeupOnFlowCapableNode implements DataTreeChangeListener<FlowCapableNode> {
  private static final Logger LOG = LoggerFactory.getLogger(WakeupOnFlowCapableNode.class);

  private SdnmudProvider sdnmudProvider;

  private FlowCommitWrapper dataStoreAccessor;

  private Set<String> switches = new HashSet<>();

  // PacketInDispatcher(String nodeId, InstanceIdentifier<FlowCapableNode>
  // node,
  // IMdsalApiManager mdsalApiManager,
  // FlowCommitWrapper flowCommitWrapper, SdnmudProvider sdnmudProvider)
  //
  public WakeupOnFlowCapableNode(SdnmudProvider sdnMudProvider) {
    this.sdnmudProvider = sdnMudProvider;
    dataStoreAccessor = sdnmudProvider.getFlowCommitWrapper();
  }

  @Override
  public void onDataTreeChanged(Collection<DataTreeModification<FlowCapableNode>> modifications) {
    LOG.debug("WakeupOnFlowCapableNode: onDataTreeChanged");

    for (DataTreeModification<FlowCapableNode> modification : modifications) {
      if (modification.getRootNode().getModificationType() == ModificationType.WRITE) {
        LOG.info("got a WRITE modification");
        InstanceIdentifier<FlowCapableNode> ii = modification.getRootPath().getRootIdentifier();
        onFlowCapableSwitchAppeared(ii);
      } else if (modification.getRootNode().getModificationType() == ModificationType.DELETE) {
        LOG.info("Got a DELETE modification");
        InstanceIdentifier<FlowCapableNode> ii = modification.getRootPath().getRootIdentifier();
        onFlowCapableSwitchDisappeared(ii);
      } else {
        LOG.debug("WakeupOnFlowCapableNode : " + modification.getRootNode().getModificationType());
      }
    }

  }

  static byte[] reverseArray(byte inputArray[]) {

    byte temp;

    for (int i = 0; i < inputArray.length / 2; i++) {
      temp = inputArray[i];

      inputArray[i] = inputArray[inputArray.length - 1 - i];

      inputArray[inputArray.length - 1 - i] = temp;
    }
    return inputArray;

  }

  private MacAddress getNodeMacAddr(NodeId nodeId) {
    // NodeId.get value returns the string representatio of the
    // NodeId. The part after the : has the mac address of the switch.
    BigInteger macAddrBigInt = MDSALUtil.getDpnIdFromNodeName(nodeId);
    LOG.info("dpnId : " + macAddrBigInt);
    // toByteArray returns the 2's complement
    byte[] contents = macAddrBigInt.toByteArray();
    byte[] rawMac = new byte[6];
    System.arraycopy(contents, 0, rawMac, 0, Math.min(6, contents.length));
    return PacketUtils.rawMacToMac(reverseArray(rawMac));
  }


  private void installUnconditionalGoToTable(String nodeId,
      InstanceIdentifier<FlowCapableNode> node, short table, short destinationTable) {
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);
    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
    FlowBuilder unconditionalGoToNextFlow =
        FlowUtils.createUnconditionalGoToNextTableFlow(table, destinationTable, flowId, flowCookie);
    dataStoreAccessor.writeFlow(unconditionalGoToNextFlow, node);
  }

  /**
   * Flow to send the IDS HELLO to the controller if it has not already been installed.
   * 
   * @param nodeUri
   * @param node
   */
  private void installSendIdsHelloToControllerFlow(String nodeUri,
      InstanceIdentifier<FlowCapableNode> node) {
    if (!sdnmudProvider.getFlowCommitWrapper().flowExists(nodeUri + ":",
        SdnMudConstants.FIRST_TABLE, node)) {
      FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":ids");
      FlowCookie flowCookie = SdnMudConstants.IDS_REGISTRATION_FLOW_COOKIE;
      LOG.info("IDS_REGISTRATION_FLOW_COOKIE " + flowCookie.getValue().toString(16));
      FlowBuilder fb =
          FlowUtils.createDestIpMatchSendToController(SdnMudConstants.IDS_REGISTRATION_ADDRESS,
              SdnMudConstants.IDS_REGISTRATION_PORT, SdnMudConstants.FIRST_TABLE, flowCookie,
              flowId, SdnMudConstants.IDS_REGISTRATION_METADATA);
      dataStoreAccessor.writeFlow(fb, node);
    }
  }

  /**
   * Flow to send packet to Controller (unconditionally)
   * 
   * @param nodePath
   * @param idsRulesTable1
   */
  private void installSendPacketToControllerFlow(String nodeUri,
      InstanceIdentifier<FlowCapableNode> node) {
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":sendToController");
    FlowCookie flowCookie = SdnMudConstants.SEND_TO_CONTROLLER_FLOW_COOKIE;
    LOG.info("SEND_TO_CONTROLLER_FLOW_COOKIE " + flowCookie.getValue().toString(16));
    FlowBuilder fb = FlowUtils.createUnconditionalSendPacketToControllerFlow(
        SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);
    this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }

  public void uninstallDefaultFlows(String nodeUri) {
    InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(nodeUri);
    if (node != null) {
      for (short tid = 0; tid < SdnMudConstants.MAX_TID + 1; tid++)
        sdnmudProvider.getFlowCommitWrapper().deleteFlows(node, nodeUri, tid, null);
    }
  }

  private synchronized void installUnditionalDropPacket(String nodeId,
      InstanceIdentifier<FlowCapableNode> nodePath, Short dropPacketTable) {
    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

    FlowBuilder flow =
        FlowUtils.createUnconditionalDropPacketFlow(dropPacketTable, flowId, flowCookie);
    this.dataStoreAccessor.writeFlow(flow, nodePath);
  }

  private void installPermitPacketsToFromDhcp(String nodeId,
      InstanceIdentifier<FlowCapableNode> node) {
    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);
    FlowBuilder flowBuilder = FlowUtils.createPermitPacketsToDhcpServerFlow(
        SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE, flowCookie, flowId);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

    // DHCP is local so both directions are installed on the CPE node.
    flowId = InstanceIdentifierUtils.createFlowId(nodeId);
    flowBuilder = FlowUtils.createPermitPacketsFromDhcpServerFlow(
        SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE, flowCookie, flowId);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
  }

  public void installInitialFlows(String nodeUri) {
    InstanceIdentifier<FlowCapableNode> nodePath = this.sdnmudProvider.getNode(nodeUri);
    if (nodePath == null) {
      LOG.info("FlowCapableNode not found. Not installing inital flows");
      return;
    }
    uninstallDefaultFlows(nodeUri);
    if (sdnmudProvider.isNpeNode(nodeUri)) {
      installSendIdsHelloToControllerFlow(nodeUri, nodePath);
    }


    // Install a passthrough pipeline for successful flow matches.

    installUnconditionalGoToTable(nodeUri, nodePath,
        SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
        SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE);
    installUnconditionalGoToTable(nodeUri, nodePath,
        SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, SdnMudConstants.SDNMUD_RULES_TABLE);

    installUnconditionalGoToTable(nodeUri, nodePath, SdnMudConstants.SDNMUD_RULES_TABLE,
        SdnMudConstants.IDS_RULES_TABLE);

    installUnconditionalGoToTable(nodeUri, nodePath, SdnMudConstants.IDS_RULES_TABLE,
        SdnMudConstants.L2SWITCH_TABLE);


    // Install an unconditional packet drop in IDS_RULES_TABLE1 (this is
    // where MUD packets that do not match
    // go. The default action is to drop the packet.


    installUnditionalDropPacket(nodeUri, nodePath, SdnMudConstants.IDS_RULES_TABLE1);

    // All devices may access DHCP (default rule).
    installPermitPacketsToFromDhcp(nodeUri, nodePath);

    // All devices may access DNS and NTP.
    try {
      MudFlowsInstaller.installAllowToDnsAndNtpFlowRules(sdnmudProvider, nodePath);
    } catch (Exception ex) {
      LOG.error("installFlows : Exception installing default flows ", ex);
    }
  }



  /**
   * This gets invoked when a switch appears and connects.
   * 
   * @param nodePath -- the node path.
   *
   */
  public synchronized void onFlowCapableSwitchAppeared(
      InstanceIdentifier<FlowCapableNode> nodePath) {

    String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();



    LOG.info("onFlowCapableSwitchAppeared");
    // The URI identifies the node instance.
    LOG.info("node URI " + nodeUri + " nodePath " + nodePath);
    // Stash away the URI to node path so we can reference it later.
    this.sdnmudProvider.putInUriToNodeMap(nodeUri, nodePath);

    switches.add(nodeUri);

    this.uninstallDefaultFlows(nodeUri);

    this.installInitialFlows(nodeUri);

    PacketInDispatcher packetInDispatcher =
        new PacketInDispatcher(nodeUri, nodePath, sdnmudProvider);

    sdnmudProvider.getNotificationService().registerNotificationListener(packetInDispatcher);
  }

  /**
   * Deal with disconnection of the switch.
   * 
   * @param nodePath - the instance id of the disconnecting switch.
   */

  public synchronized void onFlowCapableSwitchDisappeared(
      InstanceIdentifier<FlowCapableNode> nodePath) {
    String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
    LOG.info("onFlowCapableSwitchDisappeared");
    // The URI identifies the node instance.
    LOG.info("node URI " + nodeUri);
    // Remove the node URI from the uriToNodeMap.
    this.sdnmudProvider.removeNode(nodeUri);
    // Remove the node URI from our switches table.
    this.switches.remove(nodeUri);

  }

}
