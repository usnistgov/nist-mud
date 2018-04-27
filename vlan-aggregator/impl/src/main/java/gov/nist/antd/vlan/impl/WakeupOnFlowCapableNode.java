package gov.nist.antd.vlan.impl;

import java.util.Collection;
import java.util.HashSet;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;


class WakeupOnFlowCapableNode implements DataTreeChangeListener<FlowCapableNode> {
    private static final Logger LOG = LoggerFactory.getLogger(WakeupOnFlowCapableNode.class);

    private VlanProvider vlanProvider;

    private HashSet<InstanceIdentifier<FlowCapableNode>> pendingNodes = new HashSet<>();

    WakeupOnFlowCapableNode(VlanProvider vlanProvider) {
        this.vlanProvider = vlanProvider;
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<FlowCapableNode>> modifications) {
        LOG.debug("WakeupOnFlowCapableNode: onDataTreeChanged");

        for (DataTreeModification<FlowCapableNode> modification : modifications) {
            if (modification.getRootNode().getModificationType() == ModificationType.WRITE) {
                LOG.info("got a WRITE modification");
                InstanceIdentifier<FlowCapableNode> ii = modification.getRootPath().getRootIdentifier();
                onFlowCapableSwitchAppeared(ii);
            } else if (modification.getRootNode().getModificationType() == ModificationType.DELETE) {
                LOG.info("Got a DELETE modification");
                InstanceIdentifier<FlowCapableNode> ii = modification.getRootPath().getRootIdentifier();
                onFlowCapableSwitchDisappeared(ii);
            } else {
                LOG.debug("WakeupOnFlowCapableNode : " + modification.getRootNode().getModificationType());
            }
        }

    }



    private void installInitialFlows(InstanceIdentifier<FlowCapableNode> node) {
        String nodeUri  = InstanceIdentifierUtils.getNodeUri(node);
        if ( vlanProvider.isNpeSwitch(nodeUri)) {
            short tableId = BaseappConstants.DETECT_EXTERNAL_ARP_TABLE;
            int timeout = 0;

            FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri);
            FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeUri);
            FlowBuilder fb = FlowUtils.createVlanArpMatchSendToControllerAndGoToTable(tableId, timeout, flowId, flowCookie);
            vlanProvider.getFlowCommitWrapper().deleteFlows(node, nodeUri, tableId, null);
            vlanProvider.getFlowCommitWrapper().writeFlow(fb, node);
        }
    }


    private synchronized void onFlowCapableSwitchAppeared(InstanceIdentifier<FlowCapableNode> nodePath) {
        String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
        LOG.info("onFlowCapableSwitchAppeared");
        // The URI identifies the node instance.
        LOG.info("node URI " + nodeUri + " nodePath " + nodePath);
        // Stash away the URI to node path so we can reference it later.
        this.vlanProvider.putInUriToNodeMap(nodeUri, nodePath);

        if ( this.vlanProvider.isNpeSwitch(nodeUri)) {
            installInitialFlows(nodePath);
        } else {
            this.pendingNodes.add(nodePath);
        }

    }


    private synchronized void onFlowCapableSwitchDisappeared(InstanceIdentifier<FlowCapableNode> nodePath) {
        String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
        LOG.info("onFlowCapableSwitchDisappeared");
        // The URI identifies the node instance.
        LOG.info("node URI " + nodeUri);
        // Remove the node URI from the uriToNodeMap.
        this.vlanProvider.removeFromUriToNodeMap(nodeUri);

    }

    public void installInitialFlows() {
        for (InstanceIdentifier<FlowCapableNode> node :  pendingNodes  ) {
            installInitialFlows(node);
        }
        this.pendingNodes.clear();
    }

}
