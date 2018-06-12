/*
 * Copyright (c) Public Domain Jun 11, 2018.
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

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mranga
 *
 */
public class SdnmudConfigDataStoreListener implements ClusteredDataTreeChangeListener<SdnmudConfig> {
	private static final Logger LOG = LoggerFactory.getLogger(SdnmudConfigDataStoreListener.class);
	private SdnmudProvider sdnmudProvider;

	public SdnmudConfigDataStoreListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener#
	 * onDataTreeChanged(java.util.Collection)
	 */
	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<SdnmudConfig>> changes) {

		LOG.info("onDataTreeChanged");

		for (DataTreeModification<SdnmudConfig> change : changes) {
			SdnmudConfig sdnmudConfig = change.getRootNode().getDataAfter();
			sdnmudProvider.setSdnmudConfig(sdnmudConfig);
		}

	}

}
