/*
 *  Copyright (c) 2014, 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * NIST disclaimer (for code added by NIST employees):
 * 
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copyright (c) 2013, 2015 Red Hat, Inc. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Works of NIST employees are released to the public domain according to the following:
 * 
 * This software was developed by employees of the National Institute of Standards and Technology
 * (NIST), and others. This software has been contributed to the public domain. Pursuant to title 15
 * Untied States Code Section 105, works of NIST employees are not subject to copyright protection
 * in the United States and are considered to be in the public domain. As a result, a formal license
 * is not needed to use this software.
 * 
 * This software is provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED OR
 * STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, NON-INFRINGEMENT AND DATA ACCURACY. NIST does not warrant or make any
 * representations regarding the use of the software or the results thereof, including but not
 * limited to the correctness, accuracy, reliability or usefulness of this software.
 */

package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceIdentifierUtils {
	private static AtomicLong flowIdInc = new AtomicLong();
	private static String FLOW_ID_PREFIX = "flowmon:";
	private static ArrayList<String> manufacturers = new ArrayList<String>();
	private static ArrayList<String> models = new ArrayList<String>();

	static final Logger LOG = LoggerFactory.getLogger(InstanceIdentifierUtils.class);

	static {
		// Dummy constants
		models.add(SdnMudConstants.NONE);
		manufacturers.add(SdnMudConstants.NONE);
	}

	private InstanceIdentifierUtils() {
		// hiding constructor for util class
	}


	/**
	 * Shorten's node child path to node path.
	 *
	 * @param nodeChild
	 *            child of node, from which we want node path.
	 * @return
	 */
	public static final InstanceIdentifier<Node> getNodePath(final InstanceIdentifier<?> nodeChild) {

		return nodeChild.firstIdentifierOf(Node.class);
	}

	public static final String getNodeUri(InstanceIdentifier<FlowCapableNode> node) {
		return node.firstKeyOf(Node.class).getId().getValue();

	}

	public static final InstanceIdentifier<FlowCapableNode> getFlowCapableNodePath(
			final InstanceIdentifier<?> nodeChild) {

		return nodeChild.firstIdentifierOf(FlowCapableNode.class);
	}

	/**
	 * Get an instance Identifier for the given flow table.
	 * 
	 * @param nodeId
	 *            -- Instance Identifier for the node.
	 * 
	 * @param flowTableId
	 *            -- flow table identifier.
	 * 
	 * @return -- instance identifier for the table.
	 */

	public static final InstanceIdentifier<Table> getTableInstanceId(InstanceIdentifier<Node> nodeId,
			Short flowTableId) {
		// get flow table key
		TableKey flowTableKey = new TableKey(flowTableId);
		return nodeId.builder().augmentation(FlowCapableNode.class).child(Table.class, flowTableKey).build();
	}

	/**
	 * Creates a path for particular flow, by appending flow-specific
	 * information to table path.
	 *
	 * @param tablePath
	 * @param flowKey
	 * @return path to flow
	 */
	public static final InstanceIdentifier<Flow> createFlowPath(final InstanceIdentifier<Table> tablePath,
			final FlowKey flowKey) {
		return tablePath.child(Flow.class, flowKey);
	}

	/**
	 * Create an instance Identifier for the flow.
	 * 
	 * @param tablePath
	 *            -- instance Identifier for the table.
	 * 
	 * @param flowId
	 *            -- the flow Id.
	 * 
	 * @return -- instance Identifier for the flow.
	 */
	public static InstanceIdentifier<Flow> createFlowPath(final InstanceIdentifier<Table> tablePath,
			final FlowId flowId) {
		return tablePath.child(Flow.class, new FlowKey(flowId));
	}

	/**
	 * Create standard flow Instance Identifier for this application.
	 * 
	 * @param tableId
	 *            -- table instance Id.
	 * 
	 * @return -- instance Identifier for the flow.
	 */

	public static InstanceIdentifier<Flow> createFlowInstanceId(InstanceIdentifier<Table> tableId) {
		// generate unique flow key
		FlowId flowId = new FlowId(FLOW_ID_PREFIX + String.valueOf(flowIdInc.getAndIncrement()));
		FlowKey flowKey = new FlowKey(flowId);
		return tableId.child(Flow.class, flowKey);
	}

	public static final InstanceIdentifier<NodeConnector> createNodeConnectorPath(
			final InstanceIdentifier<Node> nodeKey, final NodeConnectorKey nodeConnectorKey) {
		return nodeKey.child(NodeConnector.class, nodeConnectorKey);
	}

	public static FlowId createFlowId() {
		return new FlowId(FLOW_ID_PREFIX + String.valueOf(flowIdInc.getAndIncrement()));
	}

	public static FlowRef buildFlowPath(final InstanceIdentifier<Node> nodePath, final short tableId,
			final FlowId flowId) {

		final KeyedInstanceIdentifier<Flow, FlowKey> flowPath = nodePath.augmentation(FlowCapableNode.class)
				.child(Table.class, new TableKey(tableId)).child(Flow.class, new FlowKey(new FlowId(flowId)));

		return new FlowRef(flowPath);
	}

	public static FlowId createFlowId(String mudUrl) {
		return new FlowId(mudUrl + "/" + String.valueOf(flowIdInc.getAndIncrement()));
	}

	public static FlowCookie createFlowCookie(String flowCookieId) {
		return new FlowCookie(BigInteger.valueOf(Math.abs(getFlowHash(flowCookieId))));
	}

	static String getAuthority(Uri uri) {
		return getAuthority(uri.getValue());
	}

	static String getAuthority(String uri) {
		int index = uri.indexOf("//");
		if (index == -1) {
			LOG.error("getAuthority : Malformed URI " + uri);
			return "";
		}
		String rest = uri.substring(index + 2);
		index = rest.indexOf("/");
		String authority = rest.substring(0, index);
		return authority;
	}

	static String getModel(String uri) {

		int index = uri.indexOf("//");
		if (index == -1) {
			LOG.error("getModel : Malformed URI " + uri);
			return "";
		}
		String rest = uri.substring(index + 2);
		index = rest.indexOf(".well-known/mud/") + ".well-known/mud/".length();
		if (index == -1) {
			LOG.error("getModel: malformed MUD uri" + uri);
			return "";
		}
		String model = rest.substring(index);
		return model;
	}

	/**
	 * Create the MPLS label from the flow spec.
	 * 
	 * @param flowSpec
	 *            -- the flow spec to hash.
	 * 
	 */
	public static int getFlowHash(String flowSpec) {
		// Has to be within the size of an mpls label.
		// or the flow does not appear.
		return Math.abs(flowSpec.hashCode()) % (1 << 20);
	}

	public static int getFlowHash(String manufacturer, String flowType) {
		String flowSpec = "flow:" + manufacturer + ":" + flowType;
		return getFlowHash(flowSpec);
	}

	public synchronized static int getManfuacturerId(String manufacturer) {
		int index = manufacturers.indexOf(manufacturer);
		if (index == -1) {
			manufacturers.add(manufacturer);
			index = manufacturers.indexOf(manufacturer);
		}
		return index;
	}

	public synchronized static int getModelId(String model) {
		int index = models.indexOf(model);
		if (index == -1) {
			models.add(model);
			index = models.indexOf(model);
		}
		return index;
	}



	public static String getNodeIdFromFlowId(String flowIdStr) {
		String[] pieces = flowIdStr.split("?");
		if (pieces == null || pieces.length == 0) return null;
		String[] ppieces = pieces[1].split("&");
		if (ppieces == null || ppieces.length == 0 ) return null;
		for (String piece: ppieces) {
			if (piece.startsWith("nodeId"))  {
				return piece.split("=") [1];
			}
		}
		return null;
	}

}
