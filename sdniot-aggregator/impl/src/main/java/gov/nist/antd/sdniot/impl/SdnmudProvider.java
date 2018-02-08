/*
 * Copyright © 2017 None.
 *
 * This program and the accompanying materials in the public domain.
 * 
 */
package gov.nist.antd.sdniot.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180202.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180124.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flow.controller.rev170915.NistFlowControllerService;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.ids.config.rev170915.IdsConfigData;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableSet;

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

  // Stores a set of NodeIds for a given mac address (identifies the switches
  // that have seen the mac addr).
  private HashMap<MacAddress, HashSet<String>> macToNodeIdMap = new HashMap<>();

  // Stores a map between node ID and its InstanceIdentifier<FlowCapableNode>
  private HashMap<String, InstanceIdentifier<FlowCapableNode>> uriToNodeMap = new HashMap<>();
  
  // Map between the node URI and the mud uri.
  private HashMap<String,List<Uri>> nodeToMudUriMap = new HashMap<>();

  // Maps the instance identifier to the URI of the node (should not need this
  // map)

  private HashMap<String, HashMap<String, String>> nodeConnectorMap = new HashMap<>();

  // A map between a mac address and the associated FlowCapableNodes where MUD
  // profiles were installed.
  // This is used to retrieve a set of nodes where MUD rules have been
  // installed for a given MAC address.
  private HashMap<String, HashSet<InstanceIdentifier<FlowCapableNode>>> mudNodesMap =
      new HashMap<>();

  // A map between the IDS nodes and configuration for the IDS nodes.
  private HashMap<String, IdsConfigData> idsConfigMap = new HashMap<>();

  // A map between IDS node and ports for the IDS where it announces itself.
  // This is garbage collected (TBD)
  private HashMap<String, ArrayList<IdsPort>> idsNodeToPortMap = new HashMap<>();

  // A map between the NPE switch URI and list of NPE switches
  private HashMap<String, Collection<String>> npeToCpeMap = new HashMap<>();
  
  // A map between the NODE uri and the Flow installer for that node.
  private HashMap<String, MudFlowsInstaller> flowInstallerMap = new HashMap<>();
  
 
  private FlowCommitWrapper flowCommitWrapper;

  private IMdsalApiManager mdsalApiManager;

  private RpcProviderRegistry rpcProviderRegistry;

  private IdsConfigDataStoreListener idsConfigDataStoreListener;

  private TopologyDataStoreListener topoDataStoreListener;

  private WakeupOnFlowCapableNode wakeupListener;

  class IdsPort {
    String portUri;
    long time = System.currentTimeMillis();
    int port;

    public IdsPort(String portUri) {
      this.port = Integer.parseInt(portUri);
      this.portUri = portUri;
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

  public SdnmudProvider(final DataBroker dataBroker, SalFlowService flowService,
      PacketProcessingService packetProcessingService, NotificationService notificationService,
      IMdsalApiManager mdsalApiManager, RpcProviderRegistry rpcProviderRegistry) {
    this.dataBroker = dataBroker;
    this.flowService = flowService;
    this.packetProcessingService = packetProcessingService;
    this.notificationService = notificationService;
    this.mdsalApiManager = mdsalApiManager;
    this.rpcProviderRegistry = rpcProviderRegistry;

  }

  private static InstanceIdentifier<FlowCapableNode> getWildcardPath() {
    return InstanceIdentifier.create(Nodes.class).child(Node.class)
        .augmentation(FlowCapableNode.class);
  }

  private static InstanceIdentifier<Mud> getMudWildCardPath() {
    return InstanceIdentifier.create(Mud.class);
  }

  private static InstanceIdentifier<AccessLists> getAclWildCardPath() {
    return InstanceIdentifier.create(AccessLists.class);
  }

  private static InstanceIdentifier<Mapping> getMappingWildCardPath() {
    return InstanceIdentifier.create(Mapping.class);
  }

  private static InstanceIdentifier<ControllerclassMapping> getControllerClassMappingWildCardPath() {
    return InstanceIdentifier.create(ControllerclassMapping.class);
  }

  private static InstanceIdentifier<IdsConfigData> getIdsConfigWildCardPath() {
    return InstanceIdentifier.create(IdsConfigData.class);
  }

  private static InstanceIdentifier<Topology> getTopologyWildCardPath() {
    return InstanceIdentifier.create(Topology.class);
  }
  
  
  /**
   * Method called when the blueprint container is created.
   */
  public void init() {
    LOG.info("SdnmudProvider Session Initiated");

     Set<QName> supportedFeatures = ImmutableSet.of(QName.create( "urn:ietf:params:xml:ns:yang:ietf-access-control-list",
        "2018-01-16","match-on-ipv4"));
    CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR.newBuild(supportedFeatures);
    
    this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);

    /* Register data tree change listener for Topology change */
    InstanceIdentifier<Topology> topoWildCardPath = getTopologyWildCardPath();
    final DataTreeIdentifier<Topology> topoId =
        new DataTreeIdentifier<Topology>(LogicalDatastoreType.CONFIGURATION, topoWildCardPath);
    this.topoDataStoreListener = new TopologyDataStoreListener(this);
    this.dataBroker.registerDataTreeChangeListener(topoId, topoDataStoreListener);

    /* Register a data tree change listener for MUD profiles */
    InstanceIdentifier<Mud> mudWildCardPath = getMudWildCardPath();
    final DataTreeIdentifier<Mud> treeId =
        new DataTreeIdentifier<Mud>(LogicalDatastoreType.CONFIGURATION, mudWildCardPath);
    this.mudProfileDataStoreListener = new MudProfileDataStoreListener(dataBroker, this);
    this.dataBroker.registerDataTreeChangeListener(treeId, mudProfileDataStoreListener);

    /* Register a data tree change listener for ACL profiles */
    final InstanceIdentifier<AccessLists> aclWildCardPath = getAclWildCardPath();
    final DataTreeIdentifier<AccessLists> aclTreeId =
        new DataTreeIdentifier<AccessLists>(LogicalDatastoreType.CONFIGURATION, aclWildCardPath);
    this.aclDataStoreListener = new AclDataStoreListener(dataBroker, this);
    this.dataBroker.registerDataTreeChangeListener(aclTreeId, getAclDataStoreListener());

    /*
     * Register a data tree change listener for MAC to MUD URL mapping. The MAC to URL mapping is
     * provided by the system admin.
     */
    final InstanceIdentifier<Mapping> mappingWildCardPath = getMappingWildCardPath();
    final DataTreeIdentifier<Mapping> mappingTreeId =
        new DataTreeIdentifier<Mapping>(LogicalDatastoreType.CONFIGURATION, mappingWildCardPath);
    this.mappingDataStoreListener = new MappingDataStoreListener(this);
    this.dataBroker.registerDataTreeChangeListener(mappingTreeId, mappingDataStoreListener);

    /*
     * Register a data tree change listener for Controller Class mapping. A controller class mapping
     * maps a controller class URI to a list of internet addresses
     */
    final InstanceIdentifier<ControllerclassMapping> controllerClassMappingWildCardPath =
        getControllerClassMappingWildCardPath();
    this.controllerClassMappingDataStoreListener =
        new ControllerclassMappingDataStoreListener(this);
    final DataTreeIdentifier<ControllerclassMapping> ccmappingTreeId =
        new DataTreeIdentifier<ControllerclassMapping>(LogicalDatastoreType.CONFIGURATION,
            controllerClassMappingWildCardPath);
    this.dataBroker.registerDataTreeChangeListener(ccmappingTreeId,
        controllerClassMappingDataStoreListener);

    /*
     * Register a data tree change listener for IDS registration configuration.
     */
    final InstanceIdentifier<IdsConfigData> idsConfigDataWildCardPath =
        SdnmudProvider.getIdsConfigWildCardPath();
    this.idsConfigDataStoreListener = new IdsConfigDataStoreListener(this);
    final DataTreeIdentifier<IdsConfigData> idsConfigDataTreeId =
        new DataTreeIdentifier<IdsConfigData>(LogicalDatastoreType.CONFIGURATION,
            idsConfigDataWildCardPath);
    this.dataBroker.registerDataTreeChangeListener(idsConfigDataTreeId, idsConfigDataStoreListener);

    // Create a listener that wakes up on a node being added.
    this.wakeupListener = new WakeupOnFlowCapableNode(this);
    final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier =
        new DataTreeIdentifier<FlowCapableNode>(LogicalDatastoreType.OPERATIONAL,
            getWildcardPath());
    this.dataTreeChangeListenerRegistration =
        this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier, wakeupListener);

    // RPC interface to communicate with the IDS to block flows.
    
    this.rpcProviderRegistry.addRpcImplementation(NistFlowControllerService.class, 
        new NistFlowControllerServiceImpl(this));

    IdsRegistrationScanner scanner = new IdsRegistrationScanner(this);
    Timer timer = new Timer();

    timer.scheduleAtFixedRate(scanner, 0, 60 * 1000);
    LOG.info("start() <--");

  }

  /**
   * Method called when the blueprint container is destroyed.
   */
  public void close() {
    LOG.info("SdnmudProvider Closed");
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

  public NotificationService getNotificationService() {
    return this.notificationService;
  }

  public DataBroker getDataBroker() {
    return this.dataBroker;
  }

  /**
   * Put in the node to URI map.
   * 
   * @param nodeUri -- the node Uri.
   * @param nodePath -- the flow capable node Instance Identifier.
   */
  public synchronized void putInUriToNodeMap(String nodeUri,
      InstanceIdentifier<FlowCapableNode> nodePath) {
    this.uriToNodeMap.put(nodeUri, nodePath);
  }

  /**
   * Get the flow capable node id from the node uri.
   * 
   * @param nodeUri -- the node URI
   * @return -- the flow capable node.
   */
  public synchronized InstanceIdentifier<FlowCapableNode> getNode(String nodeUri) {
    return uriToNodeMap.get(nodeUri);
  }
  
  public synchronized Collection<InstanceIdentifier<FlowCapableNode>> getNodes() {
    return uriToNodeMap.values();
  }


  public synchronized void removeNode(String nodeUri) {
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

    // Clean up the node connector map;
    this.nodeConnectorMap.remove(nodeUri);
    for (Iterator<String> nodeConnectorMapIterator =
        this.nodeConnectorMap.keySet().iterator(); nodeConnectorMapIterator.hasNext();) {
      String sourceNodeUri = nodeConnectorMapIterator.next();
      this.nodeConnectorMap.get(sourceNodeUri).remove(nodeUri);
      if (nodeConnectorMap.get(sourceNodeUri).isEmpty()) {
        nodeConnectorMapIterator.remove();
      }
    }

    // clean up the mudNodesMap
    for (Iterator<String> mudNodesIterator = this.mudNodesMap.keySet().iterator(); mudNodesIterator
        .hasNext();) {
      String manufacturer = mudNodesIterator.next();
      this.mudNodesMap.get(manufacturer).remove(node);
      if (mudNodesMap.get(manufacturer).isEmpty()) {
        mudNodesIterator.remove();
      }
    }

  }

  /**
   * Set the node connector to send packets from a sourceNodeUri to a destinationNodeUri.
   * 
   * @param sourceNodeUri -- the node URI of the source node.
   * @param destinationNodeUri -- the node URI of the destination node.
   * @param nodeConnectorUri -- the connector URI (to install in a flow rule for packet diversion).
   */
  public void setNodeConnector(String sourceNodeUri, String destinationNodeUri,
      String nodeConnectorUri) {
    LOG.debug("setNodeConnector : sourceNodeUri = " + sourceNodeUri + " destinationNodeUri = "
        + destinationNodeUri + " nodeConnectorUri " + nodeConnectorUri);
    HashMap<String, String> nodeMap = this.nodeConnectorMap.get(sourceNodeUri);
    if (nodeMap == null) {
      nodeMap = new HashMap<String, String>();
      nodeConnectorMap.put(sourceNodeUri, nodeMap);
    }
    nodeMap.put(destinationNodeUri, nodeConnectorUri);
  }

  /**
   * Get the node connector URI to send packets from sourceNodeUri to destinationNodeUri.
   * 
   * @param sourceNodeUri -- the source node URI.
   * @param destinationNodeUri -- the destinationNodeUri
   * @return -- the connectorUri
   */
  public String getNodeConnector(String sourceNodeUri, String destinationNodeUri) {
    if (this.nodeConnectorMap.get(sourceNodeUri) == null) {
      return null;
    } else {
      return this.nodeConnectorMap.get(sourceNodeUri).get(destinationNodeUri);
    }
  }

  /**
   * Add a MUD node for this device MAC address.
   * 
   * @param deviceMacAddress -- mac address of device.
   * 
   * @param node -- the node to add.
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
   * @param deviceMacAddress -- the mac address for which we want the flow capable node set.
   */
  public Collection<InstanceIdentifier<FlowCapableNode>> getMudNodes(String manufacturer) {
    return this.mudNodesMap.get(manufacturer);
  }

  /**
   * Set the IDS configuration for a node URI.
   * 
   * @param idsNodeUri -- the IDS node URI.
   * @param idsConfigData -- the IDS config data.
   */
  public void addIdsConfig(Uri idsNodeUri, IdsConfigData idsConfigData) {
    LOG.info("addIdsConfig: " + idsNodeUri.getValue());
    this.idsConfigMap.put(idsNodeUri.getValue(), idsConfigData);
  }

  /**
   * Get the IDS configuration for a node URI.
   */
  public synchronized IdsConfigData getIdsConfig(String idsNodeUri) {
    return this.idsConfigMap.get(idsNodeUri);
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

  public synchronized void garbageCollectIdsRegistrationRecords() {
    for (Iterator<String> keys = idsNodeToPortMap.keySet().iterator(); keys.hasNext();) {
      String key = keys.next();
      for (Iterator<IdsPort> idsPortIterator = idsNodeToPortMap.get(key).iterator(); idsPortIterator
          .hasNext();) {
        IdsPort idsPort = idsPortIterator.next();
        if (System.currentTimeMillis() - idsPort.time > 2 * SdnMudConstants.DEFAULT_IDS_IDLE_TIMEOUT
            * 1000) {
          idsPortIterator.remove();
        }
      }
      LOG.info("removeIdsPort : removing " + key);
      if (idsNodeToPortMap.get(key).isEmpty()) {
        keys.remove();
      }

    }

  }

  public void addTopology(String npeUri, Collection<String> cpeSwitchSet) {
    LOG.info("SdnmudProvider.addTopology : " + npeUri + " cpeSwitchSeet.size = " + cpeSwitchSet.size());
    this.npeToCpeMap.put(npeUri, cpeSwitchSet);
  }

  public Collection<String> getCpeSwitches(String npeUri) {
    return this.npeToCpeMap.get(npeUri);
  }

  public String getNpeSwitchUri(String cpeSwitchUri) {
    String npeSwitchUri = null;
    for (String npeUri : this.npeToCpeMap.keySet()) {
      if (this.npeToCpeMap.get(npeUri).contains(cpeSwitchUri)) {
        npeSwitchUri  = npeUri;
        break;
      }
    }
    LOG.info("SdnmudProvider : getNpeSwitchUri cpeSwitchUri " +  cpeSwitchUri + " npeSwitchUri " + npeSwitchUri);
    return npeSwitchUri;
  }

  public MudFlowsInstaller getMudFlowsInstaller(String nodeId) {
    return this.flowInstallerMap.get(nodeId);
  }

  public boolean isNpeNode(String nodeUri) {
       return this.npeToCpeMap.containsKey(nodeUri);
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

  public Collection<String> getNpeSwitches() {
    return this.npeToCpeMap.keySet();
  }

  

  
  public boolean isCpeNode(String nodeUri) {
    for (Collection<String> cpeSwitches : this.npeToCpeMap.values()) {
      if (cpeSwitches.contains(nodeUri)) {
        return true;
      }
    } 
    return false;
  }

  public WakeupOnFlowCapableNode getWakeupListener() {
    return wakeupListener;
  }

  public void addMudUri(String cpeNodeId, Uri mudUri) {
    List<Uri> mudUris = this.nodeToMudUriMap.get(cpeNodeId);
    if (mudUris == null ) {
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

 
}
