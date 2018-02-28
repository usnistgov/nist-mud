package gov.nist.antd.ids.impl;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
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

public class PacketInDispatcher implements PacketProcessingListener {

	private IdsProvider idsProvider;

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
	 * @param idsProvider
	 */
	public PacketInDispatcher(String nodeId, InstanceIdentifier<FlowCapableNode> node, IdsProvider idsProvider) {
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

	public void setListenerRegistration(ListenerRegistration<PacketInDispatcher> registration) {
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

		LOG.debug("onPacketReceived : matchInPortUri = " + matchInPortUri + " nodeId  " + nodeId + " tableId " + tableId
				+ " srcMac " + srcMac.getValue() + " dstMac " + dstMac.getValue());

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
		NodeConnectorRef nodeConnectorRef = notification.getIngress();
		String destinationId = nodeConnectorRef.getValue().firstKeyOf(Node.class).getId().getValue();

		if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {
			LOG.info("LLDP Packet " + matchInPortUri + " nodeId " + destinationId);
			this.idsProvider.setNodeConnector(destinationId, this.nodeId, matchInPortUri);
			// If the LLDP node ID matches one of the known NPE nodes then push
			// a flow for it.

		

			return;
		}

		if (etherType == SdnMudConstants.ETHERTYPE_IPV4) {

			if (tableId == SdnMudConstants.L2SWITCH_TABLE) {
				// Install a flow for this destination MAC routed to the
				// ingress
				String outputPortUri = this.idsProvider.getNodeConnector(this.nodeId, destinationId);
				if (outputPortUri != null) {

					LOG.debug("Installng BRIDGE flow rule for dstMac = " + dstMac.getValue() + " srcMac = " + srcMac
							+ " sendingNodeId " + destinationId + " myNodeId " + nodeId);

					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
					int time = 300;

					if (dstMac.getValue().compareToIgnoreCase("ff:ff:ff:ff:ff:ff") != 0
							&& srcMac.getValue().compareToIgnoreCase("ff:ff:ff:ff:ff:ff") != 0
							&& !dstMac.getValue().startsWith("33:33")) {
						/*
						 * Note 48-bit MAC addresses in the range
						 * 33-33-00-00-00-00 to 33-33-FF-FF-FF-FF are used for
						 * IPv6 multicast. TODO - check for loops before
						 * installing this rule.
						 */
						int mplsTag = -1;
						if (idsProvider.isCpeNode(nodeId)) {
						    mplsTag = (int) idsProvider.getCpeTag(nodeId);
						} else if (idsProvider.isNpeSwitch(nodeId)) {
							mplsTag = (int) idsProvider.getNpeTag(nodeId);
						}
						if (mplsTag == -1) {
							LOG.error("Could not find MPLS tag match ");
							return;
						}
						
						// Push an MPLS tag corresponding to this CPE switch
						// and send it.
						FlowBuilder flow = FlowUtils.createDestMacAddressMatchSetVlanTagAndSendToPort(flowCookie,
								srcMac, tableId, mplsTag, outputPortUri, time);
						idsProvider.getFlowCommitWrapper().writeFlow(flow, node);
						transmitPacket(notification.getPayload(), outputPortUri);
					}
				} else {
					LOG.info("Cannot find mapping -- dropping");
				}
			}
		}

	}

}
