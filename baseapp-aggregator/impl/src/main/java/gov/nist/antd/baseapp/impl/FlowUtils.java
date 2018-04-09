/*
* Copyright (c) 2014, 2015 Cisco Systems, Inc. and others. All rights reserved.
*
* This program and the accompanying materials are made available under the terms of the Eclipse
* Public License v1.0 which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* This is patterend on the Openflow plugin learning switch and the ovsdb project. Copyrights for
* the original projects from which the code was derived are included below.
* 
* Copyright (c) 2013, 2015 Red Hat, Inc. and others. All rights reserved.
* 
* This program and the accompanying materials are made available under the terms of the Eclipse
* Public License v1.0 which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
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
* 
* 
*/

package gov.nist.antd.baseapp.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class FlowUtils {

	private static final Logger LOG = LoggerFactory.getLogger(FlowUtils.class);

	private static AtomicLong instructionKey = new AtomicLong(0x0);

	private FlowUtils() {
		// Only static methods in this class
	}

	private synchronized static int getInstructionKey() {
		return (int) instructionKey.incrementAndGet();
	}


	

	/**
	 * Go to the target table.
	 * 
	 * @param thistable
	 * @return
	 */
	private static InstructionsBuilder createGoToNextTableInstruction(short thistable) {
		// Create an instruction allowing the interaction.
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(createGoToTableInstruction((thistable)));
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);
		return isb;
	}

	
	/**
	 * create and return a goto table instruction.
	 * 
	 * @param tableId
	 *            -- target of the goto instruction,
	 * @return
	 */
	private static Instruction createGoToTableInstruction(final Short tableId) {

		LOG.info("createGoToTable table ID " + tableId);

		return new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(tableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(0).build();

	}
	
	

	
	public static FlowBuilder createUnconditionalGoToNextTableFlow(short table, FlowId flowId,
			FlowCookie flowCookie) {
		LOG.info("createGoToTableFlow ");
		short nextTable = (short)(table + 1);
		
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(table).setFlowName("permitPackets").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		InstructionsBuilder isb = createGoToNextTableInstruction(nextTable);

		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(BaseappConstants.UNCONDITIONAL_GOTO_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	
}
