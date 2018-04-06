package gov.nist.antd.flowmon.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfig;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.flowmon.config.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FlowmonConfigDataStoreListener implements DataTreeChangeListener<FlowmonConfig> {

	private FlowmonProvider flowmonProvider;
	private static final Logger LOG = LoggerFactory.getLogger(FlowmonConfigDataStoreListener.class);

	FlowmonConfigDataStoreListener(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}


	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<FlowmonConfig>> changes) {

		LOG.info("FlowmonConfigDataStoreListener: onDataTreeChanged: got an flowmon registration");
		

		for (DataTreeModification<FlowmonConfig> change : changes) {
			FlowmonConfig flowmonConfig = change.getRootNode().getDataAfter();

			for (FlowmonConfigData flowmonConfigData : flowmonConfig.getFlowmonConfigData()) {
				Uri flowmonNodeUri = flowmonConfigData.getFlowmonNode();
				flowmonProvider.addFlowmonConfig(flowmonNodeUri, flowmonConfigData);
			}

		}
		
		flowmonProvider.getWakeupListener().installDefaultFlows();	
	}


}
