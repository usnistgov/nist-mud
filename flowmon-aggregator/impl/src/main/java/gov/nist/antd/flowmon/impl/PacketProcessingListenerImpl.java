package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
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
		if (this.flowmonProvider.getTopology() == null) {
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
		} else if (tableId == BaseappConstants.PASS_THRU_TABLE) {
			// Create a higher priority MPLS flow so we don't get interrupted
			// again.
			int mplsTag = notification.getFlowCookie().getValue().intValue();
			FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
			FlowBuilder fb = FlowUtils.createOnMplsMatchGoToTable(FlowmonConstants.MPLS_PASS_THRU_FLOW_COOKIE, flowId,
					mplsTag, BaseappConstants.PASS_THRU_TABLE, BaseappConstants.CACHE_TIMEOUT);
			InstanceIdentifier<FlowCapableNode> flowmonNode = flowmonProvider.getNode(destinationId);
			flowmonProvider.getFlowCommitWrapper().writeFlow(fb, flowmonNode);

			/*
			 * Packet diverted for outbound flow. Create a mac to mac flow for
			 * the return packet. Reverse the source and destination
			 * address 
			 */

			flowId = InstanceIdentifierUtils.createFlowId(destinationId);

			String outputPortUri = flowmonProvider.getFlowmonOutputPort(destinationId);

			if (outputPortUri != null) {
				fb = FlowUtils.onSrcDstMacMatchSendToPortAndGoToTable(FlowmonConstants.PACKET_DIVERSION_FLOW_COOKIE,
						flowId, dstMac, srcMac, outputPortUri, BaseappConstants.PASS_THRU_TABLE,
						BaseappConstants.CACHE_TIMEOUT);
				flowmonProvider.getFlowCommitWrapper().writeFlow(fb, flowmonNode);
			}

		}
	}

}
