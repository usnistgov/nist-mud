/*
 *
 *This program and the accompanying materials are made available under the
 *Public Domain.
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

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data tree changed listener for updating controller classes.
 *
 */
public class ControllerclassMappingDataStoreListener implements DataTreeChangeListener<ControllerclassMapping> {

	private SdnmudProvider sdnmudProvider;

	private static final Logger LOG = LoggerFactory.getLogger(ControllerclassMappingDataStoreListener.class);

	public ControllerclassMappingDataStoreListener(SdnmudProvider provider) {
		this.sdnmudProvider = provider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<ControllerclassMapping>> collection) {
		LOG.info("ControllerclassMappingDataStoreListener: onDataTreeModification");
		for (DataTreeModification<ControllerclassMapping> change : collection) {
			ControllerclassMapping controllerMapping = change.getRootNode().getDataAfter();
			if (controllerMapping.getSwitchId() == null) {
				LOG.info("switch ID is null -- returning");
				return;
			}
			sdnmudProvider.addControllerMap(controllerMapping);

		}
	}

}
