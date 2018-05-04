package gov.nist.antd.flowmon.impl;

import java.util.ArrayList;
import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

public class WakeupOnFlowCapableNode implements DataTreeChangeListener<FlowCapableNode> {
	private static final Logger LOG = LoggerFactory.getLogger(WakeupOnFlowCapableNode.class);

	private FlowmonProvider flowmonProvider;

	private ArrayList<InstanceIdentifier<FlowCapableNode>> pendingNodes = new ArrayList<>();

	// PacketInDispatcher(String nodeId, InstanceIdentifier<FlowCapableNode>
	// node,
	// IMdsalApiManager mdsalApiManager,
	// FlowCommitWrapper flowCommitWrapper, flowmonProvider flowmonProvider)
	//
	public WakeupOnFlowCapableNode(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
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

	private static String getFlowType(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.PASSTHRU))
			return FlowmonConstants.PASSTHRU;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[1];
	}

	private static String getManufacturer(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.UNCLASSIFIED))
			return FlowmonConstants.UNCLASSIFIED;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[0];

	}


	/**
	 * Flow to send the IDS HELLO to the controller if it has not already been
	 * installed.
	 *
	 * @param nodeUri
	 * @param node
	 */
	private synchronized void installSendFlowmonHelloToControllerFlows(InstanceIdentifier<FlowCapableNode> node, MacAddress macAddress) {
		String nodeId = InstanceIdentifierUtils.getNodeUri(node);
		if (flowmonProvider.isVnfSwitch(nodeId)) {
			FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId + ":flowmon");
			FlowCookie flowCookie = FlowmonConstants.IDS_REGISTRATION_FLOW_COOKIE;
			LOG.info("IDS_REGISTRATION_FLOW_COOKIE " + flowCookie.getValue().toString(16));
			FlowBuilder fb = FlowUtils.createSrcMacMatchSendPacketToController(macAddress, BaseappConstants.FIRST_TABLE, 0, flowId,flowCookie);
			flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
		}

	}

	public void installSendIpPacketToControllerFlow(String nodeUri, short tableId,
			InstanceIdentifier<FlowCapableNode> node) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":sendToController");
		FlowCookie flowCookie = FlowmonConstants.SEND_TO_CONTROLLER_FLOW_COOKIE;
		FlowBuilder fb = FlowUtils.createIpMatchSendPacketToControllerFlow(tableId, flowId, flowCookie);
		this.flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}



	public void installDefaultFlows() {
		if (flowmonProvider.getFlowmonConfigs().isEmpty()) {
			LOG.info("WakeupOnFlowCapableNode : installDefaultFlows : configuration is incomplete");
			return;
		}
		for (InstanceIdentifier<FlowCapableNode> flowCapableNode : this.pendingNodes) {
			String nodeUri = InstanceIdentifierUtils.getNodeUri(flowCapableNode);
			LOG.info("WakeupOnFlowCapableNode : installDefaultFlows " + nodeUri + " isVnfSwitch = "
					+ this.flowmonProvider.isVnfSwitch(nodeUri));
			if (this.flowmonProvider.isVnfSwitch(nodeUri)) {
				//this.installSendFlowmonHelloToControllerFlows(flowCapableNode);
				this.installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE,
						flowCapableNode);
			}
		}
		this.pendingNodes.clear();
	}

	/**
	 * This gets invoked when a switch appears and connects.
	 *
	 * @param nodePath
	 *            -- the node path.
	 *
	 */
	private synchronized void onFlowCapableSwitchAppeared(InstanceIdentifier<FlowCapableNode> nodePath) {

		String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();

		LOG.info("WakeupOnFlowCapableNode : onFlowCapableSwitchAppeared");
		// The URI identifies the node instance.
		LOG.info("node URI " + nodeUri );
		// Stash away the URI to node path so we can reference it later.
		this.flowmonProvider.putInUriToNodeMap(nodeUri, nodePath);
		if (flowmonProvider.getFlowmonConfigs().isEmpty()) {
			LOG.info("WakeupOnFlowCapableNode : adding node to pending nodes");
			this.pendingNodes.add(nodePath);
			return;
		}
		if (this.flowmonProvider.isVnfSwitch(nodeUri)) {
			//this.installSendFlowmonHelloToControllerFlows(nodePath);
			this.installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE,
					nodePath);
		}

	}

	/**
	 * Deal with disconnection of the switch.
	 *
	 * @param nodePath
	 *            - the instance id of the disconnecting switch.
	 */

	private synchronized void onFlowCapableSwitchDisappeared(InstanceIdentifier<FlowCapableNode> nodePath) {
		String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
		LOG.info("onFlowCapableSwitchDisappeared");
		// The URI identifies the node instance.
		LOG.info("node URI " + nodeUri);
		// Remove the node URI from the uriToNodeMap.
		// Remove the node URI from our switches table.
	}

}
