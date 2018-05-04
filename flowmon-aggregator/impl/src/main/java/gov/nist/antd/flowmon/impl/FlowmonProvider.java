/*
 * Copyright (c) None.
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
package gov.nist.antd.flowmon.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.NistFlowControllerService;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfig;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.flowmon.config.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.baseapp.impl.FlowCommitWrapper;

public class FlowmonProvider {

	private static final Logger LOG = LoggerFactory.getLogger(FlowmonProvider.class);

	private PacketProcessingService packetProcessingService;

	private final DataBroker dataBroker;

	private FlowCommitWrapper flowCommitWrapper;

	private HashMap<String, InstanceIdentifier<FlowCapableNode>> uriToNodeMap = new HashMap<>();

	private HashMap<String, FlowmonConfigData> flowmonConfigMap = new HashMap<>();

	// A map between IDS node and ports for the IDS where it announces itself.
	// This is garbage collected (TBD)
	private HashMap<String, FlowmonPort> flowmonNodeToPortMap = new HashMap<>();

	private HashMap<String, String> flowmonOutputPortMap = new HashMap<>();

	private NotificationService notificationService;

	private RpcProviderRegistry rpcProviderRegistry;

	private WakeupOnFlowCapableNode wakeupListener;

	private ListenerRegistration<WakeupOnFlowCapableNode> wakeupOnFlowCapableNodeRegistration;

	private FlowmonConfigDataStoreListener flowmonConfigDataStoreListener;

	private Map<MacAddress, Mapping> macAddressToMappingMap = new HashMap<MacAddress, Mapping>();
	private Map<Uri, HashSet<MacAddress>> uriToMacs = new HashMap<Uri, HashSet<MacAddress>>();

	// Stores a set of NodeIds for a given mac address (identifies the switches
	// that have seen the mac addr).
	private HashMap<MacAddress, HashSet<String>> macToNodeIdMap = new HashMap<>();

	private MappingDataStoreListener mappingDataStoreListener;

	private Mapping setDeviceMapping;

	private OdlInterfaceRpcService ifMRpcService;

	private class FlowmonPort {
		private String portUri;
		private long time = System.currentTimeMillis();
		private int port;

		private FlowmonPort(String portUri) {
			this.portUri = portUri;
			this.port = Integer.parseInt(portUri);
		}

		@Override
		public int hashCode() {
			return portUri.hashCode();
		}

		@Override
		public boolean equals(Object that) {
			if (that == null || !that.getClass().equals(FlowmonPort.class)) {
				return false;
			} else {
				return ((FlowmonPort) that).portUri.equals(this.portUri);
			}
		}

		private void updateTimestamp() {
			this.time = System.currentTimeMillis();
		}
	}

	private static InstanceIdentifier<Topology> getTopologyWildCardPath() {
		return InstanceIdentifier.create(Topology.class);
	}

	private static InstanceIdentifier<FlowCapableNode> getWildcardPath() {
		return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
	}

	private static InstanceIdentifier<FlowmonConfig> getFlowmonConfigWildCardPath() {
		return InstanceIdentifier.create(FlowmonConfig.class);
	}

	private static InstanceIdentifier<Mapping> getMappingWildCardPath() {
		return InstanceIdentifier.create(Mapping.class);
	}

	public FlowmonProvider(final DataBroker dataBroker, PacketProcessingService packetProcessingService,
			RpcProviderRegistry rpcProviderRegistry, NotificationService notificationService) {
		this.dataBroker = dataBroker;
		this.packetProcessingService = packetProcessingService;
		this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);
		this.notificationService = notificationService;
		this.rpcProviderRegistry = rpcProviderRegistry;

	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("FlowmonProvider Session Initiated");

		this.flowmonConfigDataStoreListener = new FlowmonConfigDataStoreListener(this);
		final DataTreeIdentifier<FlowmonConfig> flowmonConfigId = new DataTreeIdentifier<FlowmonConfig>(
				LogicalDatastoreType.CONFIGURATION, getFlowmonConfigWildCardPath());
		this.dataBroker.registerDataTreeChangeListener(flowmonConfigId, flowmonConfigDataStoreListener);

		this.rpcProviderRegistry.addRpcImplementation(NistFlowControllerService.class,
				new NistFlowControllerServiceImpl(this));

		PacketProcessingListenerImpl packetInDispatcher = new PacketProcessingListenerImpl(this);
		ListenerRegistration<PacketProcessingListenerImpl> registration = getNotificationService()
				.registerNotificationListener(packetInDispatcher);
		packetInDispatcher.setListenerRegistration(registration);

		final InstanceIdentifier<Mapping> mappingWildCardPath = getMappingWildCardPath();
		final DataTreeIdentifier<Mapping> mappingTreeId = new DataTreeIdentifier<Mapping>(
				LogicalDatastoreType.CONFIGURATION, mappingWildCardPath);
		this.mappingDataStoreListener = new MappingDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(mappingTreeId, mappingDataStoreListener);

		this.rpcProviderRegistry.addRpcImplementation(NistFlowControllerService.class,
				new NistFlowControllerServiceImpl(this));

		this.wakeupListener = new WakeupOnFlowCapableNode(this);
		final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier = new DataTreeIdentifier<FlowCapableNode>(
				LogicalDatastoreType.OPERATIONAL, getWildcardPath());
		this.wakeupOnFlowCapableNodeRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
				wakeupListener);

		this.ifMRpcService = rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class);
		LOG.info("start() <--");

	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		this.wakeupOnFlowCapableNodeRegistration.close();
		LOG.info("FlowmonProvider Closed");
	}

	public PacketProcessingService getPacketProcessingService() {
		return this.packetProcessingService;
	}

	public FlowCommitWrapper getFlowCommitWrapper() {
		return this.flowCommitWrapper;
	}

	void putInUriToNodeMap(String nodeUri, InstanceIdentifier<FlowCapableNode> nodePath) {
		this.uriToNodeMap.put(nodeUri, nodePath);
	}

	void addFlowmonConfig(Uri flowmonNodeUri, FlowmonConfigData flowmonConfigData) {
		this.flowmonConfigMap.put(flowmonNodeUri.getValue(), flowmonConfigData);
	}

	public synchronized InstanceIdentifier<FlowCapableNode> getNode(String nodeUri) {
		return uriToNodeMap.get(nodeUri);
	}

	public synchronized Collection<InstanceIdentifier<FlowCapableNode>> getNodes() {
		return uriToNodeMap.values();
	}

	public synchronized void garbageCollectFlowmonRegistrationRecords() {
		for (Iterator<String> keys = flowmonNodeToPortMap.keySet().iterator(); keys.hasNext();) {
			String key = keys.next();
			FlowmonPort flowmonPort = flowmonNodeToPortMap.get(key);
			if (System.currentTimeMillis() - flowmonPort.time > 2 * FlowmonConstants.DEFAULT_IDS_IDLE_TIMEOUT * 1000) {
				keys.remove();
				LOG.info("removeFlowmonPort : removing " + key);

			}

		}

	}

	public synchronized String getFlowmonPort(String flowmonNodeId) {

		if (this.flowmonNodeToPortMap.containsKey(flowmonNodeId))
			return this.flowmonNodeToPortMap.get(flowmonNodeId).portUri;
		else
			return null;
	}

	public synchronized Collection<FlowmonConfigData> getFlowmonConfigs() {
		return this.flowmonConfigMap.values();
	}

	public synchronized void addFlowmonPort(String flowmonNodeId, String inPortUri) {
		LOG.info("addFlowmonPort " + flowmonNodeId + " inPortUri " + inPortUri);
		FlowmonPort ports = new FlowmonPort(inPortUri);
		this.flowmonNodeToPortMap.put(flowmonNodeId, ports);
	}

	public NotificationService getNotificationService() {
		return this.notificationService;
	}

	public boolean isVnfSwitch(String nodeUri) {

		return this.flowmonConfigMap.containsKey(nodeUri);

	}

	public WakeupOnFlowCapableNode getWakeupListener() {
		return this.wakeupListener;
	}

	void setFlowmonOutputPort(String destinationId, String matchInPortUri) {
		this.flowmonOutputPortMap.put(destinationId, matchInPortUri);
	}

	String getFlowmonOutputPort(String nodeId) {
		return this.flowmonOutputPortMap.get(nodeId);
	}

	public Collection<InstanceIdentifier<FlowCapableNode>> getVnfNodes() {
		HashSet<InstanceIdentifier<FlowCapableNode>> flowCapableNodes = new HashSet<>();
		for (String flowmonUri : this.flowmonConfigMap.keySet()) {
			if (this.getNode(flowmonUri) != null) {
				flowCapableNodes.add(this.getNode(flowmonUri));
			}
		}
		return flowCapableNodes;
	}

	public FlowmonConfigData getFlowmonConfig(String nodeId) {
		return this.flowmonConfigMap.get(nodeId);
	}

	public void setDeviceMapping(Mapping mapping) {
		this.setDeviceMapping = mapping;
	}

	public Mapping getDeviceMapping() {
		return this.setDeviceMapping;
	}

	public synchronized void putInMacToNodeIdMap(MacAddress srcMac, String nodeId) {
		HashSet<String> nodes;
		if (!macToNodeIdMap.containsKey(srcMac)) {
			nodes = new HashSet<String>();
			this.macToNodeIdMap.put(srcMac, nodes);
		} else {
			nodes = macToNodeIdMap.get(srcMac);
		}
		nodes.add(nodeId);
	}

	public synchronized Collection<String> getNodeId(MacAddress deviceMacAddress) {
		return this.macToNodeIdMap.get(deviceMacAddress);
	}

	private void removeMacAddress(MacAddress macAddress) {
		Mapping mapping = macAddressToMappingMap.remove(macAddress);
		if (mapping != null) {
			HashSet<MacAddress> macs = uriToMacs.get(mapping.getMudUrl());
			macs.remove(macAddress);
			if (macs.size() == 0) {
				uriToMacs.remove(mapping.getMudUrl());
			}
		}
	}

	public Uri getMudUri(MacAddress macAddress) {
		Mapping mapping = macAddressToMappingMap.get(macAddress);
		if (mapping != null) {
			return mapping.getMudUrl();
		} else {
			return new Uri(FlowmonConstants.UNCLASSIFIED);
		}
	}

	public void addDeviceMapping(MacAddress mac, Mapping mapping) {
		// TODO Auto-generated method stub
		removeMacAddress(mac);
		LOG.info("Put MAC address mapping " + mac + " uri " + mapping.getMudUrl().getValue());

		macAddressToMappingMap.put(mac, mapping);
		HashSet<MacAddress> macs = uriToMacs.get(mapping.getMudUrl());
		if (macs == null) {
			macs = new HashSet<MacAddress>();
			uriToMacs.put(mapping.getMudUrl(), macs);
		}
		macs.add(mac);
	}

	public boolean isInitialized() {
		return !this.flowmonConfigMap.isEmpty();
	}

	public Collection<String> getVnfNodeIds() {
		return this.flowmonConfigMap.keySet();
	}

	public OdlInterfaceRpcService getInterfaceManagerRpcService() {
		return this.ifMRpcService;
	}

}
