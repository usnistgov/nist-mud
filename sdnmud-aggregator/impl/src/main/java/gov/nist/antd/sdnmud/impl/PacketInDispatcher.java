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

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

/**
 * Packet in dispatcher that gets invoked on flow table miss when a packet is
 * sent up to the controller.
 *
 * @author mranga@nist.gov
 *
 */
public class PacketInDispatcher implements PacketProcessingListener {

    private SdnmudProvider sdnmudProvider;

    private ListenerRegistration<PacketInDispatcher> listenerRegistration;

    private static final Logger LOG = LoggerFactory
            .getLogger(PacketInDispatcher.class);

    private HashMap<String, Flow> flowTable = new HashMap<>();

    public PacketInDispatcher(SdnmudProvider sdnmudProvider) {
        this.sdnmudProvider = sdnmudProvider;
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

    private void transmitPacket(PacketReceived notification) {

        TransmitPacketInputBuilder tpib = new TransmitPacketInputBuilder()
                .setPayload(notification.getPayload())
                .setBufferId(OFConstants.OFP_NO_BUFFER);
        tpib.setEgress(notification.getIngress());
        OutputActionBuilder output = new OutputActionBuilder();
        output.setMaxLength(Integer.valueOf(0xffff));
        String matchInPortUri = notification.getMatch().getInPort().getValue();

        output.setOutputNodeConnector(new Uri(matchInPortUri));
        ActionBuilder ab = new ActionBuilder();
        ab.setAction(new OutputActionCaseBuilder()
                .setOutputAction(output.build()).build());
        ab.setOrder(1);

        List<Action> actionList = new ArrayList<Action>();
        actionList.add(ab.build());
        tpib.setAction(actionList);

        this.sdnmudProvider.getPacketProcessingService()
                .transmitPacket(tpib.build());
    }

    private boolean isLocalAddress(String nodeId, String ipAddress) {
        boolean isLocalAddress = false;
        if (this.sdnmudProvider.getControllerclassMappingDataStoreListener()
                .getLocalNetworks(nodeId) != null) {
            for (String localNetworkStr : this.sdnmudProvider
                    .getControllerclassMappingDataStoreListener()
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

    private void installSrcMacMatchStampManufacturerModelFlowRules(
            MacAddress srcMac, boolean isLocalAddress, String mudUri,
            SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node) {
        String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
        int manufacturerId = InstanceIdentifierUtils
                .getManfuacturerId(manufacturer);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        int flag = 0;
        if (isLocalAddress) {
            flag = 1;
        }

        LOG.debug("installStampSrcManufacturerModelFlowRules : dstMac = "
                + srcMac.getValue() + " isLocalAddress " + isLocalAddress
                + " mudUri " + mudUri);

        BigInteger metadata = BigInteger.valueOf(manufacturerId)
                .shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
                .or(BigInteger.valueOf(flag)
                        .shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT))
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

        BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK
                .or(SdnMudConstants.SRC_MODEL_MASK)
                .or(SdnMudConstants.SRC_NETWORK_MASK);

        FlowCookie flowCookie = SdnMudConstants.SRC_MANUFACTURER_STAMP_FLOW_COOKIE;

        String flowIdStr = "/sdnmud/srcMacMatchSetMetadataAndGoToNextTable/"
                + srcMac.getValue() + "/" + metadata.toString(16) + "/"
                + metadataMask.toString(16);

        Flow flow = this.flowTable.get(flowIdStr);
        if (flow == null) {
            FlowId flowId = new FlowId(flowIdStr);

            flow = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(
                    srcMac, metadata, metadataMask,
                    BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
                    flowId, flowCookie).build();
            flowTable.put(flowIdStr, flow);

        }

        sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, node);
    }

    public void installDstMacMatchStampManufacturerModelFlowRules(
            MacAddress dstMac, boolean isLocalAddress, String mudUri,
            SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node) {

        String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
        int manufacturerId = InstanceIdentifierUtils
                .getManfuacturerId(manufacturer);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        int flag = 0;
        if (isLocalAddress) {
            flag = 1;
        }

        LOG.debug("installStampDstManufacturerModelFlowRules : dstMac = "
                + dstMac.getValue() + " isLocalAddress " + isLocalAddress
                + " mudUri " + mudUri);

        BigInteger metadata = BigInteger.valueOf(manufacturerId)
                .shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
                .or(BigInteger.valueOf(flag)
                        .shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT))
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
        BigInteger metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK
                .or(SdnMudConstants.DST_MODEL_MASK)
                .or(SdnMudConstants.DST_NETWORK_MASK);
        FlowCookie flowCookie = SdnMudConstants.DST_MANUFACTURER_MODEL_FLOW_COOKIE;

        String flowIdStr = "/sdnmud/destMacMatchSetMetadataAndGoToNextTable/"
                + dstMac.getValue() + "/" + metadata.toString(16) + "/"
                + metadataMask.toString(16);

        Flow flow = this.flowTable.get(flowIdStr);
        if (flow == null) {
            FlowId flowId = new FlowId(flowIdStr);

            flow = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(
                    dstMac, metadata, metadataMask,
                    BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE,
                    flowId, flowCookie).build();
            this.flowTable.put(flowIdStr, flow);
        }
        sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, node);
    }

    public void setListenerRegistration(
            ListenerRegistration<PacketInDispatcher> registration) {
        this.listenerRegistration = registration;

    }

    // installDstMacMatchStampDstLocalAddressFlowRules
    private static void installDstMacMatchStampDstLocalAddressFlowRules(
            MacAddress dstMac, SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node) {
        FlowId flowId = InstanceIdentifierUtils
                .createFlowId(InstanceIdentifierUtils.getNodeUri(node));
        BigInteger metadata = BigInteger.valueOf(1)
                .shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT);
        BigInteger metadataMask = SdnMudConstants.DST_NETWORK_MASK;
        FlowCookie flowCookie = SdnMudConstants.DST_MANUFACTURER_MODEL_FLOW_COOKIE;
        FlowBuilder fb = FlowUtils
                .createDestMacMatchSetMetadataAndGoToNextTableFlow(dstMac,
                        metadata, metadataMask,
                        BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE,
                        flowId, flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {

        if (this.sdnmudProvider.getCpeCollections() == null) {
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

        String nodeId = notification.getIngress().getValue()
                .firstKeyOf(Node.class).getId().getValue();

        InstanceIdentifier<FlowCapableNode> node = this.sdnmudProvider
                .getNode(nodeId);

        LOG.debug("onPacketReceived : matchInPortUri = " + matchInPortUri
                + " nodeId  " + nodeId + " tableId " + tableId + " srcMac "
                + srcMac.getValue() + " dstMac " + dstMac.getValue());

        if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {
            LOG.debug("LLDP Pakcet -- dropping it");
            return;
        }

        if (etherType == SdnMudConstants.ETHERTYPE_IPV4) {
            String sourceIpAddress = PacketUtils
                    .extractSrcIpStr(notification.getPayload());
            String destIpAddress = PacketUtils
                    .extractDstIpStr(notification.getPayload());
            LOG.info("Source IP  " + sourceIpAddress + " dest IP  "
                    + destIpAddress);

            if (!this.sdnmudProvider.isCpeNode(nodeId)) {
                return;
            }

            if (tableId == BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE
                    || tableId == BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
                // We got a notification for a device that is connected to this
                // switch.
                Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener()
                        .getMudUri(srcMac);
                boolean isLocalAddress = this.isLocalAddress(nodeId,
                        sourceIpAddress);
                installSrcMacMatchStampManufacturerModelFlowRules(srcMac,
                        isLocalAddress, mudUri.getValue(), this.sdnmudProvider,
                        node);
                // TODO -- check if a match for this already exists before
                // installing redundant rule.
                mudUri = this.sdnmudProvider.getMappingDataStoreListener()
                        .getMudUri(dstMac);

                isLocalAddress = this.isLocalAddress(nodeId, destIpAddress);

                installDstMacMatchStampManufacturerModelFlowRules(dstMac,
                        isLocalAddress, mudUri.getValue(), this.sdnmudProvider,
                        node);

            } else if (tableId == BaseappConstants.SDNMUD_RULES_TABLE) {
                LOG.debug(
                        "PacketInDispatcher: Got a TCP notification -- check and flag if this contains a TCP Syn");
                byte[] rawPacket = notification.getPayload();
                String srcIp = PacketUtils.extractSrcIpStr(rawPacket);
                int port = PacketUtils.getTCPSourcePort(rawPacket);
                BigInteger metadata = notification.getMatch().getMetadata()
                        .getMetadata();
                BigInteger metadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

                if (PacketUtils.isSYNFlagOn(rawPacket)) {
                    LOG.debug(
                            "PacketInDispatcher: Got an illegal SYN -- blocking the flow");
                    String flowIdStr = "/sdnmud/SrcIpAddressProtocolDestMacMatchDrop:"
                            + srcIp + ":" + dstMac.getValue() + ":"
                            + SdnMudConstants.TCP_PROTOCOL + ":" + port;
                    Flow fb = this.flowTable.get(flowIdStr);
                    if (fb == null) {
                        FlowId flowId = new FlowId(flowIdStr);
                        FlowCookie flowCookie = InstanceIdentifierUtils
                                .createFlowCookie(nodeId);

                        fb = FlowUtils
                                .createSrcIpAddressProtocolDestMacMatchGoTo(
                                        new Ipv4Address(srcIp), dstMac, port,
                                        SdnMudConstants.TCP_PROTOCOL, tableId,
                                        BaseappConstants.DROP_TABLE, metadata,
                                        metadataMask, 1, flowId, flowCookie)
                                .build();
                        this.flowTable.put(flowIdStr, fb);
                    }

                    this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb,
                            node);

                } else {
                    LOG.debug(
                            "PacketInDispatcher: SYN flag is OFF. Allowing the flow to pass through");
                    String flowIdStr = "/sdnmud/SrcIpAddressProtocolDestMacMatchGoTo:"
                            + srcIp + ":" + dstMac.getValue() + ":"
                            + SdnMudConstants.TCP_PROTOCOL + ":" + port;

                    Flow fb = this.flowTable.get(flowIdStr);

                    if (fb == null) {
                        FlowId flowId = new FlowId(flowIdStr);
                        FlowCookie flowCookie = InstanceIdentifierUtils
                                .createFlowCookie(nodeId);
                        /*
                         * create a short term pass through flow to allow packet
                         * through. Give it a short timeout.
                         */

                        fb = FlowUtils
                                .createSrcIpAddressProtocolDestMacMatchGoTo(
                                        new Ipv4Address(srcIp), dstMac, port,
                                        SdnMudConstants.TCP_PROTOCOL, tableId,
                                        metadata, metadataMask, 1, flowId,
                                        flowCookie)
                                .build();
                        this.flowTable.put(flowIdStr, fb);
                    }

                    this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb,
                            node);

                }
                transmitPacket(notification);

            }
        }
    }

}
