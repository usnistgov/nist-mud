/*
 *
 *This program and the accompanying materials are made available under the
 *Public Domain.
 *
 * Copyright (c) 2017 None.  No rights reserved.
 *
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

package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdUtils {
	private static AtomicLong flowIdInc = new AtomicLong();
	private static ArrayList<String> manufacturers = new ArrayList<String>();
	private static ArrayList<String> models = new ArrayList<String>();
	static {
		manufacturers.add("NONE");
		models.add("NONE");
	}

	private static final Logger LOG = LoggerFactory.getLogger(IdUtils.class);

	static {
		// Dummy constants
		models.add(SdnMudConstants.NONE);
		manufacturers.add(SdnMudConstants.NONE);
	}

	private IdUtils() {
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

	public static FlowId createFlowId(String prefix) {
		return new FlowId(prefix + "/sdnmud/" + String.valueOf(flowIdInc.getAndIncrement()));
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
			LOG.info("getAuthority : Malformed URI " + uri);
			return SdnMudConstants.UNCLASSIFIED;
		}
		String rest = uri.substring(index + 2);
		index = rest.indexOf("/");
		String authority = rest.substring(0, index);
		return authority;
	}

	/**
	 * Create a flow hash given a flow specification.
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

	public static synchronized int getManfuacturerId(String manufacturer) {
		int index = -1;
		for (int i = 0; i < manufacturers.size(); i++) {
			if (manufacturers.get(i).compareTo(manufacturer) == 0) {
				index = i;
				break;
			}
		}

		if (index == -1) {
			manufacturers.add(manufacturer);
			index = manufacturers.size() - 1;
		}
		LOG.info("getManifacturerId [" + manufacturer + "] manufacturerId " + index);
		return index;
	}

	public static synchronized int getModelId(String model) {
		int index = -1;
		for (int i = 0; i < models.size(); i++) {
			if (models.get(i).compareTo(model) == 0) {
				index = i;
				break;
			}
		}

		if (index == -1) {
			models.add(model);
			index = models.size() - 1;
		}

		LOG.info("getModelId : model [" + model + "] modelId " + index);
		return index;
	}

}
