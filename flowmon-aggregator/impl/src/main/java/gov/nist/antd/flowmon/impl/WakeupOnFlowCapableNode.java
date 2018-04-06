package gov.nist.antd.flowmon.impl;

import java.util.ArrayList;
import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.flowmon.config.FlowmonConfigData;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
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

	public synchronized void installSetMplsTagFlows(InstanceIdentifier<FlowCapableNode> node) {

		String nodeId = InstanceIdentifierUtils.getNodeUri(node);
		/* get the cpe nodes corresponding to this VNF node */
		for (FlowmonConfigData flowmonConfigData : flowmonProvider.getFlowmonConfigs()) {

			String vnfSwitch = flowmonConfigData.getFlowmonNode().getValue();
			//Collection<String> cpeSwitches = flowmonProvider.getCpeNodes(npeSwitch);
			String cpeSwitch = flowmonProvider.getCpeNodeId(vnfSwitch);
		

			if (cpeSwitch == null) {
				LOG.error("CPE switches not found for vnfSwitch");
				return;
			}

			LOG.info("installSetMplsTagFlows : nodeId " + nodeId + " Switch " + vnfSwitch);

			for (Uri uri : flowmonConfigData.getFlowSpec()) {
				if (cpeSwitch.equals(nodeId)) {

					flowmonProvider.getFlowCommitWrapper().deleteFlows(node, uri.getValue(),
							BaseappConstants.PASS_THRU_TABLE, null);
					String flowType = getFlowType(uri);

					if (flowType.equals(FlowmonConstants.REMOTE)) {
						/*
						 * IDS is configured to look for remote flows.
						 */
						FlowId flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
						/*
						 * Match on metadata, set the MPLS tag and send to NPE
						 * switch and go to l2 switch.
						 */

						FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri.getValue());

						int mplsTag = InstanceIdentifierUtils.getFlowHash(uri.getValue());

						FlowBuilder fb = FlowUtils.createMetadataMatchMplsTagGoToTableFlow(flowCookie, flowId,
								BaseappConstants.PASS_THRU_TABLE, mplsTag, 0);
						// Write the flow to the data store.
						flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
						
						
						// Install a flow to strip the mpls label.
						flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
						// Strip MPLS tag and go to the L2 switch.
						FlowBuilder flow = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId,
								BaseappConstants.STRIP_MPLS_RULE_TABLE, mplsTag);
								

						this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node); 

					} else {
						LOG.error("Flow diversion not implemented for this flow type " + uri.getValue());
					}
				} else if (nodeId.equals(vnfSwitch)) {
					LOG.info("setMplsTagFlows : " + nodeId  + " isVnfSwitch true");
					FlowId flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
					int mplsTag = InstanceIdentifierUtils.getFlowHash(uri.getValue());
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri.getValue());
					// Strip MPLS tag and go to the L2 switch.
					FlowBuilder flow = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId,
							BaseappConstants.STRIP_MPLS_RULE_TABLE, mplsTag);

					this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);

				}
			}
		}

	}

	/**
	 * Flow to send the IDS HELLO to the controller if it has not already been
	 * installed.
	 * 
	 * @param nodeUri
	 * @param node
	 */
	private synchronized void installSendFlowmonHelloToControllerFlows(InstanceIdentifier<FlowCapableNode> node) {
		String nodeId = InstanceIdentifierUtils.getNodeUri(node);
		if (flowmonProvider.isVnfSwitch(nodeId)) {
			FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId + ":flowmon");
			FlowCookie flowCookie = FlowmonConstants.IDS_REGISTRATION_FLOW_COOKIE;
			LOG.info("IDS_REGISTRATION_FLOW_COOKIE " + flowCookie.getValue().toString(16));
			FlowBuilder fb = FlowUtils.createDestIpMatchSendToController(FlowmonConstants.IDS_REGISTRATION_ADDRESS,
					FlowmonConstants.IDS_REGISTRATION_PORT, BaseappConstants.FIRST_TABLE, flowCookie, flowId,
					FlowmonConstants.IDS_REGISTRATION_METADATA);
			flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
		}

	}

	public void installDefaultFlows() {
		if (flowmonProvider.getTopology() == null || flowmonProvider.getFlowmonConfigs().isEmpty()) {
			LOG.info("WakeupOnFlowCapableNode : installDefaultFlows : configuration is incomplete");
			return;
		}
		for (InstanceIdentifier<FlowCapableNode> flowCapableNode : this.pendingNodes) {
			String nodeUri = InstanceIdentifierUtils.getNodeUri(flowCapableNode);
			LOG.info("WakeupOnFlowCapableNode : installDefaultFlows " + nodeUri + " isVnfSwitch = "
					+ this.flowmonProvider.isVnfSwitch(nodeUri) + " iscpeSwitch = "
					+ this.flowmonProvider.isCpeNode(nodeUri));
			if (this.flowmonProvider.isVnfSwitch(nodeUri)) {
				this.installSendFlowmonHelloToControllerFlows(flowCapableNode);
			}

			if (this.flowmonProvider.isVnfSwitch(nodeUri) || this.flowmonProvider.isCpeNode(nodeUri)) {
				this.installSetMplsTagFlows(flowCapableNode);
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
		LOG.info("node URI " + nodeUri + " nodePath " + nodePath);
		// Stash away the URI to node path so we can reference it later.
		this.flowmonProvider.putInUriToNodeMap(nodeUri, nodePath);
		if (flowmonProvider.getTopology() == null || flowmonProvider.getFlowmonConfigs().isEmpty()) {
			LOG.info("WakeupOnFlowCapableNode : adding node to pending nodes");
			this.pendingNodes.add(nodePath);
			return;
		}
		if (this.flowmonProvider.isCpeNode(nodeUri) || this.flowmonProvider.isVnfSwitch(nodeUri)) {
			if (this.flowmonProvider.isVnfSwitch(nodeUri)) {
				this.installSendFlowmonHelloToControllerFlows(nodePath);
			}
			this.installSetMplsTagFlows(nodePath);
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
