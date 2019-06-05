/*
 *
 *
 * Copyright (C) 2017 Public Domain.  No rights reserved.
 *
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
package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Store listener for MappingData store (Mapping of MAC addresses to MUD
 * URLs).
 *
 * @author mranga@nist.gov
 *
 */
public class MappingDataStoreListener implements DataTreeChangeListener<Mapping> {

	private SdnmudProvider sdnmudProvider;

	private Map<String, Uri> macToUri = new HashMap<String, Uri>();

	private Map<Uri, HashSet<MacAddress>> uriToMacs = new HashMap<Uri, HashSet<MacAddress>>();

	private Set<MacAddress> blockedAddressTable = new HashSet<MacAddress>();

	private static final Logger LOG = LoggerFactory.getLogger(MappingDataStoreListener.class);

	private void removeMacAddress(MacAddress macAddress) {
		Uri mudUrl = this.macToUri.remove(macAddress.getValue());
		if (mudUrl != null) {
			HashSet<MacAddress> macs = this.uriToMacs.get(mudUrl);
			macs.remove(macAddress);
			if (macs.size() == 0) {
				this.uriToMacs.remove(mudUrl);
			}
		}
	}

	public MappingDataStoreListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<Mapping>> collection) {
		LOG.info("MappingDataStoreListener: onDataTreeChanged");
		for (DataTreeModification<Mapping> change : collection) {
			Mapping mapping = change.getRootNode().getDataAfter();
			List<MacAddress> macAddresses = mapping.getDeviceId();

			// For testing purposes we support file: URIs so this may not actually
			// be the same as the URI in the mud profile.
			Uri uri = mapping.getMudUrl();
			LOG.info("mudUri = " + uri.getValue());
			String uriStr = sdnmudProvider.getMudFileFetcher().fetchAndInstallMudFile(uri.getValue());
			if (uriStr == null) {
				if (sdnmudProvider.getSdnmudConfig().isBlockMacOnMudProfileFailure()) {

					// Find the MAC address that was added. There should only be one MAC
					// address added but we could not retrieve or verify the associated MUD profile.
					// so we need to put the device in a blocked state (this is done on "packet In
					// event").
					LOG.error("Failed to verify or fetch MUD profile -- blocking the device uri = " + uri.getValue());
					for (MacAddress macAddress : macAddresses) {
						// block the mac address if a mapping has not yet been defined.
						if (!macToUri.containsKey(macAddress.getValue())) {
							this.blockedAddressTable.add(macAddress);
						}
					}
					sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
					return;
				} else {
					if (uri.getValue().startsWith("file://")) {
						LOG.error("Cannot find file in cache");
						return;
					}
				}
			} else {
			   uri = new Uri(uriStr);
			}

			boolean found = false;
			for (MacAddress macAddress : macAddresses) {
				if (this.blockedAddressTable.contains(macAddress)) {
					this.blockedAddressTable.remove(macAddress);
					found = true;
				}
			}

			if (found) {
				sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
			}
			

			// Cache the MAC addresses of the devices under the same URL.
			for (MacAddress mac : macAddresses) {
				this.removeMacAddress(mac);
				LOG.info("Put MAC address mapping " + mac.getValue() + " uri " + uri.getValue());
				this.macToUri.put(mac.getValue().toUpperCase(), uri);
				HashSet<MacAddress> macs = this.uriToMacs.get(uri);
				if (macs == null) {
					macs = new HashSet<MacAddress>();
					this.uriToMacs.put(uri, macs);
				}

				macs.add(mac);
			}

		}
	}

	public Uri getMudUri(MacAddress macAddress) {
		if (this.macToUri.containsKey(macAddress.getValue().toUpperCase())) {
			Uri retval = new Uri(this.macToUri.get(macAddress.getValue().toUpperCase()));
			LOG.info("getMudUri " + macAddress.getValue() + " uri  " + retval.getValue());
			return retval;
		} else {
			LOG.info("getMudUri : " + macAddress.getValue() + " cannot find mapping");
			return new Uri(SdnMudConstants.UNCLASSIFIED);
		}
	}

	public void clearState() {
		this.macToUri.clear();
		this.uriToMacs.clear();
		this.blockedAddressTable.clear();
	}

	public Map<Uri, HashSet<MacAddress>> getMapping() {
		return this.uriToMacs;

	}

	public boolean isBlocked(MacAddress mac) {
		return this.blockedAddressTable.contains(mac);
	}

}
