package gov.nist.antd.ids.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

public class PacketProcessingListenerImpl implements PacketProcessingListener {

	private IdsProvider idsProvider;

	private String nodeId;

	private InstanceIdentifier<FlowCapableNode> node;

	private ListenerRegistration<PacketProcessingListenerImpl> listenerRegistration;

	private static final Logger LOG = LoggerFactory.getLogger(PacketProcessingListenerImpl.class);
	
	private static HashMap<FlowCookie,InstanceIdentifier<FlowCapableNode>> arpFlowCookies = new HashMap<>();
	

	/**
	 * PacketIn dispatcher. Gets called when packet is received.
	 * 
	 * @param sdnMudHandler
	 * @param mdsalApiManager
	 * @param flowCommitWrapper
	 * @param idsProvider
	 */
	public PacketProcessingListenerImpl(String nodeId, InstanceIdentifier<FlowCapableNode> node,
			IdsProvider idsProvider) {
		LOG.info("PakcetProcessingListenerImpl : nodeId " + nodeId);
		this.node = node;
		this.nodeId = nodeId;
		this.idsProvider = idsProvider;
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

	public void setListenerRegistration(ListenerRegistration<PacketProcessingListenerImpl> registration) {
		this.listenerRegistration = registration;
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
		idsProvider.getPacketProcessingService().transmitPacket(tpib.build());
	}

	@Override
	public void onPacketReceived(PacketReceived notification) {
		if (this.idsProvider.getTopology() == null) {
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
		NodeConnectorRef nodeConnectorRef = notification.getIngress();

		String destinationId = nodeConnectorRef.getValue().firstKeyOf(Node.class).getId().getValue();

		LOG.info("onPacketReceived : matchInPortUri = " + matchInPortUri + " nodeId  " + nodeId + " destinationId "
				+ destinationId + " tableId " + tableId + " srcMac " + srcMac.getValue() + " dstMac "
				+ dstMac.getValue());

		byte[] etherTypeRaw = PacketUtils.extractEtherType(notification.getPayload());
		int etherType = PacketUtils.bytesToEtherType(etherTypeRaw);

		if (notification.getFlowCookie().getValue().equals(SdnMudConstants.IDS_REGISTRATION_FLOW_COOKIE.getValue())) {
			LOG.debug("IDS Registration seen on : " + matchInPortUri);
			// TODO -- authenticate the IDS by checking his MAC and token.
			this.idsProvider.addIdsPort(this.nodeId, matchInPortUri);
			return;
		}

		if (idsProvider.getTopology() == null) {
			LOG.info("Topology not yet registered -- dropping packet");
			return;
		}

		if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {
			LOG.info("LLDP Packet matchInPortUri " + matchInPortUri + " destinationId " + destinationId + " myNodeId "
					+ nodeId);
			if (!destinationId.equals(this.nodeId)) {
				this.idsProvider.setNodeConnector(destinationId, this.nodeId, matchInPortUri);
				FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId +":" + destinationId);

				FlowBuilder fb = FlowUtils.createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
						notification.getMatch().getInPort(), SdnMudConstants.PASS_THRU_TABLE,
						SdnMudConstants.L2SWITCH_TABLE, 300, flowId, flowCookie);
				InstanceIdentifier<FlowCapableNode> destinationNode = idsProvider.getNode(destinationId);
				idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
			}
			return;
		} else if (etherType == SdnMudConstants.ETHERTYPE_ARP  && tableId == SdnMudConstants.PASS_THRU_TABLE ) {
			if (!dstMac.getValue().equals("FF:FF:FF:FF:FF:FF")) {
				LOG.info("ARP Response " + matchInPortUri + " destinationId " + destinationId + " myNodeId " + nodeId);
				LOG.info("ARP response src mac = " + srcMac.getValue() + " destMac " + dstMac.getValue());
				// Write a destination MAC flow 
				if (! destinationId.equals(this.nodeId)) {
					InstanceIdentifier<FlowCapableNode> destinationNode = idsProvider.getNode(destinationId);
					String flowIdStr = destinationId + ":" + srcMac;
					FlowId flowId = InstanceIdentifierUtils.createFlowId(flowIdStr);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowIdStr);
					idsProvider.getFlowCommitWrapper().deleteFlows(destinationNode, flowIdStr, tableId, null);
					FlowBuilder fb = FlowUtils.createDestMacAddressMatchSendToPort(flowCookie, flowId, srcMac, 
							SdnMudConstants.L2SWITCH_TABLE, matchInPortUri, 300);
					idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				}
				
			} else {
				LOG.info("ARP Discovery " + srcMac.getValue());
			}
		}

	}

}
