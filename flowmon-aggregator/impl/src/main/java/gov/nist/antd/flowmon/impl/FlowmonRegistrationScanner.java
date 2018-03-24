/*
 * Copyright © 2017 None.
 *
 * This program and the accompanying materials are in the public domain.
 * 
 * 
 */

package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.TimerTask;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.links.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

/**
 * Scan for Flowmonitor registrations and install flow rules.
 * 
 * @author mranga
 *
 */
public class FlowmonRegistrationScanner extends TimerTask {

	static final Logger LOG = LoggerFactory.getLogger(FlowmonRegistrationScanner.class);

	private FlowmonProvider flowmonProvider;

	public FlowmonRegistrationScanner(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}

	private static String getManufacturer(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.PASSTHRU))
			return null;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[0];

	}

	private static String getFlowType(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.PASSTHRU))
			return FlowmonConstants.PASSTHRU;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[1];
	}

	private void installStripMplsTagAndGoToL2Switch(InstanceIdentifier<FlowCapableNode> nodePath, FlowId flowId,
			Short stripMplsRuleTable, int label) {

		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowId.getValue());

		FlowBuilder flow = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId, stripMplsRuleTable,
				label);

		this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, nodePath);

	}

	private BigInteger createSrcModelMetadata(String mudUri) {
		int modelId = flowmonProvider.getModelId(mudUri);
		return BigInteger.valueOf(modelId).shiftLeft(FlowmonConstants.SRC_MODEL_SHIFT);
	}

	private  BigInteger createDstModelMetadata(String mudUri) {
		return BigInteger.valueOf(flowmonProvider.getModelId(mudUri))
				.shiftLeft(FlowmonConstants.DST_MODEL_SHIFT);
	}

	private  BigInteger createSrcManufacturerMetadata(String manufacturer) {
		return BigInteger.valueOf(flowmonProvider.getManfuacturerId(manufacturer))
				.shiftLeft(FlowmonConstants.SRC_MANUFACTURER_SHIFT);
	}

	private  BigInteger createDstManufacturerModelMetadata(String mudUri) {
		int manufacturerId = flowmonProvider.getManfuacturerId(InstanceIdentifierUtils.getAuthority(mudUri));
		int modelId = flowmonProvider.getModelId(mudUri);
		return BigInteger.valueOf(manufacturerId).shiftLeft(FlowmonConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(FlowmonConstants.DST_MODEL_SHIFT));

	}

	private  BigInteger createDstManufacturerMetadata(String mudUri) {
		
		int manufacturerId = flowmonProvider.getManfuacturerId(mudUri);
		return BigInteger.valueOf(manufacturerId).shiftLeft(FlowmonConstants.DST_MANUFACTURER_SHIFT);
	}

	@Override
	public void run() {
		for (FlowmonConfigData flowmonConfigData : flowmonProvider.getFlowmonConfigs()) {
			List<Uri> flowSpec = flowmonConfigData.getFlowSpec();
			for (Link link : this.flowmonProvider.getTopology().getLink()) {
				String flowmonNodeId = link.getVnfSwitch().getValue();
				InstanceIdentifier<FlowCapableNode> flowmonNode = this.flowmonProvider.getNode(flowmonNodeId);
				if (flowmonNode == null) {
					LOG.info("IDS node not found");
					return;
				}
				FlowCommitWrapper flowCommitWrapper = flowmonProvider.getFlowCommitWrapper();
				this.flowmonProvider.garbageCollectFlowmonRegistrationRecords();
				String flowmonPorts = flowmonProvider.getFlowmonOutputPort(flowmonNodeId);

				// No IDS ports found.

				if (flowmonPorts == null) {
					LOG.debug("No IDS registrations found for " + flowmonNodeId);
					for (Uri uri : flowSpec) {
						for (InstanceIdentifier<FlowCapableNode> node : flowmonProvider.getCpeNodes()) {
							flowCommitWrapper.deleteFlows(node, uri.getValue(), BaseappConstants.PASS_THRU_TABLE, null);
							flowCommitWrapper.deleteFlows(flowmonNode, uri.getValue(),
									BaseappConstants.SDNMUD_RULES_TABLE, null);
						}
					}
					return;
				}

				LOG.debug("flowmonRegistration: found for flowmonNodeId : " + flowmonNodeId);

				int duration = flowmonConfigData.getFilterDuration();

				LOG.debug("flowmonRegistration: duration = " + duration);

				for (Uri uri : flowSpec) {
					String flowType = getFlowType(uri);

					LOG.info("Install flow rules for flow type " + flowType);

					int mplsTag = InstanceIdentifierUtils.getFlowHash(uri.getValue());

					/* Divert flow in the VNF switch. */

					FlowId flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());

					if (flowType.equals(FlowmonConstants.LOCAL)) {
						/*
						 * If the flowType is LOCAL then we do not route the
						 * packets further when then ge to the VNF switch.
						 */
						flowCommitWrapper.deleteFlows(flowmonNode, uri.getValue(), BaseappConstants.SDNMUD_RULES_TABLE,
								null);
						
						FlowCookie flowCookie = FlowmonConstants.PACKET_DIVERSION_FLOW_COOKIE;

						FlowBuilder flow = FlowUtils.createOnMplsTagMatchPopMplsTagsAndSendToPort(flowCookie, flowId,
								InstanceIdentifierUtils.getFlowHash(uri.getValue()), flowmonNodeId, flowmonPorts,
								BaseappConstants.STRIP_MPLS_RULE_TABLE, duration);

						// Install the flow.
						flowCommitWrapper.writeFlow(flow, flowmonNode);

					} else if (flowType.equals(FlowmonConstants.REMOTE)) {
						
						// Send the inbound packet to the controller.
						// When it gets to the controller we extract the src and destination mac address
						// and create a MAC to MAC flow for packet diversion to the IDS>
						// The flow cookie stores the mpls tag.
						
						FlowCookie flowCookie = new FlowCookie(BigInteger.valueOf(mplsTag));
						FlowBuilder fb = FlowUtils.createOnMplsMatchSendToControllerGoToTable(flowCookie, flowId, mplsTag,
								BaseappConstants.PASS_THRU_TABLE, duration);
						flowCommitWrapper.writeFlow(fb, flowmonNode);
					} 

					/* get the cpe nodes corresponding to this VNF node */
					InstanceIdentifier<FlowCapableNode> node = flowmonProvider.getCpeNode(flowmonNodeId);

					if (node != null) {

						String sourceNodeUri = InstanceIdentifierUtils.getNodeUri(node);
						if (sourceNodeUri == null) {
							LOG.error("flowmonRegistration: Cannot find source node URI");
							continue;
						}
						if (flowmonProvider.isCpeNode(sourceNodeUri)) {

							flowmonProvider.getFlowCommitWrapper().deleteFlows(node, uri.getValue(),
									BaseappConstants.PASS_THRU_TABLE, null);

							String outputPortUri = flowmonProvider.getFlowmonOutputPort(sourceNodeUri);
							if (outputPortUri == null) {
								LOG.info("Cannot find output port URI");
								continue;
							}
							LOG.debug("flowmonRegistration: outputPortUri " + outputPortUri);
							if (flowType.equals(FlowmonConstants.LOCAL)) {
								/*
								 * This is the case when the IDS is interested
								 * in seeing local device to device flows that
								 * are passed by MUD. The IDS is intertested in
								 * monitoring local communications between
								 * devices.
								 */
								flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
								/*
								 * Match on metadata, set the MPLS tag and send
								 * to NPE switch and go to l2 switch.
								 */
								flowCommitWrapper.deleteFlows(node, uri.getValue(), BaseappConstants.PASS_THRU_TABLE, null);

								FlowCookie flowCookie = FlowmonConstants.PACKET_DIVERSION_FLOW_COOKIE;

								FlowBuilder fb = FlowUtils.createMetadataMatchSetMplsTagSendToPortAndGoToTable(
										flowCookie, flowId, BaseappConstants.PASS_THRU_TABLE, mplsTag, outputPortUri,
										duration);
								// Write the flow to the data store.
								flowCommitWrapper.writeFlow(fb, node);
								// Install a flow to strip the mpls label.
								flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
								// Strip MPLS tag and go to the L2 switch.
								installStripMplsTagAndGoToL2Switch(node, flowId, BaseappConstants.STRIP_MPLS_RULE_TABLE,
										mplsTag);
							}
						}
					}

				}
			}

		}

	}

}
