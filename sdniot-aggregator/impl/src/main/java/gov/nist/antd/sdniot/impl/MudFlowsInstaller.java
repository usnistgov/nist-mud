package gov.nist.antd.sdniot.impl;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180202.Accept;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180202.access.lists.acl.Aces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180202.access.lists.acl.aces.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180202.access.lists.acl.aces.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180202.access.lists.acl.aces.ace.matches.l3.Ipv4;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev180124.Matches1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.Matches1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.access.lists.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.access.lists.access.lists.AccessList;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.mud.grouping.FromDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.mud.grouping.ToDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev180202.port.range.or.operator.port.range.or.operator.Operator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static String createDropFlowUri(String manufacturer) {
    return "flow:" + manufacturer + ":" + SdnMudConstants.MUD_FLOW_MISS;
  }

  private static String createFlowUri(String manufacturer, Ipv4Address address) {
    try {
      if (InetAddress.getByName(address.getValue()).isSiteLocalAddress()) {
        return "flow:" + manufacturer + ":" + SdnMudConstants.LOCAL;
      } else {
        return "flow:" + manufacturer + ":" + SdnMudConstants.REMOTE;

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


    if (matches1.getMud() != null && matches1.getMud().getController() != null) {
      return MatchesType.CONTROLLER_MAPPING;
    } else if (matches1.getMud() != null && matches1.getMud().getManufacturer() != null) {
      return MatchesType.MANUFACTURER;
    } else if (matches1.getMud() != null && matches1.getMud().getModel() != null) {
      return MatchesType.MODEL;
    } else if (matches1.getMud() != null && matches1.getMud().isLocalNetworks() != null
        && matches1.getMud().isLocalNetworks()) {
      return MatchesType.LOCAL_NETWORKS;
    } else if (matches1.getMud() != null && matches1.getMud().isSameManufacturer() != null
        && matches1.getMud().isSameManufacturer()) {
      return MatchesType.SAME_MANUFACTURER;
    } else if (matches1.getMud() != null && matches1.getMud().isMyController() != null
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

  private static void resolveDefaultDomain(ArrayList<Ipv4Address> ipAddresses, String domainName)
      throws UnknownHostException {
    InetAddress[] inetAddresses = InetAddress.getAllByName(domainName);

    for (InetAddress inetAddress : inetAddresses) {
      String hostAddress = inetAddress.getHostAddress();
      LOG.info("domainName " + domainName + "inetAddress : " + hostAddress);
      if (isValidIpV4Address(hostAddress)) {
        ipAddresses.add(new Ipv4Address(hostAddress));
      }
    }
  }


  /**
   * Resolve the addresses for a Match clause.
   * 
   *
   * @param matches - the matches clause.
   * @return - a list of addresses that match.
   * 
   */
  private static List<Ipv4Address> getMatchAddresses(Matches matches) {

    ArrayList<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
  	org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev180124.Matches1 matches1 
    		= matches.getAugmentation(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev180124.Matches1.class);
    if (matches1 != null) {
      Host dnsName = matches1.getDstDnsname() != null ? matches1.getDstDnsname() : matches1.getSrcDnsname();
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
  private void installGoToIds1OnSrcModelMetadataMatchFlow(String mudUri, String dropFlowUri,
      InstanceIdentifier<FlowCapableNode> node) {
    BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
    BigInteger metadata = createSrcModelMetadata(mudUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(dropFlowUri);
    FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask,
        flowId, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE1, 0);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }

  private void installGoToIds1OnDstModelMetadataMatchFlow(String mudUri, String dropFlowUri) {
    BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
    BigInteger metadata = createDstModelMetadata(mudUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(dropFlowUri);
    FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask,
        flowId, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE1, 0);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, getCpeNode());
  }

  private InstanceIdentifier<FlowCapableNode> getCpeNode() {
    return sdnmudProvider.getNode(cpeNodeId);
  }

  private InstanceIdentifier<FlowCapableNode> getNpeNode() {
    return sdnmudProvider.getNode(sdnmudProvider.getNpeSwitchUri(cpeNodeId));
  }

  private static SourcePortRange getSourcePortRange(Matches matches) {
    return (SourcePortRange) (matches.getL4());

  }

  private static short getProtocol(Matches matches) {
    return ((Ipv4) matches.getL3()).getIpv4().getProtocol();
  }

  private static DestinationPortRange getDestinationPortRange(Matches matches) {
    return ((DestinationPortRange) (matches.getL4()));

  }

  private static Integer getPort(Matches matches) {
    return ((Operator) (matches.getL4())).getPort().getValue();
  }



  private void installPermitFromDeviceToIpAddressFlow(FlowId flowId, BigInteger metadata,
      BigInteger metadataMask, Ipv4Address address, int destinationPort, short protocol,
      FlowCookie flowCookie) {

    FlowBuilder fb = FlowUtils.createMetadataDestIpAndPortMatchGoTo(metadata, metadataMask, address,
        destinationPort, protocol, SdnMudConstants.SDNMUD_RULES_TABLE,
        SdnMudConstants.IDS_RULES_TABLE, flowId, flowCookie);

    this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, getCpeNode());
  }


  private void installPermitFromDeviceToIpAddressFlowRules(String mudUri, Matches matches,
      List<Ipv4Address> addresses) {
    BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
    BigInteger metadata = createSrcModelMetadata(mudUri);
    String authority = InstanceIdentifierUtils.getAuthority(mudUri);

    Short protocol = getProtocol(matches);
    if (matches.getL4().getImplementedInterface().equals(DestinationPortRange.class)) {
      DestinationPortRange destinationPortRange = getDestinationPortRange(matches);
     
      for (Ipv4Address address : addresses) {
        for (int port =
            destinationPortRange.getLowerPort().getValue().intValue(); port <= destinationPortRange
                .getUpperPort().getValue().intValue(); port++) {
          String flowUri = createFlowUri(authority, address);
          FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
          FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
          installPermitFromDeviceToIpAddressFlow(flowId, metadata, metadataMask, address, port,
              protocol.shortValue(), flowCookie);
        }
      }
    } else  {
      int port = getPort(matches);
      for (Ipv4Address address : addresses) {
        String flowUri = createFlowUri(authority, address);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
        installPermitFromDeviceToIpAddressFlow(flowId, metadata, metadataMask, address, port,
            protocol.shortValue(), flowCookie);
      }
    }
  }

  private void installPermitFromIpToDeviceFlow(BigInteger metadata, BigInteger metadataMask,
      Ipv4Address address, int sourcePort, short protocol, FlowCookie flowCookie, FlowId flowId,
      InstanceIdentifier<FlowCapableNode> node) {
    try {
      FlowBuilder fb = FlowUtils.createMetadataSrcIpAndPortMatchGoTo(metadata, metadataMask,
          address, sourcePort, protocol, SdnMudConstants.SDNMUD_RULES_TABLE,
          SdnMudConstants.IDS_RULES_TABLE, flowId, flowCookie);
      this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    } catch (Exception ex) {
      LOG.error("Error installing flow ", ex);
    }
  }

  private void installPermitFromIpAddressToDeviceFlowRules(String mudUri, Matches matches,
      List<Ipv4Address> addresses) throws Exception {
    BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
    BigInteger metadata = createDstModelMetadata(mudUri);
    String authority = InstanceIdentifierUtils.getAuthority(mudUri);
    Short protocol = ((Ipv4) matches.getL3()).getIpv4().getProtocol();
    if (matches.getL4().getImplementedInterface().equals(SourcePortRange.class)) {
      SourcePortRange sourcePortRange = getSourcePortRange(matches);

      for (Ipv4Address address : addresses) {
        for (int port =
            sourcePortRange.getLowerPort().getValue().intValue(); port <= sourcePortRange
                .getUpperPort().getValue().intValue(); port++) {
          String flowUri = createFlowUri(authority, address);
          FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
          FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);

          installPermitFromIpToDeviceFlow(metadata, metadataMask, address, port,
              protocol.shortValue(), flowCookie, flowId, getCpeNode());

        }
      }
    } else {
      int port = getPort(matches);
      for (Ipv4Address address : addresses) {
        String flowUri = createFlowUri(authority, address);
        FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
        installPermitFromIpToDeviceFlow(metadata, metadataMask, address, port,
            protocol.shortValue(), flowCookie, flowId, getCpeNode());
      }
    }
  }



  private void installPermitSameManufacturerFlowRule(String mudUri, String flowUri,
      Matches matches) {
    Short protocol = getProtocol(matches);
    // Range of ports that this device is allowed to talk to.
    SourcePortRange sourcePortRange = getSourcePortRange(matches);
    DestinationPortRange destinationPortRange = getDestinationPortRange(matches);
    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
    String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
    int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
    int modelId = InstanceIdentifierUtils.getModelId(mudUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);


    BigInteger metadata =
        BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
            .or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
    BigInteger mask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);


    if (sourcePortRange == null && destinationPortRange == null) {
      installPermitFromSourceManufacturerToDestManifacturerFlow(metadata, mask,
          protocol.shortValue(), SdnMudConstants.SDNMUD_RULES_TABLE,
          SdnMudConstants.IDS_RULES_TABLE, flowCookie, flowId, this.getCpeNode());
    } else if (destinationPortRange == null) {
      for (int port = sourcePortRange.getLowerPort().getValue().intValue(); port <= sourcePortRange
          .getUpperPort().getValue().intValue(); port++) {
        installPermitFromSrcManufacturerToDestPortDestManifacturerFlow(metadata, mask,
            protocol.shortValue(), port, SdnMudConstants.SDNMUD_RULES_TABLE,
            SdnMudConstants.IDS_RULES_TABLE, flowCookie, flowId, getCpeNode());
      }
    } else if (sourcePortRange == null) {
      for (int port =
          destinationPortRange.getLowerPort().getValue().intValue(); port <= destinationPortRange
              .getUpperPort().getValue().intValue(); port++) {
        installPermitFromSrcManufacturerToDestPortDestManifacturerFlow(metadata, mask,
            protocol.shortValue(), port, SdnMudConstants.SDNMUD_RULES_TABLE,
            SdnMudConstants.IDS_RULES_TABLE, flowCookie, flowId, getCpeNode());
      }
    }

  }

  private void installPermitFromSourceManufacturerToDestManifacturerFlow(BigInteger metadata,
      BigInteger metadataMask, short protocol, short tableId, short targetTableId,
      FlowCookie flowCookie, FlowId flowId, InstanceIdentifier<FlowCapableNode> node) {

    FlowBuilder fb = FlowUtils.createMetadaAndProtocolMatchGoToTable(metadata, metadataMask,
        protocol, tableId, targetTableId, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }

  private void installPermitFromSrcManufacturerToDestPortDestManifacturerFlow(BigInteger metadata,
      BigInteger metadataMask, short protocol, int destinationPort, short tableId,
      short targetTableId, FlowCookie flowCookie, FlowId flowId,
      InstanceIdentifier<FlowCapableNode> node) {

    FlowBuilder fb = FlowUtils.createMetadaProtocolAndDestPortMatchGoToTable(metadata, metadataMask,
        protocol, destinationPort, tableId, targetTableId, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }

  private void installPermitFromSrcManufacturerSrcPortToDestManifacturerFlow(BigInteger metadata,
      BigInteger metadataMask, short protocol, int destinationPort, short tableId,
      short targetTableId, FlowCookie flowCookie, FlowId flowId,
      InstanceIdentifier<FlowCapableNode> node) {

    FlowBuilder fb = FlowUtils.createMetadaProtocolAndSrcPortMatchGoToTable(metadata, metadataMask,
        protocol, destinationPort, tableId, targetTableId, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }



  private void installPermitPacketsFromToServer(String mudUri, Ipv4Address address, int port) {

    LOG.info("installPermitPacketsFromToServer :  dnsAddress " + address.getValue());

    String authority = InstanceIdentifierUtils.getAuthority(mudUri);
    BigInteger metadata = BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
        .shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
    BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;

    FlowCookie flowCookie =
        InstanceIdentifierUtils.createFlowCookie(createFlowUri(authority, address));
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

    FlowBuilder flowBuilder = FlowUtils.createPermitPacketsToServerFlow(metadata, metadataMask,
        address, port, SdnMudConstants.UDP_PROTOCOL, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, getCpeNode());

    flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    flowBuilder = FlowUtils.createPermitPacketsToServerFlow(metadata, metadataMask, address, port,
        SdnMudConstants.TCP_PROTOCOL, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, getCpeNode());

    metadata = BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
        .shiftLeft(SdnMudConstants.DST_MODEL_SHIFT);
    metadataMask = SdnMudConstants.DST_MODEL_MASK;
    try {
      InstanceIdentifier<FlowCapableNode> node = getCpeNode();
      for (short protocol : new Short[] {SdnMudConstants.UDP_PROTOCOL,
          SdnMudConstants.TCP_PROTOCOL}) {
        flowId = InstanceIdentifierUtils.createFlowId(mudUri);
        flowBuilder = FlowUtils.createPermitPacketsFromServerFlow(metadata, metadataMask, address,
            protocol, flowId, flowCookie);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
      }
    } catch (Exception ex) {
      LOG.error("Error resolving address " + address.getValue());
    }
  }



  public static void installPermitPacketsFromToServer(SdnmudProvider sdnmudProvider,
      InstanceIdentifier<FlowCapableNode> node, Ipv4Address address, short protocol, int port) {

    LOG.info("installPermitPacketsFromToServer :  address = " + address.getValue());

    String nodeId = InstanceIdentifierUtils.getNodeUri(node);

    String flowUri = MudFlowsInstaller.createFlowUri(SdnMudConstants.NONE, address);

    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

    FlowBuilder flowBuilder = FlowUtils.createPermitPacketsToServerFlow(address, port, protocol,
        SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
    flowId = InstanceIdentifierUtils.createFlowId(nodeId);
    flowBuilder = FlowUtils.createPermitPacketsFromServerFlow(address, port, protocol,
        SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

  }

  public static void installPermitPacketsFromServer(SdnmudProvider sdnmudProvider,
      InstanceIdentifier<FlowCapableNode> node, Ipv4Address address, short protocol, int port) {

    LOG.info("installPermitPacketsFromToServer :  address = " + address.getValue());

    String nodeId = InstanceIdentifierUtils.getNodeUri(node);

    String flowUri = MudFlowsInstaller.createFlowUri(SdnMudConstants.NONE, address);

    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

    FlowBuilder flowBuilder = FlowUtils.createPermitPacketsFromServerFlow(address, port, protocol,
        SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

  }

  public static void installPermitPacketsToServer(SdnmudProvider sdnmudProvider,
      InstanceIdentifier<FlowCapableNode> node, Ipv4Address address, short protocol, int port) {

    LOG.info("installPermitPacketsFromToServer :  address = " + address.getValue());

    String nodeId = InstanceIdentifierUtils.getNodeUri(node);

    String flowUri = MudFlowsInstaller.createFlowUri(SdnMudConstants.NONE, address);

    FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeId);

    FlowBuilder flowBuilder = FlowUtils.createPermitPacketsToServerFlow(address, port, protocol,
        SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE, flowId, flowCookie);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

  }


  private void installDenyToMacFlow(String mudUri, MacAddress destinationMacAddress,
      FlowCookie flowCookie) {

    LOG.info("installDropPacketsToMacFlow " + mudUri + " destination "
        + destinationMacAddress.getValue());
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
    BigInteger metadata = createSrcModelMetadata(mudUri);

    FlowBuilder flow = FlowUtils.createMetadataAndDestMacMatchGoToTableFlow(metadata, metadataMask,
        destinationMacAddress, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.IDS_RULES_TABLE1,
        flowCookie, flowId);
    sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, getCpeNode());
  }


  public static void installAllowToDnsAndNtpFlowRules(SdnmudProvider sdnmudProvider,
      InstanceIdentifier<FlowCapableNode> node) throws Exception {
    String nodeId = InstanceIdentifierUtils.getNodeUri(node);
    if (sdnmudProvider.getControllerclassMappingDataStoreListener()
        .getControllerClass(nodeId) == null) {
      LOG.info("no controller class mapping found for node " + nodeId);
      return;
    }

    String npeNodeId = sdnmudProvider.getNpeSwitchUri(nodeId);
    InstanceIdentifier<FlowCapableNode> npeNode = sdnmudProvider.getNode(npeNodeId);

    if (npeNode == null) {
      LOG.info("NPE node is null -- not installing flows " + nodeId);
    }

    if (sdnmudProvider.getControllerclassMappingDataStoreListener().getDnsAddress(nodeId) != null) {
      Ipv4Address dnsAddress = sdnmudProvider.getControllerclassMappingDataStoreListener()
          .getDnsAddress(nodeId).getIpv4Address();
      if (dnsAddress != null) {
        LOG.info("Installing DNS rules");
        installPermitPacketsFromToServer(sdnmudProvider, node, dnsAddress,
            SdnMudConstants.UDP_PROTOCOL, SdnMudConstants.DNS_PORT);
        installPermitPacketsFromToServer(sdnmudProvider, node, dnsAddress,
            SdnMudConstants.TCP_PROTOCOL, SdnMudConstants.DNS_PORT);
        installPermitPacketsToServer(sdnmudProvider, node, dnsAddress, SdnMudConstants.TCP_PROTOCOL,
            SdnMudConstants.DNS_PORT);
        installPermitPacketsToServer(sdnmudProvider, node, dnsAddress, SdnMudConstants.UDP_PROTOCOL,
            SdnMudConstants.DNS_PORT);

      }
    }

    if (sdnmudProvider.getControllerclassMappingDataStoreListener().getNtpAddress(nodeId) != null) {
      Ipv4Address ntpAddress = sdnmudProvider.getControllerclassMappingDataStoreListener()
          .getNtpAddress(nodeId).getIpv4Address();
      if (ntpAddress != null) {
        LOG.info("Installing NTP rules");
        installPermitPacketsToServer(sdnmudProvider, node, ntpAddress, SdnMudConstants.UDP_PROTOCOL,
            SdnMudConstants.NTP_SERVER_PORT);
        installPermitPacketsFromServer(sdnmudProvider, node, ntpAddress,
            SdnMudConstants.UDP_PROTOCOL, SdnMudConstants.NTP_SERVER_PORT);
      }
    }
  }


  private static List<Ipv4Address> getControllerMatchAddresses(String nodeConnectorUri,
      ControllerclassMappingDataStoreListener ccmdsl, Uri mudUri, Uri controllerUri) {
    // Resolve it.
    List<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
    LOG.info("Resolving controller address for " + controllerUri.getValue());
    if (ccmdsl.getControllerAddresses(nodeConnectorUri, mudUri, controllerUri.getValue()) != null) {
      List<IpAddress> controllerIpAddresses =
          ccmdsl.getControllerAddresses(nodeConnectorUri, mudUri, controllerUri.getValue());
      for (IpAddress ipAddress : controllerIpAddresses) {
        LOG.info("controllerAddress " + ipAddress.getIpv4Address().getValue());
        ipAddresses.add(ipAddress.getIpv4Address());
      }
    }
    return ipAddresses;
  }



  private static String createLocalFlowUri(String manufacturer) {
    return "flow:" + manufacturer + ":" + SdnMudConstants.LOCAL;
  }

  public BigInteger createSrcModelMetadata(String mudUri) {
    int modelId = InstanceIdentifierUtils.getModelId(mudUri);
    return BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
  }

  public static BigInteger createDstModelMetadata(String mudUri) {
    return BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
        .shiftLeft(SdnMudConstants.DST_MODEL_SHIFT);
  }

  public static BigInteger createSrcManufacturerMetadata(String manufacturer) {
    return BigInteger.valueOf(InstanceIdentifierUtils.getManfuacturerId(manufacturer))
        .shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT);
  }

  public static BigInteger createDstManufacturerMetadata(String manufacturer) {
    return BigInteger.valueOf(InstanceIdentifierUtils.getManfuacturerId(manufacturer))
        .shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT);
  }

  public static void installStampManufacturerModelFlowRules(MacAddress srcMac, String mudUri,
      SdnmudProvider sdnmudProvider, InstanceIdentifier<FlowCapableNode> node) {

    String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
    int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
    int modelId = InstanceIdentifierUtils.getModelId(mudUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    BigInteger metadata =
        BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
            .or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

    BigInteger metadataMask =
        SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

    FlowCookie flowCookie =
        InstanceIdentifierUtils.createFlowCookie("stamp-manufactuer-model-flow");

    FlowBuilder fb = FlowUtils.createSourceMacMatchSetMetadataAndGoToTable(srcMac, metadata,
        metadataMask, SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
        SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);

    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

    flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
        .or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
    metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);

    fb = FlowUtils.createDestMacMatchSetMetadataAndGoToTable(srcMac, metadata, metadataMask,
        SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, SdnMudConstants.SDNMUD_RULES_TABLE,
        flowId, flowCookie);

    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }


  public static void installStampSrcMacManufacturerModelFlowRules(MacAddress srcMac, String mudUri,
      SdnmudProvider sdnmudProvider, InstanceIdentifier<FlowCapableNode> node) {
    String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
    int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
    int modelId = InstanceIdentifierUtils.getModelId(mudUri);
    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    BigInteger metadata =
        BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
            .or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

    BigInteger metadataMask =
        SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

    FlowCookie flowCookie =
        InstanceIdentifierUtils.createFlowCookie("stamp-src-mac-manufactuer-model-flow");

    FlowBuilder fb = FlowUtils.createSourceMacMatchSetMetadataAndGoToTable(srcMac, metadata,
        metadataMask, SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
        SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);

    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }

  public static void installStampDstMacManufacturerModelFlowRules(MacAddress srcMac, String mudUri,
      SdnmudProvider sdnmudProvider, InstanceIdentifier<FlowCapableNode> node) {

    String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
    int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
    int modelId = InstanceIdentifierUtils.getModelId(mudUri);

    FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
    BigInteger metadata =
        BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
            .or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
    BigInteger metadataMask =
        SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);
    FlowCookie flowCookie =
        InstanceIdentifierUtils.createFlowCookie("stamp-dst-mac-manufactuer-model-flow");

    FlowBuilder fb = FlowUtils.createDestMacMatchSetMetadataAndGoToTable(srcMac, metadata,
        metadataMask, SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE,
        SdnMudConstants.SDNMUD_RULES_TABLE, flowId, flowCookie);

    sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
  }


  /**
   * Retrieve and install flows for a device of a given MAC address.
   * 
   * @param sdnmudProvider -- our provider.
   * 
   * @param deviceMacAddress -- the mac address of the device.
   * 
   * @param node -- the node on which the packet was received.
   * @param nodeUri -- the URI of the node.
   */
  public synchronized void installFlows(Mud mud) {
    try {
      Uri mudUri = mud.getMudUrl();


      String npeNodeUri = sdnmudProvider.getNpeSwitchUri(this.cpeNodeId);
      if (npeNodeUri == null) {
        LOG.error("Cannot find NPE node URI for CPE node URI " + this.cpeNodeId);
        return;
      } else {
        LOG.info("installFlows: Found NPE node URI");
      }

      InstanceIdentifier<FlowCapableNode> npeNode = sdnmudProvider.getNode(npeNodeUri);

      if (npeNode == null) {
        LOG.error("installFlows: cannot find npe Flow capable node for npe node " + npeNodeUri);
        return;
      } else {
        LOG.info("installFlows: Found NPE flow capable node");
      }

      ControllerclassMappingDataStoreListener controllerClassMapDsListener =
          sdnmudProvider.getControllerclassMappingDataStoreListener();
      if (controllerClassMapDsListener.getControllerClass(this.cpeNodeId) == null) {
        LOG.info(
            "Cannot find ControllerClass mapping for the switch  -- not installing ACLs. nodeUrl "
                + cpeNodeId);
        return;
      } else {
        LOG.info("installFlows: Found a controllerclass mapping for the switch ");
      }

      // Get the flow commit wrapper to talk to the switch.
      FlowCommitWrapper flowCommitWrapper = sdnmudProvider.getFlowCommitWrapper();
      // Delete the existing flows corresponding to this profile.

      String authority = InstanceIdentifierUtils.getAuthority(mudUri);

      // Remove the flow rules for the given device for this MUD url.
      InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(cpeNodeId);
      if (cpeNodeId == null) {
        LOG.info("installFlows -- cpe Node is null -- skipping MUD install.");
        return;
      }
      flowCommitWrapper.deleteFlows(node, "flow:" + authority, SdnMudConstants.SDNMUD_RULES_TABLE,
          null);

      // Track that we have added a node for this device MAC address for
      // this node. i.e. we store MUD rules for this device on the given
      // node.

      LOG.info("installFlows : authority (manufacturer) = " + "[" + authority + "]");

      sdnmudProvider.addMudNode(authority, node);

      // Records that the CPE node owns this mud URI.
      sdnmudProvider.addMudUri(cpeNodeId, mudUri);

      // Create the drop flow rule for the MUD URI.
      String dropFlowUri = createDropFlowUri(authority);


      // IDS1 table is where all the unsuccessful matches land up.
      installGoToIds1OnSrcModelMetadataMatchFlow(mudUri.getValue(), dropFlowUri, getCpeNode());

      // Fetch and install the MUD ACLs.

      // First install the "from-device" rules.
      FromDevicePolicy fromDevicePolicy = mud.getFromDevicePolicy();
      if (fromDevicePolicy != null) {
        AccessLists accessLists = fromDevicePolicy.getAccessLists();
        for (AccessList accessList : accessLists.getAccessList()) {
          String aclName = accessList.getName();
          Aces aces = sdnmudProvider.getAclDataStoreListener().getAces(mudUri.getValue(), aclName);
          if (aces != null) {
            for (Ace ace : aces.getAce()) {
              if (ace.getActions().getForwarding().equals(Accept.class)) {
                Matches matches = ace.getMatches();
                int matchesType = matchesType(matches);
                LOG.info("matchType " + matchesType);
                if (matchesType == MatchesType.DNS_MATCH) {
                  List<Ipv4Address> addresses = getMatchAddresses(matches);
                  installPermitFromDeviceToIpAddressFlowRules(mudUri.getValue(), matches,
                      addresses);
                } else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
                  Matches1 matches1 = matches.getAugmentation(Matches1.class);
                  Uri controllerUri = matches1.getMud().getController();
                  List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
                      controllerClassMapDsListener, mudUri, controllerUri);
                  installPermitFromDeviceToIpAddressFlowRules(mudUri.getValue(), matches,
                      addresses);
                } else if (matchesType == MatchesType.MY_CONTROLLER) {
                  List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
                      controllerClassMapDsListener, mudUri, mudUri);
                  installPermitFromDeviceToIpAddressFlowRules(mudUri.getValue(), matches,
                      addresses);
                } else if (matchesType == MatchesType.LOCAL_NETWORKS) {
                  // Packets from this device cannot be
                  // routed.
                  String flowUri = createDropFlowUri(authority);
                  FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
                  MacAddress routerMacAddress = sdnmudProvider
                      .getControllerclassMappingDataStoreListener().getRouterMac(cpeNodeId);
                  installDenyToMacFlow(mudUri.getValue(), routerMacAddress, flowCookie);

                } else if (matchesType == MatchesType.SAME_MANUFACTURER) {
                  String flowUri = createLocalFlowUri(authority);
                  installPermitSameManufacturerFlowRule(mudUri.getValue(), flowUri, matches);
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
          Aces aces = sdnmudProvider.getAclDataStoreListener().getAces(mudUri.getValue(), aclName);
          if (aces != null) {
            for (Ace ace : aces.getAce()) {
              if (ace.getActions().getForwarding().equals(Accept.class)) {

                Matches matches = ace.getMatches();
                int matchesType = matchesType(matches);
                LOG.info("matchType " + matchesType);
                if (matchesType == MatchesType.DNS_MATCH) {
                  List<Ipv4Address> addresses = getMatchAddresses(matches);
                  installPermitFromIpAddressToDeviceFlowRules(mudUri.getValue(), matches,
                      addresses);
                } else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
                  Matches1 matches1 = matches.getAugmentation(Matches1.class);
                  Uri controllerUri = matches1.getMud().getController();
                  List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
                      controllerClassMapDsListener, mudUri, controllerUri);
                  installPermitFromIpAddressToDeviceFlowRules(mudUri.getValue(), matches,
                      addresses);
                } else if (matchesType == MatchesType.MY_CONTROLLER) {
                  List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
                      controllerClassMapDsListener, mudUri, mudUri);
                  installPermitFromIpAddressToDeviceFlowRules(mudUri.getValue(), matches,
                      addresses);
                } else if (matchesType == MatchesType.SAME_MANUFACTURER) {
                  String flowUri = createLocalFlowUri(authority);
                  installPermitSameManufacturerFlowRule(mudUri.getValue(), flowUri, matches);
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
      LOG.error("MudFlowsInstaller: Exception caught installing MUD Flow ", ex);
    }

  }



}
