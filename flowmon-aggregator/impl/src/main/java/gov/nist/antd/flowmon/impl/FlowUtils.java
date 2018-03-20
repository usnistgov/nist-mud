/* 
* This is patterend on the Openflow plugin learning switch and the ovsdb project. Copyrights for
* the original projects from which the code was derived are included below.
* 
* 
* Copyright (c) 2014, 2015 Cisco Systems, Inc. and others. All rights reserved.
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

package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpVersion;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PopMplsActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PopVlanActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushMplsActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetVlanIdActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.StripVlanActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.pop.mpls.action._case.PopMplsActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.pop.vlan.action._case.PopVlanActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.mpls.action._case.PushMplsAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.mpls.action._case.PushMplsActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.vlan.action._case.PushVlanActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.field._case.SetFieldBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.vlan.id.action._case.SetVlanIdActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.strip.vlan.action._case.StripVlanAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.strip.vlan.action._case.StripVlanActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanPcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSource;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.ProtocolMatchFieldsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.VlanMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.vlan.match.fields.VlanIdBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowUtils {

	private static final Logger LOG = LoggerFactory.getLogger(FlowUtils.class);

	private static AtomicLong instructionKey = new AtomicLong(0x0);

	private static AtomicLong flowCookieInc = new AtomicLong(0x2a00000000000000L);

	private FlowUtils() {
		// Only static methods in this class
	}

	private synchronized static int getInstructionKey() {
		return (int) instructionKey.incrementAndGet();
	}

	private static int getActionKey() {
		return 0;
	}

	private static MatchBuilder createInPortMatch(MatchBuilder matchBuilder, NodeConnectorId nodeConnectorId) {
		matchBuilder.setInPort(nodeConnectorId);
		return matchBuilder;
	}

	private static MatchBuilder createEthernetSourceAndDestinationMatch(MatchBuilder matchBuilder,
			MacAddress sourceMacAddress, MacAddress destinationMacAddress) {

		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		EthernetSourceBuilder ethernetSourceBuilder = new EthernetSourceBuilder();
		ethernetSourceBuilder.setAddress(sourceMacAddress);
		EthernetSource ethernetSource = ethernetSourceBuilder.build();

		ethernetMatchBuilder.setEthernetSource(ethernetSource);

		EthernetDestinationBuilder ethernetDestinationBuilder = new EthernetDestinationBuilder();
		ethernetDestinationBuilder.setAddress(destinationMacAddress);
		EthernetDestination ethernetDestination = ethernetDestinationBuilder.build();

		ethernetMatchBuilder.setEthernetDestination(ethernetDestination);

		EthernetMatch ethernetMatch = ethernetMatchBuilder.build();
		matchBuilder.setEthernetMatch(ethernetMatch);

		return matchBuilder;
	}

	private static MatchBuilder createEthernetSourceMatch(MatchBuilder matchBuilder, MacAddress macAddress) {
		// Set up the match field.

		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		EthernetSourceBuilder ethernetSourceBuilder = new EthernetSourceBuilder();
		ethernetSourceBuilder.setAddress(macAddress);
		// ethernetSourceBuilder.setMask(new MacAddress("FF:FF:FF:FF:FF:FF"));
		EthernetSource ethernetSource = ethernetSourceBuilder.build();

		ethernetMatchBuilder.setEthernetSource(ethernetSource);

		EthernetTypeBuilder ethernetTypeBuilder = new EthernetTypeBuilder();
		// IPV4
		ethernetTypeBuilder.setType(new EtherType((long) 0x800));
		ethernetMatchBuilder.setEthernetType(ethernetTypeBuilder.build());
		EthernetMatch ethernetMatch = ethernetMatchBuilder.build();
		matchBuilder.setEthernetMatch(ethernetMatch);

		return matchBuilder;

	}

	private static MatchBuilder createEthernetSourceNoEthTypeMatch(MatchBuilder matchBuilder, MacAddress macAddress) {
		// Set up the match field.

		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		EthernetSourceBuilder ethernetSourceBuilder = new EthernetSourceBuilder();
		ethernetSourceBuilder.setAddress(macAddress);
		// ethernetSourceBuilder.setMask(new MacAddress("FF:FF:FF:FF:FF:FF"));
		EthernetSource ethernetSource = ethernetSourceBuilder.build();

		ethernetMatchBuilder.setEthernetSource(ethernetSource);

		EthernetMatch ethernetMatch = ethernetMatchBuilder.build();
		matchBuilder.setEthernetMatch(ethernetMatch);

		return matchBuilder;

	}

	private static MatchBuilder createEthernetSourceAndDestNoEthTypeMatch(MatchBuilder matchBuilder,
			MacAddress sourceMac, MacAddress destMac) {
		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		EthernetSourceBuilder ethernetSourceBuilder = new EthernetSourceBuilder();
		ethernetSourceBuilder.setAddress(sourceMac);
		// ethernetSourceBuilder.setMask(new MacAddress("FF:FF:FF:FF:FF:FF"));
		EthernetSource ethernetSource = ethernetSourceBuilder.build();

		ethernetMatchBuilder.setEthernetSource(ethernetSource);
		EthernetDestinationBuilder ethernetDestinationBuilder = new EthernetDestinationBuilder();
		ethernetDestinationBuilder.setAddress(destMac);

		EthernetDestination ethernetDestination = ethernetDestinationBuilder.build();

		ethernetMatchBuilder.setEthernetDestination(ethernetDestination);
		EthernetMatch ethernetMatch = ethernetMatchBuilder.build();
		matchBuilder.setEthernetMatch(ethernetMatch);

		return matchBuilder;
	}

	private static MatchBuilder createEthernetDestMatch(MatchBuilder matchBuilder, MacAddress macAddress) {

		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		EthernetDestinationBuilder ethernetDestinationBuilder = new EthernetDestinationBuilder();
		ethernetDestinationBuilder.setAddress(macAddress);

		EthernetDestination ethernetDestination = ethernetDestinationBuilder.build();

		ethernetMatchBuilder.setEthernetDestination(ethernetDestination);

		EthernetTypeBuilder ethernetTypeBuilder = new EthernetTypeBuilder();
		// IPV4
		ethernetTypeBuilder.setType(new EtherType((long) 0x0800));
		ethernetMatchBuilder.setEthernetType(ethernetTypeBuilder.build());
		EthernetMatch ethernetMatch = ethernetMatchBuilder.build();
		matchBuilder.setEthernetMatch(ethernetMatch);

		return matchBuilder;
	}

	private static MatchBuilder createEthernetDestNoEthTypeMatch(MatchBuilder matchBuilder, MacAddress macAddress) {

		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		EthernetDestinationBuilder ethernetDestinationBuilder = new EthernetDestinationBuilder();
		ethernetDestinationBuilder.setAddress(macAddress);

		EthernetDestination ethernetDestination = ethernetDestinationBuilder.build();

		ethernetMatchBuilder.setEthernetDestination(ethernetDestination);

		EthernetMatch ethernetMatch = ethernetMatchBuilder.build();
		matchBuilder.setEthernetMatch(ethernetMatch);

		return matchBuilder;
	}

	private static MatchBuilder createMetadataMatch(MatchBuilder matchBuilder, BigInteger metadata) {

		MetadataBuilder metadataBuilder = new MetadataBuilder();
		metadataBuilder.setMetadata(metadata);
		metadataBuilder.setMetadataMask(new BigInteger("FFFFFFFFFFFFFFFF", 16));
		matchBuilder.setMetadata(metadataBuilder.build());
		return matchBuilder;
	}

	private static MatchBuilder createMetadataMatch(MatchBuilder matchBuilder, BigInteger metadata,
			BigInteger metadataMask) {
		MetadataBuilder metadataBuilder = new MetadataBuilder();
		metadataBuilder.setMetadata(metadata);
		metadataBuilder.setMetadataMask(metadataMask);
		matchBuilder.setMetadata(metadataBuilder.build());
		return matchBuilder;

	}

	private static MatchBuilder createMetadataNoVlanMatch(MatchBuilder matchBuilder, BigInteger metadata) {

		MetadataBuilder metadataBuilder = new MetadataBuilder();
		metadataBuilder.setMetadata(metadata);
		// metadataBuilder.setMetadataMask(BigInteger.valueOf(0xffff));
		VlanMatchBuilder vlanMatchBuilder = new VlanMatchBuilder();
		VlanIdBuilder vlanIdBuilder = new VlanIdBuilder();
		vlanIdBuilder.setVlanIdPresent(false);
		vlanMatchBuilder.setVlanId(vlanIdBuilder.build());

		matchBuilder.setVlanMatch(vlanMatchBuilder.build());
		matchBuilder.setMetadata(metadataBuilder.build());
		return matchBuilder;
	}

	private static MatchBuilder createEthernetTypeMatch(MatchBuilder matchBuilder, int ethernetType) {
		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
				.setEthernetType(new EthernetTypeBuilder().setType(new EtherType(Long.valueOf(ethernetType))).build());
		matchBuilder.setEthernetMatch(ethernetMatchBuilder.build()).build();
		return matchBuilder;
	}

	private static MatchBuilder createArpTypeMatch(MatchBuilder matchBuilder) {
		return createEthernetTypeMatch(matchBuilder, 0x806);
	}

	/**
	 * Match on a vlan label.
	 * 
	 * @param matchBuilder
	 *            -- the match builder to use.
	 * 
	 * @param vlanLabel
	 *            -- the vlan label.
	 */

	private static MatchBuilder createVlanMatch(MatchBuilder matchBuilder, int vlanLabel) {
		VlanMatchBuilder vlanMatchBuilder = new VlanMatchBuilder();
		VlanIdBuilder vlanIdBuilder = new VlanIdBuilder();
		vlanIdBuilder.setVlanId(new VlanId(vlanLabel));
		vlanIdBuilder.setVlanIdPresent(true);
		vlanMatchBuilder.setVlanId(vlanIdBuilder.build());
		matchBuilder.setVlanMatch(vlanMatchBuilder.build());
		return matchBuilder;
	}

	/**
	 * Match packet if no vlan ID is present.
	 * 
	 * @param matchBuilder
	 * @return
	 */
	private static MatchBuilder createNoVlanPresentMatch(MatchBuilder matchBuilder) {
		VlanMatchBuilder vlanMatchBuilder = new VlanMatchBuilder();
		VlanIdBuilder vlanIdBuilder = new VlanIdBuilder();
		vlanIdBuilder.setVlanIdPresent(false);
		vlanMatchBuilder.setVlanId(vlanIdBuilder.build());

		matchBuilder.setVlanMatch(vlanMatchBuilder.build());
		return matchBuilder;
	}

	/**
	 * Create an MPLS match.
	 * 
	 * @param matchBuilder
	 *            -- the input match builder
	 * @param mplsLabel
	 *            -- the mpls label to match
	 * @return
	 */
	private static MatchBuilder createMplsMatch(MatchBuilder matchBuilder, long mplsLabel) {
		ProtocolMatchFieldsBuilder pmfBuilder = new ProtocolMatchFieldsBuilder();
		pmfBuilder.setMplsLabel(mplsLabel);
		matchBuilder.setProtocolMatchFields(pmfBuilder.build());
		EthernetMatchBuilder emb = new EthernetMatchBuilder();
		EthernetTypeBuilder etb = new EthernetTypeBuilder();
		EtherType etherType = new EtherType(34887L);
		etb.setType(etherType);
		emb.setEthernetType(etb.build());
		matchBuilder.setEthernetMatch(emb.build());
		return matchBuilder;
	}

	/**
	 * Create ipv4 prefix from ipv4 address, by appending /32 mask
	 * 
	 * @param ipv4AddressString
	 *            the ip address, in string format
	 * @return Ipv4Prefix with ipv4Address and /32 mask
	 */
	private static Ipv4Prefix iPv4PrefixFromIPv4Address(String ipv4AddressString) {
		return new Ipv4Prefix(ipv4AddressString + "/32");
	}

	private static MatchBuilder createDestIpv4Match(MatchBuilder matchBuilder, Ipv4Address ipv4Address) {
		Ipv4MatchBuilder imb = new Ipv4MatchBuilder();
		Ipv4Prefix ip4Prefix = iPv4PrefixFromIPv4Address(ipv4Address.getValue());
		imb.setIpv4Destination(ip4Prefix);
		matchBuilder.setLayer3Match(imb.build());
		return matchBuilder;
	}

	private static MatchBuilder createSrcIpv4Match(MatchBuilder matchBuilder, Ipv4Address ipv4Address) {
		Ipv4MatchBuilder imb = new Ipv4MatchBuilder();
		Ipv4Prefix ip4Prefix = iPv4PrefixFromIPv4Address(ipv4Address.getValue());
		imb.setIpv4Source(ip4Prefix);
		matchBuilder.setLayer3Match(imb.build());
		return matchBuilder;
	}

	/**
	 * Create Drop Instruction
	 *
	 */

	private static Instruction createDropInstruction() {
		InstructionBuilder ib = new InstructionBuilder();
		ib.setOrder(0);
		ib.setKey(new InstructionKey(getInstructionKey()));

		DropActionBuilder dab = new DropActionBuilder();
		DropAction dropAction = dab.build();
		ActionBuilder ab = new ActionBuilder();
		ab.setAction(new DropActionCaseBuilder().setDropAction(dropAction).build());
		ab.setOrder(0);
		ab.setKey(new ActionKey(getActionKey()));

		// Add our drop action to a list
		List<Action> actionList = new ArrayList<>();
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());

		return ib.build();
	}

	/**
	 * Goto table and set the metadata.
	 * 
	 * @param targetTable
	 *            -- the IDS table
	 * @param metadata
	 *            -- the metadata tag.
	 * @return -- the instructions builder.
	 */
	private static InstructionsBuilder createGoToNextTableInstruction(short targetTable, BigInteger metadata) {
		List<Instruction> instructions = new ArrayList<Instruction>();

		Instruction gotoFlowmonTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTable).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(0).build();

		instructions.add(gotoFlowmonTableInstruction);

		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(new BigInteger("FFFFFFFFFFFFFFFF", 16));
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());
		Instruction maskInstruction = new InstructionBuilder().setOrder(1)
				.setKey(new InstructionKey(getInstructionKey())).setInstruction(wmcb.build()).build();

		instructions.add(maskInstruction);

		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);
		return isb;
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

	private static Instruction createStripVlanInstructions(int actionKey, int instructionKey) {

		InstructionBuilder ib = new InstructionBuilder();

		StripVlanActionBuilder stripVlanActionBuilder = new StripVlanActionBuilder();
		StripVlanAction vlanAction = stripVlanActionBuilder.build();
		ActionBuilder ab = new ActionBuilder().setKey(new ActionKey(actionKey)).setOrder(0);
		ab.setAction(new StripVlanActionCaseBuilder().setStripVlanAction(vlanAction).build());

		// Add our drop action to a list
		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());

		return ib.build();
	}

	private static Instruction createPopVlanActionInstruction(int actionKey, int instructionKey) {

		Action popVlanAction = new ActionBuilder()
				.setAction(new PopVlanActionCaseBuilder().setPopVlanAction(new PopVlanActionBuilder().build()).build())
				.setOrder(0).setKey(new ActionKey(actionKey)).build();

		List<Action> listAction = new ArrayList<>();
		listAction.add(popVlanAction);

		ApplyActions applyActions = new ApplyActionsBuilder().setAction(listAction).build();
		ApplyActionsCase applyActionsCase = new ApplyActionsCaseBuilder().setApplyActions(applyActions).build();

		// WriteActions writeActions = new
		// WriteActionsBuilder().setAction(listAction).build();
		// WriteActionsCase writeActionsCase = new
		// WriteActionsCaseBuilder().setWriteActions(writeActions).build();

		InstructionBuilder instructionBuilder = new InstructionBuilder();

		instructionBuilder.setInstruction(applyActionsCase);
		instructionBuilder.setOrder(0);
		instructionBuilder.setKey(new InstructionKey(instructionKey));
		return instructionBuilder.build();
	}

	private static Instruction createPopMplsActionInstruction(int actionKey, int instructionKey) {
		// Integer mplsEthertype = 2048;
		int mplsEtherType = 2048;
		Action popMplsAction = new ActionBuilder().setOrder(0).setKey(new ActionKey(instructionKey))
				.setAction(new PopMplsActionCaseBuilder()
						.setPopMplsAction(new PopMplsActionBuilder().setEthernetType(mplsEtherType).build()).build())
				.build();
		List<Action> listAction = new ArrayList<>();
		listAction.add(popMplsAction);
		ApplyActions applyActions = new ApplyActionsBuilder().setAction(listAction).build();
		ApplyActionsCase applyActionsCase = new ApplyActionsCaseBuilder().setApplyActions(applyActions).build();
		InstructionBuilder instructionBuilder = new InstructionBuilder();

		instructionBuilder.setInstruction(applyActionsCase);
		instructionBuilder.setKey(new InstructionKey(instructionKey));
		return instructionBuilder.build();
	}

	private static Instruction createPopMplsAndOutputToPortInstruction(int actionKey, int instructionKey,
			String outputPortUri) {

		LOG.info("createPopMplsAndOutputToPortInstruction " + outputPortUri);

		int order = 1;
		List<Action> listAction = new ArrayList<Action>();

		ActionBuilder ab = new ActionBuilder();
		// Integer mplsEthertype = 34887;
		Integer mplsEthertype = 2048;

		Action popMplsAction = ab.setOrder(order).setKey(new ActionKey(order))
				.setAction(new PopMplsActionCaseBuilder()
						.setPopMplsAction(new PopMplsActionBuilder().setEthernetType(mplsEthertype).build()).build())
				.build();
		order++;
		listAction.add(popMplsAction);

		OutputActionBuilder output = new OutputActionBuilder();

		output.setMaxLength(Integer.valueOf(0xffff));

		LOG.info("outputPort " + outputPortUri);
		output.setOutputNodeConnector(new Uri(outputPortUri));
		ab.setKey(new ActionKey(order));
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(order);
		order++;
		listAction.add(ab.build());

		ApplyActions applyActions = new ApplyActionsBuilder().setAction(listAction).build();
		ApplyActionsCase applyActionsCase = new ApplyActionsCaseBuilder().setApplyActions(applyActions).build();
		InstructionBuilder instructionBuilder = new InstructionBuilder();

		instructionBuilder.setInstruction(applyActionsCase);
		instructionBuilder.setOrder(0);
		instructionBuilder.setKey(new InstructionKey(0));
		return instructionBuilder.build();

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

	private static MatchBuilder createDstTcpPortMatch(MatchBuilder matchBuilder, int tcpport) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProto(IpVersion.Ipv4);
		ipmatch.setIpProtocol(SdnMudConstants.TCP_PROTOCOL);
		matchBuilder.setIpMatch(ipmatch.build());
		PortNumber tcpDstPort = new PortNumber(tcpport);
		TcpMatchBuilder tcpmatch = new TcpMatchBuilder();
		tcpmatch.setTcpDestinationPort(tcpDstPort);
		matchBuilder.setLayer4Match(tcpmatch.build());
		return matchBuilder;
	}

	private static MatchBuilder createDstUdpPortMatch(MatchBuilder matchBuilder, int udpPort) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProtocol(SdnMudConstants.UDP_PROTOCOL);
		ipmatch.setIpProto(IpVersion.Ipv4);
		matchBuilder.setIpMatch(ipmatch.build());
		PortNumber udpDstPort = new PortNumber(udpPort);
		UdpMatchBuilder udpmatch = new UdpMatchBuilder();
		udpmatch.setUdpDestinationPort(udpDstPort);
		matchBuilder.setLayer4Match(udpmatch.build());
		return matchBuilder;
	}

	private static MatchBuilder createSrcTcpPortMatch(MatchBuilder matchBuilder, int tcpport) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProto(IpVersion.Ipv4);
		ipmatch.setIpProtocol(SdnMudConstants.TCP_PROTOCOL);
		matchBuilder.setIpMatch(ipmatch.build());
		PortNumber portNumber = new PortNumber(tcpport);
		TcpMatchBuilder tcpmatch = new TcpMatchBuilder();
		tcpmatch.setTcpSourcePort(portNumber);
		matchBuilder.setLayer4Match(tcpmatch.build());
		return matchBuilder;

	}

	private static MatchBuilder createSrcUdpPortMatch(MatchBuilder matchBuilder, int udpPort) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProtocol(SdnMudConstants.UDP_PROTOCOL);
		ipmatch.setIpProto(IpVersion.Ipv4);
		matchBuilder.setIpMatch(ipmatch.build());
		PortNumber portNumber = new PortNumber(udpPort);
		UdpMatchBuilder udpmatch = new UdpMatchBuilder();
		udpmatch.setUdpSourcePort(portNumber);
		matchBuilder.setLayer4Match(udpmatch.build());
		return matchBuilder;
	}

	private static MatchBuilder createIpV4Match(MatchBuilder matchBuilder) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProto(IpVersion.Ipv4);

		matchBuilder.setIpMatch(ipmatch.build());
		return matchBuilder;
	}

	private static MatchBuilder createUdpPortMatch(MatchBuilder matchBuilder, int udpSrcPort, int udpDstPort) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProtocol(SdnMudConstants.UDP_PROTOCOL);
		ipmatch.setIpProto(IpVersion.Ipv4);
		matchBuilder.setIpMatch(ipmatch.build());
		PortNumber portNumber = new PortNumber(udpSrcPort);
		UdpMatchBuilder udpmatch = new UdpMatchBuilder();
		udpmatch.setUdpSourcePort(portNumber);
		udpmatch.setUdpDestinationPort(new PortNumber(udpDstPort));
		matchBuilder.setLayer4Match(udpmatch.build());
		return matchBuilder;
	}

	private static MatchBuilder createUdpProtocolMatch(MatchBuilder matchBuilder) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProtocol(SdnMudConstants.UDP_PROTOCOL);
		ipmatch.setIpProto(IpVersion.Ipv4);
		matchBuilder.setIpMatch(ipmatch.build());
		return matchBuilder;
	}

	private static MatchBuilder createTcpProtocolMatch(MatchBuilder matchBuilder) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProtocol(SdnMudConstants.TCP_PROTOCOL);
		ipmatch.setIpProto(IpVersion.Ipv4);
		matchBuilder.setIpMatch(ipmatch.build());
		return matchBuilder;
	}

	private static Instruction createSendPacketToControllerInstruction() {
		// Create output action -> send to controller
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));
		Uri controllerPort = new Uri(OutputPortValues.CONTROLLER.toString());
		output.setOutputNodeConnector(controllerPort);

		ActionBuilder ab = new ActionBuilder();
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(0);
		ab.setKey(new ActionKey(getActionKey()));

		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		InstructionBuilder ib = new InstructionBuilder();
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		ib.setOrder(0);
		ib.setKey(new InstructionKey(getInstructionKey()));
		return ib.build();

	}

	private static Instruction createSendToPortInstruction(String outputPortUri) {
		// Create output action -> send to controller
		LOG.info("createSendToPortInstruction : outputPortUri = " + outputPortUri);
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));

		Uri controllerPort = new Uri(outputPortUri);
		output.setOutputNodeConnector(controllerPort);

		ActionBuilder ab = new ActionBuilder();
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(1);

		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		InstructionBuilder ib = new InstructionBuilder();
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		ib.setOrder(0);
		ib.setKey(new InstructionKey(getInstructionKey()));
		return ib.build();

	}

	private static Instruction createStripVlanTagInstruction1(InstructionBuilder ib, int order) {

		StripVlanActionCaseBuilder stripCaseBuilder = new StripVlanActionCaseBuilder();
		StripVlanActionBuilder stripBuilder = new StripVlanActionBuilder();
		stripCaseBuilder.setStripVlanAction(stripBuilder.build());

		ActionBuilder ab = new ActionBuilder().setAction(stripCaseBuilder.build());
		ab.setOrder(order);
		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());

		WriteActionsBuilder wab = new WriteActionsBuilder();

		wab.setAction(actionList);

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();

		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		ib.setInstruction(new WriteActionsCaseBuilder().setWriteActions(wab.build()).build());

		ib.setOrder(order);
		return ib.build();
	}

	private static Instruction createVlanTagPacketAndSendToOutputPortInstructions(int vlanId, String outputPortUri) {

		SetVlanIdActionBuilder tab = new SetVlanIdActionBuilder();
		tab.setVlanId(new VlanId(vlanId));

		SetVlanIdActionCaseBuilder vidcb = new SetVlanIdActionCaseBuilder();

		ActionBuilder ab = new ActionBuilder().setAction(vidcb.setSetVlanIdAction(tab.build()).build());

		ab.setOrder(0);
		ab.setKey(new ActionKey(getActionKey()));

		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());

		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));

		Uri controllerPort = new Uri(outputPortUri);
		output.setOutputNodeConnector(controllerPort);

		ab = new ActionBuilder();
		// ab.setKey(new ActionKey(3));
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(1);
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();

		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		InstructionBuilder ib = new InstructionBuilder();
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		ib.setOrder(0);
		ib.setKey(new InstructionKey(getInstructionKey()));
		return ib.build();
	}

	private static Instruction createPushMplsAndOutputToPortInstructions(long mplsTag, String outputPortUri) {
		List<Action> actionList = new ArrayList<Action>();
		ActionBuilder ab = new ActionBuilder();

		// Create the push MPLS tag action.
		Integer mplsEthertype = 34887;
		PushMplsAction pmplsAction = new PushMplsActionBuilder().setEthernetType(mplsEthertype).build();
		PushMplsActionCaseBuilder pushMplsCaseBuilder = new PushMplsActionCaseBuilder();
		pushMplsCaseBuilder.setPushMplsAction(pmplsAction);
		ab.setAction(pushMplsCaseBuilder.build());
		ab.setOrder(0);
		actionList.add(ab.build());

		// Create set MPLS action
		ProtocolMatchFieldsBuilder pmfb = new ProtocolMatchFieldsBuilder();
		pmfb.setMplsLabel(mplsTag);
		SetFieldBuilder sfb = new SetFieldBuilder();
		sfb.setProtocolMatchFields(pmfb.build());
		ab.setOrder(1).setAction(new SetFieldCaseBuilder().setSetField(sfb.build()).build());
		actionList.add(ab.build());

		// Create the output to port action.
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));
		Uri controllerPort = new Uri(outputPortUri);
		output.setOutputNodeConnector(controllerPort);
		// ab.setKey(new ActionKey(3));
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(2);
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		InstructionBuilder ib = new InstructionBuilder();
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		ib.setOrder(0);
		ib.setKey(new InstructionKey(getInstructionKey()));
		return ib.build();

	}

	private static Instruction createPushMplsTagInstruction(long mplsTag) {
		List<Action> actionList = new ArrayList<Action>();
		ActionBuilder ab = new ActionBuilder();

		// Create the push MPLS tag action.
		Integer mplsEthertype = 34887;
		PushMplsAction pmplsAction = new PushMplsActionBuilder().setEthernetType(mplsEthertype).build();
		PushMplsActionCaseBuilder pushMplsCaseBuilder = new PushMplsActionCaseBuilder();
		pushMplsCaseBuilder.setPushMplsAction(pmplsAction);
		ab.setAction(pushMplsCaseBuilder.build());
		ab.setOrder(0);
		actionList.add(ab.build());

		// Create set MPLS action
		ProtocolMatchFieldsBuilder pmfb = new ProtocolMatchFieldsBuilder();
		pmfb.setMplsLabel(mplsTag);
		SetFieldBuilder sfb = new SetFieldBuilder();
		sfb.setProtocolMatchFields(pmfb.build());
		ab.setOrder(1).setAction(new SetFieldCaseBuilder().setSetField(sfb.build()).build());
		actionList.add(ab.build());

		// Create an Apply Action
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		// Wrap our Apply Action in an Instruction
		InstructionBuilder ib = new InstructionBuilder();
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		ib.setOrder(0);
		ib.setKey(new InstructionKey(getInstructionKey()));
		return ib.build();
	}

	/**
	 * Create a set vlan ID instruction. This pushes the first vlan tag.
	 * 
	 */
	private static Instruction createSetVlanInstructions(int vlanId) {
		List<Action> actionList = new ArrayList<>();

		Integer VLAN_ETHERTYPE = 0x8100;
		ActionBuilder actionBuilder = new ActionBuilder();

		// push vlan
		Action pushVlanAction = actionBuilder.setOrder(0)
				.setAction(new PushVlanActionCaseBuilder()
						.setPushVlanAction(
								new PushVlanActionBuilder().setTag(vlanId).setEthernetType(VLAN_ETHERTYPE).build())
						.build())
				.build();
		actionList.add(pushVlanAction);

		// set vlan id
		Action setVlanIdAction = actionBuilder.setOrder(1)
				.setAction(
						new SetFieldCaseBuilder()
								.setSetField(new SetFieldBuilder()
										.setVlanMatch(new VlanMatchBuilder().setVlanId(new VlanIdBuilder()
												.setVlanId(new VlanId(vlanId)).setVlanIdPresent(true).build()).build())
										.build())
								.build())
				.build();

		actionList.add(setVlanIdAction);
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		InstructionBuilder ib = new InstructionBuilder();
		ib.setOrder(0);

		ib.setKey(new InstructionKey(0));
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());

		return ib.build();
	}

	/**
	 * Create Set Vlan ID Instruction - This includes push vlan action, and set
	 * field -&gt; vlan vid action
	 *
	 * @param ib
	 *            Map InstructionBuilder without any instructions
	 * @param vlanId
	 *            Integer representing a VLAN ID Integer representing a VLAN ID
	 * @return ib Map InstructionBuilder with instructions
	 */
	private static Instruction createSetVlanAndOutputToPortInstructions(int vlanId, String outputPortUri) {

		List<Action> actionList = new ArrayList<>();
		ActionBuilder ab = new ActionBuilder();

		Integer VLAN_ETHERTYPE = 0x8100;
		ActionBuilder actionBuilder = new ActionBuilder();

		// push vlan
		Action pushVlanAction = actionBuilder.setOrder(0)
				.setAction(new PushVlanActionCaseBuilder().setPushVlanAction(new PushVlanActionBuilder()
						// .setTag(vlanId)
						.setEthernetType(VLAN_ETHERTYPE).build()).build())
				.build();
		actionList.add(pushVlanAction);

		// set vlan id

		Action setVlanIdAction = actionBuilder.setOrder(1)
				.setAction(
						new SetFieldCaseBuilder()
								.setSetField(new SetFieldBuilder()
										.setVlanMatch(new VlanMatchBuilder().setVlanId(new VlanIdBuilder()
												.setVlanId(new VlanId(vlanId)).setVlanIdPresent(true).build()).build())
										.build())
								.build())
				.build();

		actionList.add(setVlanIdAction);

		// Output to Port.
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));

		Uri controllerPort = new Uri(outputPortUri);
		output.setOutputNodeConnector(controllerPort);

		ab.setOrder(2);
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		actionList.add(ab.build());

		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);

		InstructionBuilder ib = new InstructionBuilder();
		ib.setOrder(0);

		ib.setKey(new InstructionKey(0));
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());

		return ib.build();
	}

	private static Instruction createSendToPortsAction(String[] ports) {
		int order = 1;
		OutputActionBuilder oab = new OutputActionBuilder();
		oab.setMaxLength(Integer.valueOf(60));
		ActionBuilder ab = new ActionBuilder();
		List<Action> actionList = new ArrayList<Action>();

		for (String port : ports) {
			ab.setOrder(order);
			ab.setKey(new ActionKey(0));
			LOG.info("createSendToPort " + port.substring(port.lastIndexOf(":"), port.length()));
			oab.setOutputNodeConnector(new Uri(port.substring(port.lastIndexOf(":"), port.length())));
			ab.setAction(new OutputActionCaseBuilder().setOutputAction(oab.build()).build());
			actionList.add(ab.build());
			order++;
		}
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);
		InstructionBuilder ib = new InstructionBuilder();
		ib.setOrder(0);
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		return ib.build();

	}
	

	private static Instruction createSendToPortsAction(List<Integer> ports) {
		int order = 0;
		OutputActionBuilder oab = new OutputActionBuilder();
		oab.setMaxLength(Integer.valueOf(0xffff));
		ActionBuilder ab = new ActionBuilder();
		List<Action> actionList = new ArrayList<Action>();

		for (int port : ports) {
			ab.setOrder(order);
			ab.setKey(new ActionKey(order));
			oab.setOutputNodeConnector(new Uri(String.valueOf(port)));
			ab.setAction(new OutputActionCaseBuilder().setOutputAction(oab.build()).build());
			actionList.add(ab.build());
			order++;
		}
		ApplyActionsBuilder aab = new ApplyActionsBuilder();
		aab.setAction(actionList);
		InstructionBuilder ib = new InstructionBuilder();
		ib.setOrder(0);
		ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
		return ib.build();

	}

	static FlowBuilder createMetadataMatchSetMplsTagSendToPortAndGoToTable(FlowCookie flowCookie, FlowId flowId,
			Short tableId, int label, String outputPortUri, int time) {
		LOG.info("FlowUtils: createMetadataMatchSetMplsTagSendToPortAndGoToTable " + flowCookie.getValue().toString(16)
				+ " outputPortUri " + outputPortUri + " time " + time + " tableId " + tableId);

		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, flowCookie.getValue());

		InstructionBuilder ib = new InstructionBuilder();
		Instruction createMplsTag = FlowUtils.createPushMplsAndOutputToPortInstructions(label, outputPortUri);

		InstructionsBuilder insb = new InstructionsBuilder();

		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(createMplsTag);
		short targetTableId = (short) (tableId + 1);
		Instruction gotoInstruction = ib.setOrder(3)
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(0)).build();
		instructions.add(gotoInstruction);

		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId)
				.setFlowName("metadataMatchSetMplsTagSendToPortAndGotoL2Switch").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(time).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;

	}

	public static FlowBuilder createMetadataMatchMplsTagGoToTableFlow(FlowCookie flowCookie, FlowId flowId,
			Short tableId, int mplsTag, int duration) {

		short targetTable = (short) (tableId + 1);

		LOG.info("FlowUtils: createMetadataMatchMplsTagGoToTable " + flowCookie.getValue().toString(16) + " tableId "
				+ tableId + " targetTable " + targetTable + " mplsTag " + Integer.toHexString(mplsTag));

		ArrayList<Instruction> instructions = new ArrayList<Instruction>();

		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, flowCookie.getValue());
		Instruction createMplsTag = FlowUtils.createPushMplsTagInstruction(mplsTag);

		instructions.add(createMplsTag);

		Instruction goToTableInstruction = new InstructionBuilder().setInstruction(new GoToTableCaseBuilder()
				.setGoToTable(new GoToTableBuilder().setTableId(SdnMudConstants.L2SWITCH_TABLE).build()).build())
				.setKey(new InstructionKey(2)).setOrder(2).build();

		instructions.add(goToTableInstruction);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataMatchMplsTagGoToTable").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createPushMplsTagGoToTableFlow(FlowCookie flowCookie, FlowId flowId, Short tableId,
			Short targetTable, int mplsTag, int duration) {
		LOG.info("FlowUtils: createPushMplsTagGoToTable " + flowCookie.getValue().toString(16) + " tableId " + tableId
				+ " targetTable " + targetTable + " mplsTag " + Integer.toHexString(mplsTag));

		ArrayList<Instruction> instructions = new ArrayList<Instruction>();

		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createIpV4Match(matchBuilder);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x800);

		Instruction createMplsTag = FlowUtils.createPushMplsTagInstruction(mplsTag);

		instructions.add(createMplsTag);

		Instruction goToTableInstruction = new InstructionBuilder().setInstruction(new GoToTableCaseBuilder()
				.setGoToTable(new GoToTableBuilder().setTableId(SdnMudConstants.L2SWITCH_TABLE).build()).build())
				.setKey(new InstructionKey(2)).setOrder(2).build();

		instructions.add(goToTableInstruction);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataMatchMplsTagGoToTable").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMetadataMatchGoToTableFlow(FlowCookie flowCookie, BigInteger metadata,
			BigInteger metadataMask, FlowId flowId, Short tableId, Short targetTable, int duration) {
		LOG.info("FlowUtils: createMetadataMatchGoToTable " + flowCookie.getValue().toString(16) + " metadata "
				+ metadata.toString(16) + " metadataMask " + metadataMask.toString(16) + " tableId " + tableId
				+ " targetTable " + targetTable);

		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTable, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataMatchGoToTable").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMplsMatchPopMplsLabelAndGoToTable(FlowCookie flowCookie, FlowId flowId,
			short tableId, int label) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createMplsMatch(matchBuilder, label);

		Instruction popMplsTagInstruction = createPopMplsActionInstruction(0, 0);

		InstructionsBuilder insb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(popMplsTagInstruction);
		short gototableId = (short) (tableId + 1);
		Instruction goToTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(gototableId).build()).build())
				.setKey(new InstructionKey(1)).setOrder(1).build();

		instructions.add(goToTableInstruction);
		insb.setInstruction(instructions);

		FlowBuilder stripLabelFlow = new FlowBuilder();
		stripLabelFlow.setStrict(false);
		stripLabelFlow.setBarrier(true);

		stripLabelFlow.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("mplsMatchPopMplsTagGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie)
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setInstructions(insb.build())
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return stripLabelFlow;
	}

	public static FlowBuilder createOutputToPortsFlow(FlowCookie flowCookie, FlowId flowId, short tableId,
			String[] ports) {
		MatchBuilder matchBuilder = new MatchBuilder();

		Instruction outputInstruction = FlowUtils.createSendToPortsAction(ports);
		FlowBuilder flowBuilder = new FlowBuilder().setStrict(false).setBarrier(true);
		InstructionsBuilder insb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(outputInstruction);
		flowBuilder.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("outputToPortsFlow").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie)
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setInstructions(insb.build())
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	static FlowBuilder createOnMplsTagMatchPopMplsTagsAndSendToPort(FlowCookie flowCookie, FlowId flowId, int label,
			String nodeId, String outputPorts, short tableId, int duration) {
		LOG.info("createOnMplsTagMatchPopMplsTagsAndSendToPort " + Integer.toHexString(label));

		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createMplsMatch(matchBuilder, label);

		Instruction instruction = FlowUtils.createPopMplsAndOutputToPortInstruction(0, 0, outputPorts);
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		// fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("mplsMatchPopMplsOutputToPort").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie)
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setInstructions(insb.build())
				.setInstallHw(false).setBufferId(OFConstants.ANY).setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createOnMplsTagMatchPopMpsTagsSendToPortAndGoToTable(FlowCookie flowCookie, FlowId flowId,
			int label, String portSpec, Short tableId, int duration) {
		LOG.info("createOnMplsTagMatchPopMplsTagsOutputToPortsAndGoToTable " + Integer.toHexString(label));

		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createMplsMatch(matchBuilder, label);
		Instruction instruction = FlowUtils.createPopMplsAndOutputToPortInstruction(0, 0, portSpec);
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);

		short gotoTableId = (short) (tableId + 1);

		Instruction goToTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(gotoTableId).build()).build())
				.setKey(new InstructionKey(1)).setOrder(1).build();

		instructions.add(goToTableInstruction);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setMatch(matchBuilder.build()).setTableId((short) tableId)
				.setFlowName("onMplsTagMatchPopMpsTagsSendToPortAndGoToTable").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie).setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY)
				.setInstructions(insb.build()).setInstallHw(false).setBufferId(OFConstants.ANY).setHardTimeout(duration)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createDestMacAddressMatchSetMplsTagAndSendToPort(FlowCookie flowCookie,
			MacAddress destinationMacAddress, Short tableId, long mplsTag, String outputPortUri, int time) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId();
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetDestMatch(matchBuilder, destinationMacAddress);
		Instruction sendToPort = FlowUtils.createPushMplsAndOutputToPortInstructions(mplsTag, outputPortUri);
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(sendToPort);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("OnDestinationMacAddressMatchSendToPort").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(time).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createDestMacAddressMatchSetVlanTagAndSendToPort(FlowCookie flowCookie, FlowId flowId,
			MacAddress destinationMacAddress, Short tableId, int vlanTag, String outputPortUri, int time) {

		LOG.debug(
				"destinationMacAddressMatchSetVlanTagAndSendToPort vlanTag = " + vlanTag + " port = " + outputPortUri);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetDestNoEthTypeMatch(matchBuilder, destinationMacAddress);
		Instruction instruction = FlowUtils.createSetVlanAndOutputToPortInstructions(vlanTag, outputPortUri);
		ArrayList<Instruction> instructions = new ArrayList<>();
		instructions.add(instruction);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("destinationMacMatchSetVlanSendToPort").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(time)
				.setIdleTimeout(2 * time).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createSrcDestMacAddressMatchSetVlanTagAndSendToPort(FlowCookie flowCookie, FlowId flowId,
			MacAddress sourceMacAddress, MacAddress destinationMacAddress, Short tableId, int vlanTag,
			String outputPortUri, int time) {

		LOG.info("srcDestMacAddressMatchSetVlanTagAndSendToPort sourceMacAddress = " + sourceMacAddress.getValue()
				+ " destMacAddress " + destinationMacAddress.getValue() + " vlanTag = " + vlanTag + " port = "
				+ outputPortUri);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceAndDestinationMatch(matchBuilder, sourceMacAddress, destinationMacAddress);
		Instruction instruction = FlowUtils.createSetVlanAndOutputToPortInstructions(vlanTag, outputPortUri);
		ArrayList<Instruction> instructions = new ArrayList<>();
		instructions.add(instruction);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("srcDestMacMatchSetVlanSendToPort")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(time)
				.setIdleTimeout(2 * time).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createSrcDestMacAddressVlanPortMatchGoToTable(MacAddress sourceMacAddress,
			MacAddress destinationMacAddress, int vlanTag, NodeConnectorId port, Short tableId, FlowId flowId,
			FlowCookie flowCookie, int time) {

		LOG.info("srcDestMacAddressMatchStripVlanTagAndGoToPort sourceMacAddress = " + sourceMacAddress.getValue()
				+ " destMacAddress " + destinationMacAddress.getValue() + " vlanTag = " + vlanTag);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceAndDestinationMatch(matchBuilder, sourceMacAddress, destinationMacAddress);
		FlowUtils.createVlanMatch(matchBuilder, vlanTag);
		FlowUtils.createInPortMatch(matchBuilder, port);
		short targetTable = (short) (tableId + 1);
		Instruction gotoTableInstruction = FlowUtils.createGoToTableInstruction(targetTable);
		ArrayList<Instruction> instructions = new ArrayList<>();
		instructions.add(gotoTableInstruction);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("srcDestMacVlanMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(time)
				.setIdleTimeout(2 * time).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createVlanMatchStripVlanTagAndGoToTable(FlowCookie flowCookie, FlowId flowId,
			short tableId, short targetTableId, int label) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createVlanMatch(matchBuilder, label);

		FlowBuilder stripTagFlow = new FlowBuilder().setTableId(tableId)
				.setFlowName("vlanMatchPopVlanTagGo" + targetTableId).setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		Instruction stripVlanTagInstruction = FlowUtils.createStripVlanInstructions(0, 0);
		Instruction goToTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(1).build();

		InstructionsBuilder insb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(stripVlanTagInstruction);
		instructions.add(goToTableInstruction);
		insb.setInstruction(instructions);

		stripTagFlow.setMatch(matchBuilder.build()).setInstructions(insb.build()).setId(flowId)
				.setKey(new FlowKey(flowId)).setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY)
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return stripTagFlow;
	}

	public static FlowBuilder createVlanMatchPopVlanTagAndGoToTable(FlowCookie flowCookie, FlowId flowId, short tableId,
			int label) {

		MatchBuilder matchBuilder = new MatchBuilder();
		createVlanMatch(matchBuilder, label);

		FlowBuilder stripTagFlow = new FlowBuilder().setTableId(tableId).setFlowName("vlanMatchPopVlanTagGo")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		Instruction popVlanTagInstruction = createPopVlanActionInstruction(0, 0);
		short targetTableId = (short) (tableId + 1);
		Instruction goToTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(1).build();

		InstructionsBuilder insb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(popVlanTagInstruction);
		instructions.add(goToTableInstruction);
		insb.setInstruction(instructions);

		stripTagFlow.setMatch(matchBuilder.build()).setInstructions(insb.build()).setId(flowId)
				.setKey(new FlowKey(flowId)).setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY)
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return stripTagFlow;

	}

	public static FlowBuilder createSetVlanTagAndGoToTable(FlowCookie flowCookie, FlowId flowId, short tableId,
			short targetTableId, int vlanId) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createNoVlanPresentMatch(matchBuilder);
		Instruction setVlanInstruction = FlowUtils.createSetVlanInstructions(vlanId);
		Instruction goToTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(1).build();
		InstructionsBuilder insb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(setVlanInstruction);
		instructions.add(goToTableInstruction);
		insb.setInstruction(instructions);
		FlowBuilder setTagFlow = new FlowBuilder().setTableId(tableId).setFlowName("setVlanTagGoToTable").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);
		setTagFlow.setMatch(matchBuilder.build()).setInstructions(insb.build()).setId(flowId)
				.setKey(new FlowKey(flowId)).setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY)
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return setTagFlow;

	}

	/**
	 * Unconditionally set vlan tag and send to port.
	 * 
	 */
	public static FlowBuilder createSetVlanTagAndSendToPort(FlowCookie flowCookie, FlowId flowId,
			MacAddress destinationMacAddress, Short tableId, int vlanTag, String outputPortUri, int time) {
		MatchBuilder matchBuilder = new MatchBuilder();
		LOG.info("createSetVlanTagAndSendToPort " + vlanTag);
		Instruction instruction = FlowUtils.createSetVlanAndOutputToPortInstructions(vlanTag, outputPortUri);
		ArrayList<Instruction> instructions = new ArrayList<>();
		instructions.add(instruction);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("setVlanTagAndSendToPort")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(time).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createDestMacAddressMatchSendToPort(FlowCookie flowCookie, FlowId flowId,
			MacAddress destinationMacAddress, Short tableId, String outputPortUri, int time) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetDestNoEthTypeMatch(matchBuilder, destinationMacAddress);
		Instruction sendToPort = createSendToPortInstruction(outputPortUri);
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(sendToPort);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("OnDestinationMacAddressMatchSendToPort").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(time).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createFwdAllToControllerFlow(final Short tableId) {
		FlowId flowId = InstanceIdentifierUtils.createFlowId();
		FlowCookie flowCookie = new FlowCookie(BigInteger.valueOf(flowCookieInc.incrementAndGet()));
		LOG.info("createFwdAllToControllerFlow tableId = " + tableId + " flowCookie = "
				+ flowCookie.getValue().toString(16));
		FlowBuilder allToCtrlFlow = new FlowBuilder().setTableId(tableId).setFlowName("allPacketsToCtrl").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);

		Instruction instruction = createSendPacketToControllerInstruction();
		// Put our Instruction in a list of Instructions
		InstructionsBuilder isb = new InstructionsBuilder();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);
		isb.setInstruction(instructions);

		MatchBuilder matchBuilder = new MatchBuilder();

		int priority = 0;
		allToCtrlFlow.setMatch(matchBuilder.build()).setInstructions(isb.build()).setPriority(priority)
				.setBufferId(OFConstants.OFP_NO_BUFFER).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return allToCtrlFlow;
	}

	public static FlowBuilder createDestIpMatchGoToFlowmonTableFlow(FlowCookie flowCookie, FlowId flowId,
			MacAddress macAddress, Ipv4Address destination, int port, short protocol) {

		LOG.info("createPermitPacketFlow " + " FlowCookie : " + flowCookie.getValue().toString(16) + " macAddress "
				+ macAddress.getValue() + " destination " + destination.getValue() + " protocol " + protocol);

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(SdnMudConstants.SDNMUD_RULES_TABLE)
				.setFlowName("createDestIpMatchGoToFlowmonTableFlow").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetSourceMatch(matchBuilder, macAddress);
		createDestIpv4Match(matchBuilder, destination);
		if (protocol == SdnMudConstants.TCP_PROTOCOL) {
			FlowUtils.createDstTcpPortMatch(matchBuilder, port);
		} else if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createDstUdpPortMatch(matchBuilder, port);
		}

		Match match = matchBuilder.build();

		InstructionsBuilder isb = FlowUtils.createGoToNextTableInstruction(SdnMudConstants.PASS_THRU_TABLE,
				flowCookie.getValue());

		flowBuilder.setMatch(match).setInstructions(isb.build()).setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY)
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createUnconditionalGoToNextTableFlow(short table, short nextTable, FlowId flowId,
			FlowCookie flowCookie) {
		LOG.info("createGoToTableFlow ");

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(table).setFlowName("permitPackets").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		InstructionsBuilder isb = createGoToNextTableInstruction(nextTable);

		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.UNCONDITIONAL_GOTO_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createDropPacketOnSourceMacMatchAndSendToControllerFlow(FlowCookie flowCookie,
			FlowId flowId, MacAddress macAddress, short tableId, int idleTimeout) {

		LOG.info("createDropPacketFlow: flowCookie = " + flowCookie.getValue().toString(16) + " MacAddress "
				+ macAddress.getValue());

		FlowBuilder dropPacketFlow = new FlowBuilder().setTableId(tableId).setFlowName("dropAllPackets").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);

		Instruction sendToControllerInstruction = FlowUtils.createSendPacketToControllerInstruction();

		InstructionsBuilder isb = new InstructionsBuilder();

		List<Instruction> instructions = new ArrayList<Instruction>();
		// instructions.add(metadataInstruction);
		instructions.add(sendToControllerInstruction);
		isb.setInstruction(instructions);

		MatchBuilder matchBuilder = new MatchBuilder();
		// MetadataBuilder metadataBuilder = new MetadataBuilder();
		// metadataBuilder.setMetadata(BigInteger.valueOf(SdnMudConstants.MUD_RULE_MISS_LABEL));
		// matchBuilder.setMetadata(metadataBuilder.build());
		Match match = FlowUtils.createEthernetSourceMatch(matchBuilder, macAddress).build();

		// Put our Instruction in a list of Instructions

		// NOTE the idle timeout here is not 0!!
		dropPacketFlow.setMatch(match).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(idleTimeout)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return dropPacketFlow;
	}

	public static FlowBuilder createIpMatchSendPacketToControllerFlow(Short tableId, FlowId flowId,
			FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createIpV4Match(matchBuilder);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x0800);
		Instruction instruction = FlowUtils.createSendPacketToControllerInstruction();
		InstructionsBuilder insb = new InstructionsBuilder();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);
		insb.setInstruction(instructions);
		FlowBuilder sendToControllerFlow = new FlowBuilder().setTableId(tableId)
				.setFlowName("uncoditionalSendToController").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		sendToControllerFlow.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.UNCONDITIONAL_DROP_PRIORITY + 1).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return sendToControllerFlow;
	}

	public static FlowBuilder createUnconditionalSendPacketToControllerFlow(Short tableId, Short nextTableId,
			FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		Instruction instruction = FlowUtils.createSendPacketToControllerInstruction();
		Instruction gotoInstruction = FlowUtils.createGoToTableInstruction(nextTableId);
		InstructionsBuilder insb = new InstructionsBuilder();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);
		instructions.add(gotoInstruction);
		insb.setInstruction(instructions);
		FlowBuilder sendToControllerFlow = new FlowBuilder().setTableId(tableId)
				.setFlowName("uncoditionalSendToController").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		sendToControllerFlow.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(SdnMudConstants.UNCONDITIONAL_DROP_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return sendToControllerFlow;
	}

	public static FlowBuilder createPermitPacketFromIpAddressOnDestMacMatch(FlowCookie flowCookie, FlowId flowId,
			MacAddress macAddress, Ipv4Address source, int port, short protocol) {
		LOG.info("createPermitPacketFromIpAddressOnDestMacMatch " + " FlowCookie : "
				+ flowCookie.getValue().toString(16) + " macAddress " + " flowId " + flowId.getValue()
				+ macAddress.getValue() + " destination " + source.getValue() + " protocol " + protocol);

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(SdnMudConstants.SDNMUD_RULES_TABLE)
				.setFlowName("permitPacketsFromIpAddressOnDestMacMatch").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetDestMatch(matchBuilder, macAddress);
		createSrcIpv4Match(matchBuilder, source);
		if (protocol == SdnMudConstants.TCP_PROTOCOL) {
			FlowUtils.createSrcTcpPortMatch(matchBuilder, port);
		} else if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createSrcUdpPortMatch(matchBuilder, port);
		}

		Match match = matchBuilder.build();
		InstructionsBuilder isb = createGoToNextTableInstruction(SdnMudConstants.PASS_THRU_TABLE,
				flowCookie.getValue());

		flowBuilder.setMatch(match).setInstructions(isb.build()).setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY)
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createPermitPacketsToDhcpServerFlow(MacAddress macAddress, FlowCookie flowCookie,
			FlowId flowId) {
		LOG.info("creatrePermitPacketsToDhcpServerFlow");

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(SdnMudConstants.SDNMUD_RULES_TABLE)
				.setFlowName("permitPacketsToDhcp").setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceMatch(matchBuilder, macAddress);
		FlowUtils.createUdpPortMatch(matchBuilder, SdnMudConstants.DHCP_CLIENT_PORT, SdnMudConstants.DHCP_SERVER_PORT);

		Match match = matchBuilder.build();
		InstructionsBuilder isb = createGoToNextTableInstruction((short) (SdnMudConstants.SDNMUD_RULES_TABLE + 1),
				flowCookie.getValue());

		flowBuilder.setMatch(match).setInstructions(isb.build()).setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY)
				.setBufferId(OFConstants.ANY).setHardTimeout(0).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	public static FlowBuilder createDropPacketsOnDestMacMatchFlow(MacAddress macAddress,
			MacAddress destinationMacAddress, FlowCookie flowCookie, FlowId flowId) {
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(SdnMudConstants.SDNMUD_RULES_TABLE)
				.setFlowName("permitPacketsFromNtp").setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceMatch(matchBuilder, macAddress);
		FlowUtils.createEthernetDestMatch(matchBuilder, destinationMacAddress);
		Match match = matchBuilder.build();
		Instruction instruction = FlowUtils.createGoToTableInstruction(SdnMudConstants.DROP_TABLE);
		InstructionsBuilder isb = new InstructionsBuilder();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);
		isb.setInstruction(instructions);

		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY_HIGH).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	public static FlowBuilder createPermitPacketsFromDeviceMacToMacFlow(MacAddress deviceMacAddress,
			MacAddress destinationMacAddress, int port, short protocol, FlowCookie flowCookie, FlowId flowId) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceMatch(matchBuilder, deviceMacAddress);
		FlowUtils.createEthernetDestMatch(matchBuilder, destinationMacAddress);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createDstUdpPortMatch(matchBuilder, port);
		} else {
			FlowUtils.createDstTcpPortMatch(matchBuilder, port);

		}
		short tableId = SdnMudConstants.SDNMUD_RULES_TABLE;

		InstructionsBuilder isb = FlowUtils.createGoToNextTableInstruction(SdnMudConstants.PASS_THRU_TABLE,
				flowCookie.getValue());
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("permitPacketToMac").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	public static FlowBuilder createPermitPacketsFromMacToMacFlow(MacAddress sourceMacAddress, int sourcePort,
			MacAddress destinationMacAddress, int destinationPort, short protocol, FlowCookie flowCookie,
			FlowId flowId) {
		LOG.info("createPermitPacketsFromMacToMacFlow :" + sourceMacAddress.getValue() + " sourcePort " + sourcePort
				+ " destinationMacAddress " + destinationMacAddress.getValue() + " destinationPort " + destinationPort
				+ " protocol " + protocol);
		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetSourceAndDestinationMatch(matchBuilder, sourceMacAddress, destinationMacAddress);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			if (sourcePort != -1) {
				createSrcUdpPortMatch(matchBuilder, sourcePort);
			}
			if (destinationPort != -1) {
				createDstUdpPortMatch(matchBuilder, destinationPort);
			}
			if (sourcePort == -1 && destinationPort == -1) {
				// Don't care about port.
				createUdpProtocolMatch(matchBuilder);
			}
		} else {
			if (sourcePort != -1) {
				createSrcTcpPortMatch(matchBuilder, sourcePort);
			}
			if (destinationPort != -1) {
				createDstTcpPortMatch(matchBuilder, destinationPort);
			}
			if (sourcePort == -1 && destinationPort == -1) {
				// Don't care about port.
				createTcpProtocolMatch(matchBuilder);
			}
		}

		short tableId = SdnMudConstants.SDNMUD_RULES_TABLE;
		// Goto next table and set the metadata
		InstructionsBuilder isb = FlowUtils.createGoToNextTableInstruction(SdnMudConstants.PASS_THRU_TABLE,
				flowCookie.getValue());
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("permitPacketsFromMacToDeviceMacFlow").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	public static FlowBuilder createVlanMatchDivertToPorts(int vlanLabel, String[] ports, int duration, short tableId,
			FlowCookie flowCookie, FlowId flowId) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createVlanMatch(matchBuilder, vlanLabel);

		Instruction instruction = createSendToPortsAction(ports);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);
		InstructionsBuilder insb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(instruction);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("vlanMatchDeliverToPorts").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createDestIpMatchSendToController(String address, int port, short tableId,
			FlowCookie flowCookie, FlowId flowId, BigInteger metadata) {
		Ipv4Address ipv4Address = new Ipv4Address(address);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createDestIpv4Match(matchBuilder, ipv4Address);
		FlowUtils.createDstUdpPortMatch(matchBuilder, port);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x800);
		FlowUtils.createSrcUdpPortMatch(matchBuilder, port);

		ArrayList<Instruction> instructions = new ArrayList<Instruction>();

		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(BigInteger.valueOf(0xffffffffL));
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());
		Instruction maskInstruction = new InstructionBuilder().setOrder(0).setKey(new InstructionKey(0))
				.setInstruction(wmcb.build()).build();

		instructions.add(maskInstruction);

		Instruction sendPacketToController = FlowUtils.createSendPacketToControllerInstruction();

		instructions.add(sendPacketToController);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("destIpMatchSendToController").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));
		return fb;
	}

	public static FlowBuilder createMetadataMatchSendToPortsAndGotoTable(FlowCookie flowCookie, FlowId flowId,
			BigInteger metadata, BigInteger metadataMask, Short tableId, 
			String flowmonPorts, int duration) {
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createMetadataMatch(matchBuilder, metadata, metadataMask);

		ArrayList<Instruction> instructions = new ArrayList<Instruction>();

		Instruction sendToPorts = FlowUtils.createSendToPortsAction(new String[] {flowmonPorts});
		
		short targetTableId = (short)(tableId + 1);
		instructions.add(sendToPorts);
		Instruction goToTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(1)).setOrder(1).build();

		instructions.add(goToTableInstruction);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataMatchSendToPortsAndGotoTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;

	}

	public static FlowBuilder createMetadataDestIpAndPortMatchGoTo(BigInteger metadata, BigInteger metadataMask,
			Ipv4Address address, int destinationPort, short protocol, Short tableId, Short targetTable, FlowId flowId,
			FlowCookie flowCookie) {

		MatchBuilder matchBuilder = new MatchBuilder();

		FlowUtils.createMetadataMatch(matchBuilder, metadata, metadataMask);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x800);
		FlowUtils.createDestIpv4Match(matchBuilder, address);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createDstUdpPortMatch(matchBuilder, destinationPort);
		} else {
			FlowUtils.createDstTcpPortMatch(matchBuilder, destinationPort);
		}

		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTable, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(false);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataDestIpAndPortMatchGoTo")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMetadataSrcIpAndPortMatchGoTo(BigInteger metadata, BigInteger metadataMask,
			Ipv4Address address, int destinationPort, short protocol, Short tableId, Short targetTable, FlowId flowId,
			FlowCookie flowCookie) {

		LOG.info("createMetadataSrcIpAndPortMatchGoTo metadata = " + metadata.toString(16) + " metadataMask = "
				+ metadataMask.toString(16) + " ipv4Address = " + address.getValue() + " destinationPort = "
				+ destinationPort + " protocol " + protocol + " tableId " + tableId + " targetTable = " + targetTable
				+ " flowId " + flowId);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createMetadataMatch(matchBuilder, metadata, metadataMask);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x800);
		FlowUtils.createSrcIpv4Match(matchBuilder, address);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createSrcUdpPortMatch(matchBuilder, destinationPort);
		} else {
			FlowUtils.createSrcTcpPortMatch(matchBuilder, destinationPort);
		}

		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTable, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataSrcIpAndPortMatchGoTo").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMetadataAndDestMacMatchGoToTableFlow(BigInteger metadata, BigInteger metadataMask,
			MacAddress destinationMacAddress, Short tableId, Short targetTable, FlowCookie flowCookie, FlowId flowId) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		createEthernetDestMatch(matchBuilder, destinationMacAddress);
		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTable, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataAndDestMacMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;

	}

	public static FlowBuilder createMetadataMatchDropPacket(BigInteger metadata, BigInteger metadataMask, Short tableId,
			FlowCookie flowCookie, FlowId flowId) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		Instruction dropInstruction = FlowUtils.createDropInstruction();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(dropInstruction);
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);
		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataMatchDropPacket").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY_HIGH).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));
		return fb;
	}

	public static FlowBuilder createMetadaAndProtocolMatchGoToTable(BigInteger metadata, BigInteger metadataMask,
			short protocol, short tableId, short targetTableId, FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x800);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createUdpProtocolMatch(matchBuilder);
		} else {
			FlowUtils.createTcpProtocolMatch(matchBuilder);
		}

		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTableId, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataAndDestMacMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMetadaProtocolAndDestPortMatchGoToTable(BigInteger metadata,
			BigInteger metadataMask, short protocol, int port, short tableId, short targetTableId, FlowId flowId,
			FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		FlowUtils.createEthernetTypeMatch(matchBuilder, 0x800);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			FlowUtils.createDstUdpPortMatch(matchBuilder, port);
		} else {
			FlowUtils.createDstTcpPortMatch(matchBuilder, port);
		}

		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTableId, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("MetadaProtocolAndDestPortMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMetadaProtocolAndSrcPortMatchGoToTable(BigInteger metadata, BigInteger metadataMask,
			short protocol, int port, short tableId, short targetTableId, FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		createEthernetTypeMatch(matchBuilder, 0x800);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			createSrcUdpPortMatch(matchBuilder, port);
		} else {
			createSrcTcpPortMatch(matchBuilder, port);
		}

		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTableId, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("MetadaProtocolAndSrcPortMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMetadaProtocolAndSrcDestPortMatchGoToTable(BigInteger metadata,
			BigInteger metadataMask, short protocol, int srcPort, int destPort, short tableId, short targetTableId,
			FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		createEthernetTypeMatch(matchBuilder, 0x800);
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			if (srcPort != -1)
				createSrcUdpPortMatch(matchBuilder, srcPort);
			if (destPort != -1)
				createDstUdpPortMatch(matchBuilder, destPort);
		} else {
			if (srcPort != -1)
				createSrcTcpPortMatch(matchBuilder, srcPort);
			if (destPort != -1)
				createDstTcpPortMatch(matchBuilder, destPort);
		}

		InstructionsBuilder insb = FlowUtils.createGoToNextTableInstruction(targetTableId, flowCookie.getValue());

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("MetadaProtocolAndSrcPortMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createSourceMacMatchSetMetadataAndGoToTable(MacAddress srcMac, BigInteger metadata,
			BigInteger metadataMask, short tableId, short targetTableId, FlowId flowId, FlowCookie flowCookie) {

		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceMatch(matchBuilder, srcMac);
		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(metadataMask);
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());
		Instruction maskInstruction = new InstructionBuilder().setOrder(0)
				.setKey(new InstructionKey(getInstructionKey())).setInstruction(wmcb.build()).build();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(maskInstruction);
		Instruction gotoTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(1).build();

		instructions.add(gotoTableInstruction);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("sourceMacMatchSetMetadataAndGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(SdnMudConstants.CACHE_TIMEOUT / 2).setIdleTimeout(SdnMudConstants.CACHE_TIMEOUT)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
			NodeConnectorId nodeConnector, short tableId, int timeout, FlowId flowId, FlowCookie flowCookie) {
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("matchPortArpMatchSendPacketToController").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();

		FlowUtils.createArpTypeMatch(matchBuilder);
		FlowUtils.createInPortMatch(matchBuilder, nodeConnector);
		short nextTable = (short) (tableId + 1);
		Instruction goToTableInstruction = FlowUtils.createGoToTableInstruction(nextTable);
		Instruction sendPacketToControllerInstruction = FlowUtils.createSendPacketToControllerInstruction();
		List<Instruction> li = new ArrayList<Instruction>();
		li.add(sendPacketToControllerInstruction);
		li.add(goToTableInstruction);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(li);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(timeout)
				.setIdleTimeout(2 * timeout).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createVlanAndPortMatchSendPacketToControllerAndGoToTableFlow(
			NodeConnectorId nodeConnector, int vlanLabel, short tableId, int timeout, FlowId flowId,
			FlowCookie flowCookie) {
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("matchPortArpMatchSendPacketToController").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();

		FlowUtils.createVlanMatch(matchBuilder, vlanLabel);
		FlowUtils.createInPortMatch(matchBuilder, nodeConnector);
		short nextTable = (short) (tableId + 1);
		Instruction goToTableInstruction = FlowUtils.createGoToTableInstruction(nextTable);
		Instruction sendPacketToControllerInstruction = FlowUtils.createSendPacketToControllerInstruction();
		List<Instruction> li = new ArrayList<Instruction>();
		li.add(sendPacketToControllerInstruction);
		li.add(goToTableInstruction);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(li);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(timeout).setIdleTimeout(2 * timeout)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createVlanAndPortMatchPopVlanTagAndGoToTable(Short tableId, int vlanLabel,
			NodeConnectorId inPort, int timeout, FlowId flowId, FlowCookie flowCookie) {

		LOG.info("createVlanAndPortMatchPopVlanTagAndGoToTable : tableId = " + tableId + " vlanLabel = " + vlanLabel);
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("VlanAndPortMatchPopVlanTagAndGoToTable").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createInPortMatch(matchBuilder, inPort);
		FlowUtils.createVlanMatch(matchBuilder, vlanLabel);
		Instruction popVlanActionIstruction = FlowUtils.createPopVlanActionInstruction(0, 0);
		short targetTableId = (short) (tableId + 1);
		Instruction goToTableInstruction = FlowUtils.createGoToTableInstruction(targetTableId);

		List<Instruction> li = new ArrayList<>();
		li.add(popVlanActionIstruction);
		li.add(goToTableInstruction);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(li);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(timeout).setIdleTimeout(2 * timeout)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createNoVlanArpMatchPushVlanSendToPortAndGoToTable(String outportUri, int vlanId,
			Short tableId, int timeout, FlowId flowId, FlowCookie flowCookie) {
		// TODO Auto-generated method stub
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("noVlanArpMatchPushVlanSendToPort")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createNoVlanPresentMatch(matchBuilder);
		FlowUtils.createArpTypeMatch(matchBuilder);
		Instruction pushVlanActionInstruction = FlowUtils.createSetVlanAndOutputToPortInstructions(vlanId, outportUri);
		short targetTableId = (short) (tableId + 1);

		Instruction goToTableInstruction = FlowUtils.createGoToTableInstruction(targetTableId);
		List<Instruction> li = new ArrayList<>();
		li.add(pushVlanActionInstruction);
		li.add(goToTableInstruction);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(li);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(timeout).setIdleTimeout(2 * timeout)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createSourceMacMatchSetMetadataGoToNextTableFlow(MacAddress srcMac, BigInteger metadata,
			BigInteger metadataMask, short tableId, FlowId flowId, FlowCookie flowCookie) {

		MatchBuilder matchBuilder = new MatchBuilder();
		FlowUtils.createEthernetSourceMatch(matchBuilder, srcMac);
		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(metadataMask);
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());
		Instruction maskInstruction = new InstructionBuilder().setOrder(0)
				.setKey(new InstructionKey(getInstructionKey())).setInstruction(wmcb.build()).build();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(maskInstruction);
		short targetTableId = (short) (tableId + 1);
		Instruction gotoTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(1).build();

		instructions.add(gotoTableInstruction);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("sourceMacMatchSetMetadataAndGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(SdnMudConstants.CACHE_TIMEOUT / 2).setIdleTimeout(SdnMudConstants.CACHE_TIMEOUT)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createDestMacMatchSetMetadataAndGoToNextTableFlow(MacAddress dstMac, BigInteger metadata,
			BigInteger metadataMask, short tableId, FlowId flowId, FlowCookie flowCookie) {

		MatchBuilder matchBuilder = new MatchBuilder();
		// FlowUtils.createEthernetDestNoEthTypeMatch(matchBuilder, dstMac);
		FlowUtils.createEthernetDestMatch(matchBuilder, dstMac);
		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(metadataMask);
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());
		Instruction maskInstruction = new InstructionBuilder().setOrder(0)
				.setKey(new InstructionKey(getInstructionKey())).setInstruction(wmcb.build()).build();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(maskInstruction);
		short targetTableId = (short) (tableId + 1);
		Instruction gotoTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTableId).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(1).build();

		instructions.add(gotoTableInstruction);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("destMacMatchSetMetadataAndGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(SdnMudConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(SdnMudConstants.CACHE_TIMEOUT / 2).setIdleTimeout(SdnMudConstants.CACHE_TIMEOUT)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	public static FlowBuilder createSourceMacMatchGoToNextTableFlow(MacAddress macAddress, Short tableId, FlowId flowId,
			FlowCookie flowCookie) {
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("sourceMacMatchGoToTableFlow")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetSourceNoEthTypeMatch(matchBuilder, macAddress);
		matchBuilder.setIpMatch(null);
		Match match = matchBuilder.build();
		short targetTable = (short) (tableId + 1);
		Instruction gotoInstruction = FlowUtils.createGoToTableInstruction(targetTable);
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(gotoInstruction);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);

		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(SdnMudConstants.CACHE_TIMEOUT / 2).setIdleTimeout(SdnMudConstants.CACHE_TIMEOUT)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	public static FlowBuilder createDestMacMatchGoToNextTableFlow(MacAddress macAddress, Short tableId, FlowId flowId,
			FlowCookie flowCookie) {
		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("sourceMacMatchGoToTableFlow")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetDestNoEthTypeMatch(matchBuilder, macAddress);
		matchBuilder.setIpMatch(null);
		Match match = matchBuilder.build();
		short targetTable = (short) (tableId + 1);
		Instruction gotoInstruction = FlowUtils.createGoToTableInstruction(targetTable);
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(gotoInstruction);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);

		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(SdnMudConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(SdnMudConstants.CACHE_TIMEOUT / 2).setIdleTimeout(SdnMudConstants.CACHE_TIMEOUT)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

}
