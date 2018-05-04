package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.flowmon.config.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

class PacketProcessingListenerImpl implements PacketProcessingListener {

	private FlowmonProvider flowmonProvider;

	private ListenerRegistration<PacketProcessingListenerImpl> listenerRegistration;

	private static final Logger LOG = LoggerFactory.getLogger(PacketProcessingListenerImpl.class);

	/**
	 * PacketIn dispatcher. Gets called when packet is received.
	 *
	 * @param sdnMudHandler
	 * @param mdsalApiManager
	 * @param flowCommitWrapper
	 * @param flowmonProvider
	 */
	PacketProcessingListenerImpl(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}

	/**
	 * Get the Node for a given MAC address.
	 *
	 * @param macAddress
	 * @param node
	 * @return
	 */

	public void setListenerRegistration(ListenerRegistration<PacketProcessingListenerImpl> registration) {
		this.listenerRegistration = registration;
	}

	private static MacAddress rawMacToMac(final byte[] rawMac) {
		MacAddress mac = null;
		if (rawMac != null) {
			StringBuilder sb = new StringBuilder();
			for (byte octet : rawMac) {
				sb.append(String.format(":%02X", octet));
			}
			mac = new MacAddress(sb.substring(1));
		}
		return mac;
	}

	private static String getManufacturer(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.UNCLASSIFIED))
			return FlowmonConstants.UNCLASSIFIED;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[0];

	}

	private static String getFlowType(Uri flowSpec) {

		if (flowSpec.getValue().equals(FlowmonConstants.PASSTHRU))
			return FlowmonConstants.PASSTHRU;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[1];
	}

	public synchronized void installDivertToIdsFlow(InstanceIdentifier<FlowCapableNode> node, String flowmonPorts) {

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

						BigInteger metadata = BigInteger.valueOf(InstanceIdentifierUtils.getFlowHash(manufacturer))
								.shiftLeft(FlowmonConstants.SRC_MANUFACTURER_SHIFT);

						FlowBuilder flow = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId,
								metadata, FlowmonConstants.SRC_MANUFACTURER_MASK,
								FlowmonConstants.DIVERT_TO_FLOWMON_TABLE, flowmonPorts,
								0);

						this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);

						flowId = InstanceIdentifierUtils.createFlowId(vnfSwitch);

						metadata = BigInteger.valueOf(InstanceIdentifierUtils.getFlowHash(manufacturer))
								.shiftLeft(FlowmonConstants.DST_MANUFACTURER_SHIFT);

						flow = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId, metadata,
								FlowmonConstants.DST_MANUFACTURER_MASK, FlowmonConstants.DIVERT_TO_FLOWMON_TABLE,
								flowmonPorts, 0);

						this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);
					}
				}
			}
		}
	}

	@Override
	public void onPacketReceived(PacketReceived notification) {
		if (!this.flowmonProvider.isInitialized()) {
			LOG.error("Topology node not found -- ignoring packet");
			return;
		}
		// Extract the src mac address from the packet.
		Ethernet ethernet = new Ethernet();

		try {
			ethernet.deserialize(notification.getPayload(), 0,
					notification.getPayload().length * NetUtils.NumBitsInAByte);

		} catch (Exception ex) {
			LOG.error("Error deserializing packet", ex);
		}

		// Extract various fields from the ethernet packet.

		int etherType = ethernet.getEtherType() < 0 ? 0xffff + ethernet.getEtherType() + 1 : ethernet.getEtherType();
		byte[] srcMacRaw = ethernet.getSourceMACAddress();
		byte[] dstMacRaw = ethernet.getDestinationMACAddress();

		// Extract the src mac address from the packet.
		MacAddress srcMac = rawMacToMac(srcMacRaw);
		MacAddress dstMac = rawMacToMac(dstMacRaw);

		short tableId = notification.getTableId().getValue();

		String matchInPortUri = notification.getMatch().getInPort().getValue();
		NodeConnectorRef nodeConnectorRef = notification.getIngress();

		String destinationId = nodeConnectorRef.getValue().firstKeyOf(Node.class).getId().getValue();

		LOG.info("onPacketReceived : matchInPortUri = " + matchInPortUri + " destinationId " + destinationId
				+ " tableId " + tableId + " srcMac " + srcMac.getValue() + " dstMac " + dstMac.getValue());

		if (tableId == BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
			if (flowmonProvider.isVnfSwitch(destinationId)) {
				InstanceIdentifier<FlowCapableNode> flowmonNode = flowmonProvider.getNode(destinationId);
				MacAddress macAddress = flowmonProvider.getFlowmonConfig(destinationId).getFlowmonMac();
				if (macAddress.equals(srcMac)) {
					LOG.info("Flowmon registration seen on " + matchInPortUri);
					this.installDivertToIdsFlow(flowmonNode, matchInPortUri);
				} else {
					Uri mudUri = flowmonProvider.getMudUri(srcMac);
					MappingDataStoreListener.installStampManufacturerFlowRule(flowmonProvider, srcMac, mudUri,
							flowmonNode);
				}
			}

		}
	}

}
