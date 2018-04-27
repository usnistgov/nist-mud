package gov.nist.antd.vlan.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;

class TopologyDataStoreListener implements DataTreeChangeListener<Topology> {

    private VlanProvider vlanProvider;

    TopologyDataStoreListener(VlanProvider vlanProvider) {
        this.vlanProvider = vlanProvider;
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<Topology>> changes) {
        for (DataTreeModification<Topology> change : changes) {
            Topology topology = change.getRootNode().getDataAfter();
            this.vlanProvider.setTopology(topology);
        }
        if (vlanProvider.getWakeupListener() != null ) {
            this.vlanProvider.getWakeupListener().installInitialFlows();
        }
    }

}
