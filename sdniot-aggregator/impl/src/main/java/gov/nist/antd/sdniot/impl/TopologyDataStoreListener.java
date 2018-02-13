/*
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

package gov.nist.antd.sdniot.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.Mud;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;

/**
 * Data store listener for changes in topology. The topology 
 * determines the CPE nodes where MUD rules are to be installed.
 * 
 * @author mranga
 *
 */
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
