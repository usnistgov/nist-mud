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
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.AceViolationNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuarantineDevice;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuarantineDeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.ace.violation.notification.AceViolationInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.mapping.notification.MappingInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.mapping.notification.mapping.info.MappingInfoList;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.mapping.notification.mapping.info.MappingInfoListBuilder;
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

import gov.nist.antd.sdnmud.impl.dhcp.DhcpOfferPacket;
import gov.nist.antd.sdnmud.impl.dhcp.DhcpPacket;
import gov.nist.antd.sdnmud.impl.dhcp.DhcpRequestPacket;
import gov.nist.antd.sdnmud.impl.dns.ARecord;
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

	private static final Logger LOG = LoggerFactory.getLogger(PacketInDispatcher.class);

	private int mudRelatedPacketInCounter = 0;

	private int packetInCounter = 0;

	// Set of MAC addresses that are unclassified or for which no MUD uri has been
	// assigned.
	private HashSet<MacAddress> unclassifiedMacAddresses = new HashSet<MacAddress>();

	private HashMap<String, BigInteger> srcMetadataMap = new HashMap<String, BigInteger>();

	// Set of Mac addresses for which a source mac classification rule exists
	private HashMap<String, BigInteger> srcMacRuleTable = new HashMap<String, BigInteger>();

	private HashMap<String, BigInteger> dstMetadataMap = new HashMap<String, BigInteger>();
	// Set of mac addresses for which a dst mac classification rule exists
	private HashMap<String, BigInteger> dstMacRuleTable = new HashMap<String, BigInteger>();
	// Flow rules in the first two tables -- these can be cleared via an API
	private HashSet<Flow> flowTable = new HashSet<Flow>();
	// The set of mac addresses that were seen when a packet was dropped.
	private HashSet<MacAddress> dropRuleTable = new HashSet<MacAddress>();

	private Timer timer = new Timer();

	private ArrayList<Socket> listeners = new ArrayList<Socket>();

	private ServerSocket listenerSock;

	private boolean isClosed;

	private boolean isBlocked;

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

	private class DropRuleTableTimerTask extends TimerTask {
		private MacAddress macAddress;

		public DropRuleTableTimerTask(MacAddress macAddress) {
			this.macAddress = macAddress;
		}

		public void run() {
			dropRuleTable.remove(macAddress);
		}
	}

	public void close() {
		this.timer.cancel();
		this.isClosed = true;
	}

	public void block() {
		this.isBlocked = true;
	}

	public void unblock() {
		this.isBlocked = false;
	}

	public PacketInDispatcher(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	public Collection<MacAddress> getUnclassifiedMacAddresses() {
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

	/*
	 * Generate and send a notification on the internal bus. This can be used for
	 * service composition - e.g. service that is interested in knowing how
	 * addresses are mapped by the controller.
	 */
	private void broadcastStateChange() {
		LOG.debug("Broadcast state change");
		MappingNotificationBuilder mappingNotificationBuilder = new MappingNotificationBuilder();
		MappingInfoBuilder mappingInfoBuilder = new MappingInfoBuilder();
		MappingInfoListBuilder mappingInfoListBuilder = new MappingInfoListBuilder();
		Map<Uri, HashSet<MacAddress>> map = this.sdnmudProvider.getMappingDataStoreListener().getMapping();
		ArrayList<MappingInfoList> mappingInfoArrayList = new ArrayList<MappingInfoList>();
		for (Uri uri : map.keySet()) {
			mappingInfoListBuilder.setMudUrl(uri);
			List<MacAddress> macAddressList = new ArrayList<MacAddress>();
			Collection<MacAddress> mappedAddresses = map.get(uri);
			for (MacAddress macAddr : mappedAddresses) {
				if (srcMacRuleTable.containsKey(macAddr.getValue())
						|| dstMacRuleTable.containsKey(macAddr.getValue())) {
					macAddressList.add(macAddr);
				}
			}
			if (!macAddressList.isEmpty()) {
				mappingInfoListBuilder.setDeviceId(macAddressList);
				mappingInfoListBuilder.setMudUrl(uri);
				mappingInfoArrayList.add(mappingInfoListBuilder.build());
			}
		}

		if (!this.unclassifiedMacAddresses.isEmpty()) {
			List<MacAddress> macAddressList = new ArrayList<MacAddress>();
			mappingInfoListBuilder.setMudUrl(new Uri(SdnMudConstants.UNCLASSIFIED));
			for (MacAddress macAddress : this.unclassifiedMacAddresses) {
				macAddressList.add(macAddress);
			}
			mappingInfoListBuilder.setDeviceId(macAddressList);
			mappingInfoArrayList.add(mappingInfoListBuilder.build());
			mappingInfoBuilder.setMappingInfoList(mappingInfoArrayList);
			mappingNotificationBuilder.setMappingInfo(mappingInfoBuilder.build());
		}

		sdnmudProvider.getNotificationPublishService().offerNotification(mappingNotificationBuilder.build());
	}

	/**
	 * Broadcast an ACE violation on the local bus. Save the MAC in our qurarantene
	 * database.
	 * 
	 * @param srcMac -- the violator
	 * @param mudUri -- the MUD URL of the violator.
	 */
	private void broadcastAceViolation(MacAddress srcMac, Uri mudUri) {
		AceViolationNotificationBuilder aceViolationNotificationBulder = new AceViolationNotificationBuilder();
		AceViolationInfoBuilder aceViolationInfoBuilder = new AceViolationInfoBuilder();
		aceViolationInfoBuilder.setMacAddress(srcMac);
		aceViolationInfoBuilder.setMudUrl(mudUri);
		aceViolationNotificationBulder.setAceViolationInfo(aceViolationInfoBuilder.build());
		sdnmudProvider.getNotificationPublishService().offerNotification(aceViolationNotificationBulder.build());
		QuarantineDevice quarantineDevices = sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices();
		if (!quarantineDevices.getQurantineMac().contains(srcMac)) {
			quarantineDevices.getQurantineMac().add(srcMac);
			InstanceIdentifier<QuarantineDevice> qId = InstanceIdentifier.builder(QuarantineDevice.class).build();
			ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();
			tx.put(LogicalDatastoreType.CONFIGURATION, qId, quarantineDevices);
			tx.submit();
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

	private boolean isLocalAddress(String nodeId, String ipAddress) {
		if (sdnmudProvider.getLocalNetworksExclude(nodeId) != null) {
			for (String host : sdnmudProvider.getLocalNetworksExclude(nodeId)) {
				if (host.equals(ipAddress))
					return false;
			}
		}
		Map<String, List<Ipv4Address>> controllerClassMap = sdnmudProvider.getControllerClassMap(nodeId);
		Ipv4Address ipAddr = new Ipv4Address(ipAddress);
		for (List<Ipv4Address> addList : controllerClassMap.values()) {
			if (addList.contains(ipAddr)) {
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

	private boolean isQuarantene(MacAddress macAddress) {
		if (this.sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices() == null) {
			return false;
		} else {
			return this.sdnmudProvider.getQuaranteneDevicesListener().getQuaranteneDevices().getQurantineMac()
					.contains(macAddress);
		}
	}

	private void installSrcMacMatchStampManufacturerModelFlowRules(MacAddress srcMac, boolean isLocalAddress,
			boolean isQurantened, boolean isBlocked, String mudUri, InstanceIdentifier<FlowCapableNode> node) {
		String manufacturer = IdUtils.getAuthority(mudUri);
		int manufacturerId = IdUtils.getManfuacturerId(manufacturer);
		int modelId = IdUtils.getModelId(mudUri);
		int localAddressFlag = isLocalAddress ? 1 : 0;

		int quaranteneFlag = isQurantened ? 1 : 0;
		int blockedFlag = isBlocked ? 1 : 0;

		LOG.debug("installStampSrcManufacturerModelFlowRules : dstMac = " + srcMac.getValue() + " isLocalAddress "
				+ "isQuarantine " + isQurantened + isLocalAddress + " mudUri " + mudUri);

		BigInteger metadata = (BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT))
				.or(BigInteger.valueOf(localAddressFlag).shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(blockedFlag).shiftLeft(SdnMudConstants.SRC_MAC_BLOCKED_MASK_SHIFT))
				.or(BigInteger.valueOf(quaranteneFlag).shiftLeft(SdnMudConstants.SRC_QUARANTENE_MASK_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK)
				.or(SdnMudConstants.SRC_NETWORK_MASK).or(SdnMudConstants.SRC_QUARANTENE_MASK)
				.or(SdnMudConstants.SRC_MAC_BLOCKED_MASK);

		FlowCookie flowCookie = SdnMudConstants.SRC_MANUFACTURER_STAMP_FLOW_COOKIE;

		int timeout = this.sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout().intValue();

		String flowIdStr = SdnMudConstants.SRC_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX + "/"
				+ srcMac.getValue() + "/" + metadata.toString(16);

		FlowId flowId = new FlowId(flowIdStr);

		Flow flow = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(srcMac, metadata, metadataMask,
				sdnmudProvider.getSrcDeviceManufacturerStampTable(), flowId, flowCookie, timeout).build();
		flowTable.add(flow);
		sdnmudProvider.getFlowWriter().writeFlow(flow, node);
		this.srcMetadataMap.put(srcMac.getValue(), metadata);
		this.srcMacRuleTable.put(srcMac.getValue(), metadata);
		timer.schedule(new SrcMacAddressTimerTask(srcMac),
				sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout() / 2 * 1000);
		// Classification state has changed -- broadcast it.
		this.broadcastStateChange();

	}

	private void installDstMacMatchStampManufacturerModelFlowRules(MacAddress dstMac, boolean isLocalAddress,
			boolean isQurarantened, boolean isBlocked, String mudUri, InstanceIdentifier<FlowCapableNode> node) {

		String manufacturer = IdUtils.getAuthority(mudUri);
		int manufacturerId = IdUtils.getManfuacturerId(manufacturer);
		int modelId = IdUtils.getModelId(mudUri);
		int isLocalAddressFlag = isLocalAddress ? 1 : 0;
		int isQurantenedFlag = isQurarantened ? 1 : 0;
		int isBlockedFlag = isBlocked ? 1 : 0;

		LOG.info("installStampDstManufacturerModelFlowRules : dstMac = " + dstMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " isQuarantened " + isQurarantened + " isBlocked " + isBlocked + " mudUri " + mudUri
				+ " mfgId " + manufacturerId + " modelId " + modelId);

		BigInteger metadata = BigInteger.valueOf(manufacturerId << SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId << SdnMudConstants.DST_MODEL_SHIFT))
				.or(BigInteger.valueOf(isBlockedFlag << SdnMudConstants.DST_MAC_BLOCKED_MASK_SHIFT))
				.or(BigInteger.valueOf(isQurantenedFlag << SdnMudConstants.DST_QUARANTENE_FLAGS_SHIFT))
				.or(BigInteger.valueOf(isLocalAddressFlag << SdnMudConstants.DST_NETWORK_FLAGS_SHIFT));

		int timeout = this.sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout().intValue();
		BigInteger metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK)
				.or(SdnMudConstants.DST_NETWORK_MASK).or(SdnMudConstants.DST_QURANTENE_MASK)
				.or(SdnMudConstants.DST_MAC_BLOCKED_MASK);
		FlowCookie flowCookie = SdnMudConstants.DST_MANUFACTURER_MODEL_FLOW_COOKIE;

		String flowIdStr = SdnMudConstants.DEST_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX + "/"
				+ dstMac.getValue() + "/" + metadata.toString(16);

		FlowId flowId = new FlowId(flowIdStr);
		Flow flow = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(dstMac, metadata, metadataMask,
				sdnmudProvider.getDstDeviceManufacturerStampTable(), flowId, flowCookie, timeout).build();
		flowTable.add(flow);
		this.dstMetadataMap.put(dstMac.getValue(), metadata);
		this.dstMacRuleTable.put(dstMac.getValue(), metadata);
		// Supress further notification processing for CacheTimeout/2 seconds (keeps the
		// switch from flooding the controller)
		this.timer.schedule(new DstMacAddressTimerTask(dstMac),
				sdnmudProvider.getSdnmudConfig().getMfgIdRuleCacheTimeout() / 2 * 1000);

		sdnmudProvider.getFlowWriter().writeFlow(flow, node);
		this.broadcastStateChange();
	}

	/**
	 *
	 */
	public synchronized void clearMfgModelRules() {
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
		this.srcMetadataMap.clear();
		this.dstMetadataMap.clear();

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

		if ( node == null) {
			LOG.error("Node not found " + nodeId);
			return;
		}
		
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
				if (!srcMacRuleTable.containsKey(srcMac.getValue())) {
					boolean isQuarantened = this.isQuarantene(srcMac);
					boolean isBlocked = this.sdnmudProvider.getMappingDataStoreListener().isBlocked(srcMac);
					Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(srcMac);

					boolean isLocalAddress = mudUri.getValue().equals(SdnMudConstants.UNCLASSIFIED)
							&& this.isLocalAddress(nodeId, srcIp);

					installSrcMacMatchStampManufacturerModelFlowRules(srcMac, isLocalAddress, isQuarantened, isBlocked,
							mudUri.getValue(), node);

					if (isLocalAddress) {
						this.unclassifiedMacAddresses.add(srcMac);
					}
				}

				if (!dstMacRuleTable.containsKey(dstMac.getValue())) {
					// Broadcast notification for mappings.
					Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(dstMac);
					boolean isLocalAddress = mudUri.getValue().equals(SdnMudConstants.UNCLASSIFIED)
							&& this.isLocalAddress(nodeId, dstIp);
					if (isLocalAddress) {
						this.unclassifiedMacAddresses.add(dstMac);
					}
					boolean isQuarantened = this.isQuarantene(dstMac);
					boolean isBlocked = this.sdnmudProvider.getMappingDataStoreListener().isBlocked(dstMac);
					installDstMacMatchStampManufacturerModelFlowRules(dstMac, isLocalAddress, isQuarantened, isBlocked,
							mudUri.getValue(), node);
				}

			} else if (tableId == sdnmudProvider.getDstDeviceManufacturerStampTable()) {
				this.mudRelatedPacketInCounter++;
				if (!dstMacRuleTable.containsKey(dstMac.getValue())) {
					Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(dstMac);
					boolean isLocalAddress = mudUri.getValue().equals(SdnMudConstants.UNCLASSIFIED)
							&& this.isLocalAddress(nodeId, dstIp);
					if (this.dstMacRuleTable.containsKey(dstMac.getValue())) {
						LOG.info("already installed in table 1");
						return;
					}
					boolean isQurarantene = this.isQuarantene(dstMac);
					boolean isBlocked = this.sdnmudProvider.getMappingDataStoreListener().isBlocked(dstMac);

					installDstMacMatchStampManufacturerModelFlowRules(dstMac, isLocalAddress, isQurarantene, isBlocked,
							mudUri.getValue(), node);
					// Broadcast notifications for mappings seen at the switch.
				}

			} else if (tableId == sdnmudProvider.getSrcMatchTable()) {
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
						synchronized (this) {
							if (mudUrl != null) {
								LOG.info("MUD URL = " + mudUrl);
								MappingBuilder mb = new MappingBuilder();
								ArrayList<MacAddress> macAddresses = new ArrayList<>();
								Uri mudUri = new Uri(mudUrl);
								/*
								HashSet<MacAddress> currentMacAddresses = sdnmudProvider.getMappingDataStoreListener()
										.getMapping().get(mudUri);
								macAddresses.add(srcMac);
								if (currentMacAddresses != null) {
									macAddresses.addAll(currentMacAddresses);
								}
								*/
								mb.setDeviceId(macAddresses);
								mb.setMudUrl(mudUri);
								InstanceIdentifier<Mapping> mappingId = InstanceIdentifier.builder(Mapping.class)
										.build();
								
								ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();
								
								tx.put(LogicalDatastoreType.CONFIGURATION, mappingId, mb.build());
								try {
									tx.submit().get();
								} catch (InterruptedException | ExecutionException e) {
									LOG.error("Failed to submit transaction");
								}
							} else {
								LOG.info("Mud URL is null");
							}
						}
					}
				} else if (cookie.equals(SdnMudConstants.DH_RESPONSE_FLOW_COOKIE)) {
					DhcpPacket dhcpPacket = DhcpPacket.decodeFullPacket(notification.getPayload(), DhcpPacket.ENCAP_L2);
					LOG.info("DHCP Response packet type " + dhcpPacket.getClass().getName());
					if (dhcpPacket instanceof DhcpOfferPacket) {
						DhcpOfferPacket dhcpOfferPacket = (DhcpOfferPacket) dhcpPacket;
						int leaseTime = dhcpOfferPacket.getLeaseTime();
						// when lease expires, should the device be blocked?
						// For now just log it as informational.
						LOG.info("Lease time is " + leaseTime);
					}
				} else if (cookie.equals(SdnMudConstants.DNS_REQUEST_FLOW_COOKIE)) {
					LOG.info("Saw a DNS Request");
				} else if (cookie.equals(SdnMudConstants.DNS_RESPONSE_FLOW_COOKIE)) {
					LOG.info("Saw a DNS response");
					try {
						byte[] payload = PacketUtils.getPacketPayload(notification.getPayload(), etherType, protocol);
						Message message = new Message(payload);
						LOG.debug("Message = " + message);
						Record[] records = message.getSectionArray(Section.ANSWER);
						for (Record record : records) {
							if (record.getType() == Type.A) {
								ARecord arecord = (ARecord) record;
								// Get the name resolution
								InetAddress inetAddress = arecord.getAddress();

								// Add it to the resolution cache of the MudFlows installer
								LOG.info("A record Name = " + record.getName() + " address = " + inetAddress.getHostAddress());
								sdnmudProvider.getNameResolutionCache().addCacheLookup(node,
										record.getName().toString(true), inetAddress.getHostAddress());
								sdnmudProvider.getMudFlowsInstaller().fixupDnsNameResolution(IdUtils.getNodeUri(node),
										record.getName().toString(true), inetAddress.getHostAddress());
							}
						}
					} catch (IOException e) {
						LOG.error("Could not resolve the DNS answer ", e);
					}

				} else if (cookie.equals(SdnMudConstants.DROP_FLOW_COOKIE)) {
					LOG.info("Saw an ACL violation - device is misbehaving.");
					// TBD -- generate event and send to update service.
					if (dropRuleTable.contains(srcMac)) {
						LOG.debug("DROP rule -- already saw the src MAC -- ingoring packet");
						return;
					}
					Uri mudUri = sdnmudProvider.getMappingDataStoreListener().getMudUri(srcMac);
					this.dropRuleTable.add(srcMac);
					this.broadcastAceViolation(srcMac, mudUri);
					// Start a timer so we will be interrupted again after this period of time.
					// We don't want to keep getting interrupted
					timer.schedule(new DropRuleTableTimerTask(srcMac), SdnMudConstants.DROP_RULE_TIMEOUT * 1000 / 2);

				} else if (cookie.equals(SdnMudConstants.TCP_SYN_MATCH_CHECK_COOKIE)) {
					LOG.info("Saw a TCP SYN ACL violation");
					Uri mudUri = sdnmudProvider.getMappingDataStoreListener().getMudUri(srcMac);
					// TBD -- generate event and send to update service.
					if (dropRuleTable.contains(srcMac)) {
						LOG.debug("DROP rule -- already saw the src MAC -- ingoring packet");
						return;
					}
					this.dropRuleTable.add(srcMac);
					this.broadcastAceViolation(srcMac, mudUri);
					timer.schedule(new DropRuleTableTimerTask(srcMac), SdnMudConstants.DROP_RULE_TIMEOUT * 1000 / 2);

				}

			}

		}
	}

	public BigInteger getSrcMetadata(String macAddress) {
		return this.srcMetadataMap.get(macAddress);
	}

	public BigInteger getDstMetadata(String macAddress) {
		return this.dstMetadataMap.get(macAddress);
	}

}
