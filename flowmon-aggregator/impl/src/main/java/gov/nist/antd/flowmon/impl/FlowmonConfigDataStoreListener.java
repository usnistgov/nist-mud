package gov.nist.antd.flowmon.impl;

import java.util.Collection;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowmonConfigDataStoreListener implements DataTreeChangeListener<FlowmonConfigData> {

  private FlowmonProvider flowmonProvider;
  private static final Logger LOG = LoggerFactory.getLogger(FlowmonConfigDataStoreListener.class);
  private FlowmonConfigData flowmonConfigData;

  public FlowmonConfigDataStoreListener(FlowmonProvider flowmonProvider) {
    this.flowmonProvider = flowmonProvider;
  }

  private void installSendFlowmonHelloToControllerFlow(String nodeUri,
      InstanceIdentifier<FlowCapableNode> node) {
    if (!flowmonProvider.getFlowCommitWrapper().flowExists(nodeUri + ":flowmon",
        SdnMudConstants.FIRST_TABLE, node)) {
      FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":flowmon");
      FlowCookie flowCookie = SdnMudConstants.IDS_REGISTRATION_FLOW_COOKIE;
      LOG.info("IDS_REGISTRATION_FLOOW_COOKIE " + flowCookie.getValue().toString(16));
      FlowBuilder fb =
          FlowUtils.createDestIpMatchSendToController(SdnMudConstants.IDS_REGISTRATION_ADDRESS,
              SdnMudConstants.IDS_REGISTRATION_PORT, SdnMudConstants.FIRST_TABLE, flowCookie,
              flowId, SdnMudConstants.IDS_REGISTRATION_METADATA);
      flowmonProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }
  }

  @Override
  public void onDataTreeChanged(Collection<DataTreeModification<FlowmonConfigData>> changes) {

    LOG.info("FlowmonConfigDataStoreListener: onDataTreeChanged: got an flowmon registration");

    for (DataTreeModification<FlowmonConfigData> change : changes) {
      FlowmonConfigData flowmonConfigData = change.getRootNode().getDataAfter();
      this.flowmonConfigData = flowmonConfigData;
      Uri flowmonNodeUri = this.flowmonConfigData.getFlowmonNode();
      flowmonProvider.addFlowmonConfig(flowmonNodeUri, flowmonConfigData);
      InstanceIdentifier<FlowCapableNode> nodePath = flowmonProvider.getNode(flowmonNodeUri.getValue());
      if (nodePath != null) {
        installSendFlowmonHelloToControllerFlow(flowmonNodeUri.getValue(), nodePath);
      } else {
        LOG.info("IDS node has not appeared");
      }
    }
  }

  public List<Uri> getFlowSpec() {
    if (flowmonConfigData == null)
      return null;
    else
      return flowmonConfigData.getFlowSpec();
  }

  public Uri getFlowmonNode() {
    if (flowmonConfigData == null)
      return null;
    else
      return flowmonConfigData.getFlowmonNode();
  }

}
