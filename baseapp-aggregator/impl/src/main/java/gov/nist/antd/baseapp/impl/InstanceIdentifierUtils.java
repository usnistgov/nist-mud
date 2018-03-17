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

package gov.nist.antd.baseapp.impl;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

abstract class InstanceIdentifierUtils {
	private static AtomicLong flowIdInc = new AtomicLong();

	public static int getFlowHash(String flowSpec) {
		// Has to be within the size of an mpls label.
		// or the flow does not appear.
		return Math.abs(flowSpec.hashCode()) % (1 << 20);
	}
	
	static final String getNodeUri(InstanceIdentifier<FlowCapableNode> node) {
		return node.firstKeyOf(Node.class).getId().getValue();

	}

	public static FlowId createFlowId(String mudUrl) {
		return new FlowId(mudUrl + "&counter=" + String.valueOf(flowIdInc.getAndIncrement()));
	}

	public static FlowCookie createFlowCookie(String flowCookieId) {
		return new FlowCookie(BigInteger.valueOf(Math.abs(getFlowHash(flowCookieId))));
	}

	

}
