package gov.nist.antd.vlan.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.LLDP;
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

	private VlanProvider vlanProvider;

	private ListenerRegistration<PacketProcessingListenerImpl> listenerRegistration;
	
	private HashMap<FlowCookie,String> flowCookieToVnfPortMap = new HashMap<>();
	
	private HashMap<FlowCookie,Integer> flowCookieToTagMap = new HashMap<>();

	private static final Logger LOG = LoggerFactory.getLogger(PacketProcessingListenerImpl.class);

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

	/**
	 * PacketIn dispatcher. Gets called when packet is received.
	 * 
	 * @param sdnMudHandler
	 * @param mdsalApiManager
	 * @param flowCommitWrapper
	 * @param vlanProvider
	 */
	public PacketProcessingListenerImpl(VlanProvider vlanProvider) {
		this.vlanProvider = vlanProvider;
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
		vlanProvider.getPacketProcessingService().transmitPacket(tpib.build());
	}

	@Override
	public void onPacketReceived(PacketReceived notification) {
		if (this.vlanProvider.getTopology() == null) {
			LOG.error("Topology node not found -- ignoring packet");
			return;
		}

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

		if (vlanProvider.getTopology() == null) {
			LOG.info("Topology not yet registered -- dropping packet");
			return;
		}

		if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {

			InstanceIdentifier<FlowCapableNode> destinationNode = vlanProvider.getNode(destinationId);

			LLDP lldp = (LLDP) ethernet.getPayload();

			String systemName = new String(lldp.getSystemNameId().getValue());

			LOG.info("LLDP Packet matchInPortUri " + matchInPortUri + " destinationId " + destinationId
					+ " systemName = " + systemName);

		
			if (vlanProvider.isCpeNode(destinationId)) {
				FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("PORT_MATCH_VLAN_" + destinationId);
				// Push a flow that detects and inbound ARP from the external
				// port (from which we just saw the LLDP packet.
				FlowBuilder fb = FlowUtils.createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
						notification.getMatch().getInPort(), SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE, 300, flowId,
						flowCookie);

				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);


				int tag = vlanProvider.isCpeNode(destinationId) ? (int) vlanProvider.getCpeTag(destinationId)
						: vlanProvider.getVnfTag(destinationId);

				flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				flowCookie = InstanceIdentifierUtils
						.createFlowCookie("NO_VLAN_MATCH_PUSH_ARP_" + destinationId);

				// The following sends two copies of the ARP through the
				// external port. One with VLAN tag and one without.
				fb = FlowUtils.createNoVlanArpMatchPushVlanSendToPortAndGoToTable(
						notification.getMatch().getInPort().getValue(), tag, SdnMudConstants.PUSH_VLAN_ON_ARP_TABLE,
						300, flowId, flowCookie);

				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				flowCookie = InstanceIdentifierUtils.createFlowCookie("VLAN_MATCH_POP_ARP_" + destinationId);

				fb = FlowUtils.createVlanMatchPopVlanTagAndGoToTable(flowCookie, flowId,
						SdnMudConstants.STRIP_VLAN_TABLE, tag);
				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
			} else if (vlanProvider.isNpeSwitch(destinationId) && vlanProvider.isVnfSwitch(systemName)) {
				// Got an inbound from VNF switch at the NPE. Strip the Vlan tag
				// and send to the VNF switch.
				int tag = vlanProvider.getVnfTag(systemName);
				if (tag == -1) {
					LOG.error("VNF Tag not found -- returning without installing rule");
					return;
				}
				FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("PORT_MATCH_VLAN_" + tag +  destinationId);
				this.flowCookieToVnfPortMap.put(flowCookie , matchInPortUri);
				this.flowCookieToTagMap.put(flowCookie,tag);
				// Push a flow that detects and inbound ARP from the external
				// port (from which we just saw the LLDP packet.
				FlowBuilder fb = FlowUtils.createVlanTagArpMatchSendToControllerAndGoToTable(
					    tag, SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE, 300, flowId,
						flowCookie);

				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);

				
				flowId = InstanceIdentifierUtils.createFlowId(destinationId);
		        flowCookie = InstanceIdentifierUtils.createFlowCookie("VLAN_MATCH_" + destinationId);

				fb = FlowUtils.createVlanMatchPopVlanTagAndSendToPort(flowCookie, flowId,
						SdnMudConstants.PASS_THRU_TABLE, tag, matchInPortUri, 300);
				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);

			}  else if (!vlanProvider.isNpeSwitch(destinationId)){
				FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("PORT_MATCH_VLAN_" + destinationId);
				// Push a flow that detects and inbound ARP from the external
				// port (from which we just saw the LLDP packet.
				FlowBuilder fb = FlowUtils.createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
						notification.getMatch().getInPort(), SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE, 300, flowId,
						flowCookie);

				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);

			}

			return;
		} else if (tableId == SdnMudConstants.DETECT_EXTERNAL_ARP_TABLE
				&& !dstMac.getValue().equals("FF:FF:FF:FF:FF:FF") && !dstMac.getValue().startsWith("33:33:")) {
			InstanceIdentifier<FlowCapableNode> destinationNode = vlanProvider.getNode(destinationId);

			if (vlanProvider.isCpeNode(destinationId)) {
				// Write a destination MAC flow
				String flowIdStr = "pushVLAN:" + destinationId + ":" + srcMac.getValue();
				FlowId flowId = InstanceIdentifierUtils.createFlowId(flowIdStr);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowIdStr);

				int tag = (vlanProvider.isCpeNode(destinationId) ? vlanProvider.getCpeTag(destinationId)
						: vlanProvider.getVnfTag(destinationId));

				if (tag == -1) {
					LOG.error("Tag == -1 " + destinationId);
					return;
				}
				// Override the L2Switch mac to mac rule.
				// Sends of the match packet with VLAN tag applied before it
				// gets to the L2Switch.
				FlowBuilder fb = FlowUtils.createSrcDestMacAddressMatchSetVlanTagAndSendToPort(flowCookie, flowId,
						dstMac, srcMac, SdnMudConstants.SET_VLAN_RULE_TABLE, tag, matchInPortUri, 300);

				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				LOG.info("CPE / VNF node appeared node appeared installing VLAN match rules");

			} else if (vlanProvider.isNpeSwitch(destinationId)) {
				FlowCookie flowCookie = notification.getFlowCookie();
				String inputPort = this.flowCookieToVnfPortMap.get(flowCookie);
				String outputPort = notification.getMatch().getInPort().getValue();
				String flowIdStr = "PORT_TO_PORT_FLOW:" + inputPort + ":" + outputPort;
				FlowId flowId = InstanceIdentifierUtils.createFlowId(flowIdStr);
				FlowCookie newFlowCookie = InstanceIdentifierUtils.createFlowCookie(flowIdStr);
				int vlanTag = flowCookieToTagMap.get(flowCookie);
				FlowBuilder fb = FlowUtils.createSrcPortMatchSetVlanTagAndSendToPort(inputPort,outputPort,vlanTag, SdnMudConstants.SET_VLAN_RULE_TABLE,
						300, flowId, newFlowCookie);
				vlanProvider.getFlowCommitWrapper().writeFlow(fb, destinationNode);
				
			}

		}

	}

}
