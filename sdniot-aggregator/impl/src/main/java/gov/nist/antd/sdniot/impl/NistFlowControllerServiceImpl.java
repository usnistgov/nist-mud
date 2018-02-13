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

package gov.nist.antd.sdniot.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
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
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.GetRegisteredMudUrisOutput;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.GetRegisteredMudUrisOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.NistFlowControllerService;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.get.registered.mud.uris.output.MudNodeMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.get.registered.mud.uris.output.MudNodeMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Flow controller RPC service to shut off specific flows.
 * The design for this is still under development.
 * 
 * @author mranga@nist.gov
 *
 */
public class NistFlowControllerServiceImpl implements NistFlowControllerService {
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

  public NistFlowControllerServiceImpl(SdnmudProvider sdnmudProvider) {
    this.sdnmudProvider = sdnmudProvider;
  }

  @Override
  public Future<RpcResult<BlockFlowOutput>> blockFlow(BlockFlowInput blockedFlowInput) {
    Uri mudUri = blockedFlowInput.getMudUri();
    Scope scope = blockedFlowInput.getScope();

    String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
    int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);

    int modelId = InstanceIdentifierUtils.getModelId(mudUri.getValue());

    if (manufacturerId == -1 && modelId == -1) {
      BlockFlowOutputBuilder outputBuilder =
          new BlockFlowOutputBuilder().setStatus(-1L).setStatusMessage("Bad data");
      RpcResult<BlockFlowOutput> result = RpcResultBuilder.success(outputBuilder).build();
      return new CompletedFuture<RpcResult<BlockFlowOutput>>(result);

    }

    Collection<InstanceIdentifier<FlowCapableNode>> nodes =
        sdnmudProvider.getMudNodes(manufacturer);

    String uri = "blocked-flow:" + manufacturer;

    for (InstanceIdentifier<FlowCapableNode> node : nodes) {
      short tableId = SdnMudConstants.PASS_THRU_TABLE;
      sdnmudProvider.getFlowCommitWrapper().deleteFlows(node, uri, tableId, null);
      FlowId flowId = InstanceIdentifierUtils.createFlowId(uri);
      FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri);
      if (modelId != -1) {
        BigInteger metadata =
            BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
                .or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

        BigInteger metadataMask =
            SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK);
        FlowBuilder fb = FlowUtils.createMetadataMatchDropPacket(metadata, metadataMask, tableId,
            flowCookie, flowId);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
      } else {
        BigInteger metadata =
            BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT);
        BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK;
        FlowBuilder fb = FlowUtils.createMetadataMatchDropPacket(metadata, metadataMask, tableId,
            flowCookie, flowId);
        sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
      }
    }



    BlockFlowOutputBuilder outputBuilder =
        new BlockFlowOutputBuilder().setStatus(0L).setStatusMessage("Success");
    RpcResult<BlockFlowOutput> result = RpcResultBuilder.success(outputBuilder).build();
    return new CompletedFuture<RpcResult<BlockFlowOutput>>(result);

  }

  @Override
  public Future<RpcResult<GetRegisteredMudUrisOutput>> getRegisteredMudUris() {
   
    Collection<String> cpeNodeIds = sdnmudProvider.getMudCpeNodeIds();
    GetRegisteredMudUrisOutputBuilder outputBuilder = new GetRegisteredMudUrisOutputBuilder();
    List<MudNodeMapping> mudNodeMappingList = new ArrayList<MudNodeMapping>();
    for (String cpeNodeId : cpeNodeIds) {
       List<Uri> mudUris = sdnmudProvider.getRegisteredMudUrls(cpeNodeId);       
       if (mudUris != null) {
         MudNodeMapping mudNodeMapping = new MudNodeMappingBuilder().setNodeId(new Uri(cpeNodeId)).setMudUris(mudUris).build();
         mudNodeMappingList.add(mudNodeMapping);
       }
    }
    outputBuilder.setMudNodeMapping(mudNodeMappingList);
    RpcResult<GetRegisteredMudUrisOutput> result = RpcResultBuilder.success(outputBuilder).build();
    return new CompletedFuture<RpcResult<GetRegisteredMudUrisOutput>>(result);
  }

}
