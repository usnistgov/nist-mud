/*
 * Copyright (c) Public Domain.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180303.access.lists.acl.Aces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AccessControlList data store listener. This handles callbacks from ODL when
 * the ACL data store is altered.
 * 
 * @author mranga@nist.gov
 *
 */
public class AclDataStoreListener
        implements
            ClusteredDataTreeChangeListener<AccessLists> {

    private DataBroker dataBroker;
    private SdnmudProvider sdnmudProvider;
    private static final Logger LOG = LoggerFactory
            .getLogger(AclDataStoreListener.class);
    private Map<String, Aces> nameToAcesMap = new HashMap<String, Aces>();

    public AclDataStoreListener(DataBroker broker,
            SdnmudProvider sdnmudProvider) {
        this.dataBroker = broker;
        this.sdnmudProvider = sdnmudProvider;
    }

    @Override
    public void onDataTreeChanged(
            Collection<DataTreeModification<AccessLists>> changes) {
        LOG.info("AclDataStoreListener: onDataTreeChanged");
        for (DataTreeModification<AccessLists> change : changes) {
            AccessLists accessLists = change.getRootNode().getDataAfter();

            List<Acl> acls = accessLists.getAcl();

            // Stash away the ACL. This is indexed by name later in the MUD
            // profile.
            for (Acl acl : acls) {
                String aclName = acl.getName();
                Aces aces = acl.getAces();
                this.addAces(aclName, aces);
            }
        }
    }

    /**
     * Add Aces for a given acl name scoped to a MUD URI.
     * 
     * @param mudUri
     *            -- the mudUri for wich to add the aces.
     * 
     * @param aclName
     *            -- the acl name for which we want to add aces.
     * 
     * @param aces
     *            -- the ACE entries to add.
     */
    private void addAces(String aclName, Aces aces) {
        LOG.info("adding ACEs aclName =  {} ", aclName);
        this.nameToAcesMap.put(aclName, aces);
    }

    /**
     * Get the aces for a given acl name.
     * 
     * @param aclName
     *            -- acl name
     * @return -- Aces list for the acl name
     */
    public Aces getAces(String mudUri, String aclName) {
        LOG.info("getAces aclName =  " + aclName);
        return this.nameToAcesMap.get(aclName);
    }

}
