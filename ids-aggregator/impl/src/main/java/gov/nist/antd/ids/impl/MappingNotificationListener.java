package gov.nist.antd.ids.impl;

import java.util.logging.Logger;

import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.NistMudDeviceAssociationListener;
import org.opendaylight.yangtools.yang.binding.NotificationListener;

public class MappingNotificationListener implements NistMudDeviceAssociationListener,NotificationListener {
	
	private IdsProvider idsProvider;

	private static Logger LOG = Logger.getLogger(MappingNotificationListener.class.getName());
	
	public MappingNotificationListener( IdsProvider idsProvider) {
		this.idsProvider = idsProvider;
	}

	@Override
	public void onMappingNotification(MappingNotification notification) {
		
		LOG.info("MappingNotificationListener: onMappingNotification ");
		
		idsProvider.addMappingNotification(notification);
		 
	}

}
