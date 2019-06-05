/*
 * Copyright © 2017 None.  No rights reserved.
 * This program and the accompanying materials are made available under the Public Domain.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.Accept;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.Aces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l3.Ipv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l4.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l4.Udp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev190128.Ipv41;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.acldns.rev190128.Matches1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Direction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Matches1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Tcp1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.access.lists.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.access.lists.access.lists.AccessList;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.mud.grouping.FromDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.mud.grouping.ToDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev190304.port.range.or.operator.port.range.or.operator.Operator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.nist.mud.rev190428.Mud1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.nist.mud.rev190428.mud.QuarantinedDevicePolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.nist.mud.rev190428.mud.quarantined.device.policy.EnabledAceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MudFlowsInstaller {

	private SdnmudProvider sdnmudProvider;
	static final Logger LOG = LoggerFactory.getLogger(MudFlowsInstaller.class);
	private HashMap<InstanceIdentifier<FlowCapableNode>, List<NameResolutionCacheEntry>> nameResolutionCache = new HashMap<InstanceIdentifier<FlowCapableNode>, List<NameResolutionCacheEntry>>();

	private enum MatchesType {
		CONTROLLER_MAPPING, SAME_MANUFACTURER, MANUFACTURER, MODEL, MY_CONTROLLER, LOCAL_NETWORKS, DNS_MATCH,
		UNKNOWN_MATCH;

	}

	/**
	 * Class for Name resolution when there is a mud ACE that needs the resolution
	 * but we have not been able to resolve it.
	 * 
	 * @author mranga
	 *
	 */
	class NameResolutionCacheEntry {
		private boolean qFlag;
		private Matches matches;
		private MatchesType matchesType;
		private String mudUrl;
		private String aceName;
		private List<Ipv4Address> addresses;
		private InstanceIdentifier<FlowCapableNode> node;
		private String domainName;
		private boolean toFlag;

		NameResolutionCacheEntry(Matches matches, MatchesType matchesType, boolean toFlag, boolean qFlag,
				InstanceIdentifier<FlowCapableNode> node, String mudUrl, String aceName, String domainName,
				List<Ipv4Address> addresses) {
			this.matches = matches;
			this.matchesType = matchesType;
			this.toFlag = toFlag;
			this.mudUrl = mudUrl;
			this.aceName = aceName;
			this.addresses = addresses;
			this.node = node;
			this.domainName = domainName;
			this.qFlag = qFlag;
		}
	}

	/**
	 * Cache a pending match - we install flow rules by snooping the name
	 * resolution. When further resolutions are reported, we update our flow rules.
	 * 
	 * @param node
	 * @param mudUrl
	 * @param matches
	 * @param addresses
	 * @param toDeviceFlag
	 */
	private void deferDnsMatch(InstanceIdentifier<FlowCapableNode> node, String mudUrl, String aceName, Matches matches, MatchesType matchesType,
			List<Ipv4Address> addresses, boolean toDeviceFlag, boolean qFlag) {
		Ipv41 ipv41 = ((Ipv4) matches.getL3()).getIpv4().getAugmentation(Ipv41.class);
		Host dnsName = ipv41.getDstDnsname() != null ? ipv41.getDstDnsname() : ipv41.getSrcDnsname();
		String domainName = dnsName.getDomainName().getValue();
		NameResolutionCacheEntry nameResolutionCacheEntry = new NameResolutionCacheEntry(matches, matchesType, toDeviceFlag, qFlag,
				node, mudUrl, aceName, domainName, addresses);
		if (!nameResolutionCache.containsKey(node)) {
			List<NameResolutionCacheEntry> entries = new ArrayList<NameResolutionCacheEntry>();
			nameResolutionCache.put(node, entries);
		}
		List<NameResolutionCacheEntry> entries = nameResolutionCache.get(node);
		entries.add(nameResolutionCacheEntry);
	}

	public void removeDnsMatch(InstanceIdentifier<FlowCapableNode> node) {
		nameResolutionCache.remove(node);
	}

	public void fixupNameResolution(InstanceIdentifier<FlowCapableNode> node, String name, String address) {

		if (this.nameResolutionCache.containsKey(node)) {
			List<NameResolutionCacheEntry> entries = this.nameResolutionCache.get(node);
			for (NameResolutionCacheEntry entry : entries) {
				if (entry.domainName.equals(name)) {
					boolean found = false;
					for (Ipv4Address ipv4Address1 : entry.addresses) {
						if (ipv4Address1.getValue().equals(address)) {
							// resolution already exists.
							found = true;
							break;
						}
					}
					if (found) {
						continue;
					}
					// Found an entry in our cache
					LOG.info("addNameResolution: add name resolution " + name + " address " + address);
					// Entry not found in our cache - this is a new resolution.
					entry.addresses.add(new Ipv4Address(address));
					ArrayList<Ipv4Address> newAddress = new ArrayList<Ipv4Address>();
					newAddress.add(new Ipv4Address(address));
					try {
						if (entry.toFlag) {
							this.installPermitFromIpAddressToDeviceFlowRules(node, entry.mudUrl, entry.aceName, entry.matches, entry.matchesType,
									newAddress, entry.qFlag);
						} else {
							this.installPermitFromDeviceToIpAddressFlowRules(node, entry.mudUrl, entry.aceName, entry.matches, entry.matchesType,
									newAddress, entry.qFlag);
						}
					} catch (Exception e) {
						LOG.error("Could not install flow rule ", e);
					}
				}
			}
		} else {
			LOG.info("Cannot find node in name resolution cache." + node);
		}
	}

	private void registerTcpSynFlagCheck(String mudUri, String aceName,InstanceIdentifier<FlowCapableNode> node, BigInteger metadata,
			BigInteger metadataMask, Ipv4Address sourceAddress, int sourcePort, Ipv4Address destinationAddress,
			int destinationPort, int priority) {

		// Insert a flow which will drop the packet if it sees a Syn
		// flag.
		FlowId fid = IdUtils.createFlowId(mudUri + "/" + aceName);
		FlowCookie flowCookie = SdnMudConstants.TCP_SYN_MATCH_CHECK_COOKIE;
		FlowBuilder fb = FlowUtils.createMetadataTcpSynSrcIpSrcPortDestIpDestPortMatchToToNextTableFlow(metadata,
				metadataMask, sourceAddress, sourcePort, destinationAddress, destinationPort,
				sdnmudProvider.getSdnmudRulesTable(), priority, sdnmudProvider.getDropTable(), fid, flowCookie, 0);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private void registerTcpSynFlagCheck(FlowId flowId, FlowCookie flowCookie, InstanceIdentifier<FlowCapableNode> node, BigInteger metadata,
			BigInteger metadataMask, int sourcePort, int destinationPort, int priority) {

		LOG.info("registerTcpSynFlagCheck " + flowId.getValue() + " sourcePort " + sourcePort + " destinationPort " + destinationPort + " priority "  + priority);
		// flag.
		FlowBuilder fb = FlowUtils.createMetadataTcpSynSrcPortAndDstPortMatchToToNextTableFlow(metadata, metadataMask,
				destinationPort, sourcePort, sdnmudProvider.getSdnmudRulesTable(), priority,
				sdnmudProvider.getDropTable(), flowId, flowCookie, 0);

		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

	}

	public MudFlowsInstaller(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}


	private static MatchesType matchesType(Matches matches) {
		Matches1 matches1 = matches.getAugmentation(Matches1.class);
		if (matches1 == null) {
			return MatchesType.DNS_MATCH;
		}

		if (matches1.getMud() != null && matches1.getMud().getController() != null) {
			LOG.info("MudFlowsInstaller: controllerMapping");
			return MatchesType.CONTROLLER_MAPPING;
		} else if (matches1.getMud() != null && matches1.getMud().getManufacturer() != null) {
			LOG.info("MudFlowsInstaller: manufacturer");
			return MatchesType.MANUFACTURER;
		} else if (matches1.getMud() != null && matches1.getMud().getModel() != null) {
			LOG.info("MudFlowsInstaller: model");
			return MatchesType.MODEL;
		} else if (matches1.getMud() != null && matches1.getMud().isLocalNetworks() != null
				&& matches1.getMud().isLocalNetworks()) {
			LOG.info("MudFlowsInstaller: localNetworks");
			return MatchesType.LOCAL_NETWORKS;
		} else if (matches1.getMud() != null && matches1.getMud().isSameManufacturer() != null
				&& matches1.getMud().isSameManufacturer()) {
			LOG.info("MudFlowsInstaller: sameManufacturer");
			return MatchesType.SAME_MANUFACTURER;
		} else if (matches1.getMud() != null && matches1.getMud().isMyController() != null
				&& matches1.getMud().isMyController()) {
			LOG.info("MudFlowsInstaller: myController");
			return MatchesType.MY_CONTROLLER;
		} else {
			LOG.info("MudFlowsInstaller: unknownMatch");
			return MatchesType.UNKNOWN_MATCH;
		}
	}

	private static String getManufacturer(Matches matches) {
		Matches1 matches1 = matches.getAugmentation(Matches1.class);
		return matches1.getMud().getManufacturer().getDomainName().getValue();
	}

	private static String getModel(Matches matches) {
		Matches1 matches1 = matches.getAugmentation(Matches1.class);
		return matches1.getMud().getModel().getValue();

	}

	/*
	 * 
	 * private static boolean isValidIpV4Address(String ip) { try { if (ip == null
	 * || ip.isEmpty()) { return false; }
	 * 
	 * String[] parts = ip.split("\\."); if (parts.length != 4) { return false; }
	 * 
	 * for (String s : parts) { int i = Integer.parseInt(s); if ((i < 0) || (i >
	 * 255)) { return false; } } if (ip.endsWith(".")) { return false; }
	 * 
	 * return true; } catch (NumberFormatException nfe) { return false; } }
	 * 
	 * private static void resolveDefaultDomain(ArrayList<Ipv4Address> ipAddresses,
	 * String domainName) throws UnknownHostException { InetAddress[] inetAddresses
	 * = InetAddress.getAllByName(domainName);
	 * 
	 * for (InetAddress inetAddress : inetAddresses) { String hostAddress =
	 * inetAddress.getHostAddress(); LOG.info("domainName " + domainName +
	 * "inetAddress : " + hostAddress); if (isValidIpV4Address(hostAddress)) {
	 * ipAddresses.add(new Ipv4Address(hostAddress)); } } }
	 */

	private boolean directionCheck(Direction direction, boolean fromDeviceRule) {
		return direction != null && (fromDeviceRule && direction.getName().equals(Direction.ToDevice.getName())
				|| ((!fromDeviceRule) && direction.getName().equals(Direction.FromDevice.getName())));

	}

	/**
	 * Resolve the addresses for a Match clause. Lookup address in the name lookup
	 * cache.
	 *
	 * @param matches - the matches clause.
	 * @return - a list of addresses that match.
	 * 
	 *
	 */
	private List<Ipv4Address> getMatchAddresses(InstanceIdentifier<FlowCapableNode> node, Matches matches) {

		ArrayList<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
		Ipv41 ipv41 = ((Ipv4) matches.getL3()).getIpv4().getAugmentation(Ipv41.class);

		if (ipv41 != null) {
			Host dnsName = ipv41.getDstDnsname() != null ? ipv41.getDstDnsname() : ipv41.getSrcDnsname();
			if (dnsName != null) {
				// Get the domain name of the host.
				String domainName = dnsName.getDomainName().getValue();
				List<Ipv4Address> retval = sdnmudProvider.getNameResolutionCache().doNameLookup(node, domainName);
				LOG.info("domainName : " + domainName + " Lookup " + retval);
				if (retval != null) {
					return retval;
				}
			}
		}
		return ipAddresses;
	}

	private IpAddress getControllerAddress(String nodeUri, String controllerUri) {
		LOG.info(" getControllerAddress " + nodeUri + " controllerUri " + controllerUri);
		Map<String, List<IpAddress>> controllerMap = sdnmudProvider.getControllerClassMap(nodeUri);
		if (controllerMap == null) {
			LOG.info("getControllerAddress : controllerMap is null ");
			return null;
		} else if (controllerMap.containsKey(controllerUri)) {
			LOG.info("getControllerAddress : found a controller for " + controllerUri);
			return controllerMap.get(controllerUri).get(0);
		} else {
			LOG.info("getControllerAddress : could not find controller for " + controllerUri);
		}
		return null;

	}

	/**
	 * Get the DNS server address for the given mudUri.
	 *
	 * @param nodeUri
	 *
	 * @return -- the associated DNS server adress.
	 */

	private IpAddress getDnsAddress(String nodeUri) {
		IpAddress retval = getControllerAddress(nodeUri, SdnMudConstants.DNS_SERVER_URI);
		if (retval != null) {
			LOG.info(this.getClass().getName() + " getDnsAddress " + nodeUri + " dnsAddress "
					+ retval.getIpv4Address().getValue());
		} else {
			LOG.info(this.getClass().getName() + " getDnsAddress " + nodeUri + " dnsAddress is null");
		}
		return retval;
	}

	/**
	 * Get the NTP address.
	 *
	 * @param nodeUri
	 *
	 * @return -- the ipAddress corresponding to Ntp
	 */

	public IpAddress getNtpAddress(String nodeUri) {
		IpAddress retval = getControllerAddress(nodeUri, SdnMudConstants.NTP_SERVER_URI);
		if (retval != null) {
			LOG.info(this.getClass().getName() + " getNtpAddress " + nodeUri + " ntpAddress "
					+ retval.getIpv4Address().getValue());
		} else {
			LOG.info(this.getClass().getName() + " getNtpAddress " + nodeUri + " ntpAddress is null");
		}
		return retval;

	}

	/**
	 * Drop packet if mud rules don't match.
	 *
	 * @param mudUri
	 * @param dropFlowUri
	 */
	private void installGoToDropTableAndSendToControllerOnSrcModelMetadataMatchFlow(String mudUri,
			InstanceIdentifier<FlowCapableNode> node, int priority) {
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
		BigInteger metadata = createSrcModelMetadata(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri);
		FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
		BigInteger newMetadata = metadata;
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableAndSendToControllerFlow(flowCookie, metadata,
				metadataMask, flowId, sdnmudProvider.getSdnmudRulesTable(), priority,
				newMetadata, newMetadataMask,
				sdnmudProvider.getDropTable(), 0);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private void installGotoDropTableOnQuaranteneSrcModelMetadataMatchFlow(String mudUri,
			InstanceIdentifier<FlowCapableNode> node, int priority) {
		// TODO -- pass priority in here
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK.or(SdnMudConstants.SRC_QUARANTENE_MASK);
		BigInteger metadata = createSrcModelMetadata(mudUri, true).or(SdnMudConstants.SRC_QUARANTENE_FLAG);
		FlowId flowId = IdUtils.createFlowId(mudUri);
		FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
		BigInteger newMetadata = metadata;
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask, flowId,
				sdnmudProvider.getSdnmudRulesTable(), newMetadata, newMetadataMask, sdnmudProvider.getDropTable(),
				priority, 0);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}
	
	private void installGoToDropTableOnQuaranteneDstModelMetadataMatchFlow(String mudUri,
			InstanceIdentifier<FlowCapableNode> node, int priority) {
		BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK.or(SdnMudConstants.DST_QURANTENE_MASK);
		BigInteger metadata = createSrcModelMetadata(mudUri, true).or(SdnMudConstants.DST_QUARANTENE_FLAG);
		FlowId flowId = IdUtils.createFlowId(mudUri);
		FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
		BigInteger newMetadata = metadata;
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask, flowId,
				sdnmudProvider.getSdnmudRulesTable(), newMetadata, newMetadataMask, sdnmudProvider.getDropTable(),
				priority, 0);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	/**
	 * Install flow that goes to the drop table on srcModel metadata match.
	 * 
	 * @param mudUri -- the mud URI for src metadata
	 * @param node   -- the node on which to install the flow.
	 */
	public void installGoToDropTableOnSrcModelMetadataMatchFlow(String mudUri,
			InstanceIdentifier<FlowCapableNode> node) {
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
		BigInteger metadata = createSrcModelMetadata(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri);
		FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
		BigInteger newMetadata = metadata;
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask, flowId,
				sdnmudProvider.getSdnmudRulesTable(), newMetadata, newMetadataMask, sdnmudProvider.getDropTable(),
				SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY + 1, SdnMudConstants.DROP_RULE_TIMEOUT);

		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	
	private void installGoToDropTableOnDstModelMetadataMatchFlow(String mudUri,
			InstanceIdentifier<FlowCapableNode> node, int priority) {
		BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
		BigInteger metadata = createDstModelMetadata(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri);
		FlowCookie flowCookie = SdnMudConstants.DROP_FLOW_COOKIE;
		BigInteger newMetadata = metadata;
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToTableFlow(flowCookie, metadata, metadataMask, flowId,
				sdnmudProvider.getSdnmudRulesTable(), newMetadata, newMetadataMask, sdnmudProvider.getDropTable(),
				priority, 0);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	private static short getProtocol(Matches matches) {
		if (matches.getL3() == null) {
			LOG.info("No IPV4 node foound -- cannot determine protocol ");
			return -1;
		}
		return ((Ipv4) matches.getL3()).getIpv4().getProtocol();

	}

	private static Integer getDestinationPort(Matches matches) {

		if ((matches.getL4()) != null && (matches.getL4() instanceof Tcp)
				&& ((Tcp) matches.getL4()).getTcp().getDestinationPort() != null) {

			return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l4.tcp.tcp.destination.port.destination.port.RangeOrOperator) (((Tcp) matches
					.getL4()).getTcp().getDestinationPort().getDestinationPort())).getPortRangeOrOperator()).getPort()
							.getValue();

		} else if (matches.getL4() != null && (matches.getL4() instanceof Udp)
				&& ((Udp) matches.getL4()).getUdp().getDestinationPort() != null) {

			return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l4.udp.udp.destination.port.destination.port.RangeOrOperator) (((Udp) matches
					.getL4()).getUdp().getDestinationPort().getDestinationPort())).getPortRangeOrOperator()).getPort()
							.getValue();
		} else {
			return -1;
		}

	}

	private static Integer getSourcePort(Matches matches) {
		if ((matches.getL4()) != null && (matches.getL4() instanceof Tcp)
				&& ((Tcp) matches.getL4()).getTcp().getSourcePort() != null) {

			return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l4.tcp.tcp.source.port.source.port.RangeOrOperator) (((Tcp) matches
					.getL4()).getTcp().getSourcePort().getSourcePort())).getPortRangeOrOperator()).getPort().getValue();

		} else if (matches.getL4() != null && (matches.getL4() instanceof Udp)
				&& ((Udp) matches.getL4()).getUdp().getSourcePort() != null) {

			return ((Operator) ((org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.aces.ace.matches.l4.udp.udp.source.port.source.port.RangeOrOperator) (((Udp) matches
					.getL4()).getUdp().getSourcePort().getSourcePort())).getPortRangeOrOperator()).getPort().getValue();
		} else {
			return -1;
		}

	}

	private static Direction getDirectionInitiated(Matches matches) {
		if (matches.getL4() != null && matches.getL4() instanceof Tcp
				&& ((Tcp) matches.getL4()).getTcp().getAugmentation(Tcp1.class) != null) {
			return ((Tcp) matches.getL4()).getTcp().getAugmentation(Tcp1.class).getDirectionInitiated();
		} else {
			return null;
		}
	}

	private void installPermitFromDeviceToIpAddressFlow(String mudUri, String aceName, InstanceIdentifier<FlowCapableNode> node,
			FlowId flowId, BigInteger metadata, BigInteger metadataMask, Ipv4Address destinationAddress,
			int destinationPort, short protocol, boolean synFlagCheck, boolean isEnabledOnQ, FlowCookie flowCookie) {
		BigInteger newMetadata = flowCookie.getValue();
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		boolean ctrlFlag = false;

		int priority = !isEnabledOnQ ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;

		FlowBuilder fb = FlowUtils.createMetadataDestIpAndPortMatchGoToNextTableFlow(metadata, metadataMask,
				destinationAddress, destinationPort, protocol, ctrlFlag, sdnmudProvider.getSdnmudRulesTable(), priority,
				newMetadata, newMetadataMask, flowId, flowCookie);

		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
		if (synFlagCheck) {
			assert protocol == SdnMudConstants.TCP_PROTOCOL;
			registerTcpSynFlagCheck(mudUri, aceName, node, metadata, metadataMask, null, -1, destinationAddress, destinationPort,
					priority + 1);
		}
	}

	private void installPermitFromDeviceToIpAddressFlowRules(InstanceIdentifier<FlowCapableNode> node, String mudUri,
			String aceName, Matches matches, MatchesType matchesType, List<Ipv4Address> addresses, boolean isEnabledOnQ) {
		BigInteger metadataMask = SdnMudConstants.SRC_MODEL_MASK;
		BigInteger metadata = createSrcModelMetadata(mudUri);

		Short protocol = getProtocol(matches);

		int port = getDestinationPort(matches);
		for (Ipv4Address address : addresses) {
			String flowSpec = matchesType.toString();
			FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);
			Direction direction = getDirectionInitiated(matches);
			if (direction != null) {
				LOG.info("MudFlowsInstaller: directionInitiated = " + direction.getName());
			} else {
				LOG.info("MudFlowsInstaller: direction is null ");
			}
			/*
			 * We want to make sure that the first packet from device does not contain a Syn
			 * in case the direction is ToDevice
			 */
			boolean synFlagCheck = directionCheck(direction, true);
			FlowCookie flowCookie = IdUtils.createFlowCookie(flowSpec);
			this.installPermitFromDeviceToIpAddressFlow(mudUri, aceName, node, flowId, metadata, metadataMask, address, port,
					protocol.shortValue(), synFlagCheck, isEnabledOnQ, flowCookie);

		}
	}

	private void installPermitFromIpToDeviceFlow(String mudUri, String aceName, BigInteger metadata, BigInteger metadataMask,
			Ipv4Address address, int sourcePort, short protocol, boolean checkTcpSyn, FlowCookie flowCookie,
			FlowId flowId, InstanceIdentifier<FlowCapableNode> node, boolean qFlag) {
		try {
			BigInteger newMetadata = flowCookie.getValue();
			BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

			boolean toCtrlFlag = false;

			int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
					: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;

			FlowBuilder fb = FlowUtils.createMetadataSrcIpAndPortMatchGoToNextTableFlow(metadata, metadataMask, address,
					sourcePort, protocol, toCtrlFlag, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
					newMetadataMask, flowId, flowCookie);
			this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
			if (checkTcpSyn) {
				// Check for TCP SYN when packet arrives at the controller.
				assert protocol == SdnMudConstants.TCP_PROTOCOL;
				this.registerTcpSynFlagCheck(mudUri, aceName, node, metadata, metadataMask, address, sourcePort, null, -1,
						priority + 1);
			}

		} catch (Exception ex) {
			LOG.error("Error installing flow ", ex);
		}
	}

	private void installPermitFromIpAddressToDeviceFlowRules(InstanceIdentifier<FlowCapableNode> node, String mudUri,
			String aceName, Matches matches, MatchesType matchesType, List<Ipv4Address> addresses, boolean qFlag) throws Exception {
		BigInteger metadataMask = SdnMudConstants.DST_MODEL_MASK;
		BigInteger metadata = createDstModelMetadata(mudUri);
		String authority = IdUtils.getAuthority(mudUri);
		Short protocol = getProtocol(matches);
		int port = getSourcePort(matches);

		for (Ipv4Address address : addresses) {
			Direction direction = getDirectionInitiated(matches);
			FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);
			if (direction != null) {
				LOG.info("MudFlowsInstaller : InstallePermitFromAddressToDeviceFlowRules : direction "
						+ direction.getName());
			} else {
				LOG.info("MudFlowsInstaller : InstallePermitFromAddressToDeviceFlowRules : direction is null");
			}
			FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());
			boolean checkDirectionInitiated = directionCheck(direction, false);
			this.installPermitFromIpToDeviceFlow(mudUri, aceName, metadata, metadataMask, address, port, protocol.shortValue(),
					checkDirectionInitiated, flowCookie, flowId, node, qFlag);

		}

	}

	private void installPermitFromDeviceToModelFlowRule(InstanceIdentifier<FlowCapableNode> node, String mudUri,
			String aceName,  Matches matches, MatchesType matchesType, boolean qFlag) {
		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());
		int dstModelId = IdUtils.getModelId(getModel(matches));

		int modelId = IdUtils.getModelId(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);

		BigInteger metadata = BigInteger.valueOf(dstModelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.DST_MODEL_MASK.or(SdnMudConstants.SRC_MODEL_MASK);
		BigInteger newMetadata = metadata;
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		Direction direction = getDirectionInitiated(matches);
		Short protocol = getProtocol(matches);

		/*
		 * For TCP send a packet to the controller to enforce direction initiated
		 */
		int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;

		boolean checkDirectionInitiated = directionCheck(direction, true);
		this.installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(mudUri, aceName, metadata, mask, protocol.shortValue(),
				sourcePort, destinationPort, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
				newMetadataMask, checkDirectionInitiated, flowCookie, flowId, node);

	}

	private void installPermitFromDeviceToManufacturerFlowRule(InstanceIdentifier<FlowCapableNode> node, String mudUri, String aceName,
			String manufacturer, Matches matches, MatchesType matchesType, boolean qFlag) {
		LOG.info("InstallPermitSameManufacturerFlowRule " + mudUri + " flowSpec " + matchesType);
		Short protocol = getProtocol(matches);

		// Range of ports that this device is allowed to talk to.

		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());

		int manufacturerId = IdUtils.getManfuacturerId(manufacturer);
		int modelId = IdUtils.getModelId(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

		BigInteger newMetadata = BigInteger.valueOf(IdUtils.getFlowHash(matchesType.toString()));
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

		Direction direction = getDirectionInitiated(matches);
		boolean checkDirectionInitiated = directionCheck(direction, true);
		int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;

		this.installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(mudUri, aceName, metadata, mask, protocol.shortValue(),
				sourcePort, destinationPort, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
				newMetadataMask, checkDirectionInitiated, flowCookie, flowId, node);

	}

	private void installPermitFromLocalNetworksToDeviceFlowRule(InstanceIdentifier<FlowCapableNode> node, String mudUri, String aceName,
			Matches matches, MatchesType matchesType, boolean qFlag) {
		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());
		int modelId = IdUtils.getModelId(mudUri);
		Short protocol = getProtocol(matches);
		FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);

		BigInteger metadata = SdnMudConstants.LOCAL_SRC_NETWORK_FLAG
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));

		BigInteger mask = SdnMudConstants.DST_MODEL_MASK.or(SdnMudConstants.SRC_NETWORK_MASK);
		BigInteger newMetadata = BigInteger.valueOf(IdUtils.getFlowHash(matchesType.toString()));
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		Direction direction = getDirectionInitiated(matches);

		boolean checkDirectionInitiated = directionCheck(direction, false);

		LOG.info("installMetadataProtocolAndSrcDestPortMatchGoToNextFlow  metadata = " + metadata.toString(16)
				+ " metadataMask = " + mask.toString(16) + " sourcePort " + sourcePort + " destinationPort "
				+ destinationPort);
		int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;

		this.installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(mudUri, aceName, metadata, mask, protocol.shortValue(),
				sourcePort, destinationPort, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
				newMetadataMask, checkDirectionInitiated, flowCookie, flowId, node);

	}

	private void installPermitFromDeviceToLocalNetworksFlowRule(InstanceIdentifier<FlowCapableNode> node, String mudUri, String aceName,
			 Matches matches, MatchesType matchesType, boolean qFlag) {
		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		int modelId = IdUtils.getModelId(mudUri);
		Short protocol = getProtocol(matches);

		FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);
		FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());

		BigInteger metadata = SdnMudConstants.LOCAL_DST_NETWORK_FLAG
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.SRC_MODEL_MASK.or(SdnMudConstants.DST_NETWORK_MASK);

		BigInteger newMetadata = BigInteger.valueOf(IdUtils.getFlowHash(matchesType.toString()));
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;
		Direction direction = getDirectionInitiated(matches);
		boolean checkDirectionInitiated = directionCheck(direction, true);
		int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;
		this.installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(mudUri,aceName, metadata, mask, protocol.shortValue(),
				sourcePort, destinationPort, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
				newMetadataMask, checkDirectionInitiated, flowCookie, flowId, node);

	}

	private void installPermitFromModelToDeviceRule(InstanceIdentifier<FlowCapableNode> node, String mudUri, String aceName,
			Matches matches, MatchesType matchesType, boolean qFlag) {

		Short protocol = getProtocol(matches);
		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());
		String srcModel = getModel(matches);
		int srcModelId = IdUtils.getModelId(srcModel);
		int modelId = IdUtils.getModelId(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);

		BigInteger metadata = BigInteger.valueOf(srcModelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.SRC_MODEL_MASK.or(SdnMudConstants.DST_MODEL_MASK);

		BigInteger newMetadata = BigInteger.valueOf(IdUtils.getFlowHash(matchesType.toString()));
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

		Direction direction = getDirectionInitiated(matches);

		boolean checkDirectionInitiated = directionCheck(direction, false);
		int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;
		this.installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(mudUri,aceName,  metadata, mask, protocol.shortValue(),
				sourcePort, destinationPort, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
				newMetadataMask, checkDirectionInitiated, flowCookie, flowId, node);
	}

	private void installPermitFromManufacturerToDeviceFlowRule(InstanceIdentifier<FlowCapableNode> node, String mudUri, String aceName,
			String manufacturer,  Matches matches, MatchesType matchesType, boolean qFlag) {
		LOG.info("installpermitToDeviceFromManufacturer " + mudUri + " manufacturer " + manufacturer + " cookie "
				+ matchesType);
		Short protocol = getProtocol(matches);

		// Range of ports that this device is allowed to talk to.

		int sourcePort = getSourcePort(matches);
		int destinationPort = getDestinationPort(matches);
		FlowCookie flowCookie = IdUtils.createFlowCookie(matchesType.toString());
		int manufacturerId = IdUtils.getManfuacturerId(manufacturer);
		int modelId = IdUtils.getModelId(mudUri);
		FlowId flowId = IdUtils.createFlowId(mudUri + "/" + aceName);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		BigInteger mask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);

		BigInteger newMetadata = BigInteger.valueOf(IdUtils.getFlowHash(matchesType.toString()));
		BigInteger newMetadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

		Direction direction = getDirectionInitiated(matches);

		boolean checkDirectionInitiated = directionCheck(direction, false);
		int priority = !qFlag ? SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY
				: SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY;

		this.installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(mudUri, aceName, metadata, mask, protocol.shortValue(),
				sourcePort, destinationPort, sdnmudProvider.getSdnmudRulesTable(), priority, newMetadata,
				newMetadataMask, checkDirectionInitiated, flowCookie, flowId, node);
		
	}

	private void installMetadaProtocolAndSrcDestPortMatchGoToNextFlow(String mudUri, String aceName, BigInteger metadata,
			BigInteger metadataMask, short protocol, int srcPort, int destinationPort, short tableId, int priority,
			BigInteger newMetadata, BigInteger newMetadataMask, boolean checkDirectionInitiated, FlowCookie flowCookie,
			FlowId flowId, InstanceIdentifier<FlowCapableNode> node) {
		FlowBuilder fb = FlowUtils.createMetadaProtocolAndSrcDestPortMatchGoToTable(metadata, metadataMask, protocol,
				srcPort, destinationPort, tableId, priority, newMetadata, newMetadataMask, false, flowId, flowCookie);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
		if (checkDirectionInitiated) {
			flowId = IdUtils.createFlowId(mudUri + "/" + aceName);
			FlowCookie cookie = SdnMudConstants.TCP_SYN_MATCH_CHECK_COOKIE;
			this.registerTcpSynFlagCheck(flowId, cookie, node, metadata, metadataMask, srcPort, destinationPort, priority + 1);
		}
	}

	
	/**
	 *
	 * @param nodeConnectorUri
	 * @param mudUri              -- the mud URI
	 *
	 * @param controllerUriString -- The URI string for the controller class we
	 *                            want.
	 * @return
	 */
	private List<IpAddress> getControllerAddresses(String nodeConnectorUri, String controllerUriString) {
		Map<String, List<IpAddress>> controllerMap = sdnmudProvider.getControllerClassMap(nodeConnectorUri);
		if (controllerMap == null) {
			return null;
		}
		return controllerMap.get(controllerUriString);
	}

	private List<Ipv4Address> getControllerMatchAddresses(String nodeConnectorUri, Uri mudUri, Uri controllerUri) {
		// Resolve it.
		List<Ipv4Address> ipAddresses = new ArrayList<Ipv4Address>();
		LOG.info("Resolving controller address for " + controllerUri.getValue());
		if (getControllerAddresses(nodeConnectorUri, controllerUri.getValue()) != null) {
			List<IpAddress> controllerIpAddresses = getControllerAddresses(nodeConnectorUri, controllerUri.getValue());
			for (IpAddress ipAddress : controllerIpAddresses) {
				LOG.info("controllerAddress " + ipAddress.getIpv4Address().getValue());
				ipAddresses.add(ipAddress.getIpv4Address());
			}
		}
		return ipAddresses;
	}

	private static BigInteger createSrcModelMetadata(String mudUri) {
		int modelId = IdUtils.getModelId(mudUri);
		return BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
	}

	private static BigInteger createSrcModelMetadata(String mudUri, boolean isQuarantene) {
		int modelId = IdUtils.getModelId(mudUri);
		int quaranteneFlag = isQuarantene ? 1 : 0;
		return BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT)
				.or(BigInteger.valueOf(quaranteneFlag).shiftLeft(SdnMudConstants.SRC_QUARANTENE_MASK_SHIFT));
	}

	private static BigInteger createDstModelMetadata(String mudUri) {
		return BigInteger.valueOf(IdUtils.getModelId(mudUri)).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT);
	}

	static void installPermitPacketsFromToDnsServer(SdnmudProvider sdnmudProvider, 
			InstanceIdentifier<FlowCapableNode> node, Ipv4Address address, short protocol, int port,
			boolean sendToController) {

		LOG.info("installPermitPacketsFromToServer :  address = " + address.getValue());

		String nodeId = IdUtils.getNodeUri(node);

		FlowCookie flowCookie = SdnMudConstants.DNS_REQUEST_FLOW_COOKIE;
		FlowId flowId = IdUtils.createFlowId(nodeId);

		// Under qurantene, devices may access DNS and dhcp.
		FlowBuilder flowBuilder = FlowUtils.createDestAddressPortProtocolMatchGoToNextFlow(address, port, protocol,
				sdnmudProvider.getSdnmudRulesTable(), SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY,
				sendToController, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
		flowId = IdUtils.createFlowId(nodeId);
		flowCookie = SdnMudConstants.DNS_RESPONSE_FLOW_COOKIE;
		flowBuilder = FlowUtils.createSrcAddressPortProtocolMatchGoToNextFlow(address, port, protocol,
				sdnmudProvider.getSdnmudRulesTable(), SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY, sendToController, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

	}

	public static void installPermitPacketsFromServer(SdnmudProvider sdnmudProvider, FlowCookie flowCookie,
			InstanceIdentifier<FlowCapableNode> node, Ipv4Address address, short protocol, int port) {

		LOG.info("installPermitPacketsFromServer :  address = " + address.getValue() );
		String nodeId = IdUtils.getNodeUri(node);
		FlowId flowId = IdUtils.createFlowId(nodeId);
		FlowBuilder flowBuilder = FlowUtils.createSrcAddressPortProtocolMatchGoToNextFlow(address, port, protocol,
				sdnmudProvider.getSdnmudRulesTable(), SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY, false, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

	}

	public static void installPermitPacketsToServer(SdnmudProvider sdnmudProvider, FlowCookie flowCookie,
			InstanceIdentifier<FlowCapableNode> node,  Ipv4Address address, short protocol, int port) {

		LOG.info("installPermitPacketsFromToServer :  address = " + address.getValue());

		String nodeId = IdUtils.getNodeUri(node);

		FlowId flowId = IdUtils.createFlowId(nodeId);

		FlowBuilder flowBuilder = FlowUtils.createDestAddressPortProtocolMatchGoToNextFlow(address, port, protocol,
				sdnmudProvider.getSdnmudRulesTable(), SdnMudConstants.MATCHED_GOTO_ON_QUARANTENE_PRIORITY, false,
				flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
	}

	public void installPermitPacketsToFromDhcp(InstanceIdentifier<FlowCapableNode> node) {

		String nodeId = IdUtils.getNodeUri(node);
		FlowCookie flowCookie = SdnMudConstants.DH_REQUEST_FLOW_COOKIE;
		FlowId flowId = IdUtils.createFlowId(nodeId);
		FlowBuilder flowBuilder = FlowUtils.createToDhcpServerMatchGoToNextTableFlow(
				sdnmudProvider.getSdnmudRulesTable(), flowCookie, flowId, true);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);

		// DHCP is local so both directions are installed on the CPE node.
		flowId = IdUtils.createFlowId(nodeId);
		flowBuilder = FlowUtils.createFromDhcpServerMatchGoToNextTableFlow(sdnmudProvider.getSdnmudRulesTable(),
				flowCookie, flowId);
		this.sdnmudProvider.getFlowCommitWrapper().writeFlow(flowBuilder, node);
	}
	
	public void installDropBlockedMacFlows(InstanceIdentifier<FlowCapableNode> node) {
		String nodeId = IdUtils.getNodeUri(node);
		FlowCookie flowCookie = SdnMudConstants.BLOCK_DST_MAC_FLOW_COOKIE;
		FlowId flowId = IdUtils.createFlowId(nodeId);
		BigInteger metadata = SdnMudConstants.DST_MAC_BLOCKED_MASK;
		BigInteger metadataMask = SdnMudConstants.DST_MAC_BLOCKED_FLAG;
		int priority = SdnMudConstants.MATCHED_DROP_ON_QUARANTINE_PRIORITY;
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToNextTableFlow(metadata, metadataMask, sdnmudProvider.getSdnmudRulesTable(), 
				sdnmudProvider.getDropTable(),priority, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
		
		flowCookie = SdnMudConstants.BLOCK_SRC_MAC_FLOW_COOKIE;
		flowId = IdUtils.createFlowId(nodeId);
		metadata = SdnMudConstants.SRC_MAC_BLOCKED_MASK;
		metadataMask = SdnMudConstants.SRC_MAC_BLOCKED_FLAG;
		fb = FlowUtils.createMetadataMatchGoToNextTableFlow(metadata, metadataMask, sdnmudProvider.getSdnmudRulesTable(), 
				sdnmudProvider.getDropTable(),priority,flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
	}

	public void installAllowToDnsAndNtpFlowRules(InstanceIdentifier<FlowCapableNode> node) {
		String nodeId = IdUtils.getNodeUri(node);

		if (this.getDnsAddress(nodeId) != null) {
			Ipv4Address dnsAddress = this.getDnsAddress(nodeId).getIpv4Address();
			if (dnsAddress != null) {
				LOG.info("Installing DNS rules");
				installPermitPacketsFromToDnsServer(this.sdnmudProvider, node, dnsAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.DNS_PORT, true);
				installPermitPacketsFromToDnsServer(this.sdnmudProvider,  node, dnsAddress, SdnMudConstants.TCP_PROTOCOL,
						SdnMudConstants.DNS_PORT, true);
			}
		}

		if (this.getNtpAddress(nodeId) != null) {
			Ipv4Address ntpAddress = this.getNtpAddress(nodeId).getIpv4Address();
			if (ntpAddress != null) {
				LOG.info("Installing NTP rules");
				installPermitPacketsToServer(this.sdnmudProvider, SdnMudConstants.DEFAULT_MUD_FLOW_COOKIE, node, ntpAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.NTP_SERVER_PORT);
				installPermitPacketsFromServer(this.sdnmudProvider, SdnMudConstants.DEFAULT_MUD_FLOW_COOKIE, node, ntpAddress, SdnMudConstants.UDP_PROTOCOL,
						SdnMudConstants.NTP_SERVER_PORT);
			}
		}

	}

	public void installUnknownDestinationPassThrough(InstanceIdentifier<FlowCapableNode> node) {
		BigInteger metadata = (BigInteger.valueOf(IdUtils.getManfuacturerId(SdnMudConstants.UNKNOWN))
				.shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT))
						.or(BigInteger.valueOf(IdUtils.getModelId(SdnMudConstants.UNKNOWN))
								.shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));
		int priority = SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY + 2;

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);

		FlowId flowId = IdUtils.createFlowId(IdUtils.getNodeUri(node));
		FlowCookie flowCookie = IdUtils.createFlowCookie("metadata-match-go-to-next");
		short tableId = sdnmudProvider.getSdnmudRulesTable();
		FlowBuilder fb = FlowUtils.createMetadataMatchGoToNextTableFlow(metadata, metadataMask, tableId, (short) (tableId + 1), priority, flowId,
				flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

		flowId = IdUtils.createFlowId(IdUtils.getNodeUri(node));
		metadata = BigInteger.valueOf(IdUtils.getManfuacturerId(SdnMudConstants.UNKNOWN))
				.shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(IdUtils.getModelId(SdnMudConstants.UNKNOWN))
						.shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK);
		fb = FlowUtils.createMetadataMatchGoToNextTableFlow(metadata, metadataMask, tableId, (short) (tableId + 1), priority, flowId, flowCookie);
		sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

	}

	/**
	 * Retrieve and install flows for a device of a given MAC address.
	 *
	 * @param sdnmudProvider   -- our provider.
	 *
	 * @param deviceMacAddress -- the mac address of the device.
	 *
	 * @param node             -- the node on which the packet was received.
	 * @param nodeUri          -- the URI of the node.
	 */
	public synchronized boolean tryInstallFlows(Mud mud, String cpeNodeId) {
		try {
			Uri mudUri = mud.getMudUrl();
			HashSet<String> enabledAceNames = new HashSet<String>();
			boolean hasQuarantineDevicePolicy;

			if (mud.getAugmentation(Mud1.class) != null) {
				QuarantinedDevicePolicy qdp = mud.getAugmentation(Mud1.class).getQuarantinedDevicePolicy();
				if (qdp != null) {
					hasQuarantineDevicePolicy = true;
					List<EnabledAceNames> aceNames = qdp.getEnabledAceNames();
					for (EnabledAceNames aceName : aceNames) {
						enabledAceNames.add(aceName.getAceName());
					}
				} else {
					hasQuarantineDevicePolicy = false;
				}
			} else {
				hasQuarantineDevicePolicy = false;
			}

			if (sdnmudProvider.getControllerClassMap(cpeNodeId) == null) {
				LOG.info("Cannot find ControllerClass mapping for the switch  -- not installing ACLs. nodeUrl "
						+ cpeNodeId);
				return false;
			} else {
				LOG.info("installFlows: Found a controllerclass mapping for the switch ");
			}

			// Delete the existing flows corresponding to this profile.

			String authority = IdUtils.getAuthority(mudUri);

			// Remove the flow rules for the given device for this MUD url.
			InstanceIdentifier<FlowCapableNode> node = this.sdnmudProvider.getNode(cpeNodeId);
			if (node == null) {
				LOG.info("installFlows -- cpe Node is null -- skipping MUD install.");
				return false;
			}

			// Delete all the flows previously associated with this MUD URI.
			sdnmudProvider.getPacketInDispatcher().block();
			try {

				sdnmudProvider.getFlowCommitWrapper().deleteFlows(node, mudUri.getValue(),
						sdnmudProvider.getSdnmudRulesTable(), null, null);

				/*
				 * Track that we have added a node for this device MAC address for this node.
				 * i.e. we store MUD rules for this device on the given node.
				 */

				LOG.info("installFlows : authority (manufacturer) = " + "[" + authority + "]");

				this.sdnmudProvider.addMudNode(authority, node);

				// Records that the MUD URI is installed at this CPE node.
				this.sdnmudProvider.addMudUri(cpeNodeId, mudUri);

				/*
				 * Drop table is where all the unsuccessful matches land up. Push default drop
				 * packet flows that will drop the packet if a MUD rule does not match.
				 */

				if (hasQuarantineDevicePolicy) {
					this.installGotoDropTableOnQuaranteneSrcModelMetadataMatchFlow(mudUri.getValue(), node,SdnMudConstants.MATCHED_DROP_ON_QUARANTINE_PRIORITY);
					this.installGoToDropTableOnQuaranteneDstModelMetadataMatchFlow(mudUri.getValue(),node, SdnMudConstants.MATCHED_DROP_ON_QUARANTINE_PRIORITY);
					
				}
				
				// If the packet is quarantined already it will hit this rule first.
				this.installGotoDropTableOnQuaranteneSrcModelMetadataMatchFlow(mudUri.getValue(),node,SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY + 1);
				this.installGoToDropTableAndSendToControllerOnSrcModelMetadataMatchFlow(mudUri.getValue(), node,SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY);
				this.installGoToDropTableOnDstModelMetadataMatchFlow(mudUri.getValue(), node, SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY);

				/*
				 * Fetch and install the MUD ACLs. First install the "from-device" rules.
				 */
				FromDevicePolicy fromDevicePolicy = mud.getFromDevicePolicy();
				if (fromDevicePolicy != null) {
					AccessLists accessLists = fromDevicePolicy.getAccessLists();
					for (AccessList accessList : accessLists.getAccessList()) {
						String aclName = accessList.getName();
						Aces aces = this.sdnmudProvider.getAces(aclName);
						if (aces != null) {
							for (Ace ace : aces.getAce()) {
								String aceName = ace.getName();
								// Is this ACE enabled for quarantine access?
								boolean qFlag = enabledAceNames.contains(aceName);
								if (ace.getActions().getForwarding().equals(Accept.class)) {

									Matches matches = ace.getMatches();
									MatchesType matchesType = matchesType(matches);
									LOG.info("matchType " + matchesType);
									if (matchesType == MatchesType.DNS_MATCH) {

										List<Ipv4Address> addresses = getMatchAddresses(node, matches);
										if (!addresses.isEmpty()) {
											this.installPermitFromDeviceToIpAddressFlowRules(node, mudUri.getValue(),
													aceName, matches, matchesType, addresses, qFlag);
										}
										// Cache the current resolution. In case this changes we have to update
										// it.
										this.deferDnsMatch(node, mudUri.getValue(), aceName, matches, matchesType, addresses, false,
												qFlag);
									} else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
										Matches1 matches1 = matches.getAugmentation(Matches1.class);
										Uri controllerUri = matches1.getMud().getController();
										List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId, mudUri,
												controllerUri);
										this.installPermitFromDeviceToIpAddressFlowRules(node, mudUri.getValue(), aceName,
												matches, matchesType, addresses, qFlag);
									} else if (matchesType == MatchesType.MY_CONTROLLER) {
										List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId, mudUri,
												mudUri);
										this.installPermitFromDeviceToIpAddressFlowRules(node, mudUri.getValue(), aceName,
												matches, matchesType, addresses, qFlag);
									} else if (matchesType == MatchesType.LOCAL_NETWORKS) {
										this.installPermitFromDeviceToLocalNetworksFlowRule(node, mudUri.getValue(), aceName,
												 matches, matchesType, qFlag);
									} else if (matchesType == MatchesType.MANUFACTURER) {
										String manufacturer = getManufacturer(matches);
										this.installPermitFromDeviceToManufacturerFlowRule(node, mudUri.getValue(), aceName,
												manufacturer, matches, matchesType, qFlag);
									} else if (matchesType == MatchesType.SAME_MANUFACTURER) {
										String manufacturer = IdUtils.getAuthority(mudUri.getValue());
										this.installPermitFromDeviceToManufacturerFlowRule(node, mudUri.getValue(), aceName,
												manufacturer,  matches, matchesType, qFlag);
									} else if (matchesType == MatchesType.MODEL) {
										this.installPermitFromDeviceToModelFlowRule(node, mudUri.getValue(), aceName,
												matches, matchesType, qFlag);
									}
								} else {
									LOG.info("DENY rule not implemented");
									return false;
								}
							}

						} else {
							LOG.info("Could not find ACEs for " + aclName);
							return false;
						}
					}
				}

				ToDevicePolicy toDevicePolicy = mud.getToDevicePolicy();
				if (toDevicePolicy != null) {
					AccessLists accessLists = toDevicePolicy.getAccessLists();
					for (AccessList accessList : accessLists.getAccessList()) {
						String aclName = accessList.getName();
						Aces aces = this.sdnmudProvider.getAces(aclName);
						if (aces != null) {
							for (Ace ace : aces.getAce()) {
								String aceName = ace.getName();
								boolean qFlag = enabledAceNames.contains(ace.getName());
								if (ace.getActions().getForwarding().equals(Accept.class)) {

									Matches matches = ace.getMatches();
									MatchesType matchesType = matchesType(matches);
									LOG.info("matchType " + matchesType);
									if (matchesType == MatchesType.DNS_MATCH) {
										List<Ipv4Address> addresses = getMatchAddresses(node, matches);
										if (!addresses.isEmpty()) {
											this.installPermitFromIpAddressToDeviceFlowRules(node, mudUri.getValue(), aceName,
													matches, matchesType, addresses, qFlag);
										}
										this.deferDnsMatch(node, mudUri.getValue(), aceName, matches, matchesType, addresses, true, qFlag);
									} else if (matchesType == MatchesType.CONTROLLER_MAPPING) {
										Matches1 matches1 = matches.getAugmentation(Matches1.class);
										Uri controllerUri = matches1.getMud().getController();
										List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId, mudUri, 
												controllerUri);
										this.installPermitFromIpAddressToDeviceFlowRules(node, mudUri.getValue(),
												aceName, matches, matchesType, addresses, qFlag);
									} else if (matchesType == MatchesType.MY_CONTROLLER) {
										List<Ipv4Address> addresses = getControllerMatchAddresses(cpeNodeId, mudUri,
												mudUri);
										this.installPermitFromIpAddressToDeviceFlowRules(node, mudUri.getValue(), aceName,
												matches, matchesType, addresses, qFlag);
									} else if (matchesType == MatchesType.LOCAL_NETWORKS) {
										this.installPermitFromLocalNetworksToDeviceFlowRule(node, mudUri.getValue(), aceName,
												 matches, matchesType, qFlag);
									} else if (matchesType == MatchesType.MANUFACTURER) {
										String manufacturer = getManufacturer(matches);
										this.installPermitFromManufacturerToDeviceFlowRule(node, mudUri.getValue(), aceName,
												manufacturer, matches, matchesType, qFlag);
									} else if (matchesType == MatchesType.SAME_MANUFACTURER) {
										String manufacturer = IdUtils.getAuthority(mudUri.getValue());
										this.installPermitFromManufacturerToDeviceFlowRule(node, mudUri.getValue(), aceName,
												manufacturer, matches, matchesType, qFlag);
									} else if (matchesType == MatchesType.MODEL) {
										this.installPermitFromModelToDeviceRule(node, mudUri.getValue(), aceName, 
												matches, matchesType, qFlag);
									}
								} else {
									LOG.error("DENY rules not implemented");
									return false;
								}

							}
						} else {
							LOG.info("Could not find ACEs for " + aclName);
							return false;
						}

					}
					// Clear the cache so can be re-poplulated after packets come in again.
					this.sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
				}

			} catch (Exception ex) {
				LOG.error("MudFlowsInstaller: Exception caught installing MUD Flow ", ex);
				return false;
			}
		} finally {
			this.sdnmudProvider.getPacketInDispatcher().unblock();
		}
		return true;

	}

	

	/**
	 * Clear out all the mud rules from all nodes that we know about. This is used
	 * for testing purposes.
	 */
	public void clearMudRules() {
		LOG.info("clearMudRules: clearing the mud rules table");

		for (String uri : this.sdnmudProvider.getCpeSwitches()) {
			InstanceIdentifier<FlowCapableNode> flowCapableNode = this.sdnmudProvider.getNode(uri);
			if (flowCapableNode != null) {
				for (Mud mud : this.sdnmudProvider.getMudProfiles()) {
					String uriPrefix = mud.getMudUrl().getValue() + "/sdnmud";
					short table = sdnmudProvider.getSdnmudRulesTable();
					this.sdnmudProvider.getFlowCommitWrapper().deleteFlows(flowCapableNode, uriPrefix, table, null,
							null);
				}
			}
		}
		this.nameResolutionCache.clear();
		LOG.info("clearMudRules: done cleaning mud rules");
	}

	

}
