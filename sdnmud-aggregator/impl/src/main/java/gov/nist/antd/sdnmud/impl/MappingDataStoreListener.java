/*
 * Copyright © 2017 None.  No rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
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
public class MappingDataStoreListener
        implements
            DataTreeChangeListener<Mapping> {

    private SdnmudProvider sdnmudProvider;

    private Map<MacAddress, Mapping> macAddressToMappingMap = new HashMap<MacAddress, Mapping>();

    private Map<Uri, HashSet<MacAddress>> uriToMacs = new HashMap<Uri, HashSet<MacAddress>>();

    private static final Logger LOG = LoggerFactory
            .getLogger(MappingDataStoreListener.class);

    private void removeMacAddress(MacAddress macAddress) {
        Mapping mapping = this.macAddressToMappingMap.remove(macAddress);
        if (mapping != null) {
            HashSet<MacAddress> macs = this.uriToMacs.get(mapping.getMudUrl());
            macs.remove(macAddress);
            if (macs.size() == 0) {
                this.uriToMacs.remove(mapping.getMudUrl());
            }
        }
    }

    public MappingDataStoreListener(SdnmudProvider sdnmudProvider) {
        this.sdnmudProvider = sdnmudProvider;
    }

    @Override
    public void onDataTreeChanged(
            Collection<DataTreeModification<Mapping>> collection) {
        LOG.info("onDataTreeModification");
        for (DataTreeModification<Mapping> change : collection) {
            Mapping mapping = change.getRootNode().getDataAfter();
            List<MacAddress> macAddresses = mapping.getDeviceId();
            Uri uri = mapping.getMudUrl();
            LOG.info("mudUri = " + uri.getValue());
            // Cache the MAC addresses of the devices under the same URL.
            for (MacAddress mac : macAddresses) {
                this.removeMacAddress(mac);
                LOG.info("Put MAC address mapping " + mac + " uri "
                        + uri.getValue());
                this.macAddressToMappingMap.put(mac, mapping);
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
        if (this.macAddressToMappingMap.containsKey(macAddress)) {
            return this.macAddressToMappingMap.get(macAddress).getMudUrl();
        } else {
            return new Uri(SdnMudConstants.UNCLASSIFIED);
        }
    }

}
