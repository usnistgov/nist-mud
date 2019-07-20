package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimerTask;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DroppedPacketsScanner extends TimerTask {
	private SdnmudProvider sdnmudProvider;

	

	private HashMap<String, PerSwitchDropCount> dropCounterMap = new HashMap<>();

	// Drop count by class.

	public DroppedPacketsScanner(SdnmudProvider provider) {
		this.sdnmudProvider = provider;
	}

	@Override
	public void run() {

		for (InstanceIdentifier<FlowCapableNode> node : this.sdnmudProvider.getNodes()) {

			Collection<MacAddress> macAddresses = sdnmudProvider.getPacketInDispatcher().getDroppedMacs(node);
			if (macAddresses != null) {
				String switchId = IdUtils.getNodeUri(node);
				PerSwitchDropCount perSwitchDropCount = this.dropCounterMap.get(switchId);
				if (perSwitchDropCount == null) {
					perSwitchDropCount = new PerSwitchDropCount();
					this.dropCounterMap.put(switchId, perSwitchDropCount);
				}
				for (MacAddress macAddress : macAddresses) {
					BigInteger maskedMetadata = this.sdnmudProvider.getPacketInDispatcher()
							.getDstMetadata(macAddress.getValue());
					if (maskedMetadata != null) {
						long dstManufacturerId = maskedMetadata.and(SdnMudConstants.DST_MANUFACTURER_MASK)
								.shiftRight(SdnMudConstants.DST_MANUFACTURER_SHIFT).longValue();
						long dstModelId = maskedMetadata.and(SdnMudConstants.DST_MODEL_MASK)
								.shiftRight(SdnMudConstants.DST_MODEL_SHIFT).longValue();
						boolean dstQuranteneFlag = maskedMetadata.and(SdnMudConstants.DST_QURANTENE_MASK)
								.shiftRight(SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT).longValue() != 0L;
						boolean dstLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.DST_NETWORK_MASK)
								.shiftRight(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT).longValue() != 0;
						boolean dstBlockedFlag = maskedMetadata.and(SdnMudConstants.DST_MAC_BLOCKED_MASK)
								.shiftRight(SdnMudConstants.DST_MAC_BLOCKED_MASK_SHIFT).longValue() != 0;
						long srcManufacturerId = maskedMetadata.and(SdnMudConstants.SRC_MANUFACTURER_MASK)
								.shiftRight(SdnMudConstants.SRC_MANUFACTURER_SHIFT).longValue();
						long srcModelId = maskedMetadata.and(SdnMudConstants.SRC_MODEL_MASK)
								.shiftRight(SdnMudConstants.SRC_MODEL_SHIFT).longValue();
						boolean srcQuranteneFlag = maskedMetadata.and(SdnMudConstants.SRC_QUARANTENE_MASK)
								.shiftRight(SdnMudConstants.SRC_QUARANTENE_MASK_SHIFT).longValue() != 0;
						boolean srcLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.SRC_NETWORK_MASK)
								.shiftRight(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT).longValue() != 0;
						boolean srcBlockedFlag = maskedMetadata.and(SdnMudConstants.SRC_MAC_BLOCKED_MASK)
								.shiftRight(SdnMudConstants.SRC_MAC_BLOCKED_MASK_SHIFT).longValue() != 0;
						String srcModel = IdUtils.getModel(Long.valueOf(srcModelId).intValue()).getValue();
						String dstModel = IdUtils.getModel(Long.valueOf(dstModelId).intValue()).getValue();

						perSwitchDropCount.incrementModelDropCount(dstModel);
						perSwitchDropCount.incrementModelDropCount(srcModel);
						
						if (srcBlockedFlag) {
							perSwitchDropCount.incrementBlockedDropCount(srcModel);
						}

						if (dstBlockedFlag) {
							perSwitchDropCount.incrementBlockedDropCount(dstModel);
						}
						
						if (srcLocalNetworksFlag) {
							perSwitchDropCount.incrementLocalNetworksDropCount(srcModel);
						}

						if (dstLocalNetworksFlag) {
							perSwitchDropCount.incrementLocalNetworksDropCount(srcModel);
						}
					}
				}
			}

		}

	}
	
	public PerSwitchDropCount getDropCount(String nodeId, String mudUri) {
		return this.dropCounterMap.get(nodeId);
	}
	
	public void deleteDropCounter(String nodeId) {
		this.dropCounterMap.remove(nodeId);
	}

}
