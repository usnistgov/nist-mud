package gov.nist.antd.sdnmud.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuarantineDevice;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuarantineDeviceBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModificationType;

public class QuaranteneDevicesListener implements DataTreeChangeListener<QuarantineDevice> {
	QuarantineDevice quarantineDevices;
	private SdnmudProvider sdnmudProvider;
	private static Logger LOG = LoggerFactory.getLogger(QuaranteneDevicesListener.class);

	public QuaranteneDevicesListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
		QuarantineDeviceBuilder qdb = new QuarantineDeviceBuilder();
		qdb.setQurantineMac( new ArrayList<MacAddress>());
		this.quarantineDevices = qdb.build();
	}

	public QuarantineDevice getQuaranteneDevices() {
		return this.quarantineDevices;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<QuarantineDevice>> changes) {
		// TODO Auto-generated method stub
		LOG.info("QuaranteneDevicesListener: onDataTreeChanged" + changes.size());

		for (DataTreeModification<QuarantineDevice> change : changes) {
			if (change.getRootNode() != null) {
				quarantineDevices = change.getRootNode().getDataAfter();
			}
		}
		sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
	}

}
