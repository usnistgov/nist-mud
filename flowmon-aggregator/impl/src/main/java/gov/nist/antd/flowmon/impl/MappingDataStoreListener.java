package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

public class MappingDataStoreListener implements DataTreeChangeListener<Mapping> {

	private FlowmonProvider flowmonProvider;
	private static final Logger LOG = LoggerFactory.getLogger(MappingDataStoreListener.class);

	public MappingDataStoreListener(FlowmonProvider provider) {
		this.flowmonProvider = provider;
	}

	

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<Mapping>> changes) {
		for (DataTreeModification<Mapping> change : changes) {
			Mapping mapping = change.getRootNode().getDataAfter();
			List<MacAddress> macAddresses = mapping.getDeviceId();
			Uri uri = mapping.getMudUrl();
			LOG.info("mudUri = " + uri.getValue());
			for (MacAddress mac : macAddresses) {
				flowmonProvider.addDeviceMapping(mac,mapping);
				// Remove the default mapping (the next flow miss will install
				// the right mapping). Find all the switches where this MAC
				// address has been seen.
				if (flowmonProvider.getNodeId(mac) != null) {
					for (String nodeId : flowmonProvider.getNodeId(mac)) {
						InstanceIdentifier<FlowCapableNode> node = flowmonProvider.getNode(nodeId);
						if (node != null) {
							installStampManufacturerFlowRule(flowmonProvider, mac, uri, node);
						}
					}
				}
			}

		}

	}
	
	
  static  void installStampManufacturerFlowRule(FlowmonProvider flowmonProvider, MacAddress mac, Uri uri,
			InstanceIdentifier<FlowCapableNode> node) {
	    String manufacturer = InstanceIdentifierUtils.getAuthority(uri);
		BigInteger metadata = BigInteger.valueOf(InstanceIdentifierUtils.getFlowHash(manufacturer)).shiftLeft(FlowmonConstants.SRC_MANUFACTURER_SHIFT);
		FlowId flowId = InstanceIdentifierUtils.createFlowId("MAC_MATCH");
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("MAC_MATCH");
		BigInteger metadataMask = FlowmonConstants.SRC_MANUFACTURER_MASK;
		FlowBuilder flow = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(mac, metadata, metadataMask, BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);
		flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);
		flowId = InstanceIdentifierUtils.createFlowId("MAC_MATCH");
		metadata = BigInteger.valueOf(InstanceIdentifierUtils.getFlowHash(manufacturer)).shiftLeft(FlowmonConstants.DST_MANUFACTURER_SHIFT);
		metadataMask = FlowmonConstants.DST_MANUFACTURER_MASK;
		flow = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(mac, metadata, metadataMask, BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie);
		flowmonProvider.getFlowCommitWrapper().writeFlow(flow, node);
	}
}