package gov.nist.antd.ids.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;

public class TopologyDataStoreListener implements DataTreeChangeListener<Topology> {

  private IdsProvider idsProvider;

  public TopologyDataStoreListener(IdsProvider idsProvider) {
    this.idsProvider = idsProvider;
  }

  @Override
  public void onDataTreeChanged(Collection<DataTreeModification<Topology>> changes) {
    for (DataTreeModification<Topology> change : changes) {
      Topology topology = change.getRootNode().getDataAfter();
      this.idsProvider.setTopology(topology);
    }
    idsProvider.getWakeupListener().installDefaultFlows();
  }

}
