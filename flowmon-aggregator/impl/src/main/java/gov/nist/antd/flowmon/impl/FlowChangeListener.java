package gov.nist.antd.flowmon.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;


public class FlowChangeListener implements DataTreeChangeListener<Flow> {

	private static final Logger LOG = LoggerFactory.getLogger(FlowChangeListener.class);

	private FlowmonProvider flowmonProvider;

	public FlowChangeListener(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<Flow>> changes) {
		for (DataTreeModification<Flow> change : changes) {
			/*
			 * Watch for any flow changes in the CPE nodes and duplicate the packet
			 * classification flows for unclassified packets. We will send these to the
			 * IDS process.
			 */
			Flow flow = change.getRootNode().getDataAfter();
		
			if (change.getRootNode().getModificationType()  == ModificationType.WRITE) {
				
				FlowId flowId = flow.getId();
				String flowIdStr = flowId.getValue();
				
				LOG.info("FlowChangeListener : flow appeared " + flow.getFlowName() + " ModificationType = " + 
						change.getRootNode().getModificationType().name()  + " tableId = " + flow.getTableId());
				
				LOG.info("FlowChangeListener : " + flowIdStr);


				if (flow.getTableId() == BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE
						|| flow.getTableId() == BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
					if (flow.getCookie().equals(InstanceIdentifierUtils.createFlowCookie(FlowmonConstants.UNCLASSIFIED))) {
						String nodeId = InstanceIdentifierUtils.getNodeIdFromFlowId(flowIdStr);
						if (flowmonProvider.isCpeNode(nodeId)) {
							InstanceIdentifier<FlowCapableNode> vnfNode = flowmonProvider.getVnfNode(nodeId);
							if (vnfNode != null) {
								flowmonProvider.getFlowCommitWrapper().writeFlow(flow, vnfNode);
							}
						}
					}

				}

			}
		}

	}

}
