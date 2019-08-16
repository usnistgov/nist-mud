package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.DropCount;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.DropCount.Direction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.drop.count.drop.reason.NomatchBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.drop.count.drop.reason.TcpBlockedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReportBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.Controllers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.ControllersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.Domains;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.DomainsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.DropCounts;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.DropCountsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.ManufacturersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.MatchCounts;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.MatchCountsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.ModelsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Timestamp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.flow.and.statistics.map.list.FlowAndStatisticsMapList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MudReportGenerator {
	private static final Logger LOG = LoggerFactory.getLogger(MudReportGenerator.class);

	private SdnmudProvider sdnmudProvider;

	public MudReportGenerator(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	private static String getMd5(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");

			byte[] messageDigest = md.digest(input.getBytes());
			// Convert byte array into signum representation
			BigInteger no = new BigInteger(1, messageDigest);

			// Convert message digest into hex value
			String hashtext = no.toString(16);
			while (hashtext.length() < 32) {
				hashtext = "0" + hashtext;
			}
			return hashtext;
		}

		// For specifying wrong message digest algorithms
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public MudReport getMudReport(Mud mud, String switchId) {

		MudReportBuilder mudReportBuilder = new MudReportBuilder();

		mudReportBuilder.setTime(new Timestamp(System.currentTimeMillis() / 1000 / 60));

		mudReportBuilder.setEnforcementPointId(switchId);
		ArrayList<Controllers> controllersList = new ArrayList<Controllers>();

		Map<String, List<Ipv4Address>> classMapping = sdnmudProvider.getControllerClassMap(switchId);
		InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(switchId);

		for (String uri : sdnmudProvider.getMudFlowsInstaller().getControllers(switchId, mud.getMudUrl().getValue())) {
			if (!uri.equals(mud.getMudUrl().getValue())) {
				ControllersBuilder controllersBuilder = new ControllersBuilder();
				controllersBuilder.setUri(new Uri(uri));
				if (classMapping.get(uri) == null) {
					controllersBuilder.setCount(Long.valueOf(0L));
				} else {
					controllersBuilder.setCount(Long.valueOf(classMapping.get(uri).size()));

				}
				controllersList.add(controllersBuilder.build());
			}
		}

		if (classMapping.get(SdnMudConstants.DNS_SERVER_URI) != null) {
			ControllersBuilder controllersBuilder = new ControllersBuilder();
			controllersBuilder.setUri(new Uri(SdnMudConstants.DNS_SERVER_URI));
			controllersBuilder.setCount((long) classMapping.get(SdnMudConstants.DNS_SERVER_URI).size());
			controllersList.add(controllersBuilder.build());
		}

		if (classMapping.get(SdnMudConstants.NTP_SERVER_URI) != null) {
			ControllersBuilder controllersBuilder = new ControllersBuilder();
			controllersBuilder.setUri(new Uri(SdnMudConstants.NTP_SERVER_URI));
			controllersBuilder.setCount((long) classMapping.get(SdnMudConstants.NTP_SERVER_URI).size());
			controllersList.add(controllersBuilder.build());
		}

		if (!controllersList.isEmpty()) {
			mudReportBuilder.setControllers(controllersList);
		} else {
			LOG.info("Controller class mapping is empty");
		}

		// BUGBUG -- mycontroller mapping is wrong.

		Integer myControllerCount = classMapping.get(mud.getMudUrl().getValue()) == null ? 0
				: classMapping.get(mud.getMudUrl().getValue()).size();
		if (myControllerCount != 0) {
			mudReportBuilder.setMycontrollers(Long.valueOf(myControllerCount));
		}

		HashSet<String> manufacturers = sdnmudProvider.getMudFlowsInstaller().getManufacturerMatches(mud.getMudUrl());

		if (manufacturers != null && manufacturers.size() > 0) {
			Map<Uri, HashSet<MacAddress>> mappings = sdnmudProvider.getMappingDataStoreListener().getMapping();
			for (Uri uri : mappings.keySet()) {
				for (String man : manufacturers) {
					if (uri.getValue().indexOf(man) != -1) {
						int count = mappings.get(uri).size();
						ManufacturersBuilder mfgb = new ManufacturersBuilder();
						mfgb.setCount(Long.valueOf(count));
						mfgb.setAuthority(man);
					}
				}
			}
		}

		HashSet<String> models = sdnmudProvider.getMudFlowsInstaller().getModelMatches(mud.getMudUrl());

		if (models != null && models.size() > 0) {
			Map<Uri, HashSet<MacAddress>> mappings = sdnmudProvider.getMappingDataStoreListener().getMapping();
			for (Uri uri : mappings.keySet()) {
				for (String model : models) {
					if (uri.getValue().equals(model)) {
						int count = mappings.get(uri).size();
						ModelsBuilder mfgb = new ModelsBuilder();
						mfgb.setCount(Long.valueOf(count));
						mfgb.setUri(new Uri(model));
					}
				}
			}
		}

		NameResolutionCache nameResolutionCache = this.sdnmudProvider.getNameResolutionCache();

		Collection<String> names = this.sdnmudProvider.getMudFlowsInstaller().getDnsNames(switchId,
				mud.getMudUrl().getValue());
		ArrayList<Domains> domainsList = new ArrayList<Domains>();

		for (String name : names) {
			List<Ipv4Address> addresses = nameResolutionCache.doNameLookup(node, name);
			if (addresses != null && !addresses.isEmpty()) {
				DomainsBuilder domainsBuilder = new DomainsBuilder();
				List<IpAddress> ipAddresses = new ArrayList<IpAddress>();
				for (Ipv4Address address : addresses) {
					IpAddress ipAddress = new IpAddress(address);
					ipAddresses.add(ipAddress);
				}
				domainsBuilder.setHostname(name);
				domainsBuilder.setIpAddresses(ipAddresses);
				domainsList.add(domainsBuilder.build());
			}
		}

		mudReportBuilder.setDomains(domainsList);
		HashMap<String, MatchCounts> matchCountsSet = new HashMap<String, MatchCounts>();
		HashMap<String,DropCounts> dropCountsSet = new HashMap<String,DropCounts>();

		try {
			Collection<Flow> flows = sdnmudProvider.getFlowCommitWrapper().getFlows(node);
			MatchCountsBuilder mcb = new MatchCountsBuilder();

			if (flows != null) {
				int beginIndex = mud.getMudUrl().getValue().length() + 1;

				for (Flow flow : flows) {
					if (flow.getId().getValue().startsWith(mud.getMudUrl().getValue())
							&& (flow.getTableId() == sdnmudProvider.getSrcMatchTable()
									|| flow.getTableId() == sdnmudProvider.getDstMatchTable())) {
						InstanceIdentifier<Node> outNode = node.firstIdentifierOf(Node.class);
						NodeRef nodeRef = new NodeRef(outNode);

						GetFlowStatisticsInputBuilder inputBuilder = new GetFlowStatisticsInputBuilder();

						inputBuilder.setFlowName(flow.getFlowName());
						inputBuilder.setMatch(flow.getMatch());
						inputBuilder.setTableId(flow.getTableId());
						inputBuilder.setInstructions(flow.getInstructions());
						inputBuilder.setNode(nodeRef);

						GetFlowStatisticsOutput output = sdnmudProvider.getDirectStatisticsService()
								.getFlowStatistics(inputBuilder.build()).get().getResult();
						if (output != null && output.getFlowAndStatisticsMapList() != null) {
							LOG.info("flowstatisticsMapList : " + output.getFlowAndStatisticsMapList().size());
							LOG.info("flow ID = " + flow.getId());
							for (FlowAndStatisticsMapList fmaplist : output.getFlowAndStatisticsMapList()) {
								if (flow.getId().getValue().indexOf(SdnMudConstants.NO_FROM_DEV_ACE_MATCH_DROP) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									NomatchBuilder blockedBuilder1 = new NomatchBuilder();
									dcb1.setDropReason(blockedBuilder1.build());
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									dcb1.setDirection(Direction.FromDevice);
									dcb1.setReason(DropCount.Reason.Nomatch);
									if ( dropCountsSet.get(flow.getId().getValue()) == null ) {
										dropCountsSet.put(flow.getId().getValue(),dcb1.build());
									} else {
										if ( dcb1.getDropCount().longValue() >  
										dropCountsSet.get(flow.getId().getValue()).getDropCount().longValue()) {
											dropCountsSet.put(flow.getId().getValue(), dcb1.build());
										}
									}
								} else if (flow.getId().getValue()
										.indexOf(SdnMudConstants.NO_TO_DEV_ACE_MATCH_DROP) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									NomatchBuilder bb = new NomatchBuilder();
									dcb1.setDropReason(bb.build());
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									dcb1.setDirection(Direction.ToDevice);
									dcb1.setReason(DropCount.Reason.Nomatch);
									if ( dropCountsSet.get(flow.getId().getValue()) == null ) {
										dropCountsSet.put(flow.getId().getValue(),dcb1.build());
									} else {
										if ( dcb1.getDropCount().longValue() >  
										dropCountsSet.get(flow.getId().getValue()).getDropCount().longValue()) {
											dropCountsSet.put(flow.getId().getValue(), dcb1.build());
										}
									}
								} else if (flow.getId().getValue()
										.indexOf(SdnMudConstants.DROP_ON_TCP_SYN_INBOUND) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									dcb1.setDirection(Direction.ToDevice);
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									TcpBlockedBuilder bb = new TcpBlockedBuilder();
									bb.setAceName(flow.getId().getValue().substring(beginIndex));
									dcb1.setDropReason(bb.build());
									dcb1.setReason(DropCount.Reason.ConnectionBlock);
									if ( dropCountsSet.get(flow.getId().getValue()) == null ) {
										dropCountsSet.put(flow.getId().getValue(),dcb1.build());
									} else {
										if ( dcb1.getDropCount().longValue() >  
										dropCountsSet.get(flow.getId().getValue()).getDropCount().longValue()) {
											dropCountsSet.put(flow.getId().getValue(), dcb1.build());
										}
									}
								} else if (flow.getId().getValue()
										.indexOf(SdnMudConstants.DROP_ON_TCP_SYN_OUTBOUND) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									dcb1.setDirection(Direction.FromDevice);
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									TcpBlockedBuilder bb = new TcpBlockedBuilder();
									bb.setAceName(flow.getId().getValue().substring(beginIndex));
									dcb1.setReason(DropCount.Reason.ConnectionBlock);
									dcb1.setDropReason(bb.build());
									if ( dropCountsSet.get(flow.getId().getValue()) == null ) {
										dropCountsSet.put(flow.getId().getValue(),dcb1.build());
									} else {
										if ( dcb1.getDropCount().longValue() >  
										dropCountsSet.get(flow.getId().getValue()).getDropCount().longValue()) {
											dropCountsSet.put(flow.getId().getValue(), dcb1.build());
										}
									}
								} else {
									mcb.setPacketCount(fmaplist.getPacketCount().getValue());
									mcb.setAceName(flow.getId().getValue().substring(beginIndex));
									if (matchCountsSet.get(flow.getId().getValue()) == null	) {
										matchCountsSet.put(flow.getId().getValue(), mcb.build());
									} else if (fmaplist.getPacketCount().getValue().longValue() > 
									     matchCountsSet.get(flow.getId().getValue()).getPacketCount().longValue()) {
										matchCountsSet.put(flow.getId().getValue(), mcb.build());

									}
								}
							}
						}
					} else {
						LOG.info("null FlowAndStatisticsMapList");
					}
				}
			}

			mudReportBuilder.setMatchCounts(new ArrayList<MatchCounts>(matchCountsSet.values()));
			mudReportBuilder.setDropCounts(new ArrayList<DropCounts>(dropCountsSet.values()));

			Collection<MacAddress> macAddresses = sdnmudProvider.getMappingDataStoreListener().getMacs(mud.getMudUrl());

			List<String> opaqueIdentifiers = new ArrayList<String>();
			for (MacAddress macAddress : macAddresses) {
				opaqueIdentifiers.add(getMd5(macAddress.getValue()));
			}
			mudReportBuilder.setOpaqueIdentifier(opaqueIdentifiers);

		} catch (Exception ex) {
			LOG.error("Exception getting flow stats ", ex);
		}
		// now gather up a list of offending MAC addresses

		return mudReportBuilder.build();
	}

	public List<MudReport> getMudReports(Mud mud) {
		ArrayList<MudReport> mudReports = new ArrayList<MudReport>();
		for (String switchId : sdnmudProvider.getCpeSwitches()) {
			MudReport mudReport = this.getMudReport(mud, switchId);
			mudReports.add(mudReport);
		}
		return mudReports;
	}

}
