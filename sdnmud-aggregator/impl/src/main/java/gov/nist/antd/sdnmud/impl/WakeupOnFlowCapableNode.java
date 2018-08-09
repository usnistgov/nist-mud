
/*
 * Copyright (c) Public Domain
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.Mud;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;
import gov.nist.antd.baseapp.impl.FlowCommitWrapper;

/**
 * Class that gets invoked when a switch connects.
 *
 * @author mranga@nist.gov
 *
 */

public class WakeupOnFlowCapableNode implements DataTreeChangeListener<FlowCapableNode> {
	private static final Logger LOG = LoggerFactory.getLogger(WakeupOnFlowCapableNode.class);

	private SdnmudProvider sdnmudProvider;


	public WakeupOnFlowCapableNode(SdnmudProvider sdnMudProvider) {
		this.sdnmudProvider = sdnMudProvider;
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

	private void installSendIpPacketToControllerFlow(String nodeUri, short tableId,
			InstanceIdentifier<FlowCapableNode> node, BigInteger metadata, BigInteger metadataMask) {
		FlowId flowId = IdUtils.createFlowId(nodeUri + ":sendToController");
		FlowCookie flowCookie = SdnMudConstants.SEND_TO_CONTROLLER_FLOW_COOKIE;

		boolean forwardFlag = this.sdnmudProvider.getSdnmudConfig() != null
				&& this.sdnmudProvider.getSdnmudConfig().isRelaxedAcl();

		FlowBuilder fb = FlowUtils.createIpMatchSendPacketToControllerFlow(metadata, metadataMask, forwardFlag, tableId,
				flowId, flowCookie);

		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	/**
	 * @param nodePath
	 * @param srcDeviceManufacturerStampTable
	 * @param metadata
	 * @param metadataMask
	 */
	private void installToDhcpFlow(InstanceIdentifier<FlowCapableNode> nodePath, Short tableId, BigInteger metadata,
			BigInteger metadataMask) {
		String nodeUri = IdUtils.getNodeUri(nodePath);
		FlowId flowId = IdUtils.createFlowId(nodeUri + ":bypassDhcp");
		FlowCookie flowCookie = SdnMudConstants.BYPASS_DHCP_FLOW_COOKIE;
		FlowBuilder fb = FlowUtils.createToDhcpServerMatchGoToNextTableFlow(tableId, flowCookie, flowId, false);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, nodePath);
	}

	private void installUnconditionalGoToTable(InstanceIdentifier<FlowCapableNode> node, short table) {
		FlowId flowId = IdUtils.createFlowId(IdUtils.getNodeUri(node));
		FlowCookie flowCookie = SdnMudConstants.UNCLASSIFIED_FLOW_COOKIE;
		FlowBuilder unconditionalGoToNextFlow = FlowUtils.createUnconditionalGoToNextTableFlow(table, flowId,
				flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(unconditionalGoToNextFlow, node);
	}

	private void installUnditionalDropPacket(String nodeId, InstanceIdentifier<FlowCapableNode> nodePath,
			Short dropPacketTable) {
		FlowCookie flowCookie = IdUtils.createFlowCookie(nodeId);
		FlowId flowId = IdUtils.createFlowId(nodeId);

		FlowBuilder flow = FlowUtils.createUnconditionalDropPacketFlow(dropPacketTable, flowId, flowCookie);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, nodePath);
	}

	public void installSendToControllerFlows(String nodeUri) {
		InstanceIdentifier<FlowCapableNode> nodePath = this.sdnmudProvider.getNode(nodeUri);
		if (nodePath == null) {
			LOG.info("Node not seen -- not installing flow ");
			return;
		}
		BigInteger metadata = (BigInteger.valueOf(IdUtils.getManfuacturerId(SdnMudConstants.UNKNOWN))
				.shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT))
						.or(BigInteger.valueOf(IdUtils.getModelId(SdnMudConstants.UNKNOWN))
								.shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

		installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, nodePath,
				metadata, metadataMask);
		installToDhcpFlow(nodePath, BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, metadata, metadataMask);

		metadata = BigInteger.valueOf(IdUtils.getManfuacturerId(SdnMudConstants.UNKNOWN))
				.shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(IdUtils.getModelId(SdnMudConstants.UNKNOWN))
						.shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));

		metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);

		installSendIpPacketToControllerFlow(nodeUri, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, nodePath,
				metadata, metadataMask);

		installToDhcpFlow(nodePath, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, metadata, metadataMask);

	}

	public void installInitialFlows(String nodeUri) {

		InstanceIdentifier<FlowCapableNode> nodePath = sdnmudProvider.getNode(nodeUri);

		assert nodePath != null;
		assert sdnmudProvider.isCpeNode(nodeUri);

		LOG.info("installInitialFlows: cpeNodeId = " + nodeUri);

		installSendToControllerFlows(nodeUri);

		installUnconditionalGoToTable(nodePath, BaseappConstants.SDNMUD_RULES_TABLE);

		/*
		 * Install an unconditional packet drop in the DROP_TABLE (this is where
		 * MUD packets that do not match go. The default action is to drop the
		 * packet. This is a placeholder for subsequent packet inspection.
		 */

		installUnditionalDropPacket(nodeUri, nodePath, BaseappConstants.DROP_TABLE);

		// All devices may access DNS and NTP. The DHCP rule bumps the packet up
		// to the controller.
		try {

			MudFlowsInstaller mudFlowsInstaller = sdnmudProvider.getMudFlowsInstaller();
			mudFlowsInstaller.installPermitPacketsToFromDhcp(nodePath);
			mudFlowsInstaller.installAllowToDnsAndNtpFlowRules(nodePath);
			if (sdnmudProvider.getSdnmudConfig() != null && sdnmudProvider.getSdnmudConfig().isRelaxedAcl()) {
				mudFlowsInstaller.installUnknownDestinationPassThrough(nodePath);
			} else {
				LOG.info("sdnmudConfig : isRelaxedAcl is false or sdnmudconfig not found");
			}

		} catch (RuntimeException ex) {
			LOG.error("installFlows : Exception installing default flows ", ex);
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

		String nodeUri = IdUtils.getNodeUri(nodePath);

		LOG.info("onFlowCapableSwitchAppeared " + nodeUri);
		// Stash away the URI to node path so we can reference it later.
		this.sdnmudProvider.putInUriToNodeMap(nodeUri, nodePath);
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
