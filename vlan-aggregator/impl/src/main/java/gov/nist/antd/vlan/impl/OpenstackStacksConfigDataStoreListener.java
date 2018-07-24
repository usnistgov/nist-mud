package gov.nist.antd.vlan.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.links.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.OpenstackStacksConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.openstack.stacks.config.OpenstackStacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenstackStacksConfigDataStoreListener
        implements
            DataTreeChangeListener<OpenstackStacksConfig> {
    private VlanProvider vlanProvider;
    private HashMap<String,String>  stackRef = new HashMap<>();
    private OpenstackStacksConfig stacksConfig;
    private static final Logger LOG = LoggerFactory
            .getLogger(OpenstackStacksConfigDataStoreListener.class);

    public OpenstackStacksConfigDataStoreListener(VlanProvider vlanProvider) {
        this.vlanProvider = vlanProvider;
    }

    @SuppressWarnings("unchecked")
    private String checkIfStackExists(String stackId) {

        try {
            if (vlanProvider.getOpenstackConfig() == null) {
                LOG.error("openstack is not configured");
                return null;
            } else if (vlanProvider.getAuthToken() == null
                    || vlanProvider.getOpenstackProjectId() == null) {
                if (!vlanProvider.getOpenstackConfigDataStoreListener()
                        .connectToOpenStack()) {
                    LOG.error("Could not connect to Openstack -- returning");
                    return null;
                }

            }

            String authToken = vlanProvider.getAuthToken();
            String openstackProjectId = vlanProvider.getOpenstackProjectId();

            // heat-api/v1/{tenant_id}/stacks
            if (authToken != null && openstackProjectId != null) {
                String url = vlanProvider.getOpenstackConfig().getOpenstackUrl()
                        .getValue();
                String heatUrl = url + "/heat-api/v1/" + openstackProjectId
                        + "/stacks";
                HttpGet httpGet = new HttpGet(heatUrl);
                httpGet.setHeader("X-Auth-Token", authToken);
                httpGet.setHeader("Accept-Encoding", "application/json");
                CloseableHttpClient httpclient = HttpClients.createDefault();
                CloseableHttpResponse response = httpclient.execute(httpGet);
                LOG.info("doGet : status code = "
                        + response.getStatusLine().getStatusCode());

                if (response.getStatusLine().getStatusCode() == 200) {
                    long contentLength = response.getEntity()
                            .getContentLength();

                    byte[] contentBytes = new byte[(int) contentLength];

                    response.getEntity().getContent().read(contentBytes, 0,
                            (int) contentLength);
                    String contentStr = new String(contentBytes);
                    LOG.info("checkIfStackExists : contentStr = " + contentStr);
                    Gson gson = new GsonBuilder().create();
                    Map<String, Object> stacks = gson.fromJson(contentStr,
                            Map.class);
                    ArrayList<Map<String, Object>> stacksArray = (ArrayList<Map<String, Object>>) stacks
                            .get("stacks");
                    for (Map<String, Object> stackDesc : stacksArray) {
                        String stackName = (String) stackDesc.get("stack_name");
                        if (stackName.equals(stackId)) {
                            LOG.info(
                                    "found stack description for " + stackName);
                            ArrayList<Map<String, Object>> linksArray = (ArrayList<Map<String, Object>>) stackDesc
                                    .get("links");
                            for (Map<String, Object> link : linksArray) {
                                String rel = (String) link.get("rel");
                                if (rel.equals("self")) {
                                    LOG.info("href = "
                                            + (String) link.get("href"));
                                    return (String) link.get("href");
                                }
                            }
                        }
                    }
                }
                return null;
            }

        } catch (Exception ex) {
            LOG.error("checkIfStackExists ", ex);
        }
        return null;

    }

    private CloseableHttpResponse doPostToHeatWithAuth(String url,
            String jsonBody) {
        try {

            String authToken = vlanProvider.getAuthToken();

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("X-Auth-Token", authToken);

            HttpEntity entity = new StringEntity(jsonBody);

            httpPost.setEntity(entity);
            CloseableHttpClient httpclient = HttpClients.createDefault();
            CloseableHttpResponse response = httpclient.execute(httpPost);
            LOG.info("doPost : status code = "
                    + response.getStatusLine().getStatusCode());
            return response;
        } catch (IOException ex) {
            LOG.error("Exception occured while POSTING", ex);
            return null;
        }
    }

    public void setupVlan(int vlanId, String stackName) {

        if (this.vlanProvider.getOpenstackConfig() == null) {
            LOG.error("Openstack is not yet configured - not setting up vlan");
            return;
        }
        Uri uri = this.vlanProvider.getOpenstackConfig().getOpenstackUrl();

        String authToken = vlanProvider.getAuthToken();
        String openstackProjectId = vlanProvider.getOpenstackProjectId();

        if (authToken == null || openstackProjectId == null) {
            LOG.error("Cannot setup vlan projctId = " + openstackProjectId
                    + " authToken " + authToken);
            return;
        }

        try {
            StringBuilder result = new StringBuilder("");

            String fileName = "heat-template.txt";

            ClassLoader classLoader = getClass().getClassLoader();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    classLoader.getResourceAsStream(fileName)));
            while (true) {
                String line = br.readLine();
                if (line == null)
                    break;
                result.append(line);
            }

            for (OpenstackStacks stacks : this.stacksConfig
                    .getOpenstackStacks()) {
                if (stacks.getOpenstackStackName().equals(stackName)) {
                    String privateNetCidr = stacks.getPrivateNetCidr();
                    String privateNetGateway = stacks.getPrivateNetGateway();
                    String netPoolStart = stacks.getPrivateNetPoolStart();
                    String netPoolEnd = stacks.getPrivateNetPoolEnd();
                    String setupVlanString = String.format(result.toString(),
                            stackName, privateNetCidr, netPoolStart, netPoolEnd,
                            privateNetGateway, vlanId);

                    LOG.info("setupVlanString = " + setupVlanString);
                    CloseableHttpResponse response = doPostToHeatWithAuth(
                            uri.getValue() + "/heat-api/v1/"
                                    + openstackProjectId + "/stacks",
                            setupVlanString);
                    LOG.info("setupVlan "
                            + response.getStatusLine().getStatusCode());
                }
            }

        } catch (Exception ex) {
            LOG.error("Exception when trying to setup vlan", ex);
        }

    }
    
    public void setupOpenstackStacks() {
        if (this.vlanProvider.getOpenstackConfig() != null) {
            if ( this.vlanProvider.getCpeLinks() == null ) {
                LOG.info("Cannot set up stacks -- CPE links are not configured");
            }
            for (Link link : vlanProvider.getCpeLinks()) {
                int vlanId = link.getVlan();
                if (this.vlanProvider.getOpenstackConfig() != null) {
                    String href = checkIfStackExists(link.getOpenstackStackName());
                    if (href == null) {
                         setupVlan(vlanId, link.getOpenstackStackName());
                    } else {
                        this.stackRef.put(link.getOpenstackStackName(), href);
                    }
                }
            }
        }
    }

    @Override
    public void onDataTreeChanged(
            Collection<DataTreeModification<OpenstackStacksConfig>> changes) {

        for (DataTreeModification<OpenstackStacksConfig> change : changes) {
            OpenstackStacksConfig stacksConfig = change.getRootNode()
                    .getDataAfter();
            this.stacksConfig = stacksConfig;

        }
        this.setupOpenstackStacks();

    }

}
