package gov.nist.antd.flowmon.impl;

import java.util.logging.Logger;

import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.NistMudDeviceAssociationListener;
import org.opendaylight.yangtools.yang.binding.NotificationListener;

class MappingNotificationListener implements NistMudDeviceAssociationListener,NotificationListener {
	
	private FlowmonProvider flowmonProvider;

	private static Logger LOG = Logger.getLogger(MappingNotificationListener.class.getName());
	
	MappingNotificationListener( FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}

	@Override
	public void onMappingNotification(MappingNotification notification) {
		
		LOG.info("MappingNotificationListener: onMappingNotification ");
		
		
		flowmonProvider.addMappingNotification(notification);
		 
	}

}
