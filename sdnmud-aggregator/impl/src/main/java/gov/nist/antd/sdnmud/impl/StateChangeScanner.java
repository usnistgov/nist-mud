/*
 * Copyright (c) Public Domain May 31, 2018.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimerTask;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.Mud;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.CpeCollections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mranga
 *
 */
public class StateChangeScanner extends TimerTask {
	private static final Logger LOG = LoggerFactory.getLogger(StateChangeScanner.class);

	private SdnmudProvider sdnmudProvider;
	private HashMap<String, Long> installTime = new HashMap<String, Long>();

	private HashSet<String> initialFlowsInstalled = new HashSet<String>();

	public StateChangeScanner(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.util.TimerTask#run()
	 */
	@Override
	public synchronized void run() {

		if (sdnmudProvider.getCpeCollections() == null || !sdnmudProvider.isControllerMapped()) {
			LOG.info("StateChangeScanner: Topology not found");
			return;
		}

		if (!sdnmudProvider.isConfigStateChanged()) {
			LOG.debug("Config state is unchanged -- returning");
			return;
		}

		CpeCollections topology = sdnmudProvider.getCpeCollections();

		try {

			boolean failed = false;
			for (Uri cpeSwitch : topology.getCpeSwitches()) {

				if (!initialFlowsInstalled.contains(cpeSwitch.getValue())) {
					this.sdnmudProvider.getWakeupListener().installSendToControllerFlows(cpeSwitch.getValue());
					this.sdnmudProvider.getWakeupListener().installInitialFlows(cpeSwitch.getValue());
					this.initialFlowsInstalled.add(cpeSwitch.getValue());
				}

				MudFlowsInstaller mudFlowsInstaller = this.sdnmudProvider.getMudFlowsInstaller();
				for (Mud mud : this.sdnmudProvider.getMudProfiles()) {
					String key = mud.getMudUrl().getValue() + ":" + cpeSwitch.getValue();
					if (!installTime.containsKey(key)) {
						if (mudFlowsInstaller.tryInstallFlows(mud, cpeSwitch.getValue())) {
							installTime.put(key, System.currentTimeMillis());
						} else {
							failed = true;
						}
					}
				}
			}

			if (!failed)
				sdnmudProvider.clearConfigStateChanged();

		} catch (RuntimeException ex) {
			LOG.error("Exception caught when processing state change : ", ex);

		}

	}
	
	public synchronized void clearState(String switchUrl) {
		this.clearMudState(switchUrl);
		this.initialFlowsInstalled.remove(switchUrl);
	}
	
	public void clearMudState(String switchUrl) {
		for (Iterator<String> keyIterator = this.installTime.keySet().iterator(); keyIterator.hasNext(); ) {
			String key = keyIterator.next();
			if (key.endsWith(switchUrl)) {
				keyIterator.remove();
			} 
		}
	}
	
	public synchronized void clearState() {
		this.installTime.clear();
		this.initialFlowsInstalled.clear();
	}

}
