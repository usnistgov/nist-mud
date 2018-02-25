package gov.nist.antd.ids.impl;

import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.NistMudDeviceAssociationListener;
import org.opendaylight.yangtools.yang.binding.NotificationListener;

public class MappingNotificationListener implements NistMudDeviceAssociationListener,NotificationListener {
	
	private IdsProvider idsProvider;

	public MappingNotificationListener( IdsProvider idsProvider) {
		this.idsProvider = idsProvider;
	}

	@Override
	public void onMappingNotification(MappingNotification notification) {
			 idsProvider.addMappingNotification(notification);
		 
	}

}
