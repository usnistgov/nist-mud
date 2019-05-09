/*
 * Copyright (c) Public Domain Jun 27, 2018.
 * This code is released to the public domain in accordance with the following disclaimer:
 *
 * "This software was developed at the National Institute of Standards
 * and Technology by employees of the Federal Government in the course of
 * their official duties. Pursuant to title 17 Section 105 of the United
 * States Code this software is not subject to copyright protection and is
 * in the public domain. It is an experimental system. NIST assumes no responsibility
 * whatsoever for its use by other parties, and makes no guarantees, expressed or
 * implied, about its quality, reliability, or any other characteristic. We would
 * appreciate acknowledgement if the software is used. This software can be redistributed
 * and/or modified freely provided that any derivative works bear
 * some notice that they are derived from it, and any modified versions bear some
 * notice that they have been modified."
 */

package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuaranteneDevices;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuaranteneDevicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.GetAggregateFlowStatisticsFromFlowTableForGivenMatchInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.GetAggregateFlowStatisticsFromFlowTableForGivenMatchOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.GetFlowStatisticsFromFlowTableInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.get.aggregate.flow.statistics.from.flow.table._for.given.match.output.AggregatedFlowStatistics;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearCacheOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearCacheOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearMudRulesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearMudRulesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearPacketCountOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearPacketCountOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetDstMacClassificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetDstMacClassificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetDstMacClassificationOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetSrcMacClassificationOutputBuilder;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetFlowRulesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetFlowRulesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.get.flow.rules.output.*;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudMetadataMappingInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudMetadataMappingOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudMetadataMappingOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUnmappedAddressesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUnmappedAddressesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetPacketCountOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetPacketCountOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetFlowRulesOutputBuilder;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetQuarantineMacsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetQuarantineMacsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetSrcMacClassificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetSrcMacClassificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.UnquarantineInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.UnquarantineOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.UnquarantineOutputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author mranga@nist.gov
 *
 */
public class SdnmudServiceImpl implements SdnmudService {
	
	private static final Logger LOG = LoggerFactory.getLogger(SdnmudServiceImpl.class);

	class CompletedFuture<T> implements Future<T> {
		private final T result;

		public CompletedFuture(final T result) {
			this.result = result;
		}

		@Override
		public boolean cancel(final boolean b) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public T get() throws InterruptedException, ExecutionException {
			return this.result;
		}

		@Override
		public T get(final long l, final TimeUnit timeUnit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return get();
		}

	}

	private SdnmudProvider sdnmudProvider;

	public SdnmudServiceImpl(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.
	 * rev170915.SdnmudService#getPacketCount()
	 */
	@Override
	public Future<RpcResult<GetPacketCountOutput>> getPacketCount() {
		GetPacketCountOutputBuilder gpcob = new GetPacketCountOutputBuilder();
		gpcob.setPacketCount(new Long(sdnmudProvider.getPacketInDispatcher().getPacketInCount(false)));
		gpcob.setMudPacketCount(new Long(sdnmudProvider.getPacketInDispatcher().getMudPacketInCount(false)));
		RpcResult<GetPacketCountOutput> result = RpcResultBuilder.success(gpcob).build();
		return new CompletedFuture<RpcResult<GetPacketCountOutput>>(result);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.
	 * rev170915.SdnmudService#clearPacketCount()
	 */
	@Override
	public Future<RpcResult<ClearPacketCountOutput>> clearPacketCount() {
		ClearPacketCountOutputBuilder cpcob = new ClearPacketCountOutputBuilder();
		sdnmudProvider.getPacketInDispatcher().clearPacketInCount();
		cpcob.setSuccess(true);
		RpcResult<ClearPacketCountOutput> result = RpcResultBuilder.success(cpcob).build();
		return new CompletedFuture<RpcResult<ClearPacketCountOutput>>(result);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.
	 * rev170915.SdnmudService#clearCache()
	 */
	@Override
	public Future<RpcResult<ClearCacheOutput>> clearCache() {

		ClearCacheOutputBuilder cpcob = new ClearCacheOutputBuilder();
		sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
		cpcob.setSuccess(true);
		RpcResult<ClearCacheOutput> result = RpcResultBuilder.success(cpcob).build();
		return new CompletedFuture<RpcResult<ClearCacheOutput>>(result);
	}

	@Override
	public Future<RpcResult<ClearMudRulesOutput>> clearMudRules() {
		ClearMudRulesOutputBuilder cmrob = new ClearMudRulesOutputBuilder();
		sdnmudProvider.clearMudRules();
		sdnmudProvider.getMudFlowsInstaller().clearMudRules();
		sdnmudProvider.getStateChangeScanner().clearState();
		sdnmudProvider.getMappingDataStoreListener().clearState();

		cmrob.setSuccess(true);
		RpcResult<ClearMudRulesOutput> result = RpcResultBuilder.success(cmrob).build();
		return new CompletedFuture<RpcResult<ClearMudRulesOutput>>(result);
	}

	@Override
	public Future<RpcResult<GetMudUnmappedAddressesOutput>> getMudUnmappedAddresses() {
		GetMudUnmappedAddressesOutputBuilder guaob = new GetMudUnmappedAddressesOutputBuilder();
		ArrayList<String> addrList = new ArrayList<String>();
		for (MacAddress mac : sdnmudProvider.getPacketInDispatcher().getUnclassifiedMacAddresses()) {
			addrList.add(mac.getValue());
		}
		guaob.setUnmappedDeviceAddresses(addrList);
		RpcResult<GetMudUnmappedAddressesOutput> result = RpcResultBuilder.success(guaob).build();
		return new CompletedFuture<RpcResult<GetMudUnmappedAddressesOutput>>(result);
	}

	@Override
	public Future<RpcResult<GetMudMetadataMappingOutput>> getMudMetadataMapping(GetMudMetadataMappingInput input) {
		String mm = input.getMetadataAndMask();
		String[] pieces = mm.split("/");
		String m = pieces[0].substring(2);
		String mask = pieces[1].substring(2);
	
		BigInteger metadata = new BigInteger(m, 16);
		BigInteger metadataMask = new BigInteger(mask, 16);
		BigInteger maskedMetadata = metadata.and(metadataMask);
		long srcManufacturerId = maskedMetadata.and(SdnMudConstants.SRC_MANUFACTURER_MASK)
				.shiftRight(SdnMudConstants.SRC_MANUFACTURER_SHIFT).longValue();
		long dstManufacturerId = maskedMetadata.and(SdnMudConstants.DST_MANUFACTURER_MASK)
				.shiftRight(SdnMudConstants.DST_MANUFACTURER_SHIFT).longValue();
		long srcModelId = maskedMetadata.and(SdnMudConstants.SRC_MODEL_MASK).shiftRight(SdnMudConstants.SRC_MODEL_SHIFT)
				.longValue();
		long dstModelId = maskedMetadata.and(SdnMudConstants.DST_MODEL_MASK).shiftRight(SdnMudConstants.DST_MODEL_SHIFT)
				.longValue();
		long srcLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.SRC_NETWORK_MASK)
				.shiftRight(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT).longValue();
		long dstLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.DST_NETWORK_MASK)
				.shiftRight(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT).longValue();
		long srcQuarantineFlag = maskedMetadata.and(SdnMudConstants.SRC_QUARANTENE_MASK)
				.shiftRight(SdnMudConstants.SRC_QUARANTENE_MASK_SHIFT).longValue();
		long dstQuranteneFlag = maskedMetadata.and(SdnMudConstants.DST_QURANTENE_MASK)
				.shiftRight(SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT).longValue();
		GetMudMetadataMappingOutputBuilder outputBuilder = new GetMudMetadataMappingOutputBuilder();
		if (metadataMask.and(SdnMudConstants.SRC_MANUFACTURER_MASK).longValue() > 0) {
			outputBuilder.setSrcManufacturer(IdUtils.getManufacturer((int) srcManufacturerId));
		}
		if (metadataMask.and(SdnMudConstants.DST_MANUFACTURER_MASK).longValue() > 0) {
			outputBuilder.setDstManufacturer(IdUtils.getManufacturer((int) dstManufacturerId));
		}
		if (metadataMask.and(SdnMudConstants.SRC_MODEL_MASK).longValue() > 0) {
			outputBuilder.setSrcModel(IdUtils.getModel((int) srcModelId));
		}
		if (metadataMask.and(SdnMudConstants.DST_MODEL_MASK).longValue() > 0) {
			outputBuilder.setDstModel(IdUtils.getModel((int) dstModelId));
		}
		if (metadataMask.and(SdnMudConstants.SRC_NETWORK_MASK).longValue() > 0) {
			outputBuilder.setDstLocalNetworksFlag(srcLocalNetworksFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.DST_NETWORK_MASK).longValue() > 0) {
			outputBuilder.setDstLocalNetworksFlag(dstLocalNetworksFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.SRC_QUARANTENE_MASK).longValue() > 0) {
			outputBuilder.setSrcQuarantineFlag(srcQuarantineFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.DST_QURANTENE_MASK).longValue() > 0) {
			outputBuilder.setDstQuarantineFlag(dstQuranteneFlag > 0 ? true : false);
		}
		RpcResult<GetMudMetadataMappingOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetMudMetadataMappingOutput>>(result);

	}

	@Override
	public Future<RpcResult<UnquarantineOutput>> unquarantine(UnquarantineInput input) {
		MacAddress macAddress = input.getDeviceMacAddress();
		QuaranteneDevices quaranteneDevices = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices();
		boolean found = false;
		if (quaranteneDevices != null) {
			List<MacAddress> macAddresses = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices()
					.getQuranteneMacs();
			if (macAddresses != null) {

				for (MacAddress address : macAddresses) {
					if (address.equals(macAddress)) {
						found = true;
						macAddresses.remove(macAddress);
						break;
					}
				}

				if (found) {
					QuaranteneDevicesBuilder qdb = new QuaranteneDevicesBuilder();
					qdb.setQuranteneMacs(macAddresses);
					InstanceIdentifier<QuaranteneDevices> qId = InstanceIdentifier.builder(QuaranteneDevices.class)
							.build();
					ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();
					tx.merge(LogicalDatastoreType.CONFIGURATION, qId, qdb.build());
					tx.submit();
					// Clear the flow rules so we can get a packet in to reclassify the packets.
					this.sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
				}
			}
		}
		UnquarantineOutputBuilder unquarantineOutput = new UnquarantineOutputBuilder();
		unquarantineOutput.setSuccess(found);
		RpcResult<UnquarantineOutput> result = RpcResultBuilder.success(unquarantineOutput).build();

		return new CompletedFuture<RpcResult<UnquarantineOutput>>(result);
	}

	@Override
	public Future<RpcResult<GetQuarantineMacsOutput>> getQuarantineMacs() {
		GetQuarantineMacsOutputBuilder outputBuilder = new GetQuarantineMacsOutputBuilder();
		if (sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices() != null) {
			List<MacAddress> macAddresses = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices()
					.getQuranteneMacs();
			outputBuilder.setMacAddresses(macAddresses);
		}
		RpcResult<GetQuarantineMacsOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetQuarantineMacsOutput>>(result);
	}

	@Override
	public Future<RpcResult<GetFlowRulesOutput>> getFlowRules(GetFlowRulesInput input) {
		Uri mudUrl = input.getMudUrl();
		Uri switchId = input.getSwitchId();
		FlowCommitWrapper flowCommitWrapper = this.sdnmudProvider.getFlowCommitWrapper();
		InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(switchId.getValue());
		Collection<Flow> flows = flowCommitWrapper.getFlows(node);

		GetFlowRulesOutputBuilder outputBuilder = new GetFlowRulesOutputBuilder();
		ArrayList<FlowRule> flowRules = new ArrayList<FlowRule>();

		if (flows != null) {
			for (Flow flow : flows) {
				if (flow.getId().getValue().startsWith(mudUrl.getValue())) {
					FlowRuleBuilder frBuilder = new FlowRuleBuilder();
					BigInteger metadata = flow.getMatch().getMetadata().getMetadata();
					BigInteger metadataMask = flow.getMatch().getMetadata().getMetadataMask();
					BigInteger maskedMetadata = metadata.and(metadataMask);
					long srcManufacturerId = maskedMetadata.and(SdnMudConstants.SRC_MANUFACTURER_MASK)
							.shiftRight(SdnMudConstants.SRC_MANUFACTURER_SHIFT).longValue();
					long dstManufacturerId = maskedMetadata.and(SdnMudConstants.DST_MANUFACTURER_MASK)
							.shiftRight(SdnMudConstants.DST_MANUFACTURER_SHIFT).longValue();
					long srcModelId = maskedMetadata.and(SdnMudConstants.SRC_MODEL_MASK)
							.shiftRight(SdnMudConstants.SRC_MODEL_SHIFT).longValue();
					long dstModelId = maskedMetadata.and(SdnMudConstants.DST_MODEL_MASK)
							.shiftRight(SdnMudConstants.DST_MODEL_SHIFT).longValue();
					long srcLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.SRC_NETWORK_MASK)
							.shiftRight(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT).longValue();
					long dstLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.DST_NETWORK_MASK)
							.shiftRight(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT).longValue();
					long srcQuarantineFlag = maskedMetadata.and(SdnMudConstants.SRC_QUARANTENE_MASK)
							.shiftRight(SdnMudConstants.SRC_QUARANTENE_MASK_SHIFT).longValue();
					long dstQuranteneFlag = maskedMetadata.and(SdnMudConstants.DST_QURANTENE_MASK)
							.shiftRight(SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT).longValue();
					if (metadataMask.and(SdnMudConstants.SRC_MANUFACTURER_MASK).longValue() > 0) {
						frBuilder.setSrcManufacturer(IdUtils.getManufacturer((int) srcManufacturerId));
					}
					if (metadataMask.and(SdnMudConstants.DST_MANUFACTURER_MASK).longValue() > 0) {
						frBuilder.setDstManufacturer(IdUtils.getManufacturer((int) dstManufacturerId));
					}
					if (metadataMask.and(SdnMudConstants.SRC_MODEL_MASK).longValue() > 0) {
						frBuilder.setSrcModel(IdUtils.getModel((int) srcModelId));
					}
					if (metadataMask.and(SdnMudConstants.DST_MODEL_MASK).longValue() > 0) {
						frBuilder.setDstModel(IdUtils.getModel((int) dstModelId));
					}
					if (metadataMask.and(SdnMudConstants.SRC_NETWORK_MASK).longValue() > 0) {
						frBuilder.setDstLocalNetworksFlag(srcLocalNetworksFlag > 0 ? true : false);
					}
					if (metadataMask.and(SdnMudConstants.DST_NETWORK_MASK).longValue() > 0) {
						frBuilder.setDstLocalNetworksFlag(dstLocalNetworksFlag > 0 ? true : false);
					}
					if (metadataMask.and(SdnMudConstants.SRC_QUARANTENE_MASK).longValue() > 0) {
						frBuilder.setSrcQuarantineFlag(srcQuarantineFlag > 0 ? true : false);
					}
					if (metadataMask.and(SdnMudConstants.DST_QURANTENE_MASK).longValue() > 0) {
						frBuilder.setDstQuarantineFlag(dstQuranteneFlag > 0 ? true : false);
					}

					GetAggregateFlowStatisticsFromFlowTableForGivenMatchInputBuilder flowStatsInputBuilder = new GetAggregateFlowStatisticsFromFlowTableForGivenMatchInputBuilder();
					flowStatsInputBuilder.setMatch(flow.getMatch());
					NodeRef nodeRef = new NodeRef(node);
					flowStatsInputBuilder.setNode(nodeRef);

					try {
						GetAggregateFlowStatisticsFromFlowTableForGivenMatchOutput statsOutput = sdnmudProvider
								.getFlowStatisticsService()
								.getAggregateFlowStatisticsFromFlowTableForGivenMatch(flowStatsInputBuilder.build())
								.get().getResult();
						List<AggregatedFlowStatistics> flowStats = statsOutput.getAggregatedFlowStatistics();
						// There should be only one flow matching the criterion.
						if ( flowStats.size() > 1) {
							LOG.error("Unexpected flow stats length!");
						}
						
						for (AggregatedFlowStatistics flowStat : flowStats) {
							if (flowStat.getByteCount() != null) {
								frBuilder.setByteCount(flowStat.getByteCount().getValue());
							}
							if (flowStat.getPacketCount() != null) {
								frBuilder.setPacketCount(flowStat.getPacketCount().getValue());
							}
						}

					} catch (InterruptedException | ExecutionException e) {
						LOG.error("Error getting flow stats",e);
					}
					frBuilder.setFlowId(flow.getId().getValue());
					frBuilder.setTableId(Long.valueOf(flow.getTableId()));
					frBuilder.setFlowRule(flow.getInstructions().toString());
					flowRules.add(frBuilder.build());

				}
			}
		}
		outputBuilder.setFlowRule(flowRules);
		RpcResult<GetFlowRulesOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetFlowRulesOutput>>(result);

	}

	@Override
	public Future<RpcResult<GetDstMacClassificationOutput>> getDstMacClassification(
			GetDstMacClassificationInput input) {
		String macAddress = input.getMacAddress().getValue();
		BigInteger maskedMetadata = this.sdnmudProvider.getPacketInDispatcher().getDstMetadata(macAddress);
		
		GetDstMacClassificationOutputBuilder outputBuilder = new GetDstMacClassificationOutputBuilder();
		if (maskedMetadata != null) {
			long dstManufacturerId = maskedMetadata.and(SdnMudConstants.DST_MANUFACTURER_MASK)
					.shiftRight(SdnMudConstants.DST_MANUFACTURER_SHIFT).longValue();
			long dstModelId = maskedMetadata.and(SdnMudConstants.DST_MODEL_MASK)
					.shiftRight(SdnMudConstants.DST_MODEL_SHIFT).longValue();
			long dstQuranteneFlag = maskedMetadata.and(SdnMudConstants.DST_QURANTENE_MASK)
					.shiftRight(SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT).longValue();
			long dstLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.DST_NETWORK_MASK)
					.shiftRight(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT).longValue();
			outputBuilder.setMetadata(maskedMetadata.toString(16));
			outputBuilder.setDstLocalNetworksFlag(dstLocalNetworksFlag != 0 ? true : false);
			outputBuilder.setDstManufacturer(IdUtils.getManufacturer((int) dstManufacturerId));
			outputBuilder.setDstQuarantineFlag(dstQuranteneFlag == 0 ? false: true);
			outputBuilder.setDstModel(new Uri(IdUtils.getModel((int) dstModelId)));
		}
		RpcResult<GetDstMacClassificationOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetDstMacClassificationOutput>>(result);
	}

	@Override
	public Future<RpcResult<GetSrcMacClassificationOutput>> getSrcMacClassification(
			GetSrcMacClassificationInput input) {
		// TODO Auto-generated method stub
		String macAddress = input.getMacAddress().getValue();
		BigInteger maskedMetadata = this.sdnmudProvider.getPacketInDispatcher().getSrcMetadata(macAddress);
		
		GetSrcMacClassificationOutputBuilder outputBuilder = new GetSrcMacClassificationOutputBuilder();
		if (maskedMetadata != null) {
			long srcManufacturerId = maskedMetadata.and(SdnMudConstants.SRC_MANUFACTURER_MASK)
					.shiftRight(SdnMudConstants.SRC_MANUFACTURER_SHIFT).longValue();
			long srcModelId = maskedMetadata.and(SdnMudConstants.SRC_MODEL_MASK)
					.shiftRight(SdnMudConstants.SRC_MODEL_SHIFT).longValue();
			long srcQuranteneFlag = maskedMetadata.and(SdnMudConstants.SRC_QUARANTENE_MASK)
					.shiftRight(SdnMudConstants.SRC_QUARANTENE_MASK_SHIFT).longValue();
			long srcLocalNetworksFlag = maskedMetadata.and(SdnMudConstants.SRC_NETWORK_MASK)
					.shiftRight(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT).longValue();
			
			outputBuilder.setMetadata(maskedMetadata.toString(16));
			outputBuilder.setSrcLocalNetworksFlag(srcLocalNetworksFlag != 0 ? true : false);
			outputBuilder.setSrcManufacturer(IdUtils.getManufacturer((int) srcManufacturerId));
			outputBuilder.setSrcQuarantineFlag(srcQuranteneFlag == 0 ? false: true);
			outputBuilder.setSrcModel(new Uri(IdUtils.getModel((int) srcModelId)));
		}
		RpcResult<GetSrcMacClassificationOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetSrcMacClassificationOutput>>(result);
	}

}
