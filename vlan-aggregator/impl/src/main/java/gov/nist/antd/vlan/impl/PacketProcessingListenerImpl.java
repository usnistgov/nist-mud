/* Copyright (c) None.
 * This file includes code developed by employees of the National Institute of
 * Standards and Technology (NIST)
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

package gov.nist.antd.vlan.impl;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.liblldp.BitBufferHelper;
import org.opendaylight.controller.liblldp.BufferException;
import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.HexEncode;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.EthernetPacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.EthernetPacketReceivedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.Header8021qType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.KnownEtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.fields.Header8021q;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.fields.Header8021qBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

public class PacketProcessingListenerImpl implements PacketProcessingListener {

    private static final int ETHERTYPE_LLDP = 0x88cc;

    public static final Integer LENGTH_MAX = 1500;
    public static final Integer ETHERTYPE_MIN = 1536;
    public static final Integer ETHERTYPE_8021Q = 0x8100;
    public static final Integer ETHERTYPE_QINQ = 0x9100;

    private VlanProvider vlanProvider;

    private ListenerRegistration<PacketProcessingListenerImpl> listenerRegistration;

    private static final Logger LOG = LoggerFactory
            .getLogger(PacketProcessingListenerImpl.class);

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
     * Decode a RawPacket into an EthernetPacket.
     *
     * @param packetReceived
     *            -- data from wire to deserialize
     * @return EthernetPacketReceived
     */

    public EthernetPacketReceived decode(PacketReceived packetReceived) {
        byte[] data = packetReceived.getPayload();
        EthernetPacketReceivedBuilder builder = new EthernetPacketReceivedBuilder();

        // Save original rawPacket & set the payloadOffset/payloadLength fields
        RawPacketBuilder rpb = new RawPacketBuilder()
                .setIngress(packetReceived.getIngress())
                .setConnectionCookie(packetReceived.getConnectionCookie())
                .setFlowCookie(packetReceived.getFlowCookie())
                .setTableId(packetReceived.getTableId())
                .setPacketInReason(packetReceived.getPacketInReason())
                .setPayloadOffset(0).setPayloadLength(data.length);

        RawPacket rp = rpb.build();
        ArrayList<PacketChain> packetChain = new ArrayList<>();
        packetChain.add(new PacketChainBuilder().setPacket(rp).build());

        try {
            EthernetPacketBuilder epBuilder = new EthernetPacketBuilder();

            // Deserialize the destination & source fields
            epBuilder.setDestinationMac(
                    new MacAddress(HexEncode.bytesToHexStringFormat(
                            BitBufferHelper.getBits(data, 0, 48))));
            epBuilder.setSourceMac(
                    new MacAddress(HexEncode.bytesToHexStringFormat(
                            BitBufferHelper.getBits(data, 48, 48))));

            // Deserialize the optional field 802.1Q headers
            Integer nextField = BitBufferHelper
                    .getInt(BitBufferHelper.getBits(data, 96, 16));
            int extraHeaderBits = 0;
            ArrayList<Header8021q> headerList = new ArrayList<>();
            while (nextField.equals(ETHERTYPE_8021Q)
                    || nextField.equals(ETHERTYPE_QINQ)) {
                Header8021qBuilder headerBuilder = new Header8021qBuilder();
                headerBuilder.setTPID(Header8021qType.forValue(nextField));

                // Read 2 more bytes for priority (3bits), drop eligible (1bit),
                // vlan-id (12bits)
                byte[] vlanBytes = BitBufferHelper.getBits(data,
                        112 + extraHeaderBits, 16);

                // Remove the sign & right-shift to get the priority code
                headerBuilder
                .setPriorityCode((short) ((vlanBytes[0] & 0xff) >> 5));

                // Remove the sign & remove priority code bits & right-shift to
                // get drop-eligible bit
                headerBuilder.setDropEligible(
                        1 == (vlanBytes[0] & 0xff & 0x10) >> 4);

                // Remove priority code & drop-eligible bits, to get the VLAN-id
                vlanBytes[0] = (byte) (vlanBytes[0] & 0x0F);
                headerBuilder
                .setVlan(new VlanId(BitBufferHelper.getInt(vlanBytes)));

                // Add 802.1Q header to the growing collection
                headerList.add(headerBuilder.build());

                // Reset value of "nextField" to correspond to following 2 bytes
                // for next 802.1Q header or EtherType/Length
                nextField = BitBufferHelper.getInt(BitBufferHelper.getBits(data,
                        128 + extraHeaderBits, 16));

                // 802.1Q header means payload starts at a later position
                extraHeaderBits += 32;
            }
            // Set 802.1Q headers
            if (!headerList.isEmpty()) {
                epBuilder.setHeader8021q(headerList);
            }

            // Deserialize the EtherType or Length field
            if (nextField >= ETHERTYPE_MIN) {
                epBuilder.setEthertype(KnownEtherType.forValue(nextField));
            } else if (nextField <= LENGTH_MAX) {
                epBuilder.setEthernetLength(nextField);
            } else {
                LOG.debug(
                        "Undefined header, value is not valid EtherType or length.  Value is {}",
                        nextField);
            }

            // Determine start & end of payload
            int payloadStart = (112 + extraHeaderBits)
                    / NetUtils.NumBitsInAByte;
            int payloadEnd = data.length - 4;
            epBuilder.setPayloadOffset(payloadStart);
            epBuilder.setPayloadLength(payloadEnd - payloadStart);

            // Deserialize the CRC
            epBuilder.setCrc(BitBufferHelper.getLong(BitBufferHelper.getBits(
                    data, (data.length - 4) * NetUtils.NumBitsInAByte, 32)));

            // Set EthernetPacket field
            packetChain.add(new PacketChainBuilder()
                    .setPacket(epBuilder.build()).build());

            // Set Payload field
            builder.setPayload(data);
        } catch (BufferException be) {
            LOG.info("Exception during decoding raw packet to ethernet.");
        }

        // ToDo: Possibly log these values
        /*
         * if (_logger.isTraceEnabled()) {
         * _logger.trace("{}: {}: {} (offset {} bitsize {})", new Object[] {
         * this.getClass().getSimpleName(), hdrField,
         * HexEncode.bytesToHexString(hdrFieldBytes), startOffset, numBits }); }
         */
        builder.setPacketChain(packetChain);
        return builder.build();
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

    public void setListenerRegistration(
            ListenerRegistration<PacketProcessingListenerImpl> registration) {
        this.listenerRegistration = registration;
    }

    private void transmitPacket(byte[] payload, String outputPortUri) {
        TransmitPacketInputBuilder tpib = new TransmitPacketInputBuilder()
                .setPayload(payload).setBufferId(OFConstants.OFP_NO_BUFFER);
        OutputActionBuilder output = new OutputActionBuilder();
        output.setMaxLength(Integer.valueOf(0xffff));

        output.setOutputNodeConnector(new Uri(outputPortUri));
        ActionBuilder ab = new ActionBuilder();
        ab.setAction(new OutputActionCaseBuilder()
                .setOutputAction(output.build()).build());
        ab.setOrder(1);

        List<Action> actionList = new ArrayList<Action>();
        actionList.add(ab.build());
        tpib.setAction(actionList);
        vlanProvider.getPacketProcessingService().transmitPacket(tpib.build());
    }

    private void installInputPortVlanMatchSendToOutputPortFlowRule(String inputPort, int vlanId, String outputPort,
            InstanceIdentifier<FlowCapableNode> node) {
        short tableId = BaseappConstants.SET_VLAN_RULE_TABLE;
        String flowIdStr = "/vlan/" + inputPort + ":" + vlanId + ":" + outputPort;

        FlowId flowId = new FlowId(flowIdStr);
        String destinationId = InstanceIdentifierUtils.getNodeUri(node);
        FlowCookie newFlowCookie = InstanceIdentifierUtils
                .createFlowCookie(destinationId);
        FlowBuilder fb = FlowUtils
                .createVlanAndPortMatchSendToPort(inputPort,
                        outputPort, vlanId,
                        tableId,
                        0, flowId, newFlowCookie);
        vlanProvider.getFlowCommitWrapper().writeFlow(fb,node);
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

        int etherType = ethernet.getEtherType() < 0
                ? 0xffff + ethernet.getEtherType() + 1
                        : ethernet.getEtherType();
        byte[] srcMacRaw = ethernet.getSourceMACAddress();
        byte[] dstMacRaw = ethernet.getDestinationMACAddress();

        // Extract the src mac address from the packet.
        MacAddress srcMac = rawMacToMac(srcMacRaw);
        MacAddress dstMac = rawMacToMac(dstMacRaw);

        short tableId = notification.getTableId().getValue();

        String matchInPortUri = notification.getMatch().getInPort().getValue();
        NodeConnectorRef nodeConnectorRef = notification.getIngress();

        String destinationId = nodeConnectorRef.getValue()
                .firstKeyOf(Node.class).getId().getValue();

        LOG.info("onPacketReceived : matchInPortUri = " + matchInPortUri
                + " destinationId " + destinationId + " tableId " + tableId
                + " srcMac " + srcMac.getValue() + " dstMac "
                + dstMac.getValue() + " etherType = " + etherType);

        if (vlanProvider.getTopology() == null) {
            LOG.info("Topology not yet registered -- dropping packet");
            return;
        }

        if (etherType == ETHERTYPE_LLDP) {

            InstanceIdentifier<FlowCapableNode> destinationNode = vlanProvider
                    .getNode(destinationId);

            LLDP lldp = (LLDP) ethernet.getPayload();

            String systemName = new String(lldp.getSystemNameId().getValue());

            LOG.info("LLDP Packet matchInPortUri " + matchInPortUri
                    + " destinationId " + destinationId + " systemName = "
                    + systemName);

            if (vlanProvider.isCpeNode(destinationId)) {
                FlowId flowId = InstanceIdentifierUtils
                        .createFlowId(destinationId);
                FlowCookie flowCookie = InstanceIdentifierUtils
                        .createFlowCookie("PORT_MATCH_VLAN_" + destinationId);
                // Push a flow that detects and inbound ARP from the external
                // port (from which we just saw the LLDP packet.
                FlowBuilder fb = FlowUtils
                        .createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
                                notification.getMatch().getInPort(),
                                BaseappConstants.DETECT_EXTERNAL_ARP_TABLE,
                                BaseappConstants.CACHE_TIMEOUT, flowId,
                                flowCookie);

                vlanProvider.getFlowCommitWrapper().writeFlow(fb,
                        destinationNode);

                int tag = vlanProvider.isCpeNode(destinationId)
                        ? (int) vlanProvider.getCpeTag(destinationId)
                                : vlanProvider.getVnfTag(destinationId);

                        flowId = InstanceIdentifierUtils.createFlowId(destinationId);
                        flowCookie = InstanceIdentifierUtils.createFlowCookie(
                                "NO_VLAN_MATCH_PUSH_ARP_" + destinationId);

                        // The following sends two copies of the ARP through the
                        // external port. One with VLAN tag and one without.
                        fb = FlowUtils
                                .createNoVlanArpMatchPushVlanSendToPortAndGoToTable(
                                        notification.getMatch().getInPort().getValue(),
                                        tag, BaseappConstants.PUSH_VLAN_ON_ARP_TABLE,
                                        BaseappConstants.CACHE_TIMEOUT, flowId,
                                        flowCookie);

                        vlanProvider.getFlowCommitWrapper().writeFlow(fb,
                                destinationNode);
                        flowId = InstanceIdentifierUtils.createFlowId(destinationId);
                        flowCookie = InstanceIdentifierUtils.createFlowCookie(
                                "VLAN_MATCH_POP_ARP_" + destinationId);

                        fb = FlowUtils.createVlanMatchPopVlanTagAndGoToTable(flowCookie,
                                flowId, BaseappConstants.STRIP_VLAN_TABLE, tag);
                        vlanProvider.getFlowCommitWrapper().writeFlow(fb,
                                destinationNode);
            } else if (!vlanProvider.isNpeSwitch(destinationId)) {

                FlowId flowId = InstanceIdentifierUtils
                        .createFlowId(destinationId);
                FlowCookie flowCookie = InstanceIdentifierUtils
                        .createFlowCookie("PORT_MATCH_VLAN_" + destinationId);
                // Push a flow that detects and inbound ARP from the external
                // port (from which we just saw the LLDP packet.
                FlowBuilder fb = FlowUtils
                        .createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
                                notification.getMatch().getInPort(),
                                BaseappConstants.DETECT_EXTERNAL_ARP_TABLE,
                                BaseappConstants.CACHE_TIMEOUT, flowId,
                                flowCookie);

                vlanProvider.getFlowCommitWrapper().writeFlow(fb,
                        destinationNode);

            }

            return;
        } else if (tableId == BaseappConstants.DETECT_EXTERNAL_ARP_TABLE
                && !dstMac.getValue().equals("FF:FF:FF:FF:FF:FF")
                && !dstMac.getValue().startsWith("33:33:")) {
            InstanceIdentifier<FlowCapableNode> destinationNode = vlanProvider
                    .getNode(destinationId);

            if (vlanProvider.isCpeNode(destinationId)) {
                // Write a destination MAC flow
                String flowIdStr = "pushVLAN:" + destinationId + ":"
                        + srcMac.getValue();
                FlowId flowId = InstanceIdentifierUtils.createFlowId(flowIdStr);
                FlowCookie flowCookie = InstanceIdentifierUtils
                        .createFlowCookie(flowIdStr);

                int tag = vlanProvider.getCpeTag(destinationId);

                if (tag == -1) {
                    LOG.error("Tag == -1 " + destinationId);
                    return;
                }
                // Override the L2Switch mac to mac rule.
                // Sends of the match packet with VLAN tag applied before it
                // gets to the L2Switch.
                FlowBuilder fb = FlowUtils
                        .createSrcDestMacAddressMatchSetVlanTagAndSendToPort(
                                flowCookie, flowId, dstMac, srcMac,
                                BaseappConstants.SET_VLAN_RULE_TABLE, tag,
                                matchInPortUri, BaseappConstants.CACHE_TIMEOUT);

                vlanProvider.getFlowCommitWrapper().writeFlow(fb,
                        destinationNode);
                LOG.info(
                        "CPE / VNF node appeared node appeared installing VLAN match rules");

            } else if (vlanProvider.isNpeSwitch(destinationId)) {
                // Got an ARP or ARP reply
                EthernetPacketReceived ethernetPacketReceived = this
                        .decode(notification);
                int vlanId = -1;

                for (PacketChain packetChain : ethernetPacketReceived
                        .getPacketChain()) {

                    if (packetChain.getPacket() instanceof EthernetPacket) {
                        EthernetPacket ethPacket = (EthernetPacket) packetChain
                                .getPacket();
                        for (Header8021q header : ethPacket.getHeader8021q()) {
                            vlanId = header.getVlan().getValue();
                            LOG.info("VlanID = " + vlanId);
                            break;
                        }
                    }
                }

                String ifce = this.vlanProvider.getTrunkInterface(destinationId,
                        vlanId);
                if (ifce != null) {

                    GetPortFromInterfaceInputBuilder gpfib = new GetPortFromInterfaceInputBuilder();
                    gpfib.setIntfName(ifce);
                    long trunkPortNo = -1;

                    try {
                        trunkPortNo = vlanProvider
                                .getInterfaceManagerRpcService()
                                .getPortFromInterface(gpfib.build()).get()
                                .getResult().getPortno();
                        LOG.info("port number for eth3 " + trunkPortNo);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        LOG.error("Error getting port number for eth3 ", e);
                        return;
                    }

                    if (Integer.parseUnsignedInt(notification.getMatch()
                            .getInPort().getValue()) != trunkPortNo) {

                        // Set up a vlan to vlan match flow.

                        LOG.info(
                                "Got a packet from a non - trunk port. Send it to the trunk port");

                        String inputPort = String.valueOf((int) trunkPortNo);
                        String outputPort = notification.getMatch().getInPort()
                                .getValue();

                        installInputPortVlanMatchSendToOutputPortFlowRule(inputPort,vlanId, outputPort,
                                destinationNode);


                        inputPort = notification.getMatch().getInPort()
                                .getValue();
                        outputPort = String.valueOf((int) trunkPortNo);

                        installInputPortVlanMatchSendToOutputPortFlowRule(inputPort,vlanId, outputPort,
                                destinationNode);

                    }
                } else {
                    LOG.error("Could not find trunk interface for "
                            + destinationId + " vlanId " + vlanId);

                }

            }

        }

    }

}
