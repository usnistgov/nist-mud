package gov.nist.antd.vlan.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Scanner;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
//import org.jclouds.ContextBuilder;
//import org.jclouds.openstack.keystone.config.KeystoneProperties;
//import org.jclouds.openstack.keystone.v3.KeystoneApi;
//import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.OpenstackConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import gov.nist.antd.baseapp.impl.BaseappConstants;
import gov.nist.antd.baseapp.impl.InstanceIdentifierUtils;

class WakeupOnFlowCapableNode
        implements
            DataTreeChangeListener<FlowCapableNode> {
    private static final Logger LOG = LoggerFactory
            .getLogger(WakeupOnFlowCapableNode.class);

    private VlanProvider vlanProvider;

    private HashSet<InstanceIdentifier<FlowCapableNode>> pendingNodes = new HashSet<>();

    private String authToken;

    WakeupOnFlowCapableNode(VlanProvider vlanProvider) {
        this.vlanProvider = vlanProvider;
    }

    @Override
    public void onDataTreeChanged(
            Collection<DataTreeModification<FlowCapableNode>> modifications) {
        LOG.debug("WakeupOnFlowCapableNode: onDataTreeChanged");

        for (DataTreeModification<FlowCapableNode> modification : modifications) {
            if (modification.getRootNode()
                    .getModificationType() == ModificationType.WRITE) {
                LOG.info("got a WRITE modification");
                InstanceIdentifier<FlowCapableNode> ii = modification
                        .getRootPath().getRootIdentifier();
                onFlowCapableSwitchAppeared(ii);
            } else if (modification.getRootNode()
                    .getModificationType() == ModificationType.DELETE) {
                LOG.info("Got a DELETE modification");
                InstanceIdentifier<FlowCapableNode> ii = modification
                        .getRootPath().getRootIdentifier();
                onFlowCapableSwitchDisappeared(ii);
            } else {
                LOG.debug("WakeupOnFlowCapableNode : "
                        + modification.getRootNode().getModificationType());
            }
        }

    }

    private void installInitialFlowsForNpeSwitch(
            InstanceIdentifier<FlowCapableNode> node) {
        String nodeUri = InstanceIdentifierUtils.getNodeUri(node);
        if (vlanProvider.isNpeSwitch(nodeUri)) {
            short tableId = BaseappConstants.DETECT_EXTERNAL_ARP_TABLE;
            int timeout = 0;

            FlowId flowId = InstanceIdentifierUtils.createFlowId(nodeUri);
            FlowCookie flowCookie = InstanceIdentifierUtils
                    .createFlowCookie(nodeUri);
            FlowBuilder fb = FlowUtils
                    .createVlanIpMatchSendToControllerAndGoToTable(tableId,
                            timeout, flowId, flowCookie);
            vlanProvider.getFlowCommitWrapper().deleteFlows(node, nodeUri,
                    tableId, null);
            vlanProvider.getFlowCommitWrapper().writeFlow(fb, node);
        }
    }

    private void installInitialFlowsForCpeSwitch(
            InstanceIdentifier<FlowCapableNode> node) {

        String destinationId = InstanceIdentifierUtils.getNodeUri(node);

        FlowId flowId = InstanceIdentifierUtils.createFlowId(destinationId);
        FlowCookie flowCookie = InstanceIdentifierUtils
                .createFlowCookie("PORT_MATCH_VLAN_" + destinationId);
        // Push a flow that detects and inbound ARP from the external
        // port (from which we just saw the LLDP packet.
        String ifce = this.vlanProvider.getUplinkInterface(destinationId);

        GetPortFromInterfaceInputBuilder gpfib = new GetPortFromInterfaceInputBuilder();
        gpfib.setIntfName(ifce);
        long trunkPortNo = -1;

        try {
            Thread.sleep(3000);
            trunkPortNo = vlanProvider.getInterfaceManagerRpcService()
                    .getPortFromInterface(gpfib.build()).get().getResult()
                    .getPortno();
            LOG.info("port number for interface " + ifce + " trunkPortNumber = "
                    + trunkPortNo);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            LOG.error("Error getting port number for interface " + ifce, e);
            return;
        }

        NodeConnectorId inportId = new NodeConnectorId(
                new Uri(Integer.toString((int) trunkPortNo)));
        // Flow rule to send up ARP from the external port. This allows us to
        // override the
        // L2Switch flow rule.
        FlowBuilder fb = FlowUtils
                .createMatchPortArpMatchSendPacketToControllerAndGoToTableFlow(
                        inportId, BaseappConstants.DETECT_EXTERNAL_ARP_TABLE, 0,
                        flowId, flowCookie);

        vlanProvider.getFlowCommitWrapper().writeFlow(fb, node);

        int tag = vlanProvider.getCpeTag(destinationId);

        flowId = InstanceIdentifierUtils.createFlowId(destinationId);
        flowCookie = InstanceIdentifierUtils
                .createFlowCookie("NO_VLAN_MATCH_PUSH_ARP_" + destinationId);

        // The following sends two copies of the ARP through the
        // external port. One with VLAN tag and one without.
        fb = FlowUtils.createNoVlanArpMatchPushVlanSendToPortAndGoToTable(
                new Uri(Integer.toString((int) trunkPortNo)).getValue(), tag,
                BaseappConstants.PUSH_VLAN_ON_ARP_TABLE, 0, flowId, flowCookie);

        vlanProvider.getFlowCommitWrapper().writeFlow(fb, node);
        flowId = InstanceIdentifierUtils.createFlowId(destinationId);
        flowCookie = InstanceIdentifierUtils
                .createFlowCookie("VLAN_MATCH_POP_ARP_" + destinationId);

        fb = FlowUtils.createVlanMatchPopVlanTagAndGoToTable(
                BaseappConstants.STRIP_VLAN_TABLE, tag, 0, flowId, flowCookie);
        vlanProvider.getFlowCommitWrapper().writeFlow(fb, node);
    }

    public CloseableHttpResponse doPost(String url, String jsonBody) {
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            HttpEntity entity = new StringEntity(jsonBody);
            httpPost.setEntity(entity);
            CloseableHttpClient httpclient = HttpClients.createDefault();
            CloseableHttpResponse response = httpclient.execute(httpPost);
            LOG.info("doPost : status code = "
                    + response.getStatusLine().getStatusCode());
            httpclient.close();
            return response;
        } catch (IOException ex) {
            LOG.error("Exception occured while POSTING", ex);
            return null;
        }
    }
    public void connectToOpenStack() {
        OpenstackConfig openstackConfig = vlanProvider.getOpenstackConfig();
        if (openstackConfig != null) {
            Uri uri = openstackConfig.getOpenstackUrl();
            String userName = openstackConfig.getOpenstackUser();
            String password = openstackConfig.getOpenstackPass();
            String project = openstackConfig.getOpenstackProject();
            String domain = openstackConfig.getOpenstackDomain();
            if (uri == null || userName == null || password == null) {
                LOG.info("cannot connect to openstack.");
                return;
            } else {
                StringBuilder result = new StringBuilder("");

                String fileName = "login-template.txt";

                // Get file from resources folder
                ClassLoader classLoader = getClass().getClassLoader();
                /*
                 * try {
                 * 
                 * Enumeration<URL> en = classLoader
                 * .getResources("login-template.txt"); while
                 * (en.hasMoreElements()) { LOG.info("FOUND_IT" +
                 * en.nextElement().toString()); }
                 * 
                 * } catch (Exception ex) {
                 * LOG.error("Error occured while processing ", ex); }
                 */

                try {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                    classLoader.getResourceAsStream(fileName)));
                    while (true) {
                        String line = br.readLine();
                        if (line == null)
                            break;
                        result.append(line);
                    }
                    String loginString = String.format(result.toString(),
                            domain, userName, password, project, domain);
                    CloseableHttpResponse response = doPost(
                            uri.getValue() + "/identity/v3/auth/tokens",
                            loginString);
                    if ((int) (response.getStatusLine().getStatusCode()
                            / 200) == 1) {

                        if (response
                                .getFirstHeader("X-Subject-Token") != null) {
                            this.authToken = response
                                    .getFirstHeader("X-Subject-Token")
                                    .getValue();
                        }
                    }
                    LOG.info("authToken " + authToken);

                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    LOG.error("Exception occured during read ", e1);
                }

            }
        } else {
            LOG.info("openstackConfig is null");
        }
    }

    private synchronized void onFlowCapableSwitchAppeared(
            InstanceIdentifier<FlowCapableNode> nodePath) {
        String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
        LOG.info("onFlowCapableSwitchAppeared");
        // The URI identifies the node instance.
        LOG.info("node URI " + nodeUri + " nodePath " + nodePath);
        // Stash away the URI to node path so we can reference it later.
        this.vlanProvider.putInUriToNodeMap(nodeUri, nodePath);

        if (this.vlanProvider.isNpeSwitch(nodeUri)) {
            installInitialFlowsForNpeSwitch(nodePath);
        } else if (this.vlanProvider.isCpeNode(nodeUri)) {
            installInitialFlowsForCpeSwitch(nodePath);
        } else {
            this.pendingNodes.add(nodePath);
        }

    }

    private synchronized void onFlowCapableSwitchDisappeared(
            InstanceIdentifier<FlowCapableNode> nodePath) {
        String nodeUri = nodePath.firstKeyOf(Node.class).getId().getValue();
        LOG.info("onFlowCapableSwitchDisappeared");
        // The URI identifies the node instance.
        LOG.info("node URI " + nodeUri);
        // Remove the node URI from the uriToNodeMap.
        this.vlanProvider.removeFromUriToNodeMap(nodeUri);

    }

    public void installInitialFlows() {
        for (InstanceIdentifier<FlowCapableNode> node : pendingNodes) {
            String nodeUri = InstanceIdentifierUtils.getNodeUri(node);
            if (vlanProvider.isNpeSwitch(nodeUri)) {
                installInitialFlowsForNpeSwitch(node);
            } else if (vlanProvider.isCpeNode(nodeUri)) {
                installInitialFlowsForCpeSwitch(node);
            }
        }
        this.pendingNodes.clear();
    }

}
