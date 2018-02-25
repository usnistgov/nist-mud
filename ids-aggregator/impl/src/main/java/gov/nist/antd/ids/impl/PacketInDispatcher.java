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
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketInDispatcher implements PacketProcessingListener {

	private IdsProvider idsProvider;

	private String nodeId;

	private InstanceIdentifier<FlowCapableNode> node;

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

		String sendingNodeId = matchInPortUri;

		if (notification.getFlowCookie().getValue().equals(SdnMudConstants.IDS_REGISTRATION_FLOW_COOKIE.getValue())) {
			LOG.debug("IDS Registration seen on : " + matchInPortUri);
			// TODO -- authenticate the IDS by checking his MAC and token.
			this.idsProvider.addIdsPort(sendingNodeId, matchInPortUri);
			return;
		}

	}

}
