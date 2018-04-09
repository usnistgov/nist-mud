
/*
 * This file includes code developed by employees of the National Institute of
 * Standards and Technology (NIST)
 *
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), and others. This software has been
 * contributed to the public domain. Pursuant to title 15 Untied States
 * Code Section 105, works of NIST employees are not subject to copyright
 * protection in the United States and are considered to be in the public
 * domain. As a result, a formal license is not needed to use this software.
 *
 * This software is provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND,
 * EXPRESS, IMPLIED OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * NON-INFRINGEMENT AND DATA ACCURACY. NIST does not warrant or make any
 * representations regarding the use of the software or the results thereof,
 * including but not limited to the correctness, accuracy, reliability or
 * usefulness of this software.
 */
package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

/**
 * Class that gets invoked when a switch connects.
 * 
 * @author mranga@nist.gov
 *
 */

public class WakeupOnFlowCapableNode implements DataTreeChangeListener<FlowCapableNode> {
	private static final Logger LOG = LoggerFactory.getLogger(WakeupOnFlowCapableNode.class);

	private SdnmudProvider sdnmudProvider;

	private FlowCommitWrapper dataStoreAccessor;

	private ArrayList<InstanceIdentifier<FlowCapableNode>> pendingNodes = new ArrayList<>();

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

	

	/**
	 * Flow to send packet to Controller (unconditionally)
	 * 
	 * @param nodePath
	 * @param idsRulesTable1
	 */
	private void installSendPacketToControllerFlow(String nodeUri, InstanceIdentifier<FlowCapableNode> node) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":sendToController");
		FlowCookie flowCookie = SdnMudConstants.SEND_TO_CONTROLLER_FLOW_COOKIE;
		LOG.info("SEND_TO_CONTROLLER_FLOW " + flowCookie.getValue().toString(16));
		FlowBuilder fb = FlowUtils.createUnconditionalSendPacketToControllerFlow(
				BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
		flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":sendToController");
		fb = FlowUtils.createUnconditionalSendPacketToControllerFlow(
				BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId,
				flowCookie);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private void installSendIpPacketToControllerFlow(String nodeUri, short tableId,
			InstanceIdentifier<FlowCapableNode> node) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":sendToController");
		FlowCookie flowCookie = SdnMudConstants.SEND_TO_CONTROLLER_FLOW_COOKIE;
		FlowBuilder fb = FlowUtils.createIpMatchSendPacketToControllerFlow(tableId, flowId, flowCookie);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	
	private void installUnconditionalGoToTable(InstanceIdentifier<FlowCapableNode> node, short table) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId("SDNMUD");
		FlowCookie flowCookie = SdnMudConstants.UNCLASSIFIED_FLOW_COOKIE;
		FlowBuilder unconditionalGoToNextFlow = FlowUtils.createUnconditionalGoToNextTableFlow(table,
				 flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(unconditionalGoToNextFlow, node);
	}
	

	private void installUnditionalDropPacket(String nodeId, InstanceIdentifier<FlowCapableNode> nodePath,
			Short dropPacketTable) {
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

		FlowBuilder flow = FlowUtils.createUnconditionalDropPacketFlow(dropPacketTable, flowId, flowCookie);
		this.dataStoreAccessor.writeFlow(flow, nodePath);
	}

	private void installPermitPacketsToFromDhcp(String nodeId, InstanceIdentifier<FlowCapableNode> node) {
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);
		FlowBuilder flowBuilder = FlowUtils.createToDhcpServerMatchGoToNextTableFlow(BaseappConstants.SDNMUD_RULES_TABLE,
				flowCookie, flowId);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

		// DHCP is local so both directions are installed on the CPE node.
		flowId = InstanceIdentifierUtils.createFlowId(nodeId);
		flowBuilder = FlowUtils.createFromDhcpServerMatchGoToNextTableFlow(BaseappConstants.SDNMUD_RULES_TABLE,
				flowCookie, flowId);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
	}

	public void installSendToControllerFlows(String nodeUri) {
		InstanceIdentifier<FlowCapableNode> nodePath = this.sdnmudProvider.getNode(nodeUri);
		if (nodePath == null) {
			LOG.info("Node not seen -- not installing flow ");
			return;
		}
		installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, nodePath);
		installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, nodePath);
	}

	private void installInitialFlows(InstanceIdentifier<FlowCapableNode> nodePath) {
		if (nodePath == null) {
			LOG.info("FlowCapableNode not found. Not installing inital flows");
			return;
		}
		String nodeUri = InstanceIdentifierUtils.getNodeUri(nodePath);

		if (sdnmudProvider.getTopology() != null && sdnmudProvider.isCpeNode(nodeUri)) {
			installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, nodePath);
			installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, nodePath);
		}
		
		installUnconditionalGoToTable(nodePath, BaseappConstants.SDNMUD_RULES_TABLE);

		/*
		 * Install an unconditional packet drop in the DROP_TABLE (this is 
		 * where MUD packets that do not match go. The default action is to
		 * drop the packet. This is a placeholder for  subsequent packet
		 * inspection.
		 */

		installUnditionalDropPacket(nodeUri, nodePath, BaseappConstants.DROP_TABLE);

		// All devices may access DHCP (default rule).
		installPermitPacketsToFromDhcp(nodeUri, nodePath);

		// All devices may access DNS and NTP.
		try {
			MudFlowsInstaller.installAllowToDnsAndNtpFlowRules(sdnmudProvider, nodePath);
		} catch (Exception ex) {
			LOG.error("installFlows : Exception installing default flows ", ex);
		}
	}

	private void installDefaultFlows(String nodeUri, InstanceIdentifier<FlowCapableNode> nodePath) {

		PacketInDispatcher packetInDispatcher = new PacketInDispatcher(nodeUri, nodePath, sdnmudProvider);
		ListenerRegistration<PacketInDispatcher> registration = sdnmudProvider.getNotificationService()
				.registerNotificationListener(packetInDispatcher);
		packetInDispatcher.setListenerRegistration(registration);
		this.installInitialFlows(nodePath);
	}

	public synchronized void installDefaultFlows() {
		for (InstanceIdentifier<FlowCapableNode> node : this.pendingNodes) {
			String nodeId = InstanceIdentifierUtils.getNodeUri(node);
			if (sdnmudProvider.isCpeNode(nodeId)) {
				this.installDefaultFlows(nodeId, node);
			}
		}
	}

	/**
	 * This gets invoked when a switch appears and connects.
	 * 
	 * @param nodePath
	 *            -- the node path.
	 *
	 */
	public synchronized void onFlowCapableSwitchAppeared(InstanceIdentifier<FlowCapableNode> nodePath) {

		String nodeUri = InstanceIdentifierUtils.getNodeUri(nodePath);

		LOG.info("onFlowCapableSwitchAppeared");
		// The URI identifies the node instance.
		LOG.info("node URI " + nodeUri + " nodePath " + nodePath);
		// Stash away the URI to node path so we can reference it later.

		this.sdnmudProvider.putInUriToNodeMap(nodeUri, nodePath);

		if (this.sdnmudProvider.getTopology() == null) {
			LOG.info("put in pending node list");
			this.pendingNodes.add(nodePath);
		} else if (sdnmudProvider.isCpeNode(nodeUri)) {
			// If this is a CPE node install the default permits.
			this.installDefaultFlows(nodeUri, nodePath);
		}

	}

	/**
	 * Deal with disconnection of the switch.
	 * 
	 * @param nodePath
	 *            - the instance id of the disconnecting switch.
	 */

	public void onFlowCapableSwitchDisappeared(InstanceIdentifier<FlowCapableNode> nodePath) {
		String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
		LOG.info("onFlowCapableSwitchDisappeared");
		// The URI identifies the node instance.
		LOG.info("node URI " + nodeUri);
		// Remove the node URI from the uriToNodeMap.
		this.sdnmudProvider.removeNode(nodeUri);
		// Remove the node URI from our switches table.
	}

}
