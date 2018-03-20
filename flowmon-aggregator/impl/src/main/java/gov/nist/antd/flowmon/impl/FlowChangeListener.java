package gov.nist.antd.flowmon.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
			LOG.info("FlowChangeListener : flow appeared " + flow.getFlowName() + " ModificationType = " + 
					change.getRootNode().getModificationType().name() );

			if (change.getRootNode().getModificationType().equals(ModificationType.WRITE)) {
				if (flow.getTableId() == SdnMudConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE
						|| flow.getTableId() == SdnMudConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
					FlowId flowId = flow.getId();
					String flowIdStr = flowId.getValue();
					if (flowIdStr.startsWith("UNCLASSIFIED")) {
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
