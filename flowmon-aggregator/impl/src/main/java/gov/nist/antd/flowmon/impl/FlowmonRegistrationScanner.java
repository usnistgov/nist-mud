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
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.flowmon.config.FlowmonConfigData;
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

		if (flowSpec.getValue().equals(FlowmonConstants.UNCLASSIFIED))
			return FlowmonConstants.UNCLASSIFIED;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[0];

	}

	private static String getFlowType(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.UNCLASSIFIED))
			return FlowmonConstants.UNCLASSIFIED;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[1];
	}

	public synchronized void installDivertToIdsFlow(InstanceIdentifier<FlowCapableNode> node, String flowmonPorts,
			int duration) {

		String nodeId = InstanceIdentifierUtils.getNodeUri(node);
		/* get the cpe nodes corresponding to this VNF node */
		for (FlowmonConfigData flowmonConfigData : flowmonProvider.getFlowmonConfigs()) {

			String vnfSwitch = flowmonConfigData.getFlowmonNode().getValue();

			LOG.info("installDivertToIdsFlow : nodeId " + nodeId + " Switch " + vnfSwitch);
			if (nodeId.equals(vnfSwitch)) {
				for (Uri uri : flowmonConfigData.getFlowSpec()) {
					String manufacturer = getManufacturer(uri);
					LOG.info("installDivertToIdsFlow : manufacturer " + manufacturer);

					String flowType = getFlowType(uri);
					if (flowType.equals(FlowmonConstants.REMOTE) || flowType.equals(FlowmonConstants.UNCLASSIFIED)) {
						FlowId flowId = InstanceIdentifierUtils.createFlowId(vnfSwitch);
						FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri.getValue());
						// Strip MPLS tag and go to the L2 switch.
						BigInteger metadata = BigInteger.valueOf(InstanceIdentifierUtils.getFlowHash(manufacturer))
								.shiftLeft(FlowmonConstants.SRC_MANUFACTURER_SHIFT);

						FlowBuilder flow = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId,
								metadata, FlowmonConstants.SRC_MANUFACTURER_MASK, FlowmonConstants.DIVERT_TO_FLOWMON_TABLE,
								flowmonPorts, flowmonConfigData.getFilterDuration());
						this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);

						flowId = InstanceIdentifierUtils.createFlowId(vnfSwitch);

						metadata = BigInteger.valueOf(InstanceIdentifierUtils.getFlowHash(manufacturer))
								.shiftLeft(FlowmonConstants.DST_MANUFACTURER_SHIFT);

						flow = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId, metadata,
								FlowmonConstants.DST_MANUFACTURER_MASK, FlowmonConstants.DIVERT_TO_FLOWMON_TABLE, flowmonPorts,
								flowmonConfigData.getFilterDuration());
						
						this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);
					}
				}
			}
		}
	}

	@Override
	public void run() {
		LOG.info("FlowmonRegistrationScanner : starting");
		if (this.flowmonProvider.getTopology() == null) {
			LOG.debug("Topology not fouond ");
			return;
		}

		for (FlowmonConfigData flowmonConfigData : flowmonProvider.getFlowmonConfigs()) {
			for (Link link : this.flowmonProvider.getTopology().getLink()) {
				String flowmonNodeId = link.getVnfSwitch().getValue();
				InstanceIdentifier<FlowCapableNode> flowmonNode = this.flowmonProvider.getNode(flowmonNodeId);
				if (flowmonNode == null) {
					LOG.debug("IDS node not found");
					return;
				}
				this.flowmonProvider.garbageCollectFlowmonRegistrationRecords();
				String flowmonPorts = flowmonProvider.getFlowmonOutputPort(flowmonNodeId);
				
				LOG.info("flowmonPorts : "  + flowmonPorts);

				// No IDS ports found.

				if (flowmonPorts == null) {
					LOG.debug("No IDS registrations found for " + flowmonNodeId);
					return;
				}

				LOG.debug("flowmonRegistration: found for flowmonNodeId : " + flowmonNodeId);

				int duration = flowmonConfigData.getFilterDuration();

				LOG.debug("flowmonRegistration: duration = " + duration);

				this.installDivertToIdsFlow(flowmonNode, flowmonPorts, duration);
			}

		}

	}

}
