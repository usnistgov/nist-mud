package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.Mud1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.DropCount.Direction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporterBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.drop.count.drop.type.BlockedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.drop.count.drop.type.TcpBlockedBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReportBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.Controllers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.ControllersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.Domains;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.DomainsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.DropCounts;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.DropCountsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.MatchCounts;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.MatchCountsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
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
import java.util.TimerTask;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MudReportGenerator {
	private static final Logger LOG = LoggerFactory.getLogger(MudReportGenerator.class);

	private SdnmudProvider sdnmudProvider;

	public MudReportGenerator(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	public MudReport getMudReport(Mud mud, String switchId) {

		MudReportBuilder mudReportBuilder = new MudReportBuilder();

		mudReportBuilder.setTime(new Timestamp(System.currentTimeMillis() / 1000 / 60));

		mudReportBuilder.setEnforcementPointId(switchId);
		ArrayList<Controllers> controllersList = new ArrayList<Controllers>();

		Map<String, List<Ipv4Address>> classMapping = sdnmudProvider.getControllerClassMap(switchId);
		InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(switchId);

		for (String uri : sdnmudProvider.getMudFlowsInstaller().getControllers(switchId, mud.getMudUrl().getValue())) {
			if (!uri.contentEquals(mud.getMudUrl().getValue())) {
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
		ArrayList<DropCounts> dropCountsList = new ArrayList<DropCounts>();

		/*
		 * 
		 * Map<String, Integer> blockedDropCounts =
		 * perSwitchDropCount.getBlockedFromDropCount();
		 * 
		 * for (String url : blockedDropCounts.keySet()) { BlockedBuilder blockedBuilder
		 * = new BlockedBuilder(); blockedBuilder.setUrl(new Uri(url));
		 * DropCountsBuilder dcB = new DropCountsBuilder();
		 * dcB.setDropType(blockedBuilder.build());
		 * dcB.setDropCount(BigInteger.valueOf(blockedDropCounts.get(url)));
		 * dcB.setDirection(Direction.FromDevice); dropCountsList.add(dcB.build()); }
		 * 
		 * Map<String, Integer> blockedToCounts =
		 * perSwitchDropCount.getBlockedToDropCount();
		 * 
		 * for (String url : blockedToCounts.keySet()) { BlockedBuilder blockedBuilder =
		 * new BlockedBuilder(); blockedBuilder.setUrl(new Uri(url)); DropCountsBuilder
		 * dcB = new DropCountsBuilder(); dcB.setDropType(blockedBuilder.build());
		 * dcB.setDropCount(BigInteger.valueOf(blockedDropCounts.get(url)));
		 * dcB.setDirection(Direction.ToDevice); dropCountsList.add(dcB.build()); }
		 * 
		 * int localNetworksDropCounts = perSwitchDropCount.getLocalNetworksDropCount();
		 * LocalNetworksBuilder blockedBuilder = new LocalNetworksBuilder();
		 * DropCountsBuilder dcB = new DropCountsBuilder();
		 * dcB.setDropCount(BigInteger.valueOf(localNetworksDropCounts));
		 * dcB.setDropType(blockedBuilder.build()); dropCountsList.add(dcB.build());
		 * mudReportBuilder.setDropCounts(dropCountsList);
		 */

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
		ArrayList<MatchCounts> matchCountsList = new ArrayList<MatchCounts>();
		try {
			Collection<Flow> flows = sdnmudProvider.getFlowCommitWrapper().getFlows(node);
			MatchCountsBuilder mcb = new MatchCountsBuilder();

			if (flows != null) {

				for (Flow flow : flows) {
					if (flow.getId().getValue().startsWith(mud.getMudUrl().getValue())) {
						InstanceIdentifier<Node> outNode = node.firstIdentifierOf(Node.class);
						NodeRef nodeRef = new NodeRef(outNode);

						GetFlowStatisticsInputBuilder inputBuilder = new GetFlowStatisticsInputBuilder();

						inputBuilder.setFlowName(flow.getId().getValue());
						inputBuilder.setMatch(flow.getMatch());
						inputBuilder.setTableId(flow.getTableId());
						inputBuilder.setInstructions(flow.getInstructions());
						inputBuilder.setNode(nodeRef);

						GetFlowStatisticsOutput output = sdnmudProvider.getDirectStatisticsService()
								.getFlowStatistics(inputBuilder.build()).get().getResult();
						if (output != null && output.getFlowAndStatisticsMapList() != null) {
							LOG.info("flowstatisticsMapList : " + output.getFlowAndStatisticsMapList().size());

							for (FlowAndStatisticsMapList fmaplist : output.getFlowAndStatisticsMapList()) {
								if (flow.getId().getValue().indexOf(SdnMudConstants.DROP_ON_SRC_MODEL_MATCH) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									BlockedBuilder blockedBuilder1 = new BlockedBuilder();
									blockedBuilder1.setAceName(flow.getFlowName());
									dcb1.setDropType(blockedBuilder1.build());
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									dcb1.setDirection(Direction.FromDevice);
									dropCountsList.add(dcb1.build());
								} else if (flow.getId().getValue()
										.indexOf(SdnMudConstants.DROP_ON_DST_MODEL_MATCH) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();

									BlockedBuilder blockedBuilder1 = new BlockedBuilder();
									blockedBuilder1.setAceName(flow.getFlowName());
									dcb1.setDropType(blockedBuilder1.build());
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									dcb1.setDirection(Direction.ToDevice);
									dropCountsList.add(dcb1.build());
								} else if (flow.getId().getValue()
										.indexOf(SdnMudConstants.DROP_ON_TCP_SYN_INBOUND) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									dcb1.setDirection(Direction.ToDevice);
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									TcpBlockedBuilder bb = new TcpBlockedBuilder();
									bb.setAceName(flow.getFlowName());
									dropCountsList.add(dcb1.build());
								} else if (flow.getId().getValue()
										.indexOf(SdnMudConstants.DROP_ON_TCP_SYN_OUTBOUND) != -1) {
									DropCountsBuilder dcb1 = new DropCountsBuilder();
									dcb1.setDirection(Direction.FromDevice);
									dcb1.setDropCount(fmaplist.getPacketCount().getValue());
									TcpBlockedBuilder bb = new TcpBlockedBuilder();
									bb.setAceName(flow.getFlowName());
									dropCountsList.add(dcb1.build());
								} else {
									mcb.setPacketCount(fmaplist.getPacketCount().getValue());
									mcb.setAceName(flow.getFlowName());
									matchCountsList.add(mcb.build());
								}
							}
						}
					} else {
						LOG.info("null FlowAndStatisticsMapList");
					}
				}
			}
		} catch (Exception ex) {
			LOG.error("Exception getting flow stats ", ex);
		}
		mudReportBuilder.setMatchCounts(matchCountsList);
		mudReportBuilder.setDropCounts(dropCountsList);

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
