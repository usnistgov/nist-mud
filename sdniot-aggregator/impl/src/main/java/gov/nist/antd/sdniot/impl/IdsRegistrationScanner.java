/*
 * Copyright © 2017 None.
 *
 * This program and the accompanying materials are in the public domain.
 * 
 * 
 */

package gov.nist.antd.sdniot.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.TimerTask;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.ids.config.rev170915.IdsConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scan for IDS registrations and install flow rules.
 * 
 * @author mranga
 *
 */
public class IdsRegistrationScanner extends TimerTask {

  static final Logger LOG = LoggerFactory.getLogger(IdsRegistrationScanner.class);

  private SdnmudProvider sdnmudProvider;

  public IdsRegistrationScanner(SdnmudProvider sdnmudProvider) {
    this.sdnmudProvider = sdnmudProvider;
  }

  private static String getManufacturer(Uri flowSpec) {

    String[] pieces = flowSpec.getValue().split(":");
    return pieces[1];

  }

  private static String getFlowType(Uri flowSpec) {

    String[] pieces = flowSpec.getValue().split(":");
    return pieces[2];
  }

  private void installStripMplsTagAndGoToL2Switch(InstanceIdentifier<FlowCapableNode> nodePath,
      FlowId flowId, Short stripMplsRuleTable, Short l2switchTable, int label) {

    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowId.getValue());

    FlowBuilder flow = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId,
        stripMplsRuleTable, l2switchTable, label);

    this.sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, nodePath);

  }

  @Override
  public void run() {
    for (IdsConfigData idsConfigData : sdnmudProvider.getIdsConfigs()) {
      List<Uri> flowSpec = idsConfigData.getFlowSpec();
      String idsNodeId = idsConfigData.getIdsNode().getValue();
      InstanceIdentifier<FlowCapableNode> idsNode = this.sdnmudProvider.getNode(idsNodeId);
      if (idsNode == null) {
        LOG.info("IDS node not found");
        return;
      }
      FlowCommitWrapper flowCommitWrapper = sdnmudProvider.getFlowCommitWrapper();
      this.sdnmudProvider.garbageCollectIdsRegistrationRecords();
      List<Integer> idsPorts = sdnmudProvider.getIdsPorts(idsNodeId);

      if (idsPorts == null) {
        LOG.debug("No IDS registrations found for " + idsNodeId);
        for (Uri uri : flowSpec) {
          String manufacturer = getManufacturer(uri);
          Collection<InstanceIdentifier<FlowCapableNode>> nodes =
              this.sdnmudProvider.getMudNodes(manufacturer);
          if (nodes != null) {
            for (InstanceIdentifier<FlowCapableNode> node : nodes) {
              flowCommitWrapper.deleteFlows(node, uri.getValue(), SdnMudConstants.IDS_RULES_TABLE,
                  null);
            }
          }
          flowCommitWrapper.deleteFlows(idsNode, uri.getValue(), SdnMudConstants.SDNMUD_RULES_TABLE,
              null);
        }
        return;
      }

      LOG.debug("idsRegistration: found for idsNodeId : " + idsNodeId);

      int duration = idsConfigData.getFilterDuration();
      
      LOG.debug("idsRegistration: duration = " + duration);
      for (Uri uri : flowSpec) {
        String manufacturer = getManufacturer(uri);
        String flowType = getFlowType(uri);

        LOG.debug("idsRegistration: manufacturer = " + manufacturer + " flowType = " + flowType);
        Collection<InstanceIdentifier<FlowCapableNode>> nodes =
            this.sdnmudProvider.getMudNodes(manufacturer);

        if (nodes != null) {
          LOG.debug("idsRegistration: [" + manufacturer + "] sizeof nodes " + nodes.size());

          // Set up a rule on IDS to tag divert packets on device to
          // device flows.

          FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri.getValue());
          int mplsTag = InstanceIdentifierUtils.getFlowHash(uri.getValue());

          for (InstanceIdentifier<FlowCapableNode> node : nodes) {

            String sourceNodeUri = InstanceIdentifierUtils.getNodeUri(node);
            if (sourceNodeUri == null) {
              LOG.error("idsRegistration: Cannot find source node URI");
              continue;
            }
            sdnmudProvider.getFlowCommitWrapper().deleteFlows(node, uri.getValue(),
                SdnMudConstants.IDS_RULES_TABLE, null);

            String outputPortUri = sdnmudProvider.getNodeConnector(sourceNodeUri, idsNodeId);
            if (outputPortUri == null) {
              LOG.info("Cannot find output port URI");
              continue;
            }
            LOG.debug("idsRegistration: outputPortUri " + outputPortUri);
            flowCommitWrapper.deleteFlows(node, uri.getValue(), SdnMudConstants.IDS_RULES_TABLE,
                null);
            if (flowType.equals(SdnMudConstants.LOCAL)) {
              // This is a LOCAL flow so we tee the flow.
              FlowId flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
              // Match on metadata, set the MPLS tag and send to NPE switch and go to l2 switch.
              FlowBuilder fb = FlowUtils.createMetadataMatchSetMplsTagSendToPortAndGoToTable(
                  flowCookie, flowId, SdnMudConstants.IDS_RULES_TABLE,
                  SdnMudConstants.STRIP_MPLS_RULE_TABLE, mplsTag, outputPortUri, duration);
              // Write the flow to the data store.
              flowCommitWrapper.writeFlow(fb, node);
              // Install a flow to strip the mpls label.
              flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
              // Strip MPLS tag and go to the L2 switch.
              installStripMplsTagAndGoToL2Switch(node, flowId,
                  SdnMudConstants.STRIP_MPLS_RULE_TABLE, SdnMudConstants.L2SWITCH_TABLE, mplsTag);
            } 
          }
          
          // Install flow rule for diversion in the flow capable
          // switch.
          FlowId flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
          flowCommitWrapper.deleteFlows(idsNode, uri.getValue(), SdnMudConstants.SDNMUD_RULES_TABLE,
              null);

          if (flowType.equals(SdnMudConstants.LOCAL)) {
            // If the flowType is LOCAL then we do not divert the
            // flows.
            FlowBuilder flow = FlowUtils.createOnMplsTagMatchPopMplsTagsAndSendToPort(flowCookie,
                flowId, InstanceIdentifierUtils.getFlowHash(uri.getValue()), idsNodeId, idsPorts,
                SdnMudConstants.SDNMUD_RULES_TABLE, duration);

            // Install the flow.
            flowCommitWrapper.writeFlow(flow, idsNode);

          } else if (flowType.equals(SdnMudConstants.REMOTE)) {
            // Match on the metadata and send it to the IDS node.
            flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
            BigInteger metadata = MudFlowsInstaller.createSrcManufacturerMetadata(manufacturer);
            BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK;
            FlowBuilder flow = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId,
                metadata, metadataMask, SdnMudConstants.SDNMUD_RULES_TABLE, 
                SdnMudConstants.L2SWITCH_TABLE, idsPorts, duration);
            // Install the flow.
            flowCommitWrapper.writeFlow(flow, idsNode);
            flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
            metadata = MudFlowsInstaller.createDstManufacturerMetadata(manufacturer);
            metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK;
            flow = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId,
                metadata, metadataMask, SdnMudConstants.SDNMUD_RULES_TABLE, 
                SdnMudConstants.L2SWITCH_TABLE, idsPorts, duration);
            // Install the flow.
            flowCommitWrapper.writeFlow(flow, idsNode);

          } else {
            LOG.error("Not yet implemented flow type " + flowType);
            throw new RuntimeException("Not yet implemented");
          }
        } else {
          LOG.debug("no nodes found for manufactuer " + manufacturer);
        }
      }

    }

  }

}
