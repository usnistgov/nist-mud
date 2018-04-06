/*
* This file includes code developed by employees of the National Institute of
* Standards and Technology (NIST)
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), and others. This software has been
* contributed to the public domain. Pursuant to title 15 Untied States
* Code Section 105, works of NIST employees are not subject to copyright
* protection in the United States and are considered to be in the public
* domain. As a result, a formal license is not needed to use this software.
*
* This software is provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND,
* EXPRESS, IMPLIED OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE
* IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
* NON-INFRINGEMENT AND DATA ACCURACY. NIST does not warrant or make any
* representations regarding the use of the software or the results thereof,
* including but not limited to the correctness, accuracy, reliability or
* usefulness of this software.
*/

package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.BlockFlowInput;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.BlockFlowInput.Scope;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.BlockFlowOutput;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.BlockFlowOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.NistFlowControllerService;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.RegisterMonitorInput;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.RegisterMonitorOutput;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.RegisterMonitorOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfig;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.flowmon.config.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

/**
 * Flow controller RPC service to shut off specific flows. The design for this
 * is still under development.
 * 
 * @author mranga@nist.gov
 *
 */
public class NistFlowControllerServiceImpl implements NistFlowControllerService {

	static final Logger LOG = LoggerFactory.getLogger(NistFlowControllerService.class);

	private FlowmonProvider flowmonProvider;

	private HashMap<String, FlowBuilder> flowCache = new HashMap<String,FlowBuilder>();

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

	public NistFlowControllerServiceImpl(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}

	private static String generateSetMplsFlowIdStr(String nodeUri, String flowId) {
		return nodeUri + ":" + flowId + ":" + "SET_MPLS";
	}

	private static String generateStripMplsFlowIdStr(String nodeUri, String flowId) {
		return nodeUri + ":" + flowId + ":" + "STRIP_MPLS";
	}

	@Override
	public Future<RpcResult<BlockFlowOutput>> blockFlow(BlockFlowInput blockedFlowInput) {
		Uri mudUri = blockedFlowInput.getMudUri();

		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = flowmonProvider.getManfuacturerId(manufacturer);

		int modelId = flowmonProvider.getModelId(mudUri.getValue());

		if (manufacturerId == -1 && modelId == -1) {
			BlockFlowOutputBuilder outputBuilder = new BlockFlowOutputBuilder().setStatus(-1L)
					.setStatusMessage("Bad data");
			RpcResult<BlockFlowOutput> result = RpcResultBuilder.success(outputBuilder).build();
			return new CompletedFuture<RpcResult<BlockFlowOutput>>(result);

		}

		Collection<InstanceIdentifier<FlowCapableNode>> nodes = flowmonProvider.getCpeNodes();

		String uri = "blocked-flow:" + manufacturer;

		for (InstanceIdentifier<FlowCapableNode> node : nodes) {
			short tableId = BaseappConstants.PASS_THRU_TABLE;
			flowmonProvider.getFlowCommitWrapper().deleteFlows(node, uri, tableId, null);
			FlowId flowId = InstanceIdentifierUtils.createFlowId(uri);
			FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri);
			if (modelId != -1) {
				BigInteger metadata = BigInteger.valueOf(manufacturerId)
						.shiftLeft(FlowmonConstants.SRC_MANUFACTURER_SHIFT)
						.or(BigInteger.valueOf(modelId).shiftLeft(FlowmonConstants.SRC_MODEL_SHIFT));

				BigInteger metadataMask = FlowmonConstants.SRC_MANUFACTURER_MASK.or(FlowmonConstants.SRC_MODEL_MASK);
				FlowBuilder fb = FlowUtils.createMetadataMatchDropPacket(metadata, metadataMask, tableId, flowCookie,
						flowId);
				flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
			} else {
				BigInteger metadata = BigInteger.valueOf(manufacturerId)
						.shiftLeft(FlowmonConstants.SRC_MANUFACTURER_SHIFT);
				BigInteger metadataMask = FlowmonConstants.SRC_MANUFACTURER_MASK;
				FlowBuilder fb = FlowUtils.createMetadataMatchDropPacket(metadata, metadataMask, tableId, flowCookie,
						flowId);
				flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
			}
		}

		BlockFlowOutputBuilder outputBuilder = new BlockFlowOutputBuilder().setStatus(0L).setStatusMessage("Success");
		RpcResult<BlockFlowOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<BlockFlowOutput>>(result);

	}

	@Override
	public Future<RpcResult<RegisterMonitorOutput>> registerMonitor(RegisterMonitorInput registerMonitorInput) {

		for (Uri cpeNodeUri : registerMonitorInput.getCpeNodeIds()) {
			InstanceIdentifier<FlowCapableNode> cpeNode = this.flowmonProvider.getNode(cpeNodeUri.getValue());
			if (cpeNode == null) {
				LOG.info("CPE node not yet registered");
				continue;
			}
			InstanceIdentifier<FlowCapableNode> vnfNode = this.flowmonProvider.getVnfNode(cpeNodeUri.getValue());
			
			if ( vnfNode == null) {
				LOG.info("Null vnfNodeId returned for " + cpeNodeUri.getValue());
				continue;
			}

			String vnfNodeId = InstanceIdentifierUtils.getNodeUri(vnfNode);
			FlowmonConfigData flowmonConfig = this.flowmonProvider.getFlowmonConfig(vnfNodeId);
			for (Uri flowUri : flowmonConfig.getFlowSpec()) {
				String flowIdStr = generateSetMplsFlowIdStr(cpeNodeUri.getValue(), flowUri.getValue());
				FlowBuilder fb = this.flowCache.get(flowIdStr);
				int mplsTag = InstanceIdentifierUtils.getFlowHash(flowUri.getValue());
				if (fb == null) {
					FlowId flowId = new FlowId(flowIdStr);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri.getValue());
					fb = FlowUtils.createMetadataMatchMplsTagGoToTableFlow(flowCookie, flowId,
							BaseappConstants.PASS_THRU_TABLE, mplsTag, registerMonitorInput.getTimeout().intValue());
					this.flowCache.put(flowIdStr, fb);
				}
				// This sets the MPS tag prior to the output stage
				this.flowmonProvider.getFlowCommitWrapper().writeFlow(fb, cpeNode);
				flowIdStr = generateStripMplsFlowIdStr(cpeNodeUri.getValue(), flowUri.getValue());
				fb = this.flowCache.get(flowIdStr);
				if (fb == null) {
					FlowId flowId = new FlowId(flowIdStr);
					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri.getValue());
					fb = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId,
							BaseappConstants.STRIP_MPLS_RULE_TABLE, mplsTag);
					this.flowCache.put(flowIdStr, fb);
				}
				// Push a strip MPLS rule at the CPE node.
				this.flowmonProvider.getFlowCommitWrapper().writeFlow(fb, cpeNode);
				if (vnfNode != null) {
					// At the VNF node, we push a rule to strip the MPLS tag at the last
					// stage of the pipeline.
					flowIdStr = generateStripMplsFlowIdStr(vnfNodeId, flowUri.getValue());
					fb = this.flowCache.get(flowIdStr);
					if (fb == null) {
						FlowId flowId = new FlowId(flowIdStr);
						FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowUri.getValue());
						fb = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId,
								BaseappConstants.STRIP_MPLS_RULE_TABLE, mplsTag);
						this.flowCache.put(flowIdStr, fb);
					}
					// Push a strip MPLS rule at the CPE node.
					this.flowmonProvider.getFlowCommitWrapper().writeFlow(fb, cpeNode);
				

				}

			}
		}
		RegisterMonitorOutputBuilder outputBuilder = new RegisterMonitorOutputBuilder().setStatus(0L);
		RpcResult<RegisterMonitorOutput> result = RpcResultBuilder.success(outputBuilder).build();
		return new CompletedFuture<RpcResult<RegisterMonitorOutput>>(result);

	}

}
