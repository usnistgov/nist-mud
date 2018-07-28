/*
 * Copyright (c) Public Domain Jul 13, 2018.
 * This code is released to the public domain in accordance with the following disclaimer:
 *
 * "This software was developed at the National Institute of Standards
 * and Technology by employees of the Federal Government in the course of
 * their official duties. Pursuant to title 17 Section 105 of the United
 * States Code this software is not subject to copyright protection and is
 * in the public domain. It is an experimental system. NIST assumes no responsibility
 * whatsoever for its use by other parties, and makes no guarantees, expressed or
 * implied, about its quality, reliability, or any other characteristic. We would
 * appreciate acknowledgement if the software is used. This software can be redistributed
 * and/or modified freely provided that any derivative works bear
 * some notice that they are derived from it, and any modified versions bear some
 * notice that they have been modified."
 */

package gov.nist.antd.vlan.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.OpenstackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author mranga
 *
 */
public class OpenstackConfigDatastoreListener implements DataTreeChangeListener<OpenstackConfig> {

	private VlanProvider vlanProvider;
    private static final Logger LOG = LoggerFactory.getLogger(OpenstackConfigDatastoreListener.class);
	
	 private CloseableHttpResponse doPost(String url, String jsonBody) {
	        try {
	            HttpPost httpPost = new HttpPost(url);
	            httpPost.setHeader("Content-Type", "application/json");
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
	
	public boolean connectToOpenStack() {
        OpenstackConfig openstackConfig = vlanProvider.getOpenstackConfig();
        if (openstackConfig != null) {
            Uri uri = openstackConfig.getOpenstackUrl();
            String userName = openstackConfig.getOpenstackUser();
            String password = openstackConfig.getOpenstackPass();
            String project = openstackConfig.getOpenstackProject();
            String domain = openstackConfig.getOpenstackDomain();
            if (uri == null || userName == null || password == null) {
                LOG.info("cannot connect to openstack.");
                return false;
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
                            vlanProvider.setAuthToken(response
                                    .getFirstHeader("X-Subject-Token")
                                    .getValue());
                        }

                        LOG.info("content-length "
                                + response.getEntity().getContentLength());
                        InputStream is = response.getEntity().getContent();
                        
                        
                       /* byte[] content = new byte[(int) response.getEntity()
                                .getContentLength()];
                        is.read(content, 0,
                                (int) response.getEntity().getContentLength());

                        String contentString = new String(content);

                        LOG.info(" Content = " + contentString);

                        response.close();
                        */

                        Gson gson = new GsonBuilder().create();

                        Map<String, Object> map = gson.fromJson( new InputStreamReader(is),
                                Map.class);
                        
                        response.close();

                        Map<String, Object> proj = ((Map<String, Object>) ((Map<String, Object>) map
                                .get("token")).get("project"));

                       String openstackProjectId = (String) proj.get("id");
                        vlanProvider.setOpenstackProjectId(openstackProjectId);
                        LOG.info("opestackProjectId = " + openstackProjectId);

                    }
                    response.close();
                    LOG.info("authToken " + vlanProvider.getAuthToken());

                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    LOG.error("Exception occured during read ", e1);
                    return false;
                }

            }
        } else {
            LOG.info("openstackConfig is null");
            return false;
        }
        return true;
    }

	public OpenstackConfigDatastoreListener(VlanProvider vlanProvider) {
		this.vlanProvider = vlanProvider;
		
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener#
	 * onDataTreeChanged(java.util.Collection)
	 */
	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<OpenstackConfig>> changes) {
		LOG.info("onDataTreeChanged ");

		for (DataTreeModification<OpenstackConfig> change : changes) {
			OpenstackConfig config = change.getRootNode().getDataAfter();
			vlanProvider.setOpenstackConfig(config);
			this.connectToOpenStack();
		}

	}

}
