package gov.nist.antd.sdnmud.impl;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180412.Mud;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.controllerclass.mapping.Controller;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.controllerclass.mapping.ControllerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;



public class ControllerclassMappingDataStoreListenerTest {

    private SdnmudProvider sdnmudProvider;
    private ControllerclassMappingDataStoreListener controllerClassMappingDataStoreListener;
    private String nodeId;
    private MudFlowsInstaller mudFlowsInstaller;
    private InstanceIdentifier<FlowCapableNode> node;
    private Mud mud;


    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
        sdnmudProvider = Mockito.mock(SdnmudProvider.class);
        controllerClassMappingDataStoreListener = new ControllerclassMappingDataStoreListener(sdnmudProvider);

        node = Mockito.mock(InstanceIdentifier.class);

        nodeId = "openflow:1";
        mudFlowsInstaller = Mockito.mock(MudFlowsInstaller.class);
        when(sdnmudProvider.getMudFlowsInstaller(any(String.class))).thenReturn(mudFlowsInstaller);
        when(sdnmudProvider.getNode(any(String.class))).thenReturn(node);
        mud = Mockito.mock(Mud.class);
        ArrayList<Mud> mudProfiles = new ArrayList<>();
        mudProfiles.add(mud);
        when(sdnmudProvider.getMudProfiles()).thenReturn(mudProfiles);

        doNothing().when(mudFlowsInstaller).tryInstallFlows(any(Mud.class));
    }

    @Test
    public void onDataChange() throws Exception {
        ControllerclassMappingBuilder ccmbuilder = new ControllerclassMappingBuilder();
        ccmbuilder.setSwitchId(new Uri(nodeId));
        ControllerBuilder cb = new ControllerBuilder();
        cb.setUri(new Uri("https://toaster.nist.gov"));
        ArrayList<Controller> controllerList = new ArrayList<>();
        controllerList.add(cb.build());
        ccmbuilder.setController(controllerList);

        Collection<DataTreeModification<ControllerclassMapping>> collection = new ArrayList<>();

        DataTreeModification<ControllerclassMapping> dtm = Mockito.mock(DataTreeModification.class);
        DataObjectModification<ControllerclassMapping> dom = Mockito.mock(DataObjectModification.class);

        when(dtm.getRootNode()).thenReturn(dom);
        when(dom.getDataAfter()).thenReturn(ccmbuilder.build());


        collection.add(dtm);


        controllerClassMappingDataStoreListener.onDataTreeChanged(collection);

        Thread.sleep(250);

        verify(mudFlowsInstaller,times(1)).tryInstallFlows(mud);



    }
}
