/*
 *
 * This program and accompanying materials area available under the
 * Public Domain.
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

import java.util.Collection;

import javax.annotation.Nonnull;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.access.lists.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.access.lists.access.lists.AccessList;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.mud.grouping.FromDevicePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This gets notified when a MUD profile is installed.
 *
 * @author mranga
 *
 */
public class MudProfileDataStoreListener implements ClusteredDataTreeChangeListener<Mud> {
	private DataBroker dataBroker;
	private static final Logger LOG = LoggerFactory.getLogger(MudProfileDataStoreListener.class);
	private SdnmudProvider sdnmudProvider;

	public MudProfileDataStoreListener(DataBroker broker, SdnmudProvider sdnMudProvider) {
		this.dataBroker = broker;
		this.sdnmudProvider = sdnMudProvider;
	}

	@Override
	public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Mud>> changes) {
		LOG.info("onDataTreeChanged ");
		for (DataTreeModification<Mud> change : changes) {
			Mud mud = change.getRootNode().getDataAfter();

			Uri uri = mud.getMudUrl();
			LOG.info("mudURI {}", uri.getValue());

			// Put this in a map. Later when the MAC appears, we can pick it up
			// from this map and install flow rules.
			this.sdnmudProvider.addMudProfile(mud);

		}
	}

}
