/*
 * Copyright © 2017 Public Domain and others.  All rights reserved.
 *
 */
package gov.nist.antd.flowmon.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.CpeCollections;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.accounts.MudNodes;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.NistFlowControllerService;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingNotification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.links.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	// Maps the instance identifier to the URI of the node (should not need this
	// map)

	private HashMap<String, MappingNotification> mappingNotificationMap = new HashMap<>();

	private Topology topology;

	private NotificationService notificationService;

	private RpcProviderRegistry rpcProviderRegistry;

	private TopologyDataStoreListener topoDataStoreListener;

	private CpeCollectionsDataStoreListener cpeCollectionsDataStoreListener;

	private WakeupOnFlowCapableNode wakeupListener;

	private ListenerRegistration<WakeupOnFlowCapableNode> wakeupOnFlowCapableNodeRegistration;

	private CpeCollections cpeCollections;

	private HashMap<String, HashSet<Uri>> cpeMap = new HashMap<>();

	private FlowmonConfigDataStoreListener flowmonConfigDataStoreListener;

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

	private static InstanceIdentifier<CpeCollections> getCpeCollectionsWildCardPath() {
		return InstanceIdentifier.create(CpeCollections.class);
	}

	private static InstanceIdentifier<FlowmonConfigData> getFlowmonConfigWildCardPath() {
		return InstanceIdentifier.create(FlowmonConfigData.class);
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
		this.notificationService.registerNotificationListener(new MappingNotificationListener(this));
		InstanceIdentifier<Topology> topoWildCardPath = getTopologyWildCardPath();
		final DataTreeIdentifier<Topology> topoId = new DataTreeIdentifier<Topology>(LogicalDatastoreType.CONFIGURATION,
				topoWildCardPath);
		this.topoDataStoreListener = new TopologyDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(topoId, topoDataStoreListener);

		final DataTreeIdentifier<CpeCollections> cpeConfigId = new DataTreeIdentifier<CpeCollections>(
				LogicalDatastoreType.CONFIGURATION, getCpeCollectionsWildCardPath());
		this.cpeCollectionsDataStoreListener = new CpeCollectionsDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(cpeConfigId, cpeCollectionsDataStoreListener);

		this.flowmonConfigDataStoreListener = new FlowmonConfigDataStoreListener(this);
		final DataTreeIdentifier<FlowmonConfigData> flowmonConfigId = new DataTreeIdentifier<FlowmonConfigData>(
				LogicalDatastoreType.CONFIGURATION, getFlowmonConfigWildCardPath());
		this.dataBroker.registerDataTreeChangeListener(flowmonConfigId, flowmonConfigDataStoreListener);

		this.wakeupListener = new WakeupOnFlowCapableNode(this);
		final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier = new DataTreeIdentifier<FlowCapableNode>(
				LogicalDatastoreType.OPERATIONAL, getWildcardPath());
		this.wakeupOnFlowCapableNodeRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
				wakeupListener);

		InstanceIdentifier<Flow> flowPath = InstanceIdentifier.builder(Nodes.class).child(Node.class)
				.augmentation(FlowCapableNode.class).child(Table.class).child(Flow.class).build();
		final DataTreeIdentifier<Flow> flowDataTreeIdentifier = new DataTreeIdentifier<Flow>(
				LogicalDatastoreType.CONFIGURATION, flowPath);
		FlowChangeListener flowChangeListener = new FlowChangeListener(this);
		dataBroker.registerDataTreeChangeListener(flowDataTreeIdentifier, flowChangeListener);

		this.rpcProviderRegistry.addRpcImplementation(NistFlowControllerService.class,
				new NistFlowControllerServiceImpl(this));

		PacketProcessingListenerImpl packetInDispatcher = new PacketProcessingListenerImpl(this);
		ListenerRegistration<PacketProcessingListenerImpl> registration = getNotificationService()
				.registerNotificationListener(packetInDispatcher);
		packetInDispatcher.setListenerRegistration(registration);
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

	public void setTopology(Topology topology) {
		this.topology = topology;
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
			if (System.currentTimeMillis() - flowmonPort.time > 2 * SdnMudConstants.DEFAULT_IDS_IDLE_TIMEOUT * 1000) {
				keys.remove();
				LOG.info("removeFlowmonPort : removing " + key);

			}

		}

	}

	public synchronized String getFlowmonPort(String flowmonNodeId) {

		return this.flowmonNodeToPortMap.get(flowmonNodeId).portUri;
	}

	public synchronized Collection<FlowmonConfigData> getFlowmonConfigs() {
		return this.flowmonConfigMap.values();
	}

	public Topology getTopology() {
		return topology;
	}

	public Collection<InstanceIdentifier<FlowCapableNode>> getCpeNodes() {
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
	
	public Collection<InstanceIdentifier<FlowCapableNode>> getCpeNodes(String vnfSwitch) {
		Collection<InstanceIdentifier<FlowCapableNode>> retval = new HashSet<>();
		for (Link link : this.getTopology().getLink()) {
			if ( link.getVnfSwitch().equals(vnfSwitch)) {
			     String cpeCollectionId = link.getCpeSwitchesId(); 
			     for (MudNodes cpeNodes : cpeCollections.getMudNodes())  {
			    	 if ( cpeNodes.getName().equals(cpeCollectionId)) {
			    		 for (Uri nodeUri : cpeNodes.getCpeSwitches()) {
			    			InstanceIdentifier<FlowCapableNode> node =  this.getNode(nodeUri.getValue());
			    			if ( node != null ) retval.add(node);
			    		 }
			    	 }
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

	public synchronized void addFlowmonPort(String flowmonNodeId, String inPortUri) {
		LOG.info("addFlowmonPort " + flowmonNodeId + " inPortUri " + inPortUri);
		FlowmonPort ports = new FlowmonPort(inPortUri);
		this.flowmonNodeToPortMap.put(flowmonNodeId, ports);
	}

	public NotificationService getNotificationService() {
		return this.notificationService;
	}

	public boolean isNpeSwitch(String nodeUri) {
		if (this.topology == null) {
			return false;
		}
		for (Link link : this.topology.getLink()) {
			if (link.getNpeSwitch().getValue().equals(nodeUri))
				return true;
		}
		return false;
	}

	public boolean isVnfSwitch(String nodeUri) {
		if (this.topology == null) {
			return false;
		}
		for (Link link : this.topology.getLink()) {
			if (link.getVnfSwitch().getValue().equals(nodeUri))
				return true;
		}
		return false;
	}

	public boolean isCpeNode(String nodeId) {
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

	public void addMappingNotification(MappingNotification notification) {
		String mudUrl = notification.getMappingInfo().getMudUrl().getValue();
		mappingNotificationMap.put(mudUrl, notification);
	}

	public MappingNotification getMappingNotification(String mudUrl) {
		return this.mappingNotificationMap.get(mudUrl);
	}

	public WakeupOnFlowCapableNode getWakeupListener() {
		return this.wakeupListener;
	}

	public void setCpeCollections(CpeCollections cpeCollections) {
		this.cpeCollections = cpeCollections;
		for (MudNodes mudNodes : cpeCollections.getMudNodes()) {
			String key = mudNodes.getKey().getName();
			HashSet<Uri> cpes = new HashSet<>();
			cpes.addAll(mudNodes.getCpeSwitches());
			LOG.info("setCpeCollections : key " + key);
			this.cpeMap.put(key, cpes);
		}
	}

	public Collection<Uri> getCpeNodeFlowmon() {
		ArrayList<Uri> retval = new ArrayList<>();
		for (MudNodes mudNode : this.cpeCollections.getMudNodes()) {
			retval.addAll(mudNode.getCpeSwitches());
		}
		return retval;
	}

	public void setFlowmonOutputPort(String destinationId, String matchInPortUri) {
		this.flowmonOutputPortMap.put(destinationId, matchInPortUri);
	}

	public String getFlowmonOutputPort(String nodeId) {
		return this.flowmonOutputPortMap.get(nodeId);
	}

	public InstanceIdentifier<FlowCapableNode> getVnfNode(String cpeNodeId) {
		for (Link link : this.getTopology().getLink()) {
			Uri vnfSwitch = link.getVnfSwitch();
			String cpeCollectionId = link.getCpeSwitchesId(); 
			for (MudNodes cpeNodes : cpeCollections.getMudNodes())  {
				if ( cpeNodes.getName().equals(cpeCollectionId)) {
					for (Uri nodeUri : cpeNodes.getCpeSwitches()) {
						if ( cpeNodeId.equals(nodeUri.getValue())) {
							return this.getNode(vnfSwitch.getValue());
						}
					}
				}
			}
		}
		return null;
	}

}