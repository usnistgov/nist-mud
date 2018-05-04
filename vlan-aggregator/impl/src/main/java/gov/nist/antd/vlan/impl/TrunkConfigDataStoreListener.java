package gov.nist.antd.vlan.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.TrunkSwitches;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Trunks;

public class TrunkConfigDataStoreListener implements DataTreeChangeListener<TrunkSwitches> {

    private VlanProvider vlanProvider;

    public TrunkConfigDataStoreListener(VlanProvider vlanProvider) {
        this.vlanProvider = vlanProvider;
    }

    @Override
    public void onDataTreeChanged(
            Collection<DataTreeModification<TrunkSwitches>> changes) {
        for (DataTreeModification<TrunkSwitches> change : changes) {
            Trunks trunks = change.getRootNode().getDataAfter();
            this.vlanProvider.setTrunks(trunks);
        }
        if (vlanProvider.getWakeupListener() != null && this.vlanProvider.isConfigured() ) {
            this.vlanProvider.getWakeupListener().installInitialFlows();
        }
    }

}
