/*
 * Copyright © 2017 Public Domain and others.  All rights reserved.
 *
 */
package gov.nist.antd.baseapp.impl;

import java.util.HashMap;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseappProvider {

	private static final Logger LOG = LoggerFactory.getLogger(BaseappProvider.class);

	private final DataBroker dataBroker;

	private FlowCommitWrapper flowCommitWrapper;

	private WakeupOnFlowCapableNode wakeupListener;

	private ListenerRegistration<WakeupOnFlowCapableNode> wakeupOnFlowCapableNodeRegistration;

	private static InstanceIdentifier<FlowCapableNode> getWildcardPath() {
		return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
	}


	public BaseappProvider(final DataBroker dataBroker ) {
		LOG.info("Baseapp provider created");
		this.dataBroker = dataBroker;
		this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);
	}
	
	public FlowCommitWrapper getFlowCommitWrapper() {
		return this.flowCommitWrapper;
		
	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("Baseapp Session Initiated");

		this.wakeupListener = new WakeupOnFlowCapableNode(this);
		final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier = new DataTreeIdentifier<FlowCapableNode>(
				LogicalDatastoreType.OPERATIONAL, getWildcardPath());
		this.wakeupOnFlowCapableNodeRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
				wakeupListener);
	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		this.wakeupOnFlowCapableNodeRegistration.close();
		LOG.info("FlowmonProvider Closed");
	}

}
