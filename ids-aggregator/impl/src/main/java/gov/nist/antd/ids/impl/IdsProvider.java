/*
 * Copyright © 2017 Public Domain and others.  All rights reserved.
 *
 */
package gov.nist.antd.ids.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.notify.NotificationSubscriptionService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.ids.config.rev170915.IdsConfigData;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.accounts.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;


import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class IdsProvider {

	private static final Logger LOG = LoggerFactory.getLogger(IdsProvider.class);

	private PacketProcessingService packetProcessingService;
	
	private final DataBroker dataBroker;

	private FlowCommitWrapper flowCommitWrapper;

	private HashMap<String, InstanceIdentifier<FlowCapableNode>> uriToNodeMap = new HashMap<>();

	private HashMap<String, IdsConfigData> idsConfigMap = new HashMap<>();

	// A map between IDS node and ports for the IDS where it announces itself.
	// This is garbage collected (TBD)
	private HashMap<String, ArrayList<IdsPort>> idsNodeToPortMap = new HashMap<>();

	// Maps the instance identifier to the URI of the node (should not need this
	// map)

	private HashMap<String, HashMap<String, String>> nodeConnectorMap = new HashMap<>();
	
	private HashMap<String,MappingNotification> mappingNotificationMap = new HashMap<>();

	private Topology topology;

	private NotificationService notificationService;
	
	private RpcProviderRegistry  rpcProviderRegistry;


	class IdsPort {
		String portUri;
		long time = System.currentTimeMillis();
		int port;

		public IdsPort(String portUri) {
			this.portUri = portUri;
			this.port = Integer.parseInt(portUri.split(":")[2]);
		}

		@Override
		public int hashCode() {
			return portUri.hashCode();
		}

		@Override
		public boolean equals(Object that) {
			if (!that.getClass().equals(IdsPort.class)) {
				return false;
			} else {
				return ((IdsPort) that).portUri.equals(this.portUri);
			}
		}

		public void updateTimestamp() {
			this.time = System.currentTimeMillis();
		}
	}

	public IdsProvider(final DataBroker dataBroker, 
		PacketProcessingService packetProcessingService, 
		RpcProviderRegistry rpcProviderRegistry,
		NotificationService notificationService) {
		this.dataBroker = dataBroker;
		this.packetProcessingService = packetProcessingService;
		this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);
		this.notificationService = notificationService;
	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("IdsProvider Session Initiated");
		this.notificationService.registerNotificationListener( new MappingNotificationListener(this));
	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		LOG.info("IdsProvider Closed");
	}

	public PacketProcessingService getPacketProcessingService() {
		return this.packetProcessingService;
	}

	public FlowCommitWrapper getFlowCommitWrapper() {
		return this.flowCommitWrapper;
	}

	public void putInUriToNodeMap(String nodeUri, InstanceIdentifier<FlowCapableNode> nodePath) {
		this.uriToNodeMap.put(nodeUri, nodePath);
	}

	public void addIdsConfig(Uri idsNodeUri, IdsConfigData idsConfigData) {
		this.idsConfigMap.put(idsNodeUri.getValue(), idsConfigData);
	}

	public void setTopology(Topology topology) {
		this.topology = topology;
	}

	/**
	 * Get the flow capable node id from the node uri.
	 * 
	 * @param nodeUri
	 *            -- the node URI
	 * @return -- the flow capable node.
	 */
	public synchronized InstanceIdentifier<FlowCapableNode> getNode(String nodeUri) {
		return uriToNodeMap.get(nodeUri);
	}

	public synchronized Collection<InstanceIdentifier<FlowCapableNode>> getNodes() {
		return uriToNodeMap.values();
	}

	public synchronized void garbageCollectIdsRegistrationRecords() {
		for (Iterator<String> keys = idsNodeToPortMap.keySet().iterator(); keys.hasNext();) {
			String key = keys.next();
			for (Iterator<IdsPort> idsPortIterator = idsNodeToPortMap.get(key).iterator(); idsPortIterator.hasNext();) {
				IdsPort idsPort = idsPortIterator.next();
				if (System.currentTimeMillis() - idsPort.time > 2 * SdnMudConstants.DEFAULT_IDS_IDLE_TIMEOUT * 1000) {
					idsPortIterator.remove();
				}
			}
			LOG.info("removeIdsPort : removing " + key);
			if (idsNodeToPortMap.get(key).isEmpty()) {
				keys.remove();
			}

		}

	}

	public synchronized List<Integer> getIdsPorts(String idsNodeId) {
		if (this.idsNodeToPortMap.get(idsNodeId) == null) {
			return null;
		}
		ArrayList<Integer> retval = new ArrayList<Integer>();
		for (IdsPort idsPort : idsNodeToPortMap.get(idsNodeId)) {
			retval.add(idsPort.port);
		}
		return retval;
	}

	public synchronized Collection<IdsConfigData> getIdsConfigs() {
		return this.idsConfigMap.values();
	}

	public Topology getTopology() {
		return topology;
	}

	/**
	 * All CPE nodes.
	 * 
	 * @return -- a collection of CPE nodes.
	 */
	public Collection<InstanceIdentifier<FlowCapableNode>> getCpeNodes() {
		Collection<InstanceIdentifier<FlowCapableNode>> retval = new HashSet<>();
		for (Link link : topology.getLink()) {
			for (Uri cpeUri : link.getCpeSwitches()) {
				if (this.uriToNodeMap.containsKey(cpeUri.getValue())) {
					retval.add(this.uriToNodeMap.get(cpeUri.getValue()));
				}
			}
		}
		return retval;
	}

	public Collection<InstanceIdentifier<FlowCapableNode>> getNpeNodes() {
		Collection<InstanceIdentifier<FlowCapableNode>> retval = new HashSet<>();
		for (Link link : topology.getLink()) {
			if (this.uriToNodeMap.containsKey(link.getNpeSwitch().getValue())) {
				retval.add(this.uriToNodeMap.get(link.getNpeSwitch().getValue()));
			}
		}
		return retval;
	}

	/**
	 * Set the node connector to send packets from a sourceNodeUri to a
	 * destinationNodeUri.
	 * 
	 * @param sourceNodeUri
	 *            -- the node URI of the source node.
	 * @param destinationNodeUri
	 *            -- the node URI of the destination node.
	 * @param nodeConnectorUri
	 *            -- the connector URI (to install in a flow rule for packet
	 *            diversion).
	 */
	public void setNodeConnector(String sourceNodeUri, String destinationNodeUri, String nodeConnectorUri) {
		LOG.debug("setNodeConnector : sourceNodeUri = " + sourceNodeUri + " destinationNodeUri = " + destinationNodeUri
				+ " nodeConnectorUri " + nodeConnectorUri);
		HashMap<String, String> nodeMap = this.nodeConnectorMap.get(sourceNodeUri);
		if (nodeMap == null) {
			nodeMap = new HashMap<String, String>();
			nodeConnectorMap.put(sourceNodeUri, nodeMap);
		}
		nodeMap.put(destinationNodeUri, nodeConnectorUri);
	}

	/**
	 * Get the node connector URI to send packets from sourceNodeUri to
	 * destinationNodeUri.
	 * 
	 * @param sourceNodeUri
	 *            -- the source node URI.
	 * @param destinationNodeUri
	 *            -- the destinationNodeUri
	 * @return -- the connectorUri
	 */
	public String getNodeConnector(String sourceNodeUri, String destinationNodeUri) {
		if (this.nodeConnectorMap.get(sourceNodeUri) == null) {
			return null;
		} else {
			return this.nodeConnectorMap.get(sourceNodeUri).get(destinationNodeUri);
		}
	}

	public synchronized void addIdsPort(String idsNodeId, String inPortUri) {
		LOG.info("addIdsPort " + idsNodeId + " inPortUri " + inPortUri);
		ArrayList<IdsPort> ports = this.idsNodeToPortMap.get(idsNodeId);
		if (ports == null) {
			ports = new ArrayList<IdsPort>();
			this.idsNodeToPortMap.put(idsNodeId, ports);
		}

		for (IdsPort port : ports) {
			if (port.portUri.equals(inPortUri)) {
				port.updateTimestamp();
				return;
			}
		}

		IdsPort port = new IdsPort(inPortUri);

		ports.add(port);
	}
	
	public NotificationService getNotificationService() {
		return this.notificationService;
	}

	public boolean isNpeSwitch(String nodeUri) {
		if (this.topology == null) {
			return false;
		}
		for (Link link : this.topology.getLink() ) {
			if ( link.getNpeSwitch().getValue().equals(nodeUri)) return true;
		}
		return false;
	}

	public void addMappingNotification(MappingNotification notification) {
		 String mudUrl = notification.getMappingInfo().getMudUrl().getValue();
		 mappingNotificationMap.put(mudUrl, notification);
	}

	public MappingNotification getMappingNotification(String mudUrl) {
		return this.mappingNotificationMap.get(mudUrl);
	}
	

}
