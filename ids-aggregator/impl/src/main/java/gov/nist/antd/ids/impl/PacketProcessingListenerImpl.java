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

	private ListenerRegistration<PacketProcessingListenerImpl> listenerRegistration;

	private static final Logger LOG = LoggerFactory.getLogger(PacketProcessingListenerImpl.class);



	/**
	 * PacketIn dispatcher. Gets called when packet is received.
	 * 
	 * @param sdnMudHandler
	 * @param mdsalApiManager
	 * @param flowCommitWrapper
	 * @param idsProvider
	 */
	public PacketProcessingListenerImpl(IdsProvider idsProvider) {
		this.idsProvider = idsProvider;
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

		LOG.info("onPacketReceived : matchInPortUri = " + matchInPortUri + " destinationId " + destinationId
				+ " tableId " + tableId + " srcMac " + srcMac.getValue() + " dstMac " + dstMac.getValue());

		byte[] etherTypeRaw = PacketUtils.extractEtherType(notification.getPayload());
		int etherType = PacketUtils.bytesToEtherType(etherTypeRaw);

		if (notification.getFlowCookie().getValue().equals(SdnMudConstants.IDS_REGISTRATION_FLOW_COOKIE.getValue())) {
			LOG.debug("IDS Registration seen on : " + matchInPortUri);
			// TODO -- authenticate the IDS by checking his MAC and token.
			this.idsProvider.addIdsPort(destinationId, matchInPortUri);
			return;
		}

		if (idsProvider.getTopology() == null) {
			LOG.info("Topology not yet registered -- dropping packet");
			return;
		}

		if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {
			LOG.info("LLDP Packet matchInPortUri " + matchInPortUri + " destinationId " + destinationId);

			
			InstanceIdentifier<FlowCapableNode> destinationNode = idsProvider.getNode(destinationId);

			LOG.info("LLDP Packet installing rules on " + InstanceIdentifierUtils.getNodeUri(destinationNode));


			
			if (idsProvider.isCpeNode(destinationId)) {
				FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("PORT_MATCH_VLAN_" + destinationId);
				
				int tag = (int) idsProvider.getCpeTag(destinationId);
				//FlowBuilder fb = FlowUtils.createVlanAndPortMatchSendPacketToControllerAndGoToTableFlow(
				//		notification.getMatch().getInPort(), tag, SdnMudConstants.PASS_THRU_TABLE, 300, flowId, flowCookie);
				
				FlowBuilder fb = FlowUtils.createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow
				    		(notification.getMatch().getInPort(), SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE, 300, flowId, flowCookie);
				    
				idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				flowCookie = InstanceIdentifierUtils.createFlowCookie("NO_VLAN_MATCH_PUSH_ARP_" + destinationId);

				fb = FlowUtils.createNoVlanArpMatchPushVlanSendToPortAndGoToTable(
						notification.getMatch().getInPort().getValue(), tag, 
						SdnMudConstants.PUSH_VLAN_ON_ARP_TABLE, 300, flowId, flowCookie);
				
				idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				flowCookie = InstanceIdentifierUtils.createFlowCookie("VLAN_MATCH_POP_ARP_" + destinationId);

				fb = FlowUtils.createVlanMatchPopVlanTagAndGoToTable(flowCookie, flowId,
						SdnMudConstants.STRIP_VLAN_TABLE, tag);
				idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
			} else {
				// For L2switch to build MAC to MAC flows.
				FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("ARP_MATCH_" + destinationId);
			    FlowBuilder fb = FlowUtils.createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow
			    		(notification.getMatch().getInPort(), SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE, 300, flowId, flowCookie);
			    idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
			}

			return;
		} else if (tableId == SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE && !dstMac.getValue().equals("FF:FF:FF:FF:FF:FF")
				&& !dstMac.getValue().startsWith("33:33:")) {
			InstanceIdentifier<FlowCapableNode> destinationNode = idsProvider.getNode(destinationId);
	
			if (idsProvider.isCpeNode(destinationId)) {
				// Write a destination MAC flow
				String flowIdStr = "pushVLAN:" + destinationId + ":" + srcMac.getValue();
				FlowId flowId = InstanceIdentifierUtils.createFlowId(flowIdStr);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowIdStr);
				idsProvider.getFlowCommitWrapper().deleteFlows(destinationNode, flowIdStr, tableId, null);

				int tag = (int) idsProvider.getCpeTag(destinationId);
				
				if (tag == -1) {
					LOG.error("Tag == -1 " + destinationId);
					return;
				}
				// Override the L2Switch rules (HACK alert)
				FlowBuilder fb = FlowUtils.createSrcDestMacAddressMatchSetVlanTagAndSendToPort(flowCookie, 
						flowId,dstMac, srcMac,SdnMudConstants.L2SWITCH_TABLE, tag, matchInPortUri, 300);

				idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				flowId = InstanceIdentifierUtils.createFlowId(flowIdStr);
				
				fb = FlowUtils.createSrcDestMacAddressVlanPortMatchGoToTable(srcMac, dstMac, tag, notification.getMatch().getInPort(),
						SdnMudConstants.L2SWITCH_TABLE, flowId, flowCookie,  300);
				idsProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				LOG.info("CPE / VNF node appeared node appeared installing VLAN match rules");

				
			} 

		}

	}

}
