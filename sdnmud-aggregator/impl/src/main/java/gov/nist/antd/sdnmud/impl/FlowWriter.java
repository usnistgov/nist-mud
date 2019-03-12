/*
 * Copyright (c) Public Domain Jul 16, 2018.
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

import java.util.concurrent.ExecutionException;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.RemoveFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author mranga
 *
 */
public class FlowWriter {

	private SalFlowService salFlowService;
	private static final Logger LOG = LoggerFactory.getLogger(FlowWriter.class);

	public FlowWriter(SalFlowService salFlowService) {
		this.salFlowService = salFlowService;
	}

	public void writeFlow(FlowBuilder fb, InstanceIdentifier<FlowCapableNode> node) {
		this.writeFlow(fb.build(), node);
	}
	public static final InstanceIdentifier<Node> getNodePath(final InstanceIdentifier<?> nodeChild) {

		return nodeChild.firstIdentifierOf(Node.class);
	}


	public void writeFlow(Flow flow, InstanceIdentifier<FlowCapableNode> node) {
		AddFlowInputBuilder afib = new AddFlowInputBuilder();

		afib.setNode(new NodeRef(getNodePath(node)));
		afib.setTableId(flow.getTableId());
		afib.setPriority(flow.getPriority());
		afib.setBarrier(flow.isBarrier());
		afib.setCookie(flow.getCookie());
		afib.setCookieMask(flow.getCookieMask());
		afib.setInstructions(flow.getInstructions());
		afib.setFlowName(flow.getFlowName());
		afib.setHardTimeout(flow.getHardTimeout());
		afib.setIdleTimeout(flow.getIdleTimeout());
		afib.setMatch(flow.getMatch());
		afib.setBufferId(flow.getBufferId());
		afib.setFlags(flow.getFlags());
		afib.setBarrier(true);
		final InstanceIdentifier<Flow> path1 = node.child(Table.class, new TableKey(flow.getTableId()))
				.child(Flow.class, flow.getKey());
		afib.setFlowRef(new FlowRef(path1));
		afib.setStrict(false);
		afib.setTransactionUri(new Uri(flow.getId().getValue()));

		try {
			salFlowService.addFlow(afib.build()).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("SalFlowService: problem writing the flow to switch ", e);
		}
	}

	public void deleteFlows(InstanceIdentifier<FlowCapableNode> node, Flow flow) {
		RemoveFlowInputBuilder afib = new RemoveFlowInputBuilder();

		afib.setNode(new NodeRef(getNodePath(node)));
		afib.setTableId(flow.getTableId());
		afib.setPriority(flow.getPriority());
		afib.setBarrier(flow.isBarrier());
		afib.setCookie(flow.getCookie());
		afib.setCookieMask(flow.getCookieMask());
		afib.setInstructions(flow.getInstructions());
		afib.setFlowName(flow.getFlowName());
		afib.setHardTimeout(flow.getHardTimeout());
		afib.setIdleTimeout(flow.getIdleTimeout());
		afib.setMatch(flow.getMatch());
		afib.setBufferId(flow.getBufferId());
		afib.setFlags(flow.getFlags());
		afib.setBarrier(true);
		final InstanceIdentifier<Flow> path1 = node.child(Table.class, new TableKey(flow.getTableId()))
				.child(Flow.class, flow.getKey());
		afib.setFlowRef(new FlowRef(path1));

		salFlowService.removeFlow(afib.build());
	}

}
