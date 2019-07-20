package gov.nist.antd.sdnmud.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.Mud1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporterBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReportBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.Controllers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.mud.report.ControllersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;

public class MudReportSender implements Runnable {
	private SdnmudProvider sdnmudProvider;

	public MudReportSender(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	@Override
	public void run() {
		for (Mud mud : sdnmudProvider.getMudProfiles()) {
			// If this has a MUD reporter then generate and send reports.
			if (mud.getAugmentation(Mud1.class) != null) {
				DroppedPacketsScanner dps = sdnmudProvider.getDroppedPacketsScanner();
				MudReporterBuilder mudReporterBuilder = new MudReporterBuilder();

				mudReporterBuilder.setMudurl(mud.getMudUrl());

				ArrayList<MudReport> mudReports = new ArrayList<MudReport>();
				
				for (String switchId : sdnmudProvider.getCpeSwitches()) {

					MudReportBuilder mudReportBuilder = new MudReportBuilder();

					mudReportBuilder.setEnforcementPointId(switchId);
					ArrayList<Controllers> controllersList = new ArrayList<Controllers>();

					Map<String,List<Ipv4Address>> classMapping = sdnmudProvider.getControllerClassMap(switchId);
					
					
					for (String uri : classMapping.keySet()) {
						ControllersBuilder controllersBuilder = new ControllersBuilder();
						controllersBuilder.setUri(new Uri(uri));
						controllersBuilder.setCount(Long.valueOf(classMapping.get(uri).size()));
						controllersList.add(controllersBuilder.build());
					}
					
					PerSwitchDropCount perSwitchDropCount = sdnmudProvider.getDroppedPacketsScanner().getDropCount(switchId,
							mud.getMudUrl().getValue());
					
					
					mudReportBuilder.setControllers(controllersList);
					mudReports.add(mudReportBuilder.build());
				}
				

			}
		}

	}

}
