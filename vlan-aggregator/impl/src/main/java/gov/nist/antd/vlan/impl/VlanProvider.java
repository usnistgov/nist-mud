/*
 * Copyright © 2017 Public Domain and others.  All rights reserved.
 *
 */
package gov.nist.antd.vlan.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.CpeCollections;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.accounts.MudNodes;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.links.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings("ucd")
public class VlanProvider {

	private static final Logger LOG = LoggerFactory.getLogger(VlanProvider.class);

	private PacketProcessingService packetProcessingService;

	private final DataBroker dataBroker;

	private FlowCommitWrapper flowCommitWrapper;

	private HashMap<String, InstanceIdentifier<FlowCapableNode>> uriToNodeMap = new HashMap<>();

	// Maps the instance identifier to the URI of the node (should not need this
	// map)

	private HashMap<String, HashMap<String, String>> nodeConnectorMap = new HashMap<>();

	private Topology topology;

	private NotificationService notificationService;

	private RpcProviderRegistry rpcProviderRegistry;

	private gov.nist.antd.vlan.impl.TopologyDataStoreListener topoDataStoreListener;
	
	private CpeCollectionsDataStoreListener cpeCollectionsDataStoreListener;

	private WakeupOnFlowCapableNode wakeupListener;

	private ListenerRegistration<WakeupOnFlowCapableNode> wakeupOnFlowCapableNodeRegistration;

	private CpeCollections cpeCollections;

	private HashMap<String, HashSet<Uri>> cpeMap = new HashMap<>();

	private static InstanceIdentifier<Topology> getTopologyWildCardPath() {
		return InstanceIdentifier.create(Topology.class);
	}

	private static InstanceIdentifier<FlowCapableNode> getWildcardPath() {
		return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
	}
	
	private static InstanceIdentifier<CpeCollections> getCpeCollectionsWildCardPath() {
		return InstanceIdentifier.create(CpeCollections.class);
	}


	@SuppressWarnings("ucd")
	public VlanProvider(final DataBroker dataBroker, PacketProcessingService packetProcessingService,
			RpcProviderRegistry rpcProviderRegistry, NotificationService notificationService) {
		this.dataBroker = dataBroker;
		this.packetProcessingService = packetProcessingService;
		this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);
		this.notificationService = notificationService;

	}

	/**
	 * Method called when the blueprint container is created.
	 */
	@SuppressWarnings("ucd")
	public void init() {
		LOG.info("VlanProvider Session Initiated");
		InstanceIdentifier<Topology> topoWildCardPath = getTopologyWildCardPath();
		final DataTreeIdentifier<Topology> topoId = new DataTreeIdentifier<Topology>(LogicalDatastoreType.CONFIGURATION,
				topoWildCardPath);
		this.topoDataStoreListener = new TopologyDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(topoId, topoDataStoreListener);
		
		final DataTreeIdentifier<CpeCollections> cpeConfigId = new DataTreeIdentifier<CpeCollections>(LogicalDatastoreType.CONFIGURATION,
				getCpeCollectionsWildCardPath());
		this.cpeCollectionsDataStoreListener = new CpeCollectionsDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(cpeConfigId, cpeCollectionsDataStoreListener);

		this.wakeupListener = new WakeupOnFlowCapableNode(this);
		final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier = new DataTreeIdentifier<FlowCapableNode>(
				LogicalDatastoreType.OPERATIONAL, getWildcardPath());
		this.wakeupOnFlowCapableNodeRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
				wakeupListener);
		
		PacketProcessingListenerImpl packetInDispatcher = new PacketProcessingListenerImpl(this);
		ListenerRegistration<PacketProcessingListenerImpl> registration = notificationService.registerNotificationListener(packetInDispatcher);
		packetInDispatcher.setListenerRegistration(registration);
	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	@SuppressWarnings("ucd")
	public void close() {
		this.wakeupOnFlowCapableNodeRegistration.close();
		LOG.info("VlanProvider Closed");
	}

	PacketProcessingService getPacketProcessingService() {
		return this.packetProcessingService;
	}

	FlowCommitWrapper getFlowCommitWrapper() {
		return this.flowCommitWrapper;
	}

	void putInUriToNodeMap(String nodeUri, InstanceIdentifier<FlowCapableNode> nodePath) {
		this.uriToNodeMap.put(nodeUri, nodePath);
	}
	
	void removeFromUriToNodeMap(String nodeUri) {
		this.uriToNodeMap.remove(nodeUri);
	}

    void setTopology(Topology topology) {
		this.topology = topology;
	}

	synchronized InstanceIdentifier<FlowCapableNode> getNode(String nodeUri) {
		return uriToNodeMap.get(nodeUri);
	}

	synchronized Collection<InstanceIdentifier<FlowCapableNode>> getNodes() {
		return uriToNodeMap.values();
	}

	Topology getTopology() {
		return topology;
	}

    Collection<InstanceIdentifier<FlowCapableNode>> getCpeNodes() {
		Collection<InstanceIdentifier<FlowCapableNode>> retval = new HashSet<>();
		for (MudNodes link : this.cpeCollections.getMudNodes()) {
			for (Uri cpeUri : link.getCpeSwitches()) {
				if (this.uriToNodeMap.containsKey(cpeUri.getValue())) {
					retval.add(this.uriToNodeMap.get(cpeUri.getValue()));
				}
			}
		}
		return retval;
	}

	Collection<InstanceIdentifier<FlowCapableNode>> getNpeNodes() {
		Collection<InstanceIdentifier<FlowCapableNode>> retval = new HashSet<>();
		for (Link link : topology.getLink()) {
			if (this.uriToNodeMap.containsKey(link.getNpeSwitch().getValue())) {
				retval.add(this.uriToNodeMap.get(link.getNpeSwitch().getValue()));
			}
		}
		return retval;
	}

	boolean isNpeSwitch(String nodeUri) {
		if (this.topology == null) {
			return false;
		}
		for (Link link : this.topology.getLink()) {
			if (link.getNpeSwitch().getValue().equals(nodeUri))
				return true;
		}
		return false;
	}
	
	boolean isVnfSwitch(String nodeUri) {
		if (this.topology == null) {
			return false;
		}
		for (Link link : this.topology.getLink()) {
			if (link.getVnfSwitch().getValue().equals(nodeUri))
				return true;
		}
		return false;
	}

	boolean isCpeNode(String nodeId) {
		if (this.cpeCollections == null) {
			return false;
		}
		for (MudNodes mudNodes : this.cpeCollections.getMudNodes()) {
			for (Uri uri : mudNodes.getCpeSwitches()) {
				if (uri.getValue().equals(nodeId))
					return true;
			}
		}
		return false;
	}


	WakeupOnFlowCapableNode getWakeupListener() {
		return this.wakeupListener;
	}

	 void setCpeCollections(CpeCollections cpeCollections) {
		this.cpeCollections = cpeCollections;
		for (MudNodes mudNodes : cpeCollections.getMudNodes()) {
			String key = mudNodes.getKey().getName();
			HashSet<Uri> cpes = new HashSet<>();
			cpes.addAll(mudNodes.getCpeSwitches());
			LOG.info("setCpeCollections : key " + key);
			this.cpeMap.put(key, cpes);
		}
	}

	int  getCpeTag(String nodeId) {
		String nodeKey = null;
		for (MudNodes mudNodes : this.cpeCollections.getMudNodes()) {
			for (Uri cpeUri : mudNodes.getCpeSwitches()) {
				if (cpeUri.getValue().equals(nodeId)) {
					nodeKey = mudNodes.getKey().getName();
					break;
				}
			}
		}
		if (nodeKey == null) {
			LOG.error("getCpeTag : " + nodeId + " nodeKey " + nodeKey);
			return -1;
		}
		for (Link link : this.topology.getLink()) {
			if (link.getCpeSwitchesId().equals(nodeKey)) {
				return (int) link.getTag().intValue();
			}
		}
		LOG.error("getCpeTag : " + nodeId + " nodeKey " + nodeKey);
		return -1;
	}

	public int getVnfTag(String nodeId) {		
		for (Link link : this.topology.getLink()) {
			if (link.getVnfSwitch().getValue().equals(nodeId)) {
				return (int) link.getTag().intValue();
			}
		}
		return -1;
	}

}
