
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
*
*/
package gov.nist.antd.sdnmud.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.mapping.notification.MappingInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Store listener for MappingData store (Mapping of MAC addresses to MUD URLs).
 * 
 * @author mranga@nist.gov
 *
 */

public class MappingDataStoreListener implements DataTreeChangeListener<Mapping> {

	private SdnmudProvider sdnmudProvider;

	private Map<MacAddress, Mapping> macAddressToMappingMap = new HashMap<MacAddress, Mapping>();

	private Map<Uri, HashSet<MacAddress>> uriToMacs = new HashMap<Uri, HashSet<MacAddress>>();
	private static final Logger LOG = LoggerFactory.getLogger(MappingDataStoreListener.class);

	private static String getAuthority(Uri uri) {
		int index = uri.getValue().indexOf("//") + 2;
		String rest = uri.getValue().substring(index);
		index = rest.indexOf("/");
		String authority = rest.substring(0, index);
		return authority;
	}

	private void removeMacAddress(MacAddress macAddress) {
		Mapping mapping = macAddressToMappingMap.remove(macAddress);
		if (mapping != null) {
			HashSet<MacAddress> macs = uriToMacs.get(mapping.getMudUrl());
			macs.remove(macAddress);
			if (macs.size() == 0) {
				uriToMacs.remove(mapping.getMudUrl());
			}
		}
	}

	public MappingDataStoreListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<Mapping>> collection) {
		LOG.info("onDataTreeModification");
		for (DataTreeModification<Mapping> change : collection) {
			Mapping mapping = change.getRootNode().getDataAfter();
			List<MacAddress> macAddresses = mapping.getDeviceId();
			Uri uri = mapping.getMudUrl();
			LOG.info("mudUri = " + uri.getValue());
            String manufacturer = InstanceIdentifierUtils.getAuthority(uri.getValue());
	        int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
            int modelId = InstanceIdentifierUtils.getModelId(uri.getValue());
            MappingNotificationBuilder mnb = new MappingNotificationBuilder();
            mnb.setManufacturerId(Long.valueOf(manufacturerId));
            mnb.setModelId(Long.valueOf(modelId));
            MappingInfoBuilder mib = new MappingInfoBuilder();
            mib.setMudUrl(uri);
            mib.setDeviceId(macAddresses);
            MappingNotification mappingNotification = mnb.setMappingInfo(mib.build()).build();
            // Publish that we just saw a new device 
            sdnmudProvider.getNotificationPublishService().offerNotification(mappingNotification);
            
			// Cache the MAC addresses of the devices under the same URL.
			for (MacAddress mac : macAddresses) {
				removeMacAddress(mac);
				LOG.info("Put MAC address mapping " + mac + " uri " + uri.getValue());
				macAddressToMappingMap.put(mac, mapping);
				HashSet<MacAddress> macs = uriToMacs.get(uri);
				if (macs == null) {
					macs = new HashSet<MacAddress>();
					uriToMacs.put(uri, macs);
				}
				macs.add(mac);
				// Remove the default mapping (the next flow miss will install
				// the right mapping). Find all the switches where this MAC address has been seen.
				if (this.sdnmudProvider.getNodeId(mac) != null) {
					for (String nodeId : this.sdnmudProvider.getNodeId(mac)) {
						InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(nodeId);
						if (node != null) {
							MudFlowsInstaller.installStampManufacturerModelFlowRules(mac, uri.getValue(),
									sdnmudProvider, node);
						}
					}
				}
			}
		}
	}

	public Collection<MacAddress> getMacs(Uri uri) {
		return uriToMacs.get(uri);
	}

	/**
	 * Get the MUD URI given a mac address.
	 * 
	 * @param macAddress
	 * @return
	 */
	public Uri getMudUri(MacAddress macAddress) {
		if (macAddressToMappingMap.containsKey(macAddress)) {
			return this.macAddressToMappingMap.get(macAddress).getMudUrl();
		} else {
			return null;
		}
	}

}
