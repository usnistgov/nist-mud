package gov.nist.antd.sdniot.impl;

import java.util.Collection;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.ids.config.rev170915.IdsConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdsConfigDataStoreListener implements DataTreeChangeListener<IdsConfigData> {

  private SdnmudProvider sdnmudProvider;
  private static final Logger LOG = LoggerFactory.getLogger(IdsConfigDataStoreListener.class);
  private IdsConfigData idsConfigData;

  public IdsConfigDataStoreListener(SdnmudProvider sdnmudProvider) {
    this.sdnmudProvider = sdnmudProvider;
  }

  private void installSendIdsHelloToControllerFlow(String nodeUri,
      InstanceIdentifier<FlowCapableNode> node) {
    if (!sdnmudProvider.getFlowCommitWrapper().flowExists(nodeUri + ":ids",
        SdnMudConstants.FIRST_TABLE, node)) {
      FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri + ":ids");
      FlowCookie flowCookie = SdnMudConstants.IDS_REGISTRATION_FLOW_COOKIE;
      LOG.info("IDS_REGISTRATION_FLOOW_COOKIE " + flowCookie.getValue().toString(16));
      FlowBuilder fb =
          FlowUtils.createDestIpMatchSendToController(SdnMudConstants.IDS_REGISTRATION_ADDRESS,
              SdnMudConstants.IDS_REGISTRATION_PORT, SdnMudConstants.FIRST_TABLE, flowCookie,
              flowId, SdnMudConstants.IDS_REGISTRATION_METADATA);
      sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }
  }

  @Override
  public void onDataTreeChanged(Collection<DataTreeModification<IdsConfigData>> changes) {

    LOG.info("IdsConfigDataStoreListener: onDataTreeChanged: got an ids registration");

    for (DataTreeModification<IdsConfigData> change : changes) {
      IdsConfigData idsConfigData = change.getRootNode().getDataAfter();
      this.idsConfigData = idsConfigData;
      Uri idsNodeUri = this.idsConfigData.getIdsNode();
      sdnmudProvider.addIdsConfig(idsNodeUri, idsConfigData);

      InstanceIdentifier<FlowCapableNode> nodePath = sdnmudProvider.getNode(idsNodeUri.getValue());
      if (nodePath != null) {
        installSendIdsHelloToControllerFlow(idsNodeUri.getValue(), nodePath);
      } else {
        LOG.info("IDS node has not appeared");
      }

    }
  }

  public List<Uri> getFlowSpec() {
    if (idsConfigData == null)
      return null;
    else
      return idsConfigData.getFlowSpec();
  }

  public Uri getIdsNode() {
    if (idsConfigData == null)
      return null;
    else
      return idsConfigData.getIdsNode();
  }

}
