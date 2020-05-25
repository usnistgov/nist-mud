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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.collector.rev190621.post.mud.report.input.MudReport;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.mud.reporter.extension.ReporterBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuarantineDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.flow.and.statistics.map.list.FlowAndStatisticsMapList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.AddControllerMappingInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.AddControllerWaitInputInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.AddControllerWaitInputOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.AddControllerWaitInputOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearCacheOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearCacheOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearMudRulesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearMudRulesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearPacketCountOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearPacketCountOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetDstMacClassificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetDstMacClassificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetDstMacClassificationOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetFlowRulesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetFlowRulesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetFlowRulesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudMetadataMappingInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudMetadataMappingOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudMetadataMappingOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudReportsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudReportsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudReportsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUnmappedAddressesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUnmappedAddressesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetPacketCountOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetPacketCountOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetQuarantineMacsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetQuarantineMacsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetSrcMacClassificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetSrcMacClassificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetSrcMacClassificationOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.QuarantineInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.UnquarantineInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.add.controller.wait.input.output.Report;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.get.flow.rules.output.FlowRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.get.flow.rules.output.FlowRuleBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.get.mud.reports.output.ReportBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudReportsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUrlsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUrlsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUrlsOutputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Futures;


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
		gpcob.setPacketCount(Long.valueOf(sdnmudProvider.getPacketInDispatcher().getPacketInCount(false)));
		gpcob.setMudPacketCount(Long.valueOf(sdnmudProvider.getPacketInDispatcher().getMudPacketInCount(false)));
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
		long srcMacBlockedFlag = maskedMetadata.and(SdnMudConstants.SRC_MAC_BLOCKED_MASK)
				.shiftRight(SdnMudConstants.SRC_MAC_BLOCKED_MASK_SHIFT).longValue();
		long dstQuranteneFlag = maskedMetadata.and(SdnMudConstants.DST_QURANTENE_MASK)
				.shiftRight(SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT).longValue();
		long dstMacBlockedFlag = maskedMetadata.and(SdnMudConstants.DST_MAC_BLOCKED_MASK)
				.shiftRight(SdnMudConstants.DST_MAC_BLOCKED_MASK_SHIFT).longValue();
		GetMudMetadataMappingOutputBuilder outputBuilder = new GetMudMetadataMappingOutputBuilder();
		if (metadataMask.and(SdnMudConstants.SRC_MANUFACTURER_MASK).longValue() != 0) {
			outputBuilder.setSrcManufacturer(IdUtils.getManufacturer((int) srcManufacturerId));
		}
		if (metadataMask.and(SdnMudConstants.DST_MANUFACTURER_MASK).longValue() != 0) {
			outputBuilder.setDstManufacturer(IdUtils.getManufacturer((int) dstManufacturerId));
		}
		if (metadataMask.and(SdnMudConstants.SRC_MODEL_MASK).longValue() != 0) {
			outputBuilder.setSrcModel(IdUtils.getModel((int) srcModelId));
		}
		if (metadataMask.and(SdnMudConstants.DST_MODEL_MASK).longValue() != 0) {
			outputBuilder.setDstModel(IdUtils.getModel((int) dstModelId));
		}
		if (metadataMask.and(SdnMudConstants.SRC_NETWORK_MASK).longValue() != 0) {
			outputBuilder.setSrcLocalNetworksFlag(srcLocalNetworksFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.DST_NETWORK_MASK).longValue() != 0) {
			outputBuilder.setDstLocalNetworksFlag(dstLocalNetworksFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.SRC_QUARANTENE_MASK).longValue() != 0) {
			outputBuilder.setSrcQuarantineFlag(srcQuarantineFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.DST_QURANTENE_MASK).longValue() != 0) {
			outputBuilder.setDstQuarantineFlag(dstQuranteneFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.SRC_MAC_BLOCKED_MASK).longValue() != 0) {
			outputBuilder.setSrcMacBlockedFlag(dstMacBlockedFlag > 0 ? true : false);
		}
		if (metadataMask.and(SdnMudConstants.DST_MAC_BLOCKED_MASK).longValue() != 0) {
			outputBuilder.setDstMacBlockedFlag(dstMacBlockedFlag > 0 ? true : false);
		}

		RpcResult<GetMudMetadataMappingOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetMudMetadataMappingOutput>>(result);

	}

	@Override
	public Future<RpcResult<GetQuarantineMacsOutput>> getQuarantineMacs() {
		GetQuarantineMacsOutputBuilder outputBuilder = new GetQuarantineMacsOutputBuilder();
		List<MacAddress> quarantineAddresses = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices()
				.getQurantineMac();
		outputBuilder.setMacAddresses(quarantineAddresses);
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
				if (flow.getId().getValue().startsWith(String.valueOf(mudUrl.getValue().hashCode()))) {
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
					long srcMacBlockedFlag = maskedMetadata.and(SdnMudConstants.SRC_MAC_BLOCKED_MASK)
							.shiftRight(SdnMudConstants.SRC_MAC_BLOCKED_MASK_SHIFT).longValue();
					long dstQuranteneFlag = maskedMetadata.and(SdnMudConstants.DST_QURANTENE_MASK)
							.shiftRight(SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT).longValue();
					long dstMacBlockedFlag = maskedMetadata.and(SdnMudConstants.DST_MAC_BLOCKED_MASK)
							.shiftRight(SdnMudConstants.DST_MAC_BLOCKED_MASK_SHIFT).longValue();
					if (metadataMask.and(SdnMudConstants.SRC_MANUFACTURER_MASK).longValue() != 0) {
						frBuilder.setSrcManufacturer(IdUtils.getManufacturer((int) srcManufacturerId));
					}
					if (metadataMask.and(SdnMudConstants.DST_MANUFACTURER_MASK).longValue() != 0) {
						frBuilder.setDstManufacturer(IdUtils.getManufacturer((int) dstManufacturerId));
					}
					if (metadataMask.and(SdnMudConstants.SRC_MODEL_MASK).longValue() != 0) {
						frBuilder.setSrcModel(IdUtils.getModel((int) srcModelId));
					}
					if (metadataMask.and(SdnMudConstants.DST_MODEL_MASK).longValue() != 0) {
						frBuilder.setDstModel(IdUtils.getModel((int) dstModelId));
					}
					if (metadataMask.and(SdnMudConstants.SRC_NETWORK_MASK).longValue() != 0) {
						frBuilder.setSrcLocalNetworksFlag(srcLocalNetworksFlag > 0 ? true : false);
					}
					if (metadataMask.and(SdnMudConstants.DST_NETWORK_MASK).longValue() != 0) {
						frBuilder.setDstLocalNetworksFlag(dstLocalNetworksFlag > 0 ? true : false);
					}
					if (metadataMask.and(SdnMudConstants.SRC_QUARANTENE_MASK).longValue() != 0) {
						frBuilder.setSrcQuarantineFlag(srcQuarantineFlag > 0 ? true : false);
					}
					if (metadataMask.and(SdnMudConstants.DST_QURANTENE_MASK).longValue() != 0) {
						frBuilder.setDstQuarantineFlag(dstQuranteneFlag > 0 ? true : false);
					}

					if (metadataMask.and(SdnMudConstants.DST_MAC_BLOCKED_MASK).longValue() != 0) {
						frBuilder.setDstMacBlockedFlag(dstMacBlockedFlag > 0 ? true : false);
					}

					if (metadataMask.and(SdnMudConstants.SRC_MAC_BLOCKED_FLAG).longValue() != 0) {
						frBuilder.setSrcMacBlockedFlag(srcMacBlockedFlag > 0 ? true : false);
					}

					try {
						InstanceIdentifier<Node> outNode = node.firstIdentifierOf(Node.class);
						NodeRef nodeRef = new NodeRef(outNode);

						GetFlowStatisticsInputBuilder inputBuilder = new GetFlowStatisticsInputBuilder();

						inputBuilder.setFlowName(flow.getFlowName());
						inputBuilder.setMatch(flow.getMatch());
						inputBuilder.setTableId(flow.getTableId());
						inputBuilder.setInstructions(flow.getInstructions());
						inputBuilder.setNode(nodeRef);

						GetFlowStatisticsOutput output = sdnmudProvider.getDirectStatisticsService()
								.getFlowStatistics(inputBuilder.build()).get().getResult();
						LOG.info("flowstatisticsMapList : " + output.getFlowAndStatisticsMapList().size());
						for (FlowAndStatisticsMapList fmaplist : output.getFlowAndStatisticsMapList()) {
							frBuilder.setPacketCount(fmaplist.getPacketCount().getValue());
							frBuilder.setByteCount(fmaplist.getByteCount().getValue());
						}
					} catch (Exception ex) {
						LOG.error("Exception getting flow stats ", ex);
					}

					/*
					 * GetAllFlowsStatisticsFromAllFlowTablesInputBuilder ib = new
					 * GetAllFlowsStatisticsFromAllFlowTablesInputBuilder();
					 * 
					 * InstanceIdentifier<Node> outNode = node.firstIdentifierOf(Node.class);
					 * NodeRef nodeRef = new NodeRef(outNode);
					 * 
					 * 
					 * try { sdnmudProvider.getFlowStatisticsService().
					 * getAllFlowsStatisticsFromAllFlowTables(ib.build()).get(); } catch
					 * (InterruptedException | ExecutionException e) {
					 * LOG.error("Exception in getting stats ", e); }
					 */

					frBuilder.setPriority(Long.valueOf(flow.getPriority()));
					frBuilder.setFlowId(flow.getId().getValue());
					frBuilder.setTableId(Long.valueOf(flow.getTableId()));
					// frBuilder.setFlowRule(flow.toString());
					frBuilder.setFlowName(flow.getFlowName());
					flowRules.add(frBuilder.build());

				}
			}
		}

		// Sort the results by priority within a table.
		if (flowRules != null) {
			Collections.sort(flowRules, new Comparator<FlowRule>() {
				@Override
				public int compare(FlowRule fl1, FlowRule fl2) {
					if (fl1.getTableId() < fl2.getTableId())
						return -1;
					else if (fl2.getTableId() < fl1.getTableId())
						return 1;
					else
						return -Long.compare(fl1.getPriority(), fl2.getPriority());
				}
			});
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
			long dstBlockedFlag = maskedMetadata.and(SdnMudConstants.DST_MAC_BLOCKED_MASK)
					.shiftRight(SdnMudConstants.DST_MAC_BLOCKED_MASK_SHIFT).longValue();

			outputBuilder.setMetadata(maskedMetadata.toString(16));
			outputBuilder.setDstLocalNetworksFlag(dstLocalNetworksFlag != 0 ? true : false);
			outputBuilder.setDstManufacturer(IdUtils.getManufacturer((int) dstManufacturerId));
			outputBuilder.setDstQuarantineFlag(dstQuranteneFlag == 0 ? false : true);
			outputBuilder.setDstBlockedFlag(dstBlockedFlag == 0 ? false : true);
			outputBuilder.setDstModel(new Uri(IdUtils.getModel((int) dstModelId)));
		}
		RpcResult<GetDstMacClassificationOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetDstMacClassificationOutput>>(result);
	}

	@Override
	public Future<RpcResult<GetSrcMacClassificationOutput>> getSrcMacClassification(
			GetSrcMacClassificationInput input) {
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
			long srcBlockedFlag = maskedMetadata.and(SdnMudConstants.SRC_MAC_BLOCKED_MASK)
					.shiftRight(SdnMudConstants.SRC_MAC_BLOCKED_MASK_SHIFT).longValue();

			outputBuilder.setMetadata(maskedMetadata.toString(16));
			outputBuilder.setSrcLocalNetworksFlag(srcLocalNetworksFlag != 0 ? true : false);
			outputBuilder.setSrcManufacturer(IdUtils.getManufacturer((int) srcManufacturerId));
			outputBuilder.setSrcQuarantineFlag(srcQuranteneFlag == 0 ? false : true);
			outputBuilder.setSrcBlockedFlag(srcBlockedFlag == 0 ? false : true);
			outputBuilder.setSrcModel(new Uri(IdUtils.getModel((int) srcModelId)));
		}
		RpcResult<GetSrcMacClassificationOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<GetSrcMacClassificationOutput>>(result);
	}

	@Override
	public Future<RpcResult<Void>> quarantine(QuarantineInput input) {
		MacAddress srcMac = input.getDeviceMacAddress();
		QuarantineDevice qd = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices();
		if (!qd.getQurantineMac().contains(srcMac)) {
			qd.getQurantineMac().add(srcMac);
			InstanceIdentifier<QuarantineDevice> qId = InstanceIdentifier.builder(QuarantineDevice.class).build();
			ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();
			// PUT not merge -- override existing value.
			tx.put(LogicalDatastoreType.CONFIGURATION, qId, qd);
			tx.submit();
		}
		return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
	}

	@Override
	public Future<RpcResult<Void>> unquarantine(UnquarantineInput input) {
		MacAddress srcMac = input.getDeviceMacAddress();
		QuarantineDevice qd = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices();
		if (qd.getQurantineMac().contains(srcMac)) {
			qd.getQurantineMac().remove(srcMac);
			InstanceIdentifier<QuarantineDevice> qId = InstanceIdentifier.builder(QuarantineDevice.class).build();
			ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();
			tx.put(LogicalDatastoreType.CONFIGURATION, qId, qd);
			tx.submit();
		}
		return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());

	}

	@Override
	public Future<RpcResult<Void>> unquarantineAll() {
		QuarantineDevice qd = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices();
		qd.getQurantineMac().clear();
		InstanceIdentifier<QuarantineDevice> qId = InstanceIdentifier.builder(QuarantineDevice.class).build();
		ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();
		tx.put(LogicalDatastoreType.CONFIGURATION, qId, qd);
		tx.submit();
		return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
	}

	@Override
	public Future<RpcResult<Void>> addControllerMapping(AddControllerMappingInput input) {
		String switchId = input.getSwitchId();
		String controllerUri = input.getControllerUri().getValue();
		List<Ipv4Address> addresses = input.getAddressList();
		InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(switchId);
		if (node != null) {
			for (Ipv4Address address : addresses) {
				sdnmudProvider.addControllerMap(switchId, controllerUri, address.getValue());
				sdnmudProvider.getMudFlowsInstaller().fixupControllerNameResolution(switchId, controllerUri,
						address.getValue());
			}
		}
		return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
	}

	@Override
	public Future<RpcResult<GetMudReportsOutput>> getMudReports(GetMudReportsInput input) {
		Uri mudUrl = input.getMudUrl();

		GetMudReportsOutputBuilder gmrob = new GetMudReportsOutputBuilder();
		ReportBuilder reportBuilder = new ReportBuilder();
		reportBuilder.setMudurl(mudUrl);
		Mud mud = sdnmudProvider.getMud(mudUrl);
		if (mud != null) {
			List<MudReport> mudReports = new MudReportGenerator(sdnmudProvider).getMudReports(mud);
			reportBuilder.setMudReport(mudReports);
		} else {
			List<MudReport> mudReports = new ArrayList<MudReport>();
			reportBuilder.setMudReport(mudReports);
		}
		gmrob.setReport(reportBuilder.build());
		RpcResult<GetMudReportsOutput> result = RpcResultBuilder.success(gmrob).build();
		return new CompletedFuture<RpcResult<GetMudReportsOutput>>(result);
	}

	@Override
	public Future<RpcResult<AddControllerWaitInputOutput>> addControllerWaitInput(AddControllerWaitInputInput input) {
		Uri mudUrl = input.getMudUrl();
		Mud mud = null;
		while (mud == null) {
			try {
				if (!sdnmudProvider.findMud(mudUrl.getValue())) {
					return Futures.immediateFailedFuture(new NullPointerException());
				}
			} catch (Exception ex) {
				LOG.info("Exception encoundered while trying to find MUD file");
				return null;
			}
			mud = sdnmudProvider.getMud(mudUrl);
		}
		AddControllerWaitInputOutputBuilder gmrioutbuilder = new AddControllerWaitInputOutputBuilder();
		AddControllerWaitInputOutput result = gmrioutbuilder.build();
		RpcResult<AddControllerWaitInputOutput> res = RpcResultBuilder.success(result).build();
		return Futures.immediateFuture(res);

	}

	@Override
	public Future<RpcResult<GetMudUrlsOutput>> getMudUrls(GetMudUrlsInput input) {
		HashSet<String> mudUrls = sdnmudProvider.findMuds();
		for (String mudUrl : mudUrls) {
			LOG.info("mudUrl " + mudUrl + "\n");
			LOG.info("input " + input + "\n");
			if (input == null || input.getMudUrl() == null  || !input.getMudUrl().contains(mudUrl)) {
				org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.get.mud.urls.output.Report report = 
						new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.get.mud.urls.output.ReportBuilder()
						.setMudUrl(mudUrl).build();
				GetMudUrlsOutputBuilder outputBuilder = new GetMudUrlsOutputBuilder();
				outputBuilder.setReport(report);
				RpcResultBuilder<GetMudUrlsOutput> result = RpcResultBuilder.success(outputBuilder.build());
				return new CompletedFuture<RpcResult<GetMudUrlsOutput>>(result.build());
			}
		}

		GetMudUrlsOutputBuilder outputBuilder = new GetMudUrlsOutputBuilder();
		RpcResultBuilder<GetMudUrlsOutput> result = RpcResultBuilder.success(outputBuilder.build());
		return new CompletedFuture<RpcResult<GetMudUrlsOutput>>(result.build());

	}

}
