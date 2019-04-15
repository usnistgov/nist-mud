/*
 *
 *
 * Copyright (C) 2017 Public Domain.  No rights reserved.
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
 * Parts of this file include code from here:
 *
 * https://github.com/opendaylight/daexim/blob/stable/nitrogen/impl/src/main/java/org/opendaylight/daexim/impl/ImportTask.java
 *
 * Which has the following copyright:
 *
 * Copyright (C) 2016 AT&T Intellectual Property. All rights reserved.
 * Copyright (c) 2016 Brocade Communications Systems, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package gov.nist.antd.sdnmud.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.sdnmud.impl.dhcp.DhcpPacket;
import gov.nist.antd.sdnmud.impl.dhcp.DhcpRequestPacket;

import gov.nist.antd.sdnmud.impl.dns.ARecord;
import gov.nist.antd.sdnmud.impl.dns.DNSInput;
import gov.nist.antd.sdnmud.impl.dns.Header;
import gov.nist.antd.sdnmud.impl.dns.Message;
import gov.nist.antd.sdnmud.impl.dns.Record;
import gov.nist.antd.sdnmud.impl.dns.Section;
import gov.nist.antd.sdnmud.impl.dns.Type;

/**
 * Packet in dispatcher that gets invoked on flow table miss when a packet is
 * sent up to the controller.
 *
 * @author mranga@nist.gov
 *
 */
public class PacketInDispatcher implements PacketProcessingListener {

	private SdnmudProvider sdnmudProvider;

	private SchemaService schemaService;

	private DOMDataBroker domDataBroker;

	private static final Logger LOG = LoggerFactory.getLogger(PacketInDispatcher.class);

	private int mudRelatedPacketInCounter = 0;

	private int packetInCounter = 0;

	// Set of MAC addresses that are unclassified or for which no MUD uri has been
	// assigned.
	private HashSet<String> unclassifiedMacAddresses = new HashSet<String>();

	// Set of Mac addresses for which a source mac classification rule exists
	private HashSet<String> srcMacRuleTable = new HashSet<String>();
	// Set of mac addresses for which a dst mac classification rule exists
	private HashSet<String> dstMacRuleTable = new HashSet<String>();
	// Flow rules in the first two tables -- these can be cleared via an API
	private HashSet<Flow> flowTable = new HashSet<Flow>();

	private Timer timer = new Timer();

	private ArrayList<Socket> listeners = new ArrayList<Socket>();

	private ServerSocket listenerSock;

	private boolean isClosed;

	private boolean isBlocked;

	private Thread notifier;

	private class UnclassifiedMacAddressTimerTask extends TimerTask {
		String unclassifiedMacAddress;

		public UnclassifiedMacAddressTimerTask(String macAddress) {
			this.unclassifiedMacAddress = macAddress;
		}

		@Override
		public void run() {
			unclassifiedMacAddresses.remove(unclassifiedMacAddress);
		}
	}

	private class SrcMacAddressTimerTask extends TimerTask {

		private MacAddress macAddress;

		public SrcMacAddressTimerTask(MacAddress macAddress) {
			this.macAddress = macAddress;
		}

		@Override
		public void run() {
			PacketInDispatcher.this.srcMacRuleTable.remove(macAddress.getValue());
		}

	}

	private class DstMacAddressTimerTask extends TimerTask {
		private MacAddress macAddress;

		public DstMacAddressTimerTask(MacAddress macAddress) {
			this.macAddress = macAddress;
		}

		@Override
		public void run() {
			PacketInDispatcher.this.dstMacRuleTable.remove(macAddress.getValue());

		}

	}

	public void close() {
		try {
			this.listenerSock.close();
			for (Socket s : this.listeners) {
				s.close();
			}
			this.isClosed = true;
		} catch (IOException e) {
			LOG.error("Error closing socket");
		}

	}

	public void block() {
		this.isBlocked = true;
	}

	public void unblock() {
		this.isBlocked = false;
	}

	private class UnclassifiedMacAddressNotificationServer implements Runnable {

		private UnclassifiedMacAddressNotificationServer(int port) {
			try {
				listenerSock = new ServerSocket(port);
			} catch (IOException e) {
				LOG.error("Cannot open server socket ", e);
			}
		}

		public void run() {
			try {
				while (true) {
					Socket clientSock = listenerSock.accept();
					listeners.add(clientSock);
				}
			} catch (IOException ex) {
				LOG.error("Cannot open server socket -- exiting thread.", ex);
				return;
			}
		}

	}

	public void startNotificationThread() {
		int notificationPort = sdnmudProvider.getSdnmudConfig().getNotificationPort().intValue();
		LOG.info("start thread on notification port " + notificationPort);
		this.notifier = new Thread(new UnclassifiedMacAddressNotificationServer(notificationPort));
		notifier.setDaemon(true);
		notifier.start();
	}

	public PacketInDispatcher(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
		this.domDataBroker = sdnmudProvider.getDomDataBroker();
		this.schemaService = sdnmudProvider.getSchemaService();
		this.startNotificationThread();
	}

	public Collection<String> getUnclassifiedMacAddresses() {
		return this.unclassifiedMacAddresses;
	}

	public int getMudPacketInCount(boolean clearFlag) {
		int retval = this.mudRelatedPacketInCounter;
		if (clearFlag) {
			mudRelatedPacketInCounter = 0;
		}
		return retval;
	}

	public int getPacketInCount(boolean clearFlag) {
		int retval = this.packetInCounter;
		if (clearFlag) {
			packetInCounter = 0;
		}
		return retval;
	}

	public void clearPacketInCount() {
		mudRelatedPacketInCounter = 0;
		packetInCounter = 0;
	}

	private static MacAddress rawMacToMac(final byte[] rawMac) {
		MacAddress mac = null;
		if (rawMac != null) {
			StringBuilder sb = new StringBuilder();
			for (byte octet : rawMac) {
				sb.append(String.format(":%02X", octet));
			}
			mac = new MacAddress(sb.substring(1));
		}
		return mac;
	}

	private void broadcastStateChange() {
		ArrayList<Socket> deadSockets = new ArrayList<Socket>();
		for (Socket s : listeners) {
			try {
				PrintWriter pw = new PrintWriter(s.getOutputStream());
				for (String macAddr : this.unclassifiedMacAddresses) {
					pw.println(macAddr);
				}
			} catch (IOException e) {
				try {
					deadSockets.add(s);
					s.close();
				} catch (IOException e1) {
					LOG.error("Error writing to listener socket -- closing", e1);
				}
			}
		}

		// Remove the dead sockets from our list of listeners.
		for (Socket d : deadSockets) {
			this.listeners.remove(d);
		}
	}

	private void addUclassifiedAddresses(String addr) {
		if (!this.unclassifiedMacAddresses.contains(addr)) {
			LOG.debug("addUnclassifiedAddress : " + addr);
			this.unclassifiedMacAddresses.add(addr);
			// Clears out the table in the timeout time.
			timer.schedule(new UnclassifiedMacAddressTimerTask(addr),
					2 * sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout() * 1000);

			this.broadcastStateChange();
		}
	}

	private void classifyAddress(MacAddress macAddress, boolean hasMudProfile, boolean isLocalAddress) {
		if (isLocalAddress || !hasMudProfile) {
			if (!this.isMacAddressExcluded(macAddress.getValue())) {
				this.addUclassifiedAddresses(macAddress.getValue());
			} else {
				this.removeUnclassifiedAddress(macAddress.getValue());
			}
		} else {
			this.removeUnclassifiedAddress(macAddress.getValue());
		}
	}

	private void removeUnclassifiedAddress(String addr) {
		if (this.unclassifiedMacAddresses.contains(addr)) {
			LOG.debug("removeUnclassifiedAddress : " + addr);
			this.unclassifiedMacAddresses.remove(addr);
			this.broadcastStateChange();
		}
	}

	private void transmitPacket(PacketReceived notification) {

		TransmitPacketInputBuilder tpib = new TransmitPacketInputBuilder().setPayload(notification.getPayload())
				.setBufferId(OFConstants.OFP_NO_BUFFER);
		tpib.setEgress(notification.getIngress());
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));

		String matchInPortUri = notification.getMatch().getInPort().getValue();

		output.setOutputNodeConnector(new Uri(matchInPortUri));
		ActionBuilder ab = new ActionBuilder();
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(1);

		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());
		tpib.setAction(actionList);

		this.sdnmudProvider.getPacketProcessingService().transmitPacket(tpib.build());
	}

	private boolean isMacAddressExcluded(String macAddress) {
		// TODO -- this should return TRUE if the host is a network service.
		// This is for IDS support TBD.
		return false;
	}

	private boolean isLocalAddress(String nodeId, String ipAddress) {
		if (sdnmudProvider.getLocalNetworksExclude(nodeId) != null) {
			for (String host : sdnmudProvider.getLocalNetworksExclude(nodeId)) {
				if (host.equals(ipAddress))
					return false;
			}
		}
		boolean isLocalAddress = false;
		if (sdnmudProvider.getLocalNetworks(nodeId) != null) {
			for (String localNetworkStr : sdnmudProvider.getLocalNetworks(nodeId)) {
				LOG.debug("localNetworkStr = " + localNetworkStr);
				String[] pieces = localNetworkStr.split("/");
				int prefixLength = Integer.valueOf(pieces[1]) / 8;

				String[] pieces1 = pieces[0].split("\\.");
				String prefix = "";

				for (int i = 0; i < prefixLength; i++) {
					prefix = prefix + pieces1[i] + ".";
				}
				LOG.debug("prefix = " + prefix);
				if (ipAddress.startsWith(prefix)) {
					isLocalAddress = true;
					break;
				}
			}
		}
		return isLocalAddress;
	}

	private void installSrcMacMatchStampManufacturerModelFlowRules(MacAddress srcMac, boolean isLocalAddress,
			String mudUri, InstanceIdentifier<FlowCapableNode> node) {
		String manufacturer = IdUtils.getAuthority(mudUri);
		int manufacturerId = IdUtils.getManfuacturerId(manufacturer);
		int modelId = IdUtils.getModelId(mudUri);
		int flag = 0;
		if (isLocalAddress) {
			flag = 1;
		}

		LOG.debug("installStampSrcManufacturerModelFlowRules : dstMac = " + srcMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " mudUri " + mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(flag).shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK)
				.or(SdnMudConstants.SRC_NETWORK_MASK);

		FlowCookie flowCookie = SdnMudConstants.SRC_MANUFACTURER_STAMP_FLOW_COOKIE;

		int timeout = this.sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout().intValue();

		String flowIdStr = SdnMudConstants.SRC_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX + "/"
				+ srcMac.getValue() + "/" + metadata.toString(16);

		FlowId flowId = new FlowId(flowIdStr);

		Flow flow = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(srcMac, metadata, metadataMask,
				sdnmudProvider.getSrcDeviceManufacturerStampTable(), flowId, flowCookie, timeout).build();
		flowTable.add(flow);
		sdnmudProvider.getFlowWriter().writeFlow(flow, node);
		this.srcMacRuleTable.add(srcMac.getValue());
		timer.schedule(new SrcMacAddressTimerTask(srcMac),
				sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout() / 2 * 1000);

	}

	private void installDstMacMatchStampManufacturerModelFlowRules(MacAddress dstMac, boolean isLocalAddress,
			String mudUri, InstanceIdentifier<FlowCapableNode> node) {

		String manufacturer = IdUtils.getAuthority(mudUri);
		int manufacturerId = IdUtils.getManfuacturerId(manufacturer);
		int modelId = IdUtils.getModelId(mudUri);
		int flag = 0;
		if (isLocalAddress) {
			flag = 1;
		}

		LOG.debug("installStampDstManufacturerModelFlowRules : dstMac = " + dstMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " mudUri " + mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(flag).shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		int timeout = this.sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout().intValue();
		BigInteger metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK)
				.or(SdnMudConstants.DST_NETWORK_MASK);
		FlowCookie flowCookie = SdnMudConstants.DST_MANUFACTURER_MODEL_FLOW_COOKIE;

		String flowIdStr = SdnMudConstants.DEST_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX + "/"
				+ dstMac.getValue() + "/" + metadata.toString(16);

		FlowId flowId = new FlowId(flowIdStr);
		Flow flow = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(dstMac, metadata, metadataMask,
				sdnmudProvider.getDstDeviceManufacturerStampTable(), flowId, flowCookie, timeout).build();
		flowTable.add(flow);
		this.dstMacRuleTable.add(dstMac.getValue());
		// Supress further notification processing for CacheTimeout/2 seconds (keeps the
		// switch from flooding
		// the controller)
		this.timer.schedule(new DstMacAddressTimerTask(dstMac),
				sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout() / 2 * 1000);

		sdnmudProvider.getFlowWriter().writeFlow(flow, node);
	}

	/**
	 *
	 */
	public void clearMfgModelRules() {
		LOG.info("Clear mfgModelRules");

		for (String nodeId : sdnmudProvider.getMudCpeNodeIds()) {
			InstanceIdentifier<FlowCapableNode> flowCapableNode = sdnmudProvider.getNode(nodeId);
			if (flowCapableNode != null) {
				for (Flow f : this.flowTable) {
					sdnmudProvider.getFlowWriter().deleteFlows(flowCapableNode, f);
				}
			}
		}
		this.flowTable.clear();
		this.srcMacRuleTable.clear();
		this.dstMacRuleTable.clear();

	}

	@SuppressWarnings("unchecked")
	@Override
	public void onPacketReceived(PacketReceived notification) {

		if (this.isClosed) {
			LOG.info("ignore packet -- closed");
			return;
		}

		if (this.sdnmudProvider.getCpeSwitches().isEmpty()) {
			LOG.error("No switches found -- ignoring packet");
			return;
		}

		if (this.isBlocked) {
			LOG.info("Blocked - installing flows ");
			return;
		}

		Ethernet ethernet = new Ethernet();

		try {
			ethernet.deserialize(notification.getPayload(), 0,
					notification.getPayload().length * NetUtils.NumBitsInAByte);

		} catch (Exception ex) {
			LOG.error("Error deserializing packet", ex);
		}

		// Extract various fields from the ethernet packet.

		int etherType = ethernet.getEtherType() < 0 ? 0xffff + ethernet.getEtherType() + 1 : ethernet.getEtherType();
		byte[] srcMacRaw = ethernet.getSourceMACAddress();
		byte[] dstMacRaw = ethernet.getDestinationMACAddress();

		// Extract the src mac address from the packet.
		MacAddress srcMac = rawMacToMac(srcMacRaw);
		MacAddress dstMac = rawMacToMac(dstMacRaw);

		short tableId = notification.getTableId().getValue();

		FlowCookie cookie = notification.getFlowCookie();

		String matchInPortUri = notification.getMatch().getInPort().getValue();

		String nodeId = notification.getIngress().getValue().firstKeyOf(Node.class).getId().getValue();

		InstanceIdentifier<FlowCapableNode> node = this.sdnmudProvider.getNode(nodeId);

		LOG.debug("onPacketReceived : matchInPortUri = " + matchInPortUri + " nodeId  " + nodeId + " tableId " + tableId
				+ " srcMac " + srcMac.getValue() + " dstMac " + dstMac.getValue() + "etherType = " + etherType);

		this.packetInCounter++;

		if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {
			LOG.debug("LLDP Pakcet -- dropping it");
			return;
		}

		if (etherType == SdnMudConstants.ETHERTYPE_IPV4 || etherType == SdnMudConstants.ETHERTYPE_CUSTOMER_VLAN) {
			String srcIp = PacketUtils.extractSrcIpStr(notification.getPayload());
			String dstIp = PacketUtils.extractDstIpStr(notification.getPayload());
			LOG.debug("Source IP  " + srcIp + " dest IP  " + dstIp);

			if (!this.sdnmudProvider.isCpeNode(nodeId)) {
				return;
			}
			byte[] rawPacket = notification.getPayload();

			if (tableId == sdnmudProvider.getSrcDeviceManufacturerStampTable()) {
				// Keeps track of the number of packets seen at controller.
				this.mudRelatedPacketInCounter++;
				if (srcMacRuleTable.contains(srcMac.getValue())) {
					// Rule is in cache so we dont bother
					LOG.info("already installed -- returning");
					return;
				}
				Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(srcMac);

				boolean isLocalAddress = mudUri.getValue().equals(SdnMudConstants.UNCLASSIFIED)
						&& this.isLocalAddress(nodeId, srcIp);

				installSrcMacMatchStampManufacturerModelFlowRules(srcMac, isLocalAddress, mudUri.getValue(), node);

				// TODO -- IDS support.

				// this.classifyAddress(srcMac, hasMudProfile, isLocalAddress);
				if (this.dstMacRuleTable.contains(dstMac.getValue())) {
					LOG.info("dst mac rule already installed in table 1");
					return;
				}
				mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(dstMac);

				isLocalAddress = mudUri.getValue().equals(SdnMudConstants.UNCLASSIFIED)
						&& this.isLocalAddress(nodeId, dstIp);

				installDstMacMatchStampManufacturerModelFlowRules(dstMac, isLocalAddress, mudUri.getValue(), node);

				// TODO -- IDS support.
				// boolean hasMudProfile = this.sdnmudProvider.hasMudProfile(mudUri.getValue());
				// this.classifyAddress(dstMac,
				// this.sdnmudProvider.hasMudProfile(mudUri.getValue()), isLocalAddress);

			} else if (tableId == sdnmudProvider.getDstDeviceManufacturerStampTable()) {
				this.mudRelatedPacketInCounter++;
				Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(dstMac);
				boolean isLocalAddress = mudUri.getValue().equals(SdnMudConstants.UNCLASSIFIED)
						&& this.isLocalAddress(nodeId, dstIp);
				if (this.dstMacRuleTable.contains(dstMac.getValue())) {
					LOG.info("already installed in table 1");
					return;
				}
				installDstMacMatchStampManufacturerModelFlowRules(dstMac, isLocalAddress, mudUri.getValue(), node);
				// TODO -- IDS support.
				// this.classifyAddress(dstMac,
				// this.sdnmudProvider.hasMudProfile(mudUri.getValue()), isLocalAddress);

			} else if (tableId == sdnmudProvider.getSdnmudRulesTable()) {
				this.mudRelatedPacketInCounter++;
				LOG.debug("PacketInDispatcher: Packet packetIn from SDNMUD_RULES_TABLE");
				int protocol = PacketUtils.extractIpProtocol(rawPacket);

				LOG.info("PacketInDispatcher: protocol = " + protocol + " srcIp = " + srcIp);

				if (cookie.equals(SdnMudConstants.DH_REQUEST_FLOW_COOKIE)) {
					// this is a DH request.
					DhcpPacket dhcpPacket = DhcpPacket.decodeFullPacket(notification.getPayload(), DhcpPacket.ENCAP_L2);

					LOG.info("DHCP packet type = " + dhcpPacket.getClass().getName());

					// TODO -- include DH Discover here.
					if (dhcpPacket instanceof DhcpRequestPacket) {
						DhcpRequestPacket dhcpRequestPacket = (DhcpRequestPacket) dhcpPacket;
						String mudUrl = dhcpRequestPacket.getMudUrl();

						if (mudUrl != null) {
							MappingBuilder mb = new MappingBuilder();
							ArrayList<MacAddress> macAddresses = new ArrayList<>();
							macAddresses.add(srcMac);
							mb.setDeviceId(macAddresses);
							mb.setMudUrl(new Uri(mudUrl));
							InstanceIdentifier<Mapping> mappingId = InstanceIdentifier.builder(Mapping.class).build();
							ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();

							tx.merge(LogicalDatastoreType.CONFIGURATION, mappingId, mb.build());
							tx.submit();
						}
					}
				} else if (cookie.equals(SdnMudConstants.DNS_REQUEST_FLOW_COOKIE)) {
					LOG.info("Saw a DNS Request");
				} else if (cookie.equals(SdnMudConstants.DNS_RESPONSE_FLOW_COOKIE)) {
					LOG.info("Saw a DNS response");
					try {
						byte[] payload = PacketUtils.getPacketPayload(notification.getPayload(), etherType, protocol);
						Message message = new Message(payload);
						LOG.info("Message = " + message);
						Record[] records = message.getSectionArray(Section.ANSWER);
						for (Record record : records) {
							if (record.getType() == Type.A) {
								LOG.info("A record");
								ARecord arecord = (ARecord) record;
								// Get the name resolution
								InetAddress inetAddress = arecord.getAddress();
								// Add it to the resolution cache of the MudFlows installer
								LOG.info("Name " + record.getName());
								LOG.info("Address " + inetAddress.getHostAddress());
								sdnmudProvider.getNameResolutionCache().addCacheLookup(node,record.getName().toString(true), inetAddress.getHostAddress());
								sdnmudProvider.getMudFlowsInstaller().fixupNameResolution(node,
										record.getName().toString(true), inetAddress.getHostAddress());
							}
						}
					} catch (IOException e) {
						LOG.error("Could not resolve the DNS answer ", e);
					}

				}

			}

		}
	}

}
