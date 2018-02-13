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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev180124.Ipv41;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev180202.port.range.or.operator.Range;
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
	 * @param matches
	 *            - the matches clause.
	 * @return - a list of addresses that match.
	 * 
	 */
	private static List<Ipv4Address> getMatchAddresses(Matches matches) {

		ArrayList<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
		Ipv41 ipv41 = matches.getIpv4().getAugmentation(Ipv41.class);
		if (ipv41 != null) {
			Host dnsName = ipv41.getDstDnsname() != null ? ipv41.getDstDnsname() : ipv41.getSrcDnsname();
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
	private void installGoToDropTableOnSrcModelMetadataMatchFlow(String mudUri, String dropFlowUri,
			InstanceIdentifier<FlowCapableNode> node) {
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
		BigInteger metadata = createSrcModelMetadata(mudUri);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(dropFlowUri);
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask, flowId,
				SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.DROP_TABLE, 0);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private void installGoToDropTableOnDstModelMetadataMatchFlow(String mudUri, String dropFlowUri,
			InstanceIdentifier<FlowCapableNode> node) {
		BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
		BigInteger metadata = createDstModelMetadata(mudUri);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(dropFlowUri);
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask, flowId,
				SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.DROP_TABLE, 0);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private InstanceIdentifier<FlowCapableNode> getCpeNode() {
		return sdnmudProvider.getNode(cpeNodeId);
	}

	private static Range getSourcePortRange(Matches matches) {
		if (matches.getIpv4().getProtocol() == SdnMudConstants.TCP_PROTOCOL) {
			return matches.getTcp().getSourcePortRangeOrOperator().getRange();
		} else {
			return matches.getUdp().getSourcePortRangeOrOperator().getRange();
		}

	}

	private static short getProtocol(Matches matches) {
		if (matches.getIpv4() == null) {
			LOG.error("No IPV4 node foound -- cannto determine protocol ");
			return -1;
		}
		return matches.getIpv4().getProtocol();
	}

	private static Range getDestinationPortRange(Matches matches) {
		if (matches.getIpv4().getProtocol() == SdnMudConstants.UDP_PROTOCOL)
			return matches.getUdp().getDestinationPortRangeOrOperator().getRange();
		else
			return matches.getTcp().getDestinationPortRangeOrOperator().getRange();

	}

	private static Integer getDestinationPort(Matches matches) {
		if (matches.getTcp() != null && matches.getTcp().getDestinationPortRangeOrOperator() != null
				&& matches.getTcp().getDestinationPortRangeOrOperator().getPort() != null)
			return matches.getTcp().getDestinationPortRangeOrOperator().getPort().getValue();
		else if (matches.getUdp() != null && matches.getUdp().getDestinationPortRangeOrOperator() != null
				&& matches.getUdp().getDestinationPortRangeOrOperator().getPort() != null)
			return matches.getUdp().getDestinationPortRangeOrOperator().getPort().getValue();
		else
			return -1;

	}

	private static Integer getSourcePort(Matches matches) {

		if (matches.getTcp() != null && matches.getTcp().getSourcePortRangeOrOperator() != null
				&& matches.getTcp().getSourcePortRangeOrOperator().getPort() != null)
			return matches.getTcp().getSourcePortRangeOrOperator().getPort().getValue();

		else if (matches.getUdp() != null && matches.getUdp().getSourcePortRangeOrOperator() != null
				&& matches.getUdp().getSourcePortRangeOrOperator().getPort() != null)
			return matches.getUdp().getSourcePortRangeOrOperator().getPort().getValue();
		else
			return -1;
	}

	private void installPermitFromDeviceToIpAddressFlow(FlowId flowId, BigInteger metadata, BigInteger metadataMask,
			Ipv4Address address, int destinationPort, short protocol, FlowCookie flowCookie) {

		FlowBuilder fb = FlowUtils.createMetadataDestIpAndPortMatchGoTo(metadata, metadataMask, address,
				destinationPort, protocol, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowId,
				flowCookie);

		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, getCpeNode());
	}

	private void installPermitFromDeviceToIpAddressFlowRules(String mudUri, Matches matches,
			List<Ipv4Address> addresses) {
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
		BigInteger metadata = createSrcModelMetadata(mudUri);
		String authority = InstanceIdentifierUtils.getAuthority(mudUri);

		Short protocol = getProtocol(matches);
		if (protocol == -1) {
			LOG.error("Cannot install -- protocol field missing");
			return;
		}
		if (getDestinationPortRange(matches) != null) {
			Range destinationPortRange = getDestinationPortRange(matches);

			for (Ipv4Address address : addresses) {
				for (int port = destinationPortRange.getLowerPort().getValue().intValue(); port <= destinationPortRange
						.getUpperPort().getValue().intValue(); port++) {
					String flowUri = createFlowUri(authority, address);
					FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
					installPermitFromDeviceToIpAddressFlow(flowId, metadata, metadataMask, address, port,
							protocol.shortValue(), flowCookie);
				}
			}
		} else {
			int port = getDestinationPort(matches);
			for (Ipv4Address address : addresses) {
				String flowUri = createFlowUri(authority, address);
				FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
				installPermitFromDeviceToIpAddressFlow(flowId, metadata, metadataMask, address, port,
						protocol.shortValue(), flowCookie);
			}
		}
	}

	private void installPermitFromIpToDeviceFlow(BigInteger metadata, BigInteger metadataMask, Ipv4Address address,
			int sourcePort, short protocol, FlowCookie flowCookie, FlowId flowId,
			InstanceIdentifier<FlowCapableNode> node) {
		try {
			FlowBuilder fb = FlowUtils.createMetadataSrcIpAndPortMatchGoTo(metadata, metadataMask, address, sourcePort,
					protocol, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowId, flowCookie);
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
		Short protocol = matches.getIpv4().getProtocol();
		if (getSourcePortRange(matches) != null) {
			Range sourcePortRange = getSourcePortRange(matches);

			for (Ipv4Address address : addresses) {
				for (int port = sourcePortRange.getLowerPort().getValue().intValue(); port <= sourcePortRange
						.getUpperPort().getValue().intValue(); port++) {
					String flowUri = createFlowUri(authority, address);
					FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);

					installPermitFromIpToDeviceFlow(metadata, metadataMask, address, port, protocol.shortValue(),
							flowCookie, flowId, getCpeNode());

				}
			}
		} else {
			int port = getSourcePort(matches);
			for (Ipv4Address address : addresses) {
				String flowUri = createFlowUri(authority, address);
				FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
				FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
				installPermitFromIpToDeviceFlow(metadata, metadataMask, address, port, protocol.shortValue(),
						flowCookie, flowId, getCpeNode());
			}
		}
	}

	private void installPermitFromSameManufacturerFlowRule(String mudUri, String flowUri, Matches matches) {
		LOG.info("InstallPermitSameManufacturerFlowRule " + mudUri + " flowUri " + flowUri);
		Short protocol = getProtocol(matches);
		if (protocol == -1) {
			LOG.error("Cannot install ");
			return;
		}
		// Range of ports that this device is allowed to talk to.

		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

		installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask, protocol.shortValue(), sourcePort,
				destinationPort, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowCookie,
				flowId, getCpeNode());

	}

	private void installPermitFromLocalNetworksFlowRule(String mudUri, String flowUri, Matches matches) {
		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		Short protocol = getProtocol(matches);
		if (protocol == -1) {
			LOG.error("Cannot install ");
			return;
		}
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

		BigInteger metadata = BigInteger.valueOf(1).shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT));

		BigInteger mask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.SRC_NETWORK_MASK);
		installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask, protocol.shortValue(), sourcePort,
				destinationPort, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowCookie,
				flowId, getCpeNode());
	}

	private void installPermitToLocalNetworksFlowRule(String mudUri, String flowUri, Matches matches) {
		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		Short protocol = getProtocol(matches);
		if (protocol == -1) {
			LOG.error("Cannot install protocol unspecified");
			return;
		}
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

		BigInteger metadata = BigInteger.valueOf(1).shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT));
		BigInteger mask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.DST_NETWORK_MASK);

		installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask, protocol.shortValue(), sourcePort,
				destinationPort, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowCookie,
				flowId, getCpeNode());
	}

	private void installPermitToSameManufacturerFlowRule(String mudUri, String flowUri, Matches matches) {
		LOG.info("InstallPermitSameManufacturerFlowRule " + mudUri + " flowUri " + flowUri);
		Short protocol = getProtocol(matches);
		if (protocol == -1) {
			LOG.error("invlid protocol -- cannot install ");
			return;
		}
		// Range of ports that this device is allowed to talk to.

		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri);
		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);

		installPermitFromSrcManSrcPortToDestManDestPortFlow(metadata, mask, protocol.shortValue(), sourcePort,
				destinationPort, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowCookie,
				flowId, getCpeNode());
	}

	private void installPermitFromSrcManSrcPortToDestManDestPortFlow(BigInteger metadata, BigInteger metadataMask,
			short protocol, int srcPort, int destinationPort, short tableId, short targetTableId, FlowCookie flowCookie,
			FlowId flowId, InstanceIdentifier<FlowCapableNode> node) {

		FlowBuilder fb = FlowUtils.createMetadaProtocolAndSrcDestPortMatchGoToTable(metadata, metadataMask, protocol,
				srcPort, destinationPort, tableId, targetTableId, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private void installPermitPacketsFromToServer(String mudUri, Ipv4Address address, int port) {

		LOG.info("installPermitPacketsFromToServer :  dnsAddress " + address.getValue());

		String authority = InstanceIdentifierUtils.getAuthority(mudUri);
		BigInteger metadata = BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
				.shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;

		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(createFlowUri(authority, address));
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);

		FlowBuilder flowBuilder = FlowUtils.createPermitPacketsToServerFlow(metadata, metadataMask, address, port,
				SdnMudConstants.UDP_PROTOCOL, flowId, flowCookie);
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
			for (short protocol : new Short[] { SdnMudConstants.UDP_PROTOCOL, SdnMudConstants.TCP_PROTOCOL }) {
				flowId = InstanceIdentifierUtils.createFlowId(mudUri);
				flowBuilder = FlowUtils.createPermitPacketsFromServerFlow(metadata, metadataMask, address, protocol,
						flowId, flowCookie);
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
				SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
		flowId = InstanceIdentifierUtils.createFlowId(nodeId);
		flowBuilder = FlowUtils.createPermitPacketsFromServerFlow(address, port, protocol,
				SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowId, flowCookie);
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
				SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowId, flowCookie);
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
				SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.PASS_THRU_TABLE, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

	}

	private void installDenyToMacFlow(String mudUri, MacAddress destinationMacAddress, FlowCookie flowCookie) {

		LOG.info("installDropPacketsToMacFlow " + mudUri + " destination " + destinationMacAddress.getValue());
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
		BigInteger metadata = createSrcModelMetadata(mudUri);

		FlowBuilder flow = FlowUtils.createMetadataAndDestMacMatchGoToTableFlow(metadata, metadataMask,
				destinationMacAddress, SdnMudConstants.SDNMUD_RULES_TABLE, SdnMudConstants.DROP_TABLE, flowCookie,
				flowId);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, getCpeNode());
	}

	public static void installAllowToDnsAndNtpFlowRules(SdnmudProvider sdnmudProvider,
			InstanceIdentifier<FlowCapableNode> node) throws Exception {
		String nodeId = InstanceIdentifierUtils.getNodeUri(node);
		if (sdnmudProvider.getControllerclassMappingDataStoreListener().getControllerClass(nodeId) == null) {
			LOG.info("no controller class mapping found for node " + nodeId);
			return;
		}

		if (sdnmudProvider.getControllerclassMappingDataStoreListener().getDnsAddress(nodeId) != null) {
			Ipv4Address dnsAddress = sdnmudProvider.getControllerclassMappingDataStoreListener().getDnsAddress(nodeId)
					.getIpv4Address();
			if (dnsAddress != null) {
				LOG.info("Installing DNS rules");
				installPermitPacketsFromToServer(sdnmudProvider, node, dnsAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.DNS_PORT);
				installPermitPacketsFromToServer(sdnmudProvider, node, dnsAddress, SdnMudConstants.TCP_PROTOCOL,
						SdnMudConstants.DNS_PORT);
				installPermitPacketsToServer(sdnmudProvider, node, dnsAddress, SdnMudConstants.TCP_PROTOCOL,
						SdnMudConstants.DNS_PORT);
				installPermitPacketsToServer(sdnmudProvider, node, dnsAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.DNS_PORT);

			}
		}

		if (sdnmudProvider.getControllerclassMappingDataStoreListener().getNtpAddress(nodeId) != null) {
			Ipv4Address ntpAddress = sdnmudProvider.getControllerclassMappingDataStoreListener().getNtpAddress(nodeId)
					.getIpv4Address();
			if (ntpAddress != null) {
				LOG.info("Installing NTP rules");
				installPermitPacketsToServer(sdnmudProvider, node, ntpAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.NTP_SERVER_PORT);
				installPermitPacketsFromServer(sdnmudProvider, node, ntpAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.NTP_SERVER_PORT);
			}
		}
	}

	private static List<Ipv4Address> getControllerMatchAddresses(String nodeConnectorUri,
			ControllerclassMappingDataStoreListener ccmdsl, Uri mudUri, Uri controllerUri) {
		// Resolve it.
		List<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
		LOG.info("Resolving controller address for " + controllerUri.getValue());
		if (ccmdsl.getControllerAddresses(nodeConnectorUri, mudUri, controllerUri.getValue()) != null) {
			List<IpAddress> controllerIpAddresses = ccmdsl.getControllerAddresses(nodeConnectorUri, mudUri,
					controllerUri.getValue());
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

	private static BigInteger createSrcModelMetadata(String mudUri) {
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		return BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
	}

	private static BigInteger createDstModelMetadata(String mudUri) {
		return BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
				.shiftLeft(SdnMudConstants.DST_MODEL_SHIFT);
	}

	private static BigInteger createSrcManufacturerMetadata(String manufacturer) {
		return BigInteger.valueOf(InstanceIdentifierUtils.getManfuacturerId(manufacturer))
				.shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT);
	}

	private static BigInteger createDstManufacturerMetadata(String manufacturer) {
		return BigInteger.valueOf(InstanceIdentifierUtils.getManfuacturerId(manufacturer))
				.shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT);
	}

	public static void installStampManufacturerModelFlowRules(MacAddress srcMac, String mudUri,
			SdnmudProvider sdnmudProvider, InstanceIdentifier<FlowCapableNode> node) {

		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		FlowId flowId = InstanceIdentifierUtils.createFlowId(mudUri);
		BigInteger metadata = createSrcManufacturerMetadata(manufacturer).or(createSrcModelMetadata(mudUri));

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("stamp-manufactuer-model-flow");

		FlowBuilder fb = FlowUtils.createSourceMacMatchSetMetadataAndGoToTable(srcMac, metadata, metadataMask,
				SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE,
				SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);

		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

		flowId = InstanceIdentifierUtils.createFlowId(mudUri);
		metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);

		fb = FlowUtils.createDestMacMatchSetMetadataAndGoToTable(srcMac, metadata, metadataMask,
				SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, SdnMudConstants.SDNMUD_RULES_TABLE, flowId,
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
	public synchronized void installFlows(Mud mud) {
		try {
			Uri mudUri = mud.getMudUrl();

			ControllerclassMappingDataStoreListener controllerClassMapDsListener = sdnmudProvider
					.getControllerclassMappingDataStoreListener();
			if (controllerClassMapDsListener.getControllerClass(this.cpeNodeId) == null) {
				LOG.info("Cannot find ControllerClass mapping for the switch  -- not installing ACLs. nodeUrl "
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
			flowCommitWrapper.deleteFlows(node, "flow:" + authority, SdnMudConstants.SDNMUD_RULES_TABLE, null);

			// Track that we have added a node for this device MAC address for
			// this node. i.e. we store MUD rules for this device on the given
			// node.

			LOG.info("installFlows : authority (manufacturer) = " + "[" + authority + "]");

			sdnmudProvider.addMudNode(authority, node);

			// Records that the CPE node owns this mud URI.
			sdnmudProvider.addMudUri(cpeNodeId, mudUri);

			// Create the drop flow rule for the MUD URI.
			String dropFlowUri = createDropFlowUri(authority);

			// Drop table is where all the unsuccessful matches land up.
			// Push default drop packet flows that will drop the packet if a MUD
			// rule does
			// not match.
			installGoToDropTableOnSrcModelMetadataMatchFlow(mudUri.getValue(), dropFlowUri, getCpeNode());
			installGoToDropTableOnDstModelMetadataMatchFlow(mudUri.getValue(), dropFlowUri, getCpeNode());

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
									installPermitFromDeviceToIpAddressFlowRules(mudUri.getValue(), matches, addresses);
								} else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
									Matches1 matches1 = matches.getAugmentation(Matches1.class);
									Uri controllerUri = matches1.getMud().getController();
									List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
											controllerClassMapDsListener, mudUri, controllerUri);
									installPermitFromDeviceToIpAddressFlowRules(mudUri.getValue(), matches, addresses);
								} else if (matchesType == MatchesType.MY_CONTROLLER) {
									List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
											controllerClassMapDsListener, mudUri, mudUri);
									installPermitFromDeviceToIpAddressFlowRules(mudUri.getValue(), matches, addresses);
								} else if (matchesType == MatchesType.LOCAL_NETWORKS) {
									String flowUri = createLocalFlowUri(authority);
									installPermitToLocalNetworksFlowRule(mudUri.getValue(), flowUri, matches);
								} else if (matchesType == MatchesType.SAME_MANUFACTURER) {
									String flowUri = createLocalFlowUri(authority);
									installPermitFromSameManufacturerFlowRule(mudUri.getValue(), flowUri, matches);
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
									installPermitFromIpAddressToDeviceFlowRules(mudUri.getValue(), matches, addresses);
								} else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
									Matches1 matches1 = matches.getAugmentation(Matches1.class);
									Uri controllerUri = matches1.getMud().getController();
									List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
											controllerClassMapDsListener, mudUri, controllerUri);
									installPermitFromIpAddressToDeviceFlowRules(mudUri.getValue(), matches, addresses);
								} else if (matchesType == MatchesType.MY_CONTROLLER) {
									List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId,
											controllerClassMapDsListener, mudUri, mudUri);
									installPermitFromIpAddressToDeviceFlowRules(mudUri.getValue(), matches, addresses);
								} else if (matchesType == MatchesType.LOCAL_NETWORKS) {
									String flowUri = createLocalFlowUri(authority);
									installPermitFromLocalNetworksFlowRule(mudUri.getValue(), flowUri, matches);
								} else if (matchesType == MatchesType.SAME_MANUFACTURER) {
									String flowUri = createLocalFlowUri(authority);
									installPermitToSameManufacturerFlowRule(mudUri.getValue(), flowUri, matches);
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
