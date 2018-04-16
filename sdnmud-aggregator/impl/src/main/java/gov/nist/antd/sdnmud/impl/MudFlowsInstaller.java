/*
*Copyright (c) 2018 Public Domain. 
* 
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.Accept;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.Aces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l3.Ipv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l4.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l4.Udp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev180301.Ipv41;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev180301.Matches1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.Direction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.Matches1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.Tcp1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.access.lists.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.access.lists.access.lists.AccessList;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.mud.grouping.FromDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.mud.grouping.ToDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev180303.port.range.or.operator.port.range.or.operator.Operator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

public class MudFlowsInstaller {

    private SdnmudProvider sdnmudProvider;
    private String cpeNodeId;
    static final Logger LOG = LoggerFactory.getLogger(MudFlowsInstaller.class);

    private interface MatchesType {
        int CONTROLLER_MAPPING = 1;
        int SAME_MANUFACTURER = 3;
        int MANUFACTURER = 4;
        int MODEL = 5;
        int MY_CONTROLLER = 6;
        int LOCAL_NETWORKS = 7;
        int DNS_MATCH = 8;
        int UNKNOWN_MATCH = 9;

    }

    public MudFlowsInstaller(SdnmudProvider sdnmudProvider, String cpeNodeId) {
        this.sdnmudProvider = sdnmudProvider;
        this.cpeNodeId = cpeNodeId;
    }

    private static String createDropFlowSpec(String manufacturer) {
        return "DROP";
    }

    private static String createFlowSpec(Ipv4Address address) {
        try {
            if (InetAddress.getByName(address.getValue())
                    .isSiteLocalAddress()) {
                return SdnMudConstants.NONE + ":" + SdnMudConstants.LOCAL;
            } else {
                return SdnMudConstants.NONE + ":" + SdnMudConstants.REMOTE;
            }
        } catch (UnknownHostException e) {
            LOG.error("Unexpected exception ", e);
            throw new RuntimeException(e);
        }

    }

    private static String createFlowSpec(String manufacturer,
            Ipv4Address address) {
        try {
            if (InetAddress.getByName(address.getValue())
                    .isSiteLocalAddress()) {
                return manufacturer + ":" + SdnMudConstants.LOCAL;
            } else {
                return manufacturer + ":" + SdnMudConstants.REMOTE;
            }
        } catch (UnknownHostException e) {
            LOG.error("Unexpected exception ", e);
            throw new RuntimeException(e);
        }
    }

    private static int matchesType(Matches matches) {
        Matches1 matches1 = matches.getAugmentation(Matches1.class);
        if (matches1 == null) {
            return MatchesType.DNS_MATCH;
        }

        if (matches1.getMud() != null
                && matches1.getMud().getController() != null) {
            return MatchesType.CONTROLLER_MAPPING;
        } else if (matches1.getMud() != null
                && matches1.getMud().getManufacturer() != null) {
            return MatchesType.MANUFACTURER;
        } else if (matches1.getMud() != null
                && matches1.getMud().getModel() != null) {
            return MatchesType.MODEL;
        } else if (matches1.getMud() != null
                && matches1.getMud().isLocalNetworks() != null
                && matches1.getMud().isLocalNetworks()) {
            return MatchesType.LOCAL_NETWORKS;
        } else if (matches1.getMud() != null
                && matches1.getMud().isSameManufacturer() != null
                && matches1.getMud().isSameManufacturer()) {
            return MatchesType.SAME_MANUFACTURER;
        } else if (matches1.getMud() != null
                && matches1.getMud().isMyController() != null
                && matches1.getMud().isMyController()) {
            return MatchesType.MY_CONTROLLER;
        } else {
            return MatchesType.UNKNOWN_MATCH;
        }
    }

    private static boolean isValidIpV4Address(String ip) {
        try {
            if (ip == null || ip.isEmpty()) {
                return false;
            }

            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }

            for (String s : parts) {
                int i = Integer.parseInt(s);
                if ((i < 0) || (i > 255)) {
                    return false;
                }
            }
            if (ip.endsWith(".")) {
                return false;
            }

            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    private static void resolveDefaultDomain(ArrayList<Ipv4Address> ipAddresses,
            String domainName) throws UnknownHostException {
        InetAddress[] inetAddresses = InetAddress.getAllByName(domainName);

        for (InetAddress inetAddress : inetAddresses) {
            String hostAddress = inetAddress.getHostAddress();
            LOG.info("domainName " + domainName + "inetAddress : "
                    + hostAddress);
            if (isValidIpV4Address(hostAddress)) {
                ipAddresses.add(new Ipv4Address(hostAddress));
            }
        }
    }

    /**
     * Resolve the addresses for a Match clause.
     * 
     *
     * @param matches
     *            - the matches clause.
     * @return - a list of addresses that match.
     * 
     */
    private static List<Ipv4Address> getMatchAddresses(Matches matches)
            throws Exception {

        ArrayList<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
        Ipv41 ipv41 = ((Ipv4) matches.getL3()).getIpv4()
                .getAugmentation(Ipv41.class);

        if (ipv41 != null) {
            Host dnsName = ipv41.getDstDnsname() != null
                    ? ipv41.getDstDnsname()
                    : ipv41.getSrcDnsname();
            if (dnsName != null) {
                // Get the domain name of the host.
                String domainName = dnsName.getDomainName().getValue();
                try {
                    LOG.info("domainName : " + domainName);
                    resolveDefaultDomain(ipAddresses, domainName);

                } catch (UnknownHostException e) {
                    LOG.error("Unknown host  " + domainName, e);
                }
            }
        }
        return ipAddresses;
    }

    /**
     * Drop packet if mud rules don't match.
     * 
     * @param mudUri
     * @param dropFlowUri
     */
    private void installGoToDropTableOnSrcModelMetadataMatchFlow(String mudUri,
            InstanceIdentifier<FlowCapableNode> node) {
        BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
        BigInteger metadata = createSrcModelMetadata(mudUri);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
        BigInteger newMetadata = flowCookie.getValue();
        BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
        FlowBuilder fb = FlowUtils.createMetadataMatchGoToDropTableFlow(
                flowCookie, metadata, metadataMask, flowId,
                BaseappConstants.SDNMUD_RULES_TABLE, newMetadata,
                newMetadataMask, BaseappConstants.DROP_TABLE, 0);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }

    private void installGoToDropTableOnDstModelMetadataMatchFlow(String mudUri,
            InstanceIdentifier<FlowCapableNode> node) {
        BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
        BigInteger metadata = createDstModelMetadata(mudUri);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
        BigInteger newMetadata = flowCookie.getValue();
        BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
        FlowBuilder fb = FlowUtils.createMetadataMatchGoToDropTableFlow(
                flowCookie, metadata, metadataMask, flowId,
                BaseappConstants.SDNMUD_RULES_TABLE, newMetadata,
                newMetadataMask, BaseappConstants.DROP_TABLE, 0);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }

    private InstanceIdentifier<FlowCapableNode> getCpeNode() {
        return sdnmudProvider.getNode(cpeNodeId);
    }

    private static short getProtocol(Matches matches) {
        if (matches.getL3() == null) {
            LOG.error("No IPV4 node foound -- cannto determine protocol ");
            return -1;
        }
        return ((Ipv4) matches.getL3()).getIpv4().getProtocol();

    }

    private static Integer getDestinationPort(Matches matches) {

        if ((matches.getL4()) != null && (matches.getL4() instanceof Tcp)
                && ((Tcp) matches.getL4()).getTcp()
                        .getDestinationPort() != null) {

            return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l4.tcp.tcp.destination.port.destination.port.RangeOrOperator) (((Tcp) matches
                    .getL4()).getTcp().getDestinationPort()
                            .getDestinationPort())).getPortRangeOrOperator())
                                    .getPort().getValue();

        } else if (matches.getL4() != null && (matches.getL4() instanceof Udp)
                && ((Udp) matches.getL4()).getUdp()
                        .getDestinationPort() != null) {

            return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l4.udp.udp.destination.port.destination.port.RangeOrOperator) (((Udp) matches
                    .getL4()).getUdp().getDestinationPort()
                            .getDestinationPort())).getPortRangeOrOperator())
                                    .getPort().getValue();
        } else
            return -1;

    }

    private static Integer getSourcePort(Matches matches) {
        if ((matches.getL4()) != null && (matches.getL4() instanceof Tcp)
                && ((Tcp) matches.getL4()).getTcp().getSourcePort() != null) {

            return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l4.tcp.tcp.source.port.source.port.RangeOrOperator) (((Tcp) matches
                    .getL4()).getTcp().getSourcePort().getSourcePort()))
                            .getPortRangeOrOperator()).getPort().getValue();

        } else if (matches.getL4() != null && (matches.getL4() instanceof Udp)
                && ((Udp) matches.getL4()).getUdp().getSourcePort() != null) {

            return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.aces.ace.matches.l4.udp.udp.source.port.source.port.RangeOrOperator) (((Udp) matches
                    .getL4()).getUdp().getSourcePort().getSourcePort()))
                            .getPortRangeOrOperator()).getPort().getValue();
        } else
            return -1;

    }

    private static Direction getDirectionInitiated(Matches matches) {
        if (matches.getL4() != null && (matches.getL4() instanceof Tcp)) {
            return ((Tcp) matches.getL4()).getTcp().getAugmentation(Tcp1.class)
                    .getDirectionInitiated();
        } else {
            return null;
        }
    }

    private void installPermitFromDeviceToIpAddressFlow(FlowId flowId,
            BigInteger metadata, BigInteger metadataMask, Ipv4Address address,
            int destinationPort, short protocol, boolean sendToController,
            FlowCookie flowCookie) {
        BigInteger newMetadata = flowCookie.getValue();
        BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
        FlowBuilder fb = FlowUtils
                .createMetadataDestIpAndPortMatchGoToNextTableFlow(metadata,
                        metadataMask, address, destinationPort, protocol,
                        sendToController, BaseappConstants.SDNMUD_RULES_TABLE,
                        newMetadata, newMetadataMask, flowId, flowCookie);

        this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, getCpeNode());
    }

    private void installPermitFromDeviceToIpAddressFlowRules(String mudUri,
            Matches matches, List<Ipv4Address> addresses) {
        BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
        BigInteger metadata = createSrcModelMetadata(mudUri);
        String authority = InstanceIdentifierUtils.getAuthority(mudUri);

        Short protocol = getProtocol(matches);
        if (protocol == -1) {
            LOG.error("Cannot install -- protocol field missing");
            return;
        }

        int port = getDestinationPort(matches);
        for (Ipv4Address address : addresses) {
            String flowSpec = createFlowSpec(authority, address);
            FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
            Direction direction = getDirectionInitiated(matches);
            if (direction != null) {
                LOG.info("MudFlowsInstaller: directionInitiated = "
                        + direction.getName());
            } else {
                LOG.info("MudFlowsInstaller: direction is null ");
            }
            // We want to make sure that the first packet from device does not
            // contain a Syn
            // in case the direction is ToDevice
            boolean sendToController = direction != null
                    && direction.getName().equals(Direction.ToDevice.getName());
            FlowCookie flowCookie = InstanceIdentifierUtils
                    .createFlowCookie(flowSpec);
            installPermitFromDeviceToIpAddressFlow(flowId, metadata,
                    metadataMask, address, port, protocol.shortValue(),
                    sendToController, flowCookie);
        }

    }

    private void installPermitFromIpToDeviceFlow(BigInteger metadata,
            BigInteger metadataMask, Ipv4Address address, int sourcePort,
            short protocol, boolean sendToController, FlowCookie flowCookie,
            FlowId flowId, InstanceIdentifier<FlowCapableNode> node) {
        try {
            BigInteger newMetadata = flowCookie.getValue();
            BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

            FlowBuilder fb = FlowUtils
                    .createMetadataSrcIpAndPortMatchGoToNextTableFlow(metadata,
                            metadataMask, address, sourcePort, protocol,
                            sendToController,
                            BaseappConstants.SDNMUD_RULES_TABLE, newMetadata,
                            newMetadataMask, flowId, flowCookie);
            this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

        } catch (Exception ex) {
            LOG.error("Error installing flow ", ex);
        }
    }

    private void installPermitFromIpAddressToDeviceFlowRules(String mudUri,
            Matches matches, List<Ipv4Address> addresses) throws Exception {
        BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
        BigInteger metadata = createDstModelMetadata(mudUri);
        String authority = InstanceIdentifierUtils.getAuthority(mudUri);
        Short protocol = getProtocol(matches);
        int port = getSourcePort(matches);

        for (Ipv4Address address : addresses) {
            String flowSpec = createFlowSpec(authority, address);
            Direction direction = getDirectionInitiated(matches);

            FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

            boolean sendToController = direction != null && direction.getName()
                    .equals(Direction.FromDevice.getName());

            if (direction != null) {
                LOG.info(
                        "MudFlowsInstaller : InstallePermitFromAddressToDeviceFlowRules : direction "
                                + direction.getName());
            } else {
                LOG.info(
                        "MudFlowsInstaller : InstallePermitFromAddressToDeviceFlowRules : direction is null");
            }
            FlowCookie flowCookie = InstanceIdentifierUtils
                    .createFlowCookie(flowSpec);

            installPermitFromIpToDeviceFlow(metadata, metadataMask, address,
                    port, protocol.shortValue(), sendToController, flowCookie,
                    flowId, getCpeNode());

        }

    }

    private void installPermitFromSameManufacturerFlowRule(String mudUri,
            String flowSpec, Matches matches) {
        LOG.info("InstallPermitSameManufacturerFlowRule " + mudUri
                + " flowSpec " + flowSpec);
        Short protocol = getProtocol(matches);
        if (protocol == -1) {
            LOG.error("Cannot install ");
            return;
        }
        // Range of ports that this device is allowed to talk to.

        int sourcePort = getSourcePort(matches);
        int destinationPort = getDestinationPort(matches);
        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);
        String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
        int manufacturerId = InstanceIdentifierUtils
                .getManfuacturerId(manufacturer);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

        BigInteger metadata = BigInteger.valueOf(manufacturerId)
                .shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
        BigInteger mask = SdnMudConstants.DST_MANUFACTURER_MASK
                .or(SdnMudConstants.SRC_MODEL_MASK);

        installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask,
                protocol.shortValue(), sourcePort, destinationPort,
                BaseappConstants.SDNMUD_RULES_TABLE, flowCookie, flowId,
                getCpeNode());

    }

    private void installPermitFromLocalNetworksFlowRule(String mudUri,
            String flowSpec, Matches matches) {
        int sourcePort = getSourcePort(matches);
        int destinationPort = getDestinationPort(matches);
        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        Short protocol = getProtocol(matches);
        if (protocol == -1) {
            LOG.error("Cannot install ");
            return;
        }
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

        BigInteger metadata = BigInteger.valueOf(1)
                .shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT)
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT));

        BigInteger mask = SdnMudConstants.DST_MANUFACTURER_MASK
                .or(SdnMudConstants.SRC_NETWORK_MASK);
        installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask,
                protocol.shortValue(), sourcePort, destinationPort,
                BaseappConstants.SDNMUD_RULES_TABLE, flowCookie, flowId,
                getCpeNode());
    }

    private void installPermitToLocalNetworksFlowRule(String mudUri,
            String flowSpec, Matches matches) {
        int sourcePort = getSourcePort(matches);
        int destinationPort = getDestinationPort(matches);
        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        Short protocol = getProtocol(matches);
        if (protocol == -1) {
            LOG.error("Cannot install protocol unspecified");
            return;
        }
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

        BigInteger metadata = BigInteger.valueOf(1)
                .shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT)
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT));
        BigInteger mask = SdnMudConstants.SRC_MANUFACTURER_MASK
                .or(SdnMudConstants.DST_NETWORK_MASK);

        installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask,
                protocol.shortValue(), sourcePort, destinationPort,
                BaseappConstants.SDNMUD_RULES_TABLE, flowCookie, flowId,
                getCpeNode());
    }

    private void installPermitToSameManufacturerFlowRule(String mudUri,
            String flowSpec, Matches matches) {
        LOG.info("InstallPermitSameManufacturerFlowRule " + mudUri
                + " flowSpec " + flowSpec);
        Short protocol = getProtocol(matches);
        if (protocol == -1) {
            LOG.error("invlid protocol -- cannot install ");
            return;
        }
        // Range of ports that this device is allowed to talk to.

        int sourcePort = getSourcePort(matches);
        int destinationPort = getDestinationPort(matches);
        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);
        String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
        int manufacturerId = InstanceIdentifierUtils
                .getManfuacturerId(manufacturer);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

        BigInteger metadata = BigInteger.valueOf(manufacturerId)
                .shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
        BigInteger mask = SdnMudConstants.SRC_MANUFACTURER_MASK
                .or(SdnMudConstants.DST_MODEL_MASK);

        installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask,
                protocol.shortValue(), sourcePort, destinationPort,
                BaseappConstants.SDNMUD_RULES_TABLE, flowCookie, flowId,
                getCpeNode());
    }

    private void installPermitFromSrcManSrcPortToDestManDestPortFlow(
            BigInteger metadata, BigInteger metadataMask, short protocol,
            int srcPort, int destinationPort, short tableId,
            FlowCookie flowCookie, FlowId flowId,
            InstanceIdentifier<FlowCapableNode> node) {

        BigInteger newMetadata = flowCookie.getValue();
        BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
        FlowBuilder fb = FlowUtils
                .createMetadaProtocolAndSrcDestPortMatchGoToTable(metadata,
                        metadataMask, protocol, srcPort, destinationPort,
                        tableId, newMetadata, newMetadataMask, flowId,
                        flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }

    static void installPermitPacketsFromToServer(SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node, Ipv4Address address,
            short protocol, int port) {

        LOG.info("installPermitPacketsFromToServer :  address = "
                + address.getValue());

        String nodeId = InstanceIdentifierUtils.getNodeUri(node);

        String flowSpec = MudFlowsInstaller.createFlowSpec(address);
        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);
        FlowBuilder flowBuilder = FlowUtils
                .createDestAddressPortProtocolMatchGoToNextFlow(address, port,
                        protocol, BaseappConstants.SDNMUD_RULES_TABLE, flowId,
                        flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
        flowSpec = MudFlowsInstaller.createFlowSpec(address);
        flowId = InstanceIdentifierUtils.createFlowId(nodeId);
        flowBuilder = FlowUtils.createSrcAddressPortProtocolMatchGoToNextFlow(
                address, port, protocol, BaseappConstants.SDNMUD_RULES_TABLE,
                flowId, flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

    }

    public static void installPermitPacketsFromServer(
            SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node, Ipv4Address address,
            short protocol, int port) {

        LOG.info("installPermitPacketsFromServer :  address = "
                + address.getValue());

        String nodeId = InstanceIdentifierUtils.getNodeUri(node);

        String flowSpec = MudFlowsInstaller.createFlowSpec(address);

        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);

        FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

        FlowBuilder flowBuilder = FlowUtils
                .createSrcAddressPortProtocolMatchGoToNextFlow(address, port,
                        protocol, BaseappConstants.SDNMUD_RULES_TABLE, flowId,
                        flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

    }

    public static void installPermitPacketsToServer(
            SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node, Ipv4Address address,
            short protocol, int port) {

        LOG.info("installPermitPacketsFromToServer :  address = "
                + address.getValue());

        String nodeId = InstanceIdentifierUtils.getNodeUri(node);

        String flowSpec = MudFlowsInstaller.createFlowSpec(address);

        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie(flowSpec);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

        FlowBuilder flowBuilder = FlowUtils
                .createDestAddressPortProtocolMatchGoToNextFlow(address, port,
                        protocol, BaseappConstants.SDNMUD_RULES_TABLE, flowId,
                        flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
    }

    public void installAllowToDnsAndNtpFlowRules(
            InstanceIdentifier<FlowCapableNode> node) throws Exception {
        String nodeId = InstanceIdentifierUtils.getNodeUri(node);
        if (sdnmudProvider.getControllerclassMappingDataStoreListener()
                .getControllerClass(nodeId) == null) {
            LOG.info("no controller class mapping found for node " + nodeId);
            return;
        }

        if (sdnmudProvider.getControllerclassMappingDataStoreListener()
                .getDnsAddress(nodeId) != null) {
            Ipv4Address dnsAddress = sdnmudProvider
                    .getControllerclassMappingDataStoreListener()
                    .getDnsAddress(nodeId).getIpv4Address();
            if (dnsAddress != null) {
                LOG.info("Installing DNS rules");
                installPermitPacketsFromToServer(sdnmudProvider, node,
                        dnsAddress, SdnMudConstants.UDP_PROTOCOL,
                        SdnMudConstants.DNS_PORT);
                installPermitPacketsFromToServer(sdnmudProvider, node,
                        dnsAddress, SdnMudConstants.TCP_PROTOCOL,
                        SdnMudConstants.DNS_PORT);
                installPermitPacketsToServer(sdnmudProvider, node, dnsAddress,
                        SdnMudConstants.TCP_PROTOCOL, SdnMudConstants.DNS_PORT);
                installPermitPacketsToServer(sdnmudProvider, node, dnsAddress,
                        SdnMudConstants.UDP_PROTOCOL, SdnMudConstants.DNS_PORT);

            }
        }

        if (sdnmudProvider.getControllerclassMappingDataStoreListener()
                .getNtpAddress(nodeId) != null) {
            Ipv4Address ntpAddress = sdnmudProvider
                    .getControllerclassMappingDataStoreListener()
                    .getNtpAddress(nodeId).getIpv4Address();
            if (ntpAddress != null) {
                LOG.info("Installing NTP rules");
                installPermitPacketsToServer(sdnmudProvider, node, ntpAddress,
                        SdnMudConstants.UDP_PROTOCOL,
                        SdnMudConstants.NTP_SERVER_PORT);
                installPermitPacketsFromServer(sdnmudProvider, node, ntpAddress,
                        SdnMudConstants.UDP_PROTOCOL,
                        SdnMudConstants.NTP_SERVER_PORT);
            }
        }
    }

    private static List<Ipv4Address> getControllerMatchAddresses(
            String nodeConnectorUri,
            ControllerclassMappingDataStoreListener ccmdsl, Uri mudUri,
            Uri controllerUri) {
        // Resolve it.
        List<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
        LOG.info(
                "Resolving controller address for " + controllerUri.getValue());
        if (ccmdsl.getControllerAddresses(nodeConnectorUri, mudUri,
                controllerUri.getValue()) != null) {
            List<IpAddress> controllerIpAddresses = ccmdsl
                    .getControllerAddresses(nodeConnectorUri, mudUri,
                            controllerUri.getValue());
            for (IpAddress ipAddress : controllerIpAddresses) {
                LOG.info("controllerAddress "
                        + ipAddress.getIpv4Address().getValue());
                ipAddresses.add(ipAddress.getIpv4Address());
            }
        }
        return ipAddresses;
    }

    private static String createLocalFlowSpec(String manufacturer) {
        return "manufacturer=" + manufacturer + "%scope="
                + SdnMudConstants.LOCAL;
    }

    private static BigInteger createSrcModelMetadata(String mudUri) {
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        return BigInteger.valueOf(modelId)
                .shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
    }

    private static BigInteger createDstModelMetadata(String mudUri) {
        return BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
                .shiftLeft(SdnMudConstants.DST_MODEL_SHIFT);
    }

    private static BigInteger createSrcManufacturerMetadata(
            String manufacturer) {
        return BigInteger
                .valueOf(
                        InstanceIdentifierUtils.getManfuacturerId(manufacturer))
                .shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT);
    }

    public static void installStampManufacturerModelFlowRules(MacAddress srcMac,
            String mudUri, SdnmudProvider sdnmudProvider,
            InstanceIdentifier<FlowCapableNode> node) {

        String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
        int manufacturerId = InstanceIdentifierUtils
                .getManfuacturerId(manufacturer);
        int modelId = InstanceIdentifierUtils.getModelId(mudUri);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        BigInteger metadata = createSrcManufacturerMetadata(manufacturer)
                .or(createSrcModelMetadata(mudUri));

        BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK
                .or(SdnMudConstants.SRC_MODEL_MASK);

        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie("stamp-manufactuer-model-flow");

        FlowBuilder fb = FlowUtils
                .createSourceMacMatchSetMetadataGoToNextTableFlow(srcMac,
                        metadata, metadataMask,
                        BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
                        flowId, flowCookie);

        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

        flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        metadata = BigInteger.valueOf(manufacturerId)
                .shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
                .or(BigInteger.valueOf(modelId)
                        .shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
        metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK
                .or(SdnMudConstants.DST_MODEL_MASK);

        fb = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(srcMac,
                metadata, metadataMask,
                BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId,
                flowCookie);

        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }

    /**
     * Retrieve and install flows for a device of a given MAC address.
     * 
     * @param sdnmudProvider
     *            -- our provider.
     * 
     * @param deviceMacAddress
     *            -- the mac address of the device.
     * 
     * @param node
     *            -- the node on which the packet was received.
     * @param nodeUri
     *            -- the URI of the node.
     */
    public synchronized void tryInstallFlows(Mud mud) {
        try {
            Uri mudUri = mud.getMudUrl();

            ControllerclassMappingDataStoreListener controllerClassMapDsListener = sdnmudProvider
                    .getControllerclassMappingDataStoreListener();
            if (controllerClassMapDsListener
                    .getControllerClass(this.cpeNodeId) == null) {
                LOG.info(
                        "Cannot find ControllerClass mapping for the switch  -- not installing ACLs. nodeUrl "
                                + cpeNodeId);
                return;
            } else {
                LOG.info(
                        "installFlows: Found a controllerclass mapping for the switch ");
            }

            // Get the flow commit wrapper to talk to the switch.
            FlowCommitWrapper flowCommitWrapper = sdnmudProvider
                    .getFlowCommitWrapper();
            // Delete the existing flows corresponding to this profile.

            String authority = InstanceIdentifierUtils.getAuthority(mudUri);

            // Remove the flow rules for the given device for this MUD url.
            InstanceIdentifier<FlowCapableNode> node = sdnmudProvider
                    .getNode(cpeNodeId);
            if (node == null) {
                LOG.info(
                        "installFlows -- cpe Node is null -- skipping MUD install.");
                return;
            }

            // Delete all the flows previously associated with this MUD URI.

            flowCommitWrapper.deleteFlows(node, mudUri.getValue(),
                    BaseappConstants.SDNMUD_RULES_TABLE, null);

            /*
             * Track that we have added a node for this device MAC address for
             * this node. i.e. we store MUD rules for this device on the given
             * node.
             */

            LOG.info("installFlows : authority (manufacturer) = " + "["
                    + authority + "]");

            sdnmudProvider.addMudNode(authority, node);

            // Records that the CPE node owns this mud URI.
            sdnmudProvider.addMudUri(cpeNodeId, mudUri);

            /*
             * Drop table is where all the unsuccessful matches land up. Push
             * default drop packet flows that will drop the packet if a MUD rule
             * does not match.
             */
            installGoToDropTableOnSrcModelMetadataMatchFlow(mudUri.getValue(),
                    getCpeNode());
            installGoToDropTableOnDstModelMetadataMatchFlow(mudUri.getValue(),
                    getCpeNode());

            /*
             * Fetch and install the MUD ACLs. First install the "from-device"
             * rules.
             */
            FromDevicePolicy fromDevicePolicy = mud.getFromDevicePolicy();
            if (fromDevicePolicy != null) {
                AccessLists accessLists = fromDevicePolicy.getAccessLists();
                for (AccessList accessList : accessLists.getAccessList()) {
                    String aclName = accessList.getName();
                    Aces aces = sdnmudProvider.getAclDataStoreListener()
                            .getAces(mudUri.getValue(), aclName);
                    if (aces != null) {
                        for (Ace ace : aces.getAce()) {
                            if (ace.getActions().getForwarding()
                                    .equals(Accept.class)) {

                                Matches matches = ace.getMatches();
                                int matchesType = matchesType(matches);
                                LOG.info("matchType " + matchesType);
                                if (matchesType == MatchesType.DNS_MATCH) {
                                    List<Ipv4Address> addresses = getMatchAddresses(
                                            matches);
                                    installPermitFromDeviceToIpAddressFlowRules(
                                            mudUri.getValue(), matches,
                                            addresses);
                                } else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
                                    Matches1 matches1 = matches
                                            .getAugmentation(Matches1.class);
                                    Uri controllerUri = matches1.getMud()
                                            .getController();
                                    List<Ipv4Address> addresses = getControllerMatchAddresses(
                                            cpeNodeId,
                                            controllerClassMapDsListener,
                                            mudUri, controllerUri);
                                    installPermitFromDeviceToIpAddressFlowRules(
                                            mudUri.getValue(), matches,
                                            addresses);
                                } else if (matchesType == MatchesType.MY_CONTROLLER) {
                                    List<Ipv4Address> addresses = getControllerMatchAddresses(
                                            cpeNodeId,
                                            controllerClassMapDsListener,
                                            mudUri, mudUri);
                                    installPermitFromDeviceToIpAddressFlowRules(
                                            mudUri.getValue(), matches,
                                            addresses);
                                } else if (matchesType == MatchesType.LOCAL_NETWORKS) {
                                    String flowSpec = createLocalFlowSpec(
                                            authority);
                                    installPermitToLocalNetworksFlowRule(
                                            mudUri.getValue(), flowSpec,
                                            matches);
                                } else if (matchesType == MatchesType.SAME_MANUFACTURER) {
                                    String flowSpec = createLocalFlowSpec(
                                            authority);
                                    installPermitFromSameManufacturerFlowRule(
                                            mudUri.getValue(), flowSpec,
                                            matches);
                                }
                            } else {
                                LOG.info("DENY rule not implemented");
                            }
                        }

                    } else {
                        LOG.info("Could not find ACEs for " + aclName);
                    }
                }
            }

            ToDevicePolicy toDevicePolicy = mud.getToDevicePolicy();
            if (toDevicePolicy != null) {
                AccessLists accessLists = toDevicePolicy.getAccessLists();
                for (AccessList accessList : accessLists.getAccessList()) {
                    String aclName = accessList.getName();
                    Aces aces = sdnmudProvider.getAclDataStoreListener()
                            .getAces(mudUri.getValue(), aclName);
                    if (aces != null) {
                        for (Ace ace : aces.getAce()) {
                            if (ace.getActions().getForwarding()
                                    .equals(Accept.class)) {

                                Matches matches = ace.getMatches();
                                int matchesType = matchesType(matches);
                                LOG.info("matchType " + matchesType);
                                if (matchesType == MatchesType.DNS_MATCH) {
                                    List<Ipv4Address> addresses = getMatchAddresses(
                                            matches);
                                    installPermitFromIpAddressToDeviceFlowRules(
                                            mudUri.getValue(), matches,
                                            addresses);
                                } else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
                                    Matches1 matches1 = matches
                                            .getAugmentation(Matches1.class);
                                    Uri controllerUri = matches1.getMud()
                                            .getController();
                                    List<Ipv4Address> addresses = getControllerMatchAddresses(
                                            cpeNodeId,
                                            controllerClassMapDsListener,
                                            mudUri, controllerUri);
                                    installPermitFromIpAddressToDeviceFlowRules(
                                            mudUri.getValue(), matches,
                                            addresses);
                                } else if (matchesType == MatchesType.MY_CONTROLLER) {
                                    List<Ipv4Address> addresses = getControllerMatchAddresses(
                                            cpeNodeId,
                                            controllerClassMapDsListener,
                                            mudUri, mudUri);
                                    installPermitFromIpAddressToDeviceFlowRules(
                                            mudUri.getValue(), matches,
                                            addresses);
                                } else if (matchesType == MatchesType.LOCAL_NETWORKS) {
                                    String flowSpec = createLocalFlowSpec(
                                            authority);
                                    installPermitFromLocalNetworksFlowRule(
                                            mudUri.getValue(), flowSpec,
                                            matches);
                                } else if (matchesType == MatchesType.SAME_MANUFACTURER) {
                                    String flowSpec = createLocalFlowSpec(
                                            authority);
                                    installPermitToSameManufacturerFlowRule(
                                            mudUri.getValue(), flowSpec,
                                            matches);
                                }
                            } else {
                                LOG.info("DENY rules not implemented");
                            }

                        }
                    } else {
                        LOG.info("Could not find ACEs for " + aclName);
                    }

                }
            }

        } catch (Exception ex) {
            LOG.error(
                    "MudFlowsInstaller: Exception caught installing MUD Flow ",
                    ex);
        }

    }

}
