/*
 * Copyright © 2017 None.  No rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSource;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Layer4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.BaseappConstants;

public class FlowUtils {

	private static final Logger LOG = LoggerFactory.getLogger(FlowUtils.class);

	private static AtomicLong instructionKey = new AtomicLong(0x0);

	private FlowUtils() {
		// Only static methods in this class
	}

	private synchronized static int getInstructionKey() {
		return (int) instructionKey.incrementAndGet();
	}

	private static int getActionKey() {
		return 0;
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

	private static MatchBuilder createMetadataMatch(MatchBuilder matchBuilder, BigInteger metadata,
			BigInteger metadataMask) {
		MetadataBuilder metadataBuilder = new MetadataBuilder();
		metadataBuilder.setMetadata(metadata);
		metadataBuilder.setMetadataMask(metadataMask);
		matchBuilder.setMetadata(metadataBuilder.build());
		return matchBuilder;

	}

	private static MatchBuilder createEthernetTypeMatch(MatchBuilder matchBuilder, int ethernetType) {
		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
				.setEthernetType(new EthernetTypeBuilder().setType(new EtherType(Long.valueOf(ethernetType))).build());
		matchBuilder.setEthernetMatch(ethernetMatchBuilder.build()).build();
		return matchBuilder;
	}

	private static Instruction createWriteMetadataInstruction(BigInteger metadata, BigInteger metadataMask) {
		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(metadataMask);
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());
		Instruction maskInstruction = new InstructionBuilder().setOrder(0)
				.setKey(new InstructionKey(getInstructionKey())).setInstruction(wmcb.build()).build();
		return maskInstruction;

	}

	private static List<Instruction> addGoToTableInstruction(List<Instruction> instructions, short targetTable) {
		Instruction gotoTableInstruction = new InstructionBuilder()
				.setInstruction(new GoToTableCaseBuilder()
						.setGoToTable(new GoToTableBuilder().setTableId(targetTable).build()).build())
				.setKey(new InstructionKey(getInstructionKey())).setOrder(instructions.size()).build();

		instructions.add(gotoTableInstruction);
		return instructions;
	}

	private static List<Instruction> addWriteMetadataInstruction(List<Instruction> instructions, BigInteger metadata,
			BigInteger metadataMask) {
		WriteMetadataBuilder wmb = new WriteMetadataBuilder();
		wmb.setMetadata(metadata);
		wmb.setMetadataMask(metadataMask);
		WriteMetadataCaseBuilder wmcb = new WriteMetadataCaseBuilder().setWriteMetadata(wmb.build());

		Instruction maskInstruction = new InstructionBuilder().setOrder(instructions.size())
				.setKey(new InstructionKey(getInstructionKey())).setInstruction(wmcb.build()).build();

		instructions.add(maskInstruction);
		return instructions;
	}

	private static List<Instruction> addSendPacketToControllerInstruction(List<Instruction> instructions) {
		Instruction instruction = FlowUtils.createSendPacketToControllerInstruction();
		instructions.add(instruction);
		return instructions;
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

	private static InstructionsBuilder createGoToNextTableInstruction(short targetTable, BigInteger metadata,
			BigInteger metadataMask) {
		List<Instruction> instructions = new ArrayList<Instruction>();

		addGoToTableInstruction(instructions, targetTable);
		addWriteMetadataInstruction(instructions, metadata, metadataMask);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);
		return isb;
	}

	private static InstructionsBuilder createGoToNextTableAndSendToControllerInstruction(short targetTable,
			BigInteger metadata, BigInteger metadataMask) {
		List<Instruction> instructions = new ArrayList<Instruction>();
		addSendPacketToControllerInstruction(instructions);
		addGoToTableInstruction(instructions, targetTable);
		addWriteMetadataInstruction(instructions, metadata, metadataMask);
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);
		return isb;
	}

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

	private static MatchBuilder createDstProtocolPortMatch(MatchBuilder matchBuilder, short protocol, int port) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProto(IpVersion.Ipv4);
		ipmatch.setIpProtocol(protocol);
		matchBuilder.setIpMatch(ipmatch.build());

		if (port != -1) {
			PortNumber portNumber = new PortNumber(port);
			Layer4Match l4match = null;
			if (protocol == SdnMudConstants.TCP_PROTOCOL) {
				l4match = new TcpMatchBuilder().setTcpDestinationPort(portNumber).build();
			} else if (protocol == SdnMudConstants.UDP_PROTOCOL) {
				l4match = new UdpMatchBuilder().setUdpDestinationPort(portNumber).build();
			}
			matchBuilder.setLayer4Match(l4match);
		}

		return matchBuilder;

	}

	private static MatchBuilder createSrcProtocolPortMatch(MatchBuilder matchBuilder, short protocol, int port) {
		IpMatchBuilder ipmatch = new IpMatchBuilder();
		ipmatch.setIpProtocol(protocol);
		ipmatch.setIpProto(IpVersion.Ipv4);
		matchBuilder.setIpMatch(ipmatch.build());

		if (port != -1) {
			PortNumber portNumber = new PortNumber(port);
			Layer4Match l4match = null;
			if (protocol == SdnMudConstants.TCP_PROTOCOL) {
				l4match = new TcpMatchBuilder().setTcpSourcePort(portNumber).build();
			} else if (protocol == SdnMudConstants.UDP_PROTOCOL) {
				l4match = new UdpMatchBuilder().setUdpSourcePort(portNumber).build();
			}
			matchBuilder.setLayer4Match(l4match);
		}

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

	static FlowBuilder createUnconditionalDropPacketFlow(short table, FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		Instruction dropInstruction = createDropInstruction();
		InstructionsBuilder isb = new InstructionsBuilder();
		List<Instruction> instructions = new ArrayList<Instruction>();
		instructions.add(dropInstruction);
		isb.setInstruction(instructions);
		FlowBuilder flowBuilder = new FlowBuilder();
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build()).setTableId(table).setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie)
				.setPriority(BaseappConstants.UNCONDITIONAL_DROP_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));
		return flowBuilder;

	}

	/**********************************************************************************/

	static FlowBuilder createMetadataMatchGoToDropTableFlow(FlowCookie flowCookie, BigInteger metadata,
			BigInteger metadataMask, FlowId flowId, Short tableId, BigInteger newMetadata, BigInteger newMetadataMask,
			Short dropTableId, int duration) {
		LOG.info("FlowUtils: createMetadataMatchGoToTable " + flowCookie.getValue().toString(16) + " metadata "
				+ metadata.toString(16) + " metadataMask " + metadataMask.toString(16) + " tableId " + tableId
				+ " targetTable " + dropTableId);

		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		InstructionsBuilder insb = createGoToNextTableInstruction(dropTableId, newMetadata, newMetadataMask);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataMatchGoToTable").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(BaseappConstants.MATCHED_DROP_PACKET_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(duration).setIdleTimeout(0)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	static FlowBuilder createUnconditionalGoToNextTableFlow(short table, FlowId flowId, FlowCookie flowCookie) {
		LOG.info("createGoToTableFlow ");

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(table).setFlowName("permitPackets").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();
		ArrayList<Instruction> instructions = new ArrayList<>();
		Instruction wmd = createWriteMetadataInstruction(flowCookie.getValue(), new BigInteger("ffffffffffffffff", 16));
		instructions.add(wmd);
		short nextTable = (short) (table + 1);
		Instruction ins = createGoToTableInstruction(nextTable);
		instructions.add(ins);
		InstructionsBuilder isb = new InstructionsBuilder().setInstruction(instructions);

		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(BaseappConstants.UNCONDITIONAL_GOTO_PRIORITY + 1).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	static FlowBuilder createEthernetMatchSendPacketToControllerFlow(BigInteger metadata, BigInteger metadataMask,
			Short tableId, FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		// createIpV4Match(matchBuilder);
		createEthernetTypeMatch(matchBuilder, 0x0800);
		List<Instruction> instructions = new ArrayList<Instruction>();
		addSendPacketToControllerInstruction(instructions);
		addWriteMetadataInstruction(instructions, metadata, metadataMask);
		// addGoToTableInstruction(instructions, (short) (tableId + 1));
		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);
		FlowBuilder sendToControllerFlow = new FlowBuilder().setTableId(tableId)
				.setFlowName("uncoditionalSendToController").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		sendToControllerFlow.setMatch(matchBuilder.build()).setInstructions(insb.build())
				.setPriority(BaseappConstants.UNCONDITIONAL_DROP_PRIORITY + 1).setBufferId(OFConstants.ANY)
				.setHardTimeout(0).setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return sendToControllerFlow;
	}

	static FlowBuilder createToDhcpServerMatchGoToNextTableFlow(short tableId, FlowCookie flowCookie, FlowId flowId,
			boolean sendToController) {

		LOG.info("createPermitPacketsToDhcpServerFlow ");

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("permitPacketsToDhcpServerFlow")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		// Set up the ports.
		createEthernetTypeMatch(matchBuilder, 0x800);
		createUdpPortMatch(matchBuilder, SdnMudConstants.DHCP_CLIENT_PORT, SdnMudConstants.DHCP_SERVER_PORT);
		/*
		 * Ipv4Address destinationAddress = new Ipv4Address("255.255.255.255");
		 * createDestIpv4Match(matchBuilder, destinationAddress); Ipv4Address
		 * srcAddress = new Ipv4Address("0.0.0.0");
		 * createSrcIpv4Match(matchBuilder, srcAddress);
		 */

		Match match = matchBuilder.build();
		short destinationTableId = (short) (tableId + 1);

		InstructionsBuilder isb = new InstructionsBuilder();
		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		if (sendToController)
			addSendPacketToControllerInstruction(instructions);
		addGoToTableInstruction(instructions, destinationTableId);
		isb.setInstruction(instructions);

		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	static FlowBuilder createFromDhcpServerMatchGoToNextTableFlow(short tableId, FlowCookie flowCookie, FlowId flowId) {

		LOG.info("createPermitPacketsFromDhcpServerFlow ");

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("permitPacketsFromDhcpServerFlow")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetTypeMatch(matchBuilder, 0x800);
		createUdpPortMatch(matchBuilder, SdnMudConstants.DHCP_SERVER_PORT, SdnMudConstants.DHCP_CLIENT_PORT);

		Match match = matchBuilder.build();
		short destinationTableId = (short) (tableId + 1);
		InstructionsBuilder isb = createGoToNextTableInstruction(destinationTableId);
		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;

	}

	static FlowBuilder createMetadataDestIpAndPortMatchGoToNextTableFlow(BigInteger metadata, BigInteger metadataMask,
			Ipv4Address address, int destinationPort, short protocol, boolean sendToController, Short tableId,
			BigInteger newMetadata, BigInteger newMetadataMask, FlowId flowId, FlowCookie flowCookie) {

		MatchBuilder matchBuilder = new MatchBuilder();

		Short targetTableId = (short) (tableId + 1);

		createMetadataMatch(matchBuilder, metadata, metadataMask);
		createEthernetTypeMatch(matchBuilder, 0x800);
		createDestIpv4Match(matchBuilder, address);
		createDstProtocolPortMatch(matchBuilder, protocol, destinationPort);

		InstructionsBuilder insb = sendToController
				? createGoToNextTableAndSendToControllerInstruction(targetTableId, newMetadata, newMetadataMask)
				: createGoToNextTableInstruction(targetTableId, newMetadata, newMetadataMask);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(false);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataDestIpAndPortMatchGoTo")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	static FlowBuilder createMetadataSrcIpAndPortMatchGoToNextTableFlow(BigInteger metadata, BigInteger metadataMask,
			Ipv4Address address, int srcPort, short protocol, boolean sendToController, Short tableId,
			BigInteger newMetadata, BigInteger newMetadataMask, FlowId flowId, FlowCookie flowCookie) {

		LOG.info("createMetadataSrcIpAndPortMatchGoTo metadata = " + metadata.toString(16) + " metadataMask = "
				+ metadataMask.toString(16) + " ipv4Address = " + address.getValue() + " destinationPort = " + srcPort
				+ " protocol " + protocol + " tableId " + tableId + " flowId " + flowId);
		short targetTable = (short) (tableId + 1);
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		createEthernetTypeMatch(matchBuilder, 0x800);
		createSrcIpv4Match(matchBuilder, address);
		createSrcProtocolPortMatch(matchBuilder, protocol, srcPort);

		InstructionsBuilder insb = sendToController
				? createGoToNextTableAndSendToControllerInstruction(targetTable, newMetadata, newMetadataMask)
				: createGoToNextTableInstruction(targetTable, newMetadata, newMetadataMask);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("metadataSrcIpAndPortMatchGoTo").setId(flowId)
				.setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	static FlowBuilder createMetadaProtocolAndSrcDestPortMatchGoToTable(BigInteger metadata, BigInteger metadataMask,
			short protocol, int srcPort, int destPort, short tableId, BigInteger newMetadata,
			BigInteger newMetadataMask, boolean sendToController, FlowId flowId, FlowCookie flowCookie) {
		MatchBuilder matchBuilder = new MatchBuilder();
		createMetadataMatch(matchBuilder, metadata, metadataMask);
		createEthernetTypeMatch(matchBuilder, 0x800);
		createSrcProtocolPortMatch(matchBuilder, protocol, srcPort);
		createDstProtocolPortMatch(matchBuilder, protocol, destPort);

		short targetTableId = (short) (tableId + 1);

		ArrayList<Instruction> instructions = new ArrayList<Instruction>();
		if (sendToController)
			addSendPacketToControllerInstruction(instructions);
		addGoToTableInstruction(instructions, targetTableId);

		InstructionsBuilder insb = new InstructionsBuilder();
		insb.setInstruction(instructions);

		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("MetadaProtocolAndSrcPortMatchGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	static FlowBuilder createSourceMacMatchSetMetadataGoToNextTableFlow(MacAddress srcMac, BigInteger metadata,
			BigInteger metadataMask, short tableId, FlowId flowId, FlowCookie flowCookie, int timeout) {

		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetSourceMatch(matchBuilder, srcMac);
		InstructionsBuilder insb = createGoToNextTableInstruction((short) (tableId + 1), metadata, metadataMask);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);
		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("sourceMacMatchSetMetadataAndGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(timeout / 2).setIdleTimeout(timeout)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	static FlowBuilder createDestMacMatchSetMetadataAndGoToNextTableFlow(MacAddress dstMac, BigInteger metadata,
			BigInteger metadataMask, short tableId, FlowId flowId, FlowCookie flowCookie, int timeout) {

		MatchBuilder matchBuilder = new MatchBuilder();
		// createEthernetDestNoEthTypeMatch(matchBuilder, dstMac);
		createEthernetDestMatch(matchBuilder, dstMac);
		InstructionsBuilder insb = createGoToNextTableInstruction((short) (tableId + 1), metadata, metadataMask);
		FlowBuilder fb = new FlowBuilder();
		fb.setStrict(false);
		fb.setBarrier(true);

		fb.setMatch(matchBuilder.build()).setTableId(tableId).setFlowName("destMacMatchSetMetadataAndGoToTable")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie).setInstructions(insb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY)
				.setHardTimeout(timeout / 2).setIdleTimeout(timeout)
				.setFlags(new FlowModFlags(false, false, false, false, false));

		return fb;
	}

	static FlowBuilder createDestAddressPortProtocolMatchGoToNextFlow(Ipv4Address dnsAddress, int port, short protocol,
			short tableId, FlowId flowId, FlowCookie flowCookie) {

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("permitPacketsToServerFlow")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();
		createEthernetTypeMatch(matchBuilder, 0x800);

		createDstProtocolPortMatch(matchBuilder, protocol, port);

		createDestIpv4Match(matchBuilder, dnsAddress);
		Match match = matchBuilder.build();

		short nextTable = (short) (tableId + 1);

		InstructionsBuilder isb = createGoToNextTableInstruction(nextTable);

		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	static FlowBuilder createSrcAddressPortProtocolMatchGoToNextFlow(Ipv4Address address, int port, short protocol,
			short tableId, FlowId flowId, FlowCookie flowCookie) {

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("SrcAddressPortProtocolMatchGoToNextFlow").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();

		createEthernetTypeMatch(matchBuilder, 0x800);
		createSrcProtocolPortMatch(matchBuilder, protocol, port);
		createSrcIpv4Match(matchBuilder, address);

		Match match = matchBuilder.build();
		short nextTable = (short) (tableId + 1);

		InstructionsBuilder isb = createGoToNextTableInstruction(nextTable);

		flowBuilder.setMatch(match).setInstructions(isb.build())
				.setPriority(BaseappConstants.MATCHED_GOTO_FLOW_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(0)
				.setIdleTimeout(0).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	static FlowBuilder createSrcIpAddressProtocolDestMacMatchGoTo(Ipv4Address srcIp, MacAddress dstMac, int port,
			short protocol, short tableId, BigInteger metadata, BigInteger metadataMask, int timeout, FlowId flowId,
			FlowCookie flowCookie) {

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("SrcIpAddressProtocolDestMacMatchGoTo").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();

		createSrcIpv4Match(matchBuilder, srcIp);
		createEthernetDestMatch(matchBuilder, dstMac);
		FlowUtils.createSrcProtocolPortMatch(matchBuilder, protocol, port);

		InstructionsBuilder isb = FlowUtils.createGoToNextTableAndSendToControllerInstruction((short) (tableId + 1),
				metadata, metadataMask);

		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(BaseappConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(timeout / 2)
				.setIdleTimeout(timeout).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	static FlowBuilder createSrcIpAddressProtocolDestMacMatchGoTo(Ipv4Address srcIp, MacAddress dstMac, int port,
			short protocol, short tableId, short targetTableId, BigInteger metadata, BigInteger metadataMask,
			int timeout, FlowId flowId, FlowCookie flowCookie) {

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId)
				.setFlowName("SrcIpAddressProtocolDestMacMatchGoTo").setId(flowId).setKey(new FlowKey(flowId))
				.setCookie(flowCookie);

		MatchBuilder matchBuilder = new MatchBuilder();

		createSrcIpv4Match(matchBuilder, srcIp);
		createEthernetDestMatch(matchBuilder, dstMac);
		FlowUtils.createSrcProtocolPortMatch(matchBuilder, protocol, port);

		InstructionsBuilder isb = FlowUtils.createGoToNextTableAndSendToControllerInstruction(targetTableId, metadata,
				metadataMask);

		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(BaseappConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(timeout / 2)
				.setIdleTimeout(timeout).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

	public static FlowBuilder createSrcIpAddressProtocolMatchGoTo(Ipv4Address srcIp, int port, short protocol,
			short tableId, BigInteger metadata, BigInteger metadataMask, int timeout, FlowId flowId,
			FlowCookie flowCookie) {

		FlowBuilder flowBuilder = new FlowBuilder().setTableId(tableId).setFlowName("srcIpAddressProtocolMatchGoTo")
				.setId(flowId).setKey(new FlowKey(flowId)).setCookie(flowCookie);
		MatchBuilder matchBuilder = new MatchBuilder();

		createSrcIpv4Match(matchBuilder, srcIp);
		createSrcProtocolPortMatch(matchBuilder, protocol, port);
		ArrayList<Instruction> instructions = new ArrayList<>();
		FlowUtils.addGoToTableInstruction(instructions, (short) (tableId + 1));
		InstructionsBuilder isb = new InstructionsBuilder();
		isb.setInstruction(instructions);
		flowBuilder.setMatch(matchBuilder.build()).setInstructions(isb.build())
				.setPriority(BaseappConstants.MAX_PRIORITY).setBufferId(OFConstants.ANY).setHardTimeout(timeout / 2)
				.setIdleTimeout(timeout).setFlags(new FlowModFlags(false, false, false, false, false));

		return flowBuilder;
	}

}
