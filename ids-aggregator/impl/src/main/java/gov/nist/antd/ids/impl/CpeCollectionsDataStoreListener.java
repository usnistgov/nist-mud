package gov.nist.antd.ids.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.cpe.nodes.rev170915.CpeCollections;

public class CpeCollectionsDataStoreListener implements DataTreeChangeListener<CpeCollections> {
	
	private IdsProvider idsProvider;

	public CpeCollectionsDataStoreListener(IdsProvider idsProvider) {
		this.idsProvider = idsProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<CpeCollections>> changes) {
		for (DataTreeModification<CpeCollections> change : changes) {
			CpeCollections topology = change.getRootNode().getDataAfter();
			idsProvider.setCpeCollections(topology);
		}
		
	}

}
