package gov.nist.antd.flowmon.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;

class TopologyDataStoreListener implements DataTreeChangeListener<Topology> {

  private FlowmonProvider flowmonProvider;

  TopologyDataStoreListener(FlowmonProvider flowmonProvider) {
    this.flowmonProvider = flowmonProvider;
  }

  @Override
  public void onDataTreeChanged(Collection<DataTreeModification<Topology>> changes) {
    for (DataTreeModification<Topology> change : changes) {
      Topology topology = change.getRootNode().getDataAfter();
      this.flowmonProvider.setTopology(topology);
    }
    flowmonProvider.getWakeupListener().installDefaultFlows();
  }

}
