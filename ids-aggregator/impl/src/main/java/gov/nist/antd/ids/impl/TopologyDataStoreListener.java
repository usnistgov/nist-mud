package gov.nist.antd.ids.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.accounts.Link;

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
  }

}
