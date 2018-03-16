/*
 * This code is released to the public domain in accordance with the following disclaimer:
 * 
 * "This software was developed at the National Institute of Standards 
 * and Technology by employees of the Federal Government in the course of 
 * their official duties. Pursuant to title 17 Section 105 of the United 
 * States Code this software is not subject to copyright protection and is 
 * in the public domain. It is an experimental system. NIST assumes no responsibility 
 * whatsoever for its use by other parties, and makes no guarantees, expressed or 
 * implied, about its quality, reliability, or any other characteristic. We would 
 * appreciate acknowledgement if the software is used. This software can be redistributed 
 * and/or modified freely provided that any derivative works bear 
 * some notice that they are derived from it, and any modified versions bear some 
 * notice that they have been modified."
 */

package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packet in dispatcher that gets invoked on flow table miss when a packet
 * is sent up to the controller.
 * 
 * @author mranga@nist.gov
 *
 */
public class PacketInDispatcher implements PacketProcessingListener {

	private SdnmudProvider sdnmudProvider;

	private String nodeId;

	private InstanceIdentifier<FlowCapableNode> node;

	private ListenerRegistration<PacketInDispatcher> listenerRegistration;

	private static final Logger LOG = LoggerFactory.getLogger(PacketInDispatcher.class);

	/**
	 * PacketIn dispatcher. Gets called when packet is received.
	 * 
	 * @param sdnMudHandler
	 * @param mdsalApiManager
	 * @param flowCommitWrapper
	 * @param sdnmudProvider
	 */
	public PacketInDispatcher(String nodeId, InstanceIdentifier<FlowCapableNode> node, SdnmudProvider sdnmudProvider) {
		this.node = node;
		this.nodeId = nodeId;
		LOG.info("PacketInDispatcher: " + nodeId);
		this.sdnmudProvider = sdnmudProvider;
	}

	/**
	 * Get the Node for a given MAC address.
	 * 
	 * @param macAddress
	 * @param node
	 * @return
	 */
	public InstanceIdentifier<FlowCapableNode> getNode() {
		return node;
	}

	private void transmitPacket(byte[] payload, String outputPortUri) {
		TransmitPacketInputBuilder tpib = new TransmitPacketInputBuilder().setPayload(payload)
				.setBufferId(OFConstants.OFP_NO_BUFFER);
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));

		output.setOutputNodeConnector(new Uri(outputPortUri));
		ActionBuilder ab = new ActionBuilder();
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(1);

		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());
		tpib.setAction(actionList);

		sdnmudProvider.getPacketProcessingService().transmitPacket(tpib.build());
	}

	private boolean isLocalAddress(String ipAddress) {
		boolean isLocalAddress = false;
		if (sdnmudProvider.getControllerclassMappingDataStoreListener().getLocalNetworks(nodeId) != null) {
			for (String localNetworkStr : sdnmudProvider.getControllerclassMappingDataStoreListener()
					.getLocalNetworks(nodeId)) {
				LOG.debug("localNetworkStr = " + localNetworkStr);
				String[] pieces = localNetworkStr.split("/");
				int prefixLength = new Integer(pieces[1]) / 8;

				String[] pieces1 = pieces[0].split("\\.");
				String prefix = "";

				for (int i = 0; i < prefixLength; i++) {
					prefix = prefix + pieces1[i] + ".";
				}
				LOG.debug("prefix = " + prefix);
				if (ipAddress.startsWith(prefix)) {
					isLocalAddress = true;
					break;
				}
			}
		}
		return isLocalAddress;
	}

	private static void installSrcMacMatchStampManufacturerModelFlowRules(MacAddress srcMac, boolean isLocalAddress,
			String mudUri, SdnmudProvider sdnmudProvider, InstanceIdentifier<FlowCapableNode> node) {
		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		String nodeSuffix = "?" + InstanceIdentifierUtils.getNodeUri(node);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(srcMac.getValue() + nodeSuffix);
		int flag = 0;
		if (isLocalAddress)
			flag = 1;

		LOG.debug("installStampSrcManufacturerModelFlowRules : dstMac = " + srcMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " mudUri " + mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(flag).shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK)
				.or(SdnMudConstants.SRC_NETWORK_MASK);

		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("stamp-src-mac-manufactuer-model-flow");

		FlowBuilder fb = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(srcMac, metadata, metadataMask,
				SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);

		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private static void installSrcMacMatchStampLocalAddressFlowRules(MacAddress srcMac, SdnmudProvider sdnmudProvider,
			InstanceIdentifier<FlowCapableNode> node) {
		String nodeIdSuffix = "?nodeId=" + InstanceIdentifierUtils.getNodeUri(node);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(SdnMudConstants.UNCLASSIFIED+ nodeIdSuffix);

		BigInteger metadata = BigInteger.valueOf(1).shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT);

		BigInteger metadataMask = SdnMudConstants.SRC_NETWORK_MASK;

		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(SdnMudConstants.PASSTHRU);

		FlowBuilder fb = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(srcMac, metadata, metadataMask,
				SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);

		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	public static void installDstMacMatchStampManufacturerModelFlowRules(MacAddress dstMac, boolean isLocalAddress,
			String mudUri, SdnmudProvider sdnmudProvider, InstanceIdentifier<FlowCapableNode> node) {

		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		int flag = 0;
		if (isLocalAddress)
			flag = 1;

		LOG.debug("installStampDstManufacturerModelFlowRules : dstMac = " + dstMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " mudUri " + mudUri);
		
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri + "?nodeId=" + InstanceIdentifierUtils.getNodeUri(node));
		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(flag).shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		BigInteger metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK)
				.or(SdnMudConstants.DST_NETWORK_MASK);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("stamp-dst-mac-manufactuer-model-flow");

		FlowBuilder fb = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(dstMac, metadata, metadataMask,
				SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId,
				flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}
	
	public void setListenerRegistration(ListenerRegistration<PacketInDispatcher> registration) {
		this.listenerRegistration = registration;
		
	}

	// installDstMacMatchStampDstLocalAddressFlowRules
	private static void installDstMacMatchStampDstLocalAddressFlowRules(MacAddress dstMac, SdnmudProvider sdnmudProvider,
			InstanceIdentifier<FlowCapableNode> node) {
		String nodeSuffix = "?" + InstanceIdentifierUtils.getNodeUri(node);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(SdnMudConstants.UNCLASSIFIED+ nodeSuffix);
		BigInteger metadata = BigInteger.valueOf(1).shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT);
		BigInteger metadataMask = SdnMudConstants.DST_NETWORK_MASK;
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(SdnMudConstants.PASSTHRU);

		FlowBuilder fb = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(dstMac, metadata, metadataMask,
				SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId,
				flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	@Override
	public void onPacketReceived(PacketReceived notification) {

		if (this.sdnmudProvider.getTopology() == null) {
			LOG.error("Topology node not found -- ignoring packet");
			return;
		}
		// Extract the src mac address from the packet.
		byte[] srcMacRaw = PacketUtils.extractSrcMac(notification.getPayload());
		MacAddress srcMac = PacketUtils.rawMacToMac(srcMacRaw);

		byte[] dstMacRaw = PacketUtils.extractDstMac(notification.getPayload());
		MacAddress dstMac = PacketUtils.rawMacToMac(dstMacRaw);

		short tableId = notification.getTableId().getValue();

		String matchInPortUri = notification.getMatch().getInPort().getValue();

		LOG.debug("onPacketReceived : matchInPortUri = " + matchInPortUri + " nodeId  " + nodeId + " tableId " + tableId
				+ " srcMac " + srcMac.getValue() + " dstMac " + dstMac.getValue());

		byte[] etherTypeRaw = PacketUtils.extractEtherType(notification.getPayload());
		int etherType = PacketUtils.bytesToEtherType(etherTypeRaw);


		if ( etherType == SdnMudConstants.ETHERTYPE_LLDP) {
			LOG.debug("LLDP Pakcet -- dropping it");
			return;
		}
		
		
		if (etherType == SdnMudConstants.ETHERTYPE_IPV4) {
			String sourceIpAddress = PacketUtils.extractSrcIpStr(notification.getPayload());
			String destIpAddress = PacketUtils.extractDstIpStr(notification.getPayload());
			LOG.info("Source IP  " + sourceIpAddress + " dest IP  " + destIpAddress);

			String sendingNodeId = matchInPortUri;
			
			
			if (!sdnmudProvider.isCpeNode(nodeId)) {
				return;
			}


			sdnmudProvider.putInMacToNodeIdMap(srcMac, nodeId);
			if (tableId == SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE || 
                tableId == SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
				// We got a notification for a device that is connected to this
				// switch.
				Uri mudUri = sdnmudProvider.getMappingDataStoreListener().getMudUri(srcMac);
				boolean isLocalAddress = isLocalAddress(sourceIpAddress);
				if (mudUri != null) {
					// MUD URI was found for this MAc adddress so install the
					// rules to stamp the src manufacturer
					installSrcMacMatchStampManufacturerModelFlowRules(srcMac, isLocalAddress, mudUri.getValue(),
							sdnmudProvider, node);
				} else if (isLocalAddress) {
					// Mud URI was not found so classify this as a local source
					// address packet.
					LOG.debug("MUD URI found for MAC address IS a local packet" + srcMac.getValue());
					installSrcMacMatchStampLocalAddressFlowRules(srcMac, sdnmudProvider, node);
				} else {
					LOG.debug("MUD URI not found for MAC address and not a local packet" + srcMac.getValue());
					FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
					FlowBuilder fb = FlowUtils.createSourceMacMatchGoToNextTableFlow(srcMac, 
							SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
							flowId, flowCookie);
					this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
				}

				// TODO -- check if a match for this already exists before installing redundant rule.
				mudUri = sdnmudProvider.getMappingDataStoreListener().getMudUri(dstMac);

			    isLocalAddress = isLocalAddress(destIpAddress);

				if (mudUri != null) {
					// MUD URI was found for this MAc adddress so install the
					// rules to stamp the manufacturer using metadata.
					installDstMacMatchStampManufacturerModelFlowRules(dstMac, isLocalAddress, mudUri.getValue(),
							sdnmudProvider, node);
				} else if (isLocalAddress) {
					// MUD URI was not found but is local address so stamp local
					// address on metadata
					LOG.debug("MUD URI not found for MAC address IS a local packet" + dstMac.getValue());
					installDstMacMatchStampDstLocalAddressFlowRules(dstMac, sdnmudProvider, node);
				} else {
					LOG.debug("MUD URI not found for MAC address and not a local packet" + dstMac.getValue());
					FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
					FlowBuilder fb = FlowUtils.createDestMacMatchGoToNextTableFlow(dstMac, 
                            SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE,
							 flowId, flowCookie);
					this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
				}
				// transmitPacket(notification.getPayload(), matchInPortUri);

			} 
		}
	}

	

}
