package gov.nist.antd.flowmon.impl;

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
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

		byte[] etherTypeRaw = PacketUtils.extractEtherType(notification.getPayload());

		if (notification.getFlowCookie().getValue().equals(FlowmonConstants.IDS_REGISTRATION_FLOW_COOKIE.getValue())) {
			LOG.debug("IDS Registration seen on : " + matchInPortUri);
			// TODO -- authenticate the IDS by checking his MAC and token.
			this.flowmonProvider.setFlowmonOutputPort(destinationId, matchInPortUri);
			return;
		} else if (tableId == BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
			if ( flowmonProvider.isVnfSwitch(destinationId)) {
				Uri mudUri = flowmonProvider.getMudUri(srcMac);
				InstanceIdentifier<FlowCapableNode> flowmonNode = flowmonProvider.getNode(destinationId);
				MappingDataStoreListener.installStampManufacturerFlowRule(flowmonProvider, srcMac,mudUri, flowmonNode);
			}

		}
	}

}
