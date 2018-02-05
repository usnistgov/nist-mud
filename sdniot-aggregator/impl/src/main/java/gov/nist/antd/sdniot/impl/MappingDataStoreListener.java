package gov.nist.antd.sdniot.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev171007.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingDataStoreListener implements DataTreeChangeListener<Mapping> {

  private SdnmudProvider sdnmudProvider;

  private Map<MacAddress, Mapping> macAddressToMappingMap = new HashMap<MacAddress, Mapping>();

  private Map<Uri, HashSet<MacAddress>> uriToMacs = new HashMap<Uri, HashSet<MacAddress>>();
  private static final Logger LOG = LoggerFactory.getLogger(MappingDataStoreListener.class);

  private static String getAuthority(Uri uri) {

    int index = uri.getValue().indexOf("//") + 2;
    String rest = uri.getValue().substring(index);
    index = rest.indexOf("/");
    String authority = rest.substring(0, index);
    return authority;
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

  public MappingDataStoreListener(SdnmudProvider sdnmudProvider) {
    this.sdnmudProvider = sdnmudProvider;
  }

  @Override
  public void onDataTreeChanged(Collection<DataTreeModification<Mapping>> collection) {
    LOG.info("onDataTreeModification");
    for (DataTreeModification<Mapping> change : collection) {
      Mapping mapping = change.getRootNode().getDataAfter();
      List<MacAddress> macAddresses = mapping.getDeviceId();
      Uri uri = mapping.getMudUrl();
      LOG.info("mudUri = " + uri.getValue());

      // Cache the MAC addresses of the devices under the same URL.
      for (MacAddress mac : macAddresses) {
        removeMacAddress(mac);
        LOG.info("Put MAC address mapping " + mac + " uri " + uri.getValue());
        macAddressToMappingMap.put(mac, mapping);
        HashSet<MacAddress> macs = uriToMacs.get(uri);
        if (macs == null) {
          macs = new HashSet<MacAddress>();
          uriToMacs.put(uri, macs);
        }
        macs.add(mac);
        // Remove the default mapping (the next flow miss will install the right mapping).
        // Find all the switches where this MAC address has been seen.
        HashSet<String> npeNodes = new HashSet<String>();
        for (String nodeId : this.sdnmudProvider.getNodeId(mac)) {
          InstanceIdentifier<FlowCapableNode> node = sdnmudProvider.getNode(nodeId);
          
          if (node != null) {
            MudFlowsInstaller.installStampManufacturerModelFlowRules(mac, uri.getValue(),
                sdnmudProvider, node);
          }
          // Get the ID of the NPE switch for this cpe switch.
          String npeNodeId = this.sdnmudProvider.getNpeSwitchUri(nodeId);
          if (! npeNodes.contains(npeNodeId)) {
              npeNodes.add(npeNodeId);
              InstanceIdentifier<FlowCapableNode> npeNode = sdnmudProvider.getNode(npeNodeId);
              if (npeNode != null) {
                MudFlowsInstaller.installStampManufacturerModelFlowRules(mac, uri.getValue(), sdnmudProvider, npeNode);
              }
          }
          
        }

      }

    }
  }


  public Collection<MacAddress> getMacs(Uri uri) {
    return uriToMacs.get(uri);
  }

  /**
   * Get the MUD URI given a mac address.
   * 
   * @param macAddress
   * @return
   */
  public Uri getMudUri(MacAddress macAddress) {
    if (macAddressToMappingMap.containsKey(macAddress)) {
      return this.macAddressToMappingMap.get(macAddress).getMudUrl();
    } else {
      return null;
    }
  }

  /**
   * Get all the MAC addresses for the same manufacturer.
   * 
   * @param mudUri
   * @return
   */
  public Collection<MacAddress> getSameManufacturerMacs(Uri mudUri) {
    HashSet<MacAddress> macs = new HashSet<MacAddress>();
    String authority = getAuthority(mudUri);
    LOG.info("getSameManufacturerMacs for " + mudUri.getValue() + " manufacturer " + authority);
    for (Uri uri : uriToMacs.keySet()) {
      if (authority.equals(getAuthority(uri))) {
        macs.addAll(uriToMacs.get(uri));
      }
    }
    LOG.info("getSameManufacturerMacs : Found " + macs.size() + " macs = " + macs);
    return macs;
  }

  /**
   * Get all mac addresses for the same manufacturer.
   * 
   * @param manufacturer -- the manufacturer.
   * 
   * @return -- a set containing the mac addresses for the manufactuer.
   */
  public Collection<MacAddress> getSameManufactuerMacs(String manufacturer) {
    HashSet<MacAddress> macs = new HashSet<MacAddress>();
    for (Uri uri : uriToMacs.keySet()) {
      if (manufacturer.equals(getAuthority(uri))) {
        macs.addAll(uriToMacs.get(uri));
      }
    }
    return macs;
  }

}
