/*
 * Copyright © 2017 Public Domain.
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
package gov.nist.antd.sdnmud.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180427.Acls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180412.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.CpeCollections;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;

import gov.nist.antd.baseapp.impl.FlowCommitWrapper;

public class SdnmudProvider {

	private static final Logger LOG = LoggerFactory.getLogger(SdnmudProvider.class);

	private final DataBroker dataBroker;

	private SalFlowService flowService;

	private PacketProcessingService packetProcessingService;

	private NotificationService notificationService;

	private ListenerRegistration<WakeupOnFlowCapableNode> dataTreeChangeListenerRegistration;

	private AclDataStoreListener aclDataStoreListener;

	private MappingDataStoreListener mappingDataStoreListener;

	private MudProfileDataStoreListener mudProfileDataStoreListener;

	private ControllerclassMappingDataStoreListener controllerClassMappingDataStoreListener;

	private Map<Uri, Mud> uriToMudMap = new HashMap<Uri, Mud>();

	// Stores a set of NodeIds for a given mac address (identifies the switches
	// that have seen the mac addr).
	private HashMap<MacAddress, HashSet<String>> macToNodeIdMap = new HashMap<>();

	// Stores a map between node ID and its InstanceIdentifier<FlowCapableNode>
	private HashMap<String, InstanceIdentifier<FlowCapableNode>> uriToNodeMap = new HashMap<>();

	// Map between the node URI and the mud uri.
	private HashMap<String, List<Uri>> nodeToMudUriMap = new HashMap<>();

	// A map between a mac address and the associated FlowCapableNodes where MUD
	// profiles were installed.
	// This is used to retrieve a set of nodes where MUD rules have been
	// installed for a given MAC address.
	private HashMap<String, HashSet<InstanceIdentifier<FlowCapableNode>>> mudNodesMap = new HashMap<>();

	// A map between the NODE uri and the Flow installer for that node.
	private HashMap<String, MudFlowsInstaller> flowInstallerMap = new HashMap<>();

	private FlowCommitWrapper flowCommitWrapper;

	private CpeCollectionsDataStoreListener topoDataStoreListener;

	private WakeupOnFlowCapableNode wakeupListener;

	private CpeCollections topology;

	private DOMDataBroker domDataBroker;

	private SchemaService schemaService;

	private BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer;

	public SdnmudProvider(final DataBroker dataBroker, SalFlowService flowService,
			PacketProcessingService packetProcessingService, NotificationService notificationService,
			DOMDataBroker domDataBroker, SchemaService schemaService,
			BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer) {

		LOG.info("SdnMudProvider: SdnMudProvider - init");
		this.dataBroker = dataBroker;
		this.flowService = flowService;
		this.packetProcessingService = packetProcessingService;
		this.notificationService = notificationService;
		this.domDataBroker = domDataBroker;
		this.schemaService = schemaService;
		this.bindingNormalizedNodeSerializer = bindingNormalizedNodeSerializer;

	}

	private static InstanceIdentifier<FlowCapableNode> getWildcardPath() {
		return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
	}

	private static InstanceIdentifier<Mud> getMudWildCardPath() {
		return InstanceIdentifier.create(Mud.class);
	}

	private static InstanceIdentifier<Acls> getAclWildCardPath() {
		return InstanceIdentifier.create(Acls.class);
	}

	private static InstanceIdentifier<Mapping> getMappingWildCardPath() {
		return InstanceIdentifier.create(Mapping.class);
	}

	private static InstanceIdentifier<ControllerclassMapping> getControllerClassMappingWildCardPath() {
		return InstanceIdentifier.create(ControllerclassMapping.class);
	}

	private static InstanceIdentifier<CpeCollections> getTopologyWildCardPath() {
		return InstanceIdentifier.create(CpeCollections.class);
	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("SdnmudProvider Session Initiated");

		this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);

		/* Register data tree change listener for Topology change */
		InstanceIdentifier<CpeCollections> topoWildCardPath = getTopologyWildCardPath();
		final DataTreeIdentifier<CpeCollections> topoId = new DataTreeIdentifier<CpeCollections>(
				LogicalDatastoreType.CONFIGURATION, topoWildCardPath);
		this.topoDataStoreListener = new CpeCollectionsDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(topoId, topoDataStoreListener);

		/* Register a data tree change listener for MUD profiles */
		InstanceIdentifier<Mud> mudWildCardPath = getMudWildCardPath();
		final DataTreeIdentifier<Mud> treeId = new DataTreeIdentifier<Mud>(LogicalDatastoreType.CONFIGURATION,
				mudWildCardPath);
		this.mudProfileDataStoreListener = new MudProfileDataStoreListener(dataBroker, this);
		this.dataBroker.registerDataTreeChangeListener(treeId, mudProfileDataStoreListener);

		/* Register a data tree change listener for ACL profiles */
		final InstanceIdentifier<Acls> aclWildCardPath = getAclWildCardPath();
		final DataTreeIdentifier<Acls> aclTreeId = new DataTreeIdentifier<Acls>(LogicalDatastoreType.CONFIGURATION,
				aclWildCardPath);
		this.aclDataStoreListener = new AclDataStoreListener(dataBroker, this);
		this.dataBroker.registerDataTreeChangeListener(aclTreeId, getAclDataStoreListener());

		/*
		 * Register a data tree change listener for MAC to MUD URL mapping. The
		 * MAC to URL mapping is provided by the system admin.
		 */
		final InstanceIdentifier<Mapping> mappingWildCardPath = getMappingWildCardPath();
		final DataTreeIdentifier<Mapping> mappingTreeId = new DataTreeIdentifier<Mapping>(
				LogicalDatastoreType.CONFIGURATION, mappingWildCardPath);
		this.mappingDataStoreListener = new MappingDataStoreListener(this);
		this.dataBroker.registerDataTreeChangeListener(mappingTreeId, mappingDataStoreListener);

		/* Listener for flow miss packets sent to the controller */
		PacketInDispatcher packetInDispatcher = new PacketInDispatcher(this);
		ListenerRegistration<PacketInDispatcher> registration = this.getNotificationService()
				.registerNotificationListener(packetInDispatcher);
		packetInDispatcher.setListenerRegistration(registration);

		/*
		 * Register a data tree change listener for Controller Class mapping. A
		 * controller class mapping maps a controller class URI to a list of
		 * internet addresses
		 */
		final InstanceIdentifier<ControllerclassMapping> controllerClassMappingWildCardPath = getControllerClassMappingWildCardPath();
		this.controllerClassMappingDataStoreListener = new ControllerclassMappingDataStoreListener(this);
		final DataTreeIdentifier<ControllerclassMapping> ccmappingTreeId = new DataTreeIdentifier<ControllerclassMapping>(
				LogicalDatastoreType.CONFIGURATION, controllerClassMappingWildCardPath);
		this.dataBroker.registerDataTreeChangeListener(ccmappingTreeId, controllerClassMappingDataStoreListener);
		// Create a listener that wakes up on a node being added.
		this.wakeupListener = new WakeupOnFlowCapableNode(this);
		final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier = new DataTreeIdentifier<FlowCapableNode>(
				LogicalDatastoreType.OPERATIONAL, getWildcardPath());
		this.dataTreeChangeListenerRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
				wakeupListener);
		LOG.info("start() <--");

	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		LOG.info("SdnmudProvider Closed");
		this.dataTreeChangeListenerRegistration.close();
		this.uriToMudMap.clear();
	}

	public MudProfileDataStoreListener getMudProfileDataStoreListener() {
		return this.mudProfileDataStoreListener;
	}

	/**
	 * @return the mappingDataStoreListener
	 */
	public MappingDataStoreListener getMappingDataStoreListener() {
		return mappingDataStoreListener;
	}

	/**
	 * @return the aclDataStoreListener
	 */
	public AclDataStoreListener getAclDataStoreListener() {
		return aclDataStoreListener;
	}

	public ControllerclassMappingDataStoreListener getControllerclassMappingDataStoreListener() {
		return this.controllerClassMappingDataStoreListener;
	}

	public FlowCommitWrapper getFlowCommitWrapper() {
		return this.flowCommitWrapper;
	}

	public NotificationService getNotificationService() {
		return this.notificationService;
	}

	public DataBroker getDataBroker() {
		return this.dataBroker;
	}

	public DOMDataBroker getDomDataBroker() {
		return this.domDataBroker;
	}

	public BindingNormalizedNodeSerializer getBindingNormalizedNodeSerializer() {
		return this.bindingNormalizedNodeSerializer;
	}

	/**
	 * Put in the node to URI map.
	 *
	 * @param nodeUri
	 *            -- the node Uri.
	 * @param nodePath
	 *            -- the flow capable node Instance Identifier.
	 */
	synchronized void putInUriToNodeMap(String nodeUri, InstanceIdentifier<FlowCapableNode> nodePath) {
		this.uriToNodeMap.put(nodeUri, nodePath);
	}

	/**
	 * Get the flow capable node id from the node uri.
	 *
	 * @param nodeUri
	 *            -- the node URI
	 * @return -- the flow capable node.
	 */
	synchronized InstanceIdentifier<FlowCapableNode> getNode(String nodeUri) {
		return uriToNodeMap.get(nodeUri);
	}

	synchronized Collection<InstanceIdentifier<FlowCapableNode>> getNodes() {
		return uriToNodeMap.values();
	}

	synchronized void removeNode(String nodeUri) {
		InstanceIdentifier<FlowCapableNode> node = this.uriToNodeMap.remove(nodeUri);
		if (node == null) {
			LOG.info("remvoeNode: Cannot find node to remove");
			return;
		}

		for (Iterator<MacAddress> it = macToNodeIdMap.keySet().iterator(); it.hasNext();) {
			MacAddress ma = it.next();
			HashSet<String> hs = this.macToNodeIdMap.get(ma);
			if (hs != null && hs.contains(nodeUri)) {
				hs.remove(nodeUri);
				if (hs.isEmpty()) {
					it.remove();
				}
			}
		}

		// clean up the mudNodesMap
		for (Iterator<String> mudNodesIterator = this.mudNodesMap.keySet().iterator(); mudNodesIterator.hasNext();) {
			String manufacturer = mudNodesIterator.next();
			this.mudNodesMap.get(manufacturer).remove(node);
			if (mudNodesMap.get(manufacturer).isEmpty()) {
				mudNodesIterator.remove();
			}
		}

	}

	/**
	 * Add a MUD node for this device MAC address.
	 *
	 * @param deviceMacAddress
	 *            -- mac address of device.
	 *
	 * @param node
	 *            -- the node to add.
	 */
	public void addMudNode(String manufacturerId, InstanceIdentifier<FlowCapableNode> node) {

		HashSet<InstanceIdentifier<FlowCapableNode>> nodes = this.mudNodesMap.get(manufacturerId);
		if (nodes == null) {
			nodes = new HashSet<InstanceIdentifier<FlowCapableNode>>();
			this.mudNodesMap.put(manufacturerId, nodes);
		}
		nodes.add(node);
	}

	/**
	 * Get the MUD nodes where flow rules were installed.
	 *
	 * @param deviceMacAddress
	 *            -- the mac address for which we want the flow capable node
	 *            set.
	 */
	public Collection<InstanceIdentifier<FlowCapableNode>> getMudNodes(String manufacturer) {
		return this.mudNodesMap.get(manufacturer);
	}

	public MudFlowsInstaller getMudFlowsInstaller(String nodeId) {
		return this.flowInstallerMap.get(nodeId);
	}

	public void addMudFlowsInstaller(String nodeUri, MudFlowsInstaller MudFlowsInstaller) {
		this.flowInstallerMap.put(nodeUri, MudFlowsInstaller);
	}

	public SalFlowService getFlowService() {
		return flowService;
	}

	public PacketProcessingService getPacketProcessingService() {
		return packetProcessingService;
	}

	public WakeupOnFlowCapableNode getWakeupListener() {
		return wakeupListener;
	}

	public void addMudUri(String cpeNodeId, Uri mudUri) {
		List<Uri> mudUris = this.nodeToMudUriMap.get(cpeNodeId);
		if (mudUris == null) {
			mudUris = new ArrayList<Uri>();
			this.nodeToMudUriMap.put(cpeNodeId, mudUris);
		}
		mudUris.add(mudUri);
	}

	public Collection<String> getMudCpeNodeIds() {
		return this.nodeToMudUriMap.keySet();
	}

	public List<Uri> getRegisteredMudUrls(String cpeNodeId) {
		return this.nodeToMudUriMap.get(cpeNodeId);
	}

	public void setTopology(CpeCollections topology) {
		this.topology = topology;

	}

	public CpeCollections getCpeCollections() {
		return topology;
	}

	public Collection<Mud> getMudProfiles() {
		return this.uriToMudMap.values();
	}

	public void addMudProfile(Mud mud) {
		this.uriToMudMap.put(mud.getMudUrl(), mud);
	}

	public boolean isCpeNode(String nodeId) {
		if (this.getCpeCollections() == null) {
			return false;
		}
		for (Uri cpeNode : topology.getCpeSwitches()) {
			if (nodeId.equals(cpeNode.getValue())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return
	 */
	public SchemaService getSchemaService() {
		return this.schemaService;
	}

}
