package gov.nist.antd.baseapp.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Timer;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WakeupOnFlowCapableNode implements DataTreeChangeListener<FlowCapableNode> {
	private static final Logger LOG = LoggerFactory.getLogger(WakeupOnFlowCapableNode.class);

	private BaseappProvider baseappProvider;

	private HashSet<String> switches = new HashSet<String>();

	private static final String getNodeUri(InstanceIdentifier<FlowCapableNode> node) {
		return node.firstKeyOf(Node.class).getId().getValue();

	}

	public WakeupOnFlowCapableNode(BaseappProvider baseappProvider) {
		this.baseappProvider = baseappProvider;
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

	private void installUnconditionalGoToTable(InstanceIdentifier<FlowCapableNode> node, short table) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId("BASEAPP");
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(BaseappConstants.UNCONDITIONAL_GOTO);
		FlowBuilder unconditionalGoToNextFlow = FlowUtils.createUnconditionalGoToNextTableFlow(table, flowId,
				flowCookie);
		baseappProvider.getFlowWriter().writeFlow(unconditionalGoToNextFlow, node);
	}

	private synchronized void installNormalFlow(InstanceIdentifier<FlowCapableNode> node) {
		LOG.info("install normal flow");
		FlowId flowId = InstanceIdentifierUtils.createFlowId("BASEAPP");
		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie("NORMAL");
		FlowBuilder fb = FlowUtils.createNormalFlow(
				baseappProvider.getNormalRuleTable(), flowId, flowCookie);
		baseappProvider.getFlowWriter().writeFlow(fb, node);
	}

	private synchronized void installFlows(InstanceIdentifier<FlowCapableNode> nodePath) {

		for (int i = 0; i < baseappProvider.getNormalRuleTable(); i++) {
			installUnconditionalGoToTable(nodePath, (short) i);
		}
		installNormalFlow(nodePath);

	}

	/**
	 * This gets invoked when a switch appears and connects.
	 * 
	 * @param nodePath
	 *            -- the node path.
	 *
	 */
	private synchronized void onFlowCapableSwitchAppeared(InstanceIdentifier<FlowCapableNode> nodePath) {
		LOG.info("onFlowCapableSwitchAppeared : " + nodePath + " myId " + this);
		if (this.switches.contains(getNodeUri(nodePath))) {
			LOG.info("Already installed flow");
			return;
		}
		this.switches.add(getNodeUri(nodePath));
		installFlows(nodePath);
	}

	/**
	 * Deal with disconnection of the switch.
	 * 
	 * @param nodePath
	 *            - the instance id of the disconnecting switch.
	 */

	private synchronized void onFlowCapableSwitchDisappeared(InstanceIdentifier<FlowCapableNode> nodePath) {
		String nodeUri = getNodeUri(nodePath);
		this.switches.remove(nodeUri);
		LOG.info("onFlowCapableSwitchDisappeared");
		// The URI identifies the node instance.
		LOG.info("node URI " + nodeUri);
	}

}
