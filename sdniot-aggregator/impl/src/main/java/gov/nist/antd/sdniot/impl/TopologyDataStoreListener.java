package gov.nist.antd.sdniot.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.Mud;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;

public class TopologyDataStoreListener implements DataTreeChangeListener<Topology> {

	private SdnmudProvider sdnmudProvider;

	public TopologyDataStoreListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<Topology>> changes) {
		
		for (DataTreeModification<Topology> change : changes) {
			Topology topology = change.getRootNode().getDataAfter();
			sdnmudProvider.setTopology(topology);

			List<Uri> cpeSwitches = topology.getCpeSwitches();
			HashSet<String> cpeSwitchSet = new HashSet<String>();

			for (Uri cpeSwitch : cpeSwitches) {
				cpeSwitchSet.add(cpeSwitch.getValue());
				sdnmudProvider.addMudFlowsInstaller(cpeSwitch.getValue(),
						new MudFlowsInstaller(sdnmudProvider, cpeSwitch.getValue()));
				sdnmudProvider.getWakeupListener().installSendToControllerFlows(cpeSwitch.getValue());
				MudFlowsInstaller mudFlowsInstaller = sdnmudProvider.getMudFlowsInstaller(cpeSwitch.getValue());
				if (mudFlowsInstaller != null) {
					for (Mud mud : sdnmudProvider.getMudProfiles()) {
						mudFlowsInstaller.installFlows(mud);
					}
				}
			}

		}
	}

}
