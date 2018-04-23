/*
 * Copyright © 2017 None.  No rights reserved.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.Mud;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.controllerclass.mapping.Controller;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data tree changed listener for updating controller classes.
 *
 */
public class ControllerclassMappingDataStoreListener
        implements
            DataTreeChangeListener<ControllerclassMapping> {

    private HashMap<String, ControllerclassMapping> controllerMap = new HashMap<String, ControllerclassMapping>();

    private SdnmudProvider sdnmudProvider;

    private static final Logger LOG = LoggerFactory
            .getLogger(ControllerclassMappingDataStoreListener.class);

    public ControllerclassMappingDataStoreListener(SdnmudProvider provider) {
        this.sdnmudProvider = provider;
    }

    @Override
    public void onDataTreeChanged(
            Collection<DataTreeModification<ControllerclassMapping>> collection) {
        LOG.info(
                "ControllerclassMappingDataStoreListener: onDataTreeModification");
        for (DataTreeModification<ControllerclassMapping> change : collection) {
            ControllerclassMapping controllerMapping = change.getRootNode()
                    .getDataAfter();
            if (controllerMapping.getSwitchId() == null) {
                LOG.info("switch ID is null -- returning");
                return;
            }
            String nodeId = controllerMapping.getSwitchId().getValue();
            LOG.info(
                    "ControllerclassMappingDataStoreListener: onDataTreeChanged : Registering Controller for SwitchId "
                            + nodeId);
            this.controllerMap.put(nodeId, controllerMapping);
            // Retrieve the MUD profile if it has been registered.
            assert sdnmudProvider != null;
            Collection<Mud> mudProfiles = sdnmudProvider.getMudProfiles();
            // Note that the mudflow installer will be null for NodeIds that
            MudFlowsInstaller mudFlowInstaller = sdnmudProvider
                    .getMudFlowsInstaller(nodeId);
            if (mudFlowInstaller != null) {
                for (Mud mud : mudProfiles) {
                    mudFlowInstaller.tryInstallFlows(mud);
                }
                InstanceIdentifier<FlowCapableNode> nodePath = sdnmudProvider
                        .getNode(nodeId);
                // Install allow all devices to access DNS and NTP.
                if (nodePath != null) {
                    try {
                        mudFlowInstaller
                                .installAllowToDnsAndNtpFlowRules(nodePath);
                    } catch (Exception ex) {
                        LOG.error(
                                "onDataTreeChanged : Could not install Dns and Ntp flow rules ",
                                ex);
                    }
                }
            }
        }
    }

    /**
     * Get the DNS server address for the given mudUri.
     *
     * @param nodeUri
     *
     * @return -- the associated DNS server adress.
     */

    public IpAddress getDnsAddress(String nodeUri) {
        LOG.info(this.getClass().getName() + " getDnsAddress " + nodeUri);
        if (!this.controllerMap.containsKey(nodeUri)) {
            return null;
        }
        List<Controller> controllers = this.controllerMap.get(nodeUri)
                .getController();
        for (Controller controller : controllers) {
            Uri controllerUri = controller.getUri();
            if (controllerUri.getValue()
                    .equals(SdnMudConstants.DNS_SERVER_URI)) {
                return controller.getAddressList().get(0);
            } else {
                LOG.info(this.getClass().getName() + ": controllerUri "
                        + controllerUri.getValue() + "/"
                        + SdnMudConstants.DNS_SERVER_URI);
            }
        }
        return null;
    }

    /**
     * Get the NTP address.
     *
     * @param nodeUri
     *
     * @return -- the ipAddress corresponding to Ntp
     */

    public IpAddress getNtpAddress(String nodeUri) {
        if (!controllerMap.containsKey(nodeUri))
            return null;
        List<Controller> controllers = this.controllerMap.get(nodeUri)
                .getController();
        for (Controller controller : controllers) {
            Uri controllerUri = controller.getUri();
            if (controllerUri.getValue()
                    .equals(SdnMudConstants.NTP_SERVER_URI)) {
                return controller.getAddressList().get(0);
            }
        }
        return null;
    }

    /**
     *
     * @param nodeConnectorUri
     * @param mudUri
     *            -- the mud URI
     *
     * @param controllerUriString
     *            -- The URI string for the controller class we want.
     * @return
     */
    public List<IpAddress> getControllerAddresses(String nodeConnectorUri,
            Uri mudUri, String controllerUriString) {
        if (!controllerMap.containsKey(nodeConnectorUri)) {
            return null;
        }
        List<Controller> controllers = this.controllerMap.get(nodeConnectorUri)
                .getController();
        for (Controller controller : controllers) {
            Uri controllerUri = controller.getUri();
            // TODO : could do this faster with a hash map.
            if (controllerUri.getValue()
                    .compareToIgnoreCase(controllerUriString) == 0) {
                return controller.getAddressList();
            }
        }
        return null;
    }

    /**
     * Get a list of controller URIs for this MUD URI.
     *
     * @param mudUri
     *            -- the mud URI for which we need the controller URIs
     *
     * @return -- a list of controler URIs.
     */

    public Collection<Uri> getControllerUris(String nodeUri) {
        if (!controllerMap.containsKey(nodeUri)) {
            return null;
        }

        List<Controller> controllers = this.controllerMap.get(nodeUri)
                .getController();
        HashSet<Uri> retval = new HashSet<>();
        for (Controller controller : controllers) {
            retval.add(controller.getUri());
        }
        return retval;
    }

    public ControllerclassMapping getControllerClass(String nodeUri) {
        return controllerMap.get(nodeUri);
    }

    public Collection<String> getLocalNetworks(String nodeConnectorUri) {

        if (!controllerMap.containsKey(nodeConnectorUri)) {
            return null;
        }
        return this.controllerMap.get(nodeConnectorUri).getLocalNetworks();
    }

}
