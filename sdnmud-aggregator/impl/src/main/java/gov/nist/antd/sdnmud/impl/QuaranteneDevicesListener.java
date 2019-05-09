package gov.nist.antd.sdnmud.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuaranteneDevices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuaranteneDevicesListener implements DataTreeChangeListener<QuaranteneDevices> {
	QuaranteneDevices quaranteneDevices ;
	private SdnmudProvider sdnmudProvider;
	private static Logger LOG = LoggerFactory.getLogger(QuaranteneDevicesListener.class);
	
	public QuaranteneDevicesListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<QuaranteneDevices>> changes) {
		LOG.info("QuaranteneDevicesListener: onDataTreeChanged");
		this.quaranteneDevices = null;
		for (DataTreeModification<QuaranteneDevices> change : changes) {
			quaranteneDevices = change.getRootNode().getDataAfter();
		}
	}
	
	public QuaranteneDevices getQuaranteneDevices() {
		return this.quaranteneDevices;
	}

}
