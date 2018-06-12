/*
 *
 *
 * Copyright (C) 2017 Public Domain.  No rights reserved.
 *
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
 *
 * Parts of this file include code from here:
 *
 * https://github.com/opendaylight/daexim/blob/stable/nitrogen/impl/src/main/java/org/opendaylight/daexim/impl/ImportTask.java
 *
 * Which has the following copyright:
 *
 * Copyright (C) 2016 AT&T Intellectual Property. All rights reserved.
 * Copyright (c) 2016 Brocade Communications Systems, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package gov.nist.antd.sdnmud.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180427.Acls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180412.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import gov.nist.antd.baseapp.impl.BaseappConstants;
import gov.nist.antd.sdnmud.impl.dhcp.DhcpPacket;
import gov.nist.antd.sdnmud.impl.dhcp.DhcpRequestPacket;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.X509EncodedKeySpec;

/**
 * Packet in dispatcher that gets invoked on flow table miss when a packet is
 * sent up to the controller.
 *
 * @author mranga@nist.gov
 *
 */
public class PacketInDispatcher implements PacketProcessingListener {

	private SdnmudProvider sdnmudProvider;

	private SchemaService schemaService;

	private DOMDataBroker domDataBroker;

	private static final Logger LOG = LoggerFactory.getLogger(PacketInDispatcher.class);

	private HashMap<String, Flow> flowTable = new HashMap<String, Flow>();

	private static class MapDeserializerDoubleAsIntFix implements JsonDeserializer<LinkedHashMap<String, Object>> {
		/*
		 * (non-Javadoc)
		 *
		 * Bug fix for JSON serialization in Gson.
		 *
		 * @see com.google.gson.JsonDeserializer#deserialize(com.google.gson.
		 * JsonElement, java.lang.reflect.Type,
		 * com.google.gson.JsonDeserializationContext)
		 *
		 * @see
		 * https://stackoverflow.com/questions/36508323/how-can-i-prevent-gson-
		 * from-converting-integers-to-doubles
		 */

		@Override
		@SuppressWarnings("unchecked")
		public LinkedHashMap<String, Object> deserialize(JsonElement json, Type typeOfT,
				JsonDeserializationContext context) throws JsonParseException {
			return (LinkedHashMap<String, Object>) read(json);
		}

		public Object read(JsonElement in) {

			if (in.isJsonArray()) {
				List<Object> list = new ArrayList<Object>();
				JsonArray arr = in.getAsJsonArray();
				for (JsonElement anArr : arr) {
					list.add(read(anArr));
				}
				return list;
			} else if (in.isJsonObject()) {
				LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
				JsonObject obj = in.getAsJsonObject();
				Set<Map.Entry<String, JsonElement>> entitySet = obj.entrySet();
				for (Map.Entry<String, JsonElement> entry : entitySet) {
					map.put(entry.getKey(), read(entry.getValue()));
				}
				return map;
			} else if (in.isJsonPrimitive()) {
				JsonPrimitive prim = in.getAsJsonPrimitive();
				if (prim.isBoolean()) {
					return prim.getAsBoolean();
				} else if (prim.isString()) {
					return prim.getAsString();
				} else if (prim.isNumber()) {
					Number num = prim.getAsNumber();
					// here you can handle double int/long values
					// and return any type you want
					// this solution will transform 3.0 float to long values
					if (Math.ceil(num.doubleValue()) == num.longValue())
						return num.longValue();
					else {
						return num.doubleValue();
					}
				}
			}
			return null;
		}
	}

	public PacketInDispatcher(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
		this.domDataBroker = sdnmudProvider.getDomDataBroker();
		this.schemaService = sdnmudProvider.getSchemaService();
	}

	private static MacAddress rawMacToMac(final byte[] rawMac) {
		MacAddress mac = null;
		if (rawMac != null) {
			StringBuilder sb = new StringBuilder();
			for (byte octet : rawMac) {
				sb.append(String.format(":%02X", octet));
			}
			mac = new MacAddress(sb.substring(1));
		}
		return mac;
	}

	private void transmitPacket(PacketReceived notification) {

		TransmitPacketInputBuilder tpib = new TransmitPacketInputBuilder().setPayload(notification.getPayload())
				.setBufferId(OFConstants.OFP_NO_BUFFER);
		tpib.setEgress(notification.getIngress());
		OutputActionBuilder output = new OutputActionBuilder();
		output.setMaxLength(Integer.valueOf(0xffff));
		String matchInPortUri = notification.getMatch().getInPort().getValue();

		output.setOutputNodeConnector(new Uri(matchInPortUri));
		ActionBuilder ab = new ActionBuilder();
		ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
		ab.setOrder(1);

		List<Action> actionList = new ArrayList<Action>();
		actionList.add(ab.build());
		tpib.setAction(actionList);

		this.sdnmudProvider.getPacketProcessingService().transmitPacket(tpib.build());
	}

	private Collection<String> getLocalNetworks(String nodeConnectorUri) {

		ControllerclassMapping controllerMap = sdnmudProvider.getControllerClassMap(nodeConnectorUri);

		if (controllerMap == null) {
			return null;
		}

		return controllerMap.getLocalNetworks();
	}

	private boolean isLocalAddress(String nodeId, String ipAddress) {
		boolean isLocalAddress = false;
		if (this.getLocalNetworks(nodeId) != null) {
			for (String localNetworkStr : this.getLocalNetworks(nodeId)) {
				LOG.debug("localNetworkStr = " + localNetworkStr);
				String[] pieces = localNetworkStr.split("/");
				int prefixLength = new Integer(pieces[1]) / 8;

				String[] pieces1 = pieces[0].split("\\.");
				String prefix = "";

				for (int i = 0; i < prefixLength; i++) {
					prefix = prefix + pieces1[i] + ".";
				}
				LOG.debug("prefix = " + prefix);
				if (ipAddress.startsWith(prefix)) {
					isLocalAddress = true;
					break;
				}
			}
		}
		return isLocalAddress;
	}

	private void installSrcMacMatchStampManufacturerModelFlowRules(MacAddress srcMac, boolean isLocalAddress,
			String mudUri, InstanceIdentifier<FlowCapableNode> node) {
		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		int flag = 0;
		if (isLocalAddress) {
			flag = 1;
		}

		LOG.debug("installStampSrcManufacturerModelFlowRules : dstMac = " + srcMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " mudUri " + mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(flag).shiftLeft(SdnMudConstants.SRC_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT));

		BigInteger metadataMask = SdnMudConstants.SRC_MANUFACTURER_MASK.or(SdnMudConstants.SRC_MODEL_MASK)
				.or(SdnMudConstants.SRC_NETWORK_MASK);

		FlowCookie flowCookie = SdnMudConstants.SRC_MANUFACTURER_STAMP_FLOW_COOKIE;

		String flowIdStr = SdnMudConstants.SRC_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX + srcMac.getValue()
				+ "/" + metadata.toString(16);

		Flow flow = this.flowTable.get(flowIdStr);

		if (flow == null) {
			FlowId flowId = new FlowId(flowIdStr);

			flow = FlowUtils.createSourceMacMatchSetMetadataGoToNextTableFlow(srcMac, metadata, metadataMask,
					BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie,
					BaseappConstants.CACHE_TIMEOUT).build();
			this.flowTable.put(flowIdStr, flow);
		}

		sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, node);
	}

	public void installDstMacMatchStampManufacturerModelFlowRules(MacAddress dstMac, boolean isLocalAddress,
			String mudUri, InstanceIdentifier<FlowCapableNode> node) {

		String manufacturer = InstanceIdentifierUtils.getAuthority(mudUri);
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(manufacturer);
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		int flag = 0;
		if (isLocalAddress) {
			flag = 1;
		}

		LOG.debug("installStampDstManufacturerModelFlowRules : dstMac = " + dstMac.getValue() + " isLocalAddress "
				+ isLocalAddress + " mudUri " + mudUri);

		BigInteger metadata = BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(flag).shiftLeft(SdnMudConstants.DST_NETWORK_FLAGS_SHIFT))
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));
		BigInteger metadataMask = SdnMudConstants.DST_MANUFACTURER_MASK.or(SdnMudConstants.DST_MODEL_MASK)
				.or(SdnMudConstants.DST_NETWORK_MASK);
		FlowCookie flowCookie = SdnMudConstants.DST_MANUFACTURER_MODEL_FLOW_COOKIE;

		String flowIdStr = SdnMudConstants.DEST_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX + dstMac.getValue()
				+ "/" + metadata.toString(16);

		Flow flow = this.flowTable.get(flowIdStr);

		if (flow == null) {

			FlowId flowId = new FlowId(flowIdStr);

			flow = FlowUtils.createDestMacMatchSetMetadataAndGoToNextTableFlow(dstMac, metadata, metadataMask,
					BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE, flowId, flowCookie,
					BaseappConstants.CACHE_TIMEOUT).build();
			this.flowTable.put(flowIdStr, flow);

		}

		sdnmudProvider.getFlowCommitWrapper().writeFlow(flow, node);
	}

	private void importFromNormalizedNode(final DOMDataReadWriteTransaction rwTrx, final LogicalDatastoreType type,
			final NormalizedNode<?, ?> data) throws TransactionCommitFailedException, ReadFailedException {
		if (data instanceof NormalizedNodeContainer) {
			@SuppressWarnings("unchecked")
			YangInstanceIdentifier yid = YangInstanceIdentifier.create(data.getIdentifier());
			// rwTrx.put(type, yid, data);
			rwTrx.merge(type, yid, data);
			rwTrx.submit();
		} else {
			throw new IllegalStateException("Root node is not instance of NormalizedNodeContainer");
		}
	}

	private int doHttpGet(String url, byte[] data) throws NoSuchAlgorithmException, KeyStoreException,
			KeyManagementException, ClientProtocolException, IOException {
		SSLContextBuilder builder = new SSLContextBuilder();

		/*
		 * DUMMY Host Name verifier for testing purposes
		 */

		HostnameVerifier hv = new HostnameVerifier() {
			@Override
			public boolean verify(String urlHostName, SSLSession session) {
				return true;
			}

		};

		// BUGBUG -- only for testing. Should not trust self signed certs.

		builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());

		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), hv);

		CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

		HttpGet httpGet = new HttpGet(url);

		CloseableHttpResponse response = httpclient.execute(httpGet);

		try {
			// Get the response
			if (response.getStatusLine().getStatusCode() == 200) {
				return response.getEntity().getContent().read(data);
			} else {
				LOG.error("Could not fetch from " + url + " statusCode = " + response.getStatusLine().getStatusCode());
				throw new IOException(
						"Could not fetch from " + url + " statusCode = " + response.getStatusLine().getStatusCode());
			}
		} finally {
			response.close();
		}

	}

	private void importDatastore(String jsonData, QName qname) throws TransactionCommitFailedException, IOException,
			ReadFailedException, SchemaSourceException, YangSyntaxErrorException {
		// create StringBuffer object

		LOG.info("jsonData = " + jsonData);

		byte bytes[] = jsonData.getBytes();
		InputStream is = new ByteArrayInputStream(bytes);

		final NormalizedNodeContainerBuilder<?, ?, ?, ?> builder = ImmutableContainerNodeBuilder.create()
				.withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(qname));

		try (NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(builder)) {

			SchemaPath schemaPath = SchemaPath.create(true, qname);

			LOG.debug("SchemaPath " + schemaPath);

			SchemaNode parentNode = SchemaContextUtil.findNodeInSchemaContext(schemaService.getGlobalContext(),
					schemaPath.getPathFromRoot());

			LOG.debug("parentNode " + parentNode);

			try (JsonParserStream jsonParser = JsonParserStream.create(writer, schemaService.getGlobalContext(),
					parentNode)) {
				try (JsonReader reader = new JsonReader(new InputStreamReader(is))) {
					reader.setLenient(true);
					jsonParser.parse(reader);
					DOMDataReadWriteTransaction rwTrx = domDataBroker.newReadWriteTransaction();
					importFromNormalizedNode(rwTrx, LogicalDatastoreType.CONFIGURATION, builder.build());
				}
			}
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public void onPacketReceived(PacketReceived notification) {

		if (this.sdnmudProvider.getCpeCollections() == null) {
			LOG.error("Topology node not found -- ignoring packet");
			return;
		}

		Ethernet ethernet = new Ethernet();

		try {
			ethernet.deserialize(notification.getPayload(), 0,
					notification.getPayload().length * NetUtils.NumBitsInAByte);

		} catch (Exception ex) {
			LOG.error("Error deserializing packet", ex);
		}

		// Extract various fields from the ethernet packet.

		int etherType = ethernet.getEtherType() < 0 ? 0xffff + ethernet.getEtherType() + 1 : ethernet.getEtherType();
		byte[] srcMacRaw = ethernet.getSourceMACAddress();
		byte[] dstMacRaw = ethernet.getDestinationMACAddress();

		// Extract the src mac address from the packet.
		MacAddress srcMac = rawMacToMac(srcMacRaw);
		MacAddress dstMac = rawMacToMac(dstMacRaw);

		short tableId = notification.getTableId().getValue();

		String matchInPortUri = notification.getMatch().getInPort().getValue();

		String nodeId = notification.getIngress().getValue().firstKeyOf(Node.class).getId().getValue();

		InstanceIdentifier<FlowCapableNode> node = this.sdnmudProvider.getNode(nodeId);

		LOG.debug("onPacketReceived : matchInPortUri = " + matchInPortUri + " nodeId  " + nodeId + " tableId " + tableId
				+ " srcMac " + srcMac.getValue() + " dstMac " + dstMac.getValue());

		if (etherType == SdnMudConstants.ETHERTYPE_LLDP) {
			LOG.debug("LLDP Pakcet -- dropping it");
			return;
		}

		if (etherType == SdnMudConstants.ETHERTYPE_IPV4) {
			String sourceIpAddress = PacketUtils.extractSrcIpStr(notification.getPayload());
			String destIpAddress = PacketUtils.extractDstIpStr(notification.getPayload());
			LOG.info("Source IP  " + sourceIpAddress + " dest IP  " + destIpAddress);

			if (!this.sdnmudProvider.isCpeNode(nodeId)) {
				return;
			}

			if (tableId == BaseappConstants.SRC_DEVICE_MANUFACTURER_STAMP_TABLE) {
				Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(srcMac);
				boolean isLocalAddress = this.isLocalAddress(nodeId, sourceIpAddress);
				installSrcMacMatchStampManufacturerModelFlowRules(srcMac, isLocalAddress, mudUri.getValue(), node);
				// transmitPacket(notification);
			} else if (tableId == BaseappConstants.DST_DEVICE_MANUFACTURER_STAMP_TABLE) {
				Uri mudUri = this.sdnmudProvider.getMappingDataStoreListener().getMudUri(dstMac);
				boolean isLocalAddress = this.isLocalAddress(nodeId, destIpAddress);
				installDstMacMatchStampManufacturerModelFlowRules(dstMac, isLocalAddress, mudUri.getValue(), node);
				// transmitPacket(notification);
			} else if (tableId == BaseappConstants.SDNMUD_RULES_TABLE) {
				LOG.debug("PacketInDispatcher: Packet packetIn from SDNMUD_RULES_TABLE");
				byte[] rawPacket = notification.getPayload();
				int protocol = PacketUtils.extractIpProtocol(rawPacket);
				String srcIp = PacketUtils.extractSrcIpStr(rawPacket);

				LOG.info("PacketInDispatcher: protocol = " + protocol + " srcIp = " + srcIp);

				if (protocol == SdnMudConstants.TCP_PROTOCOL) {
					int port = PacketUtils.getSourcePort(rawPacket);
					BigInteger metadata = notification.getMatch().getMetadata().getMetadata();
					BigInteger metadataMask = SdnMudConstants.DEFAULT_METADATA_MASK;

					if (PacketUtils.isSYNFlagOn(rawPacket)) {
						LOG.debug("PacketInDispatcher: Got an illegal SYN -- blocking the flow");
						String flowIdStr = "/sdnmud/SrcIpAddressProtocolDestMacMatchDrop:" + srcIp + ":"
								+ dstMac.getValue() + ":" + SdnMudConstants.TCP_PROTOCOL + ":" + port;
						FlowId flowId = new FlowId(flowIdStr);
						Flow fb = this.flowTable.get(flowIdStr);
						if (fb == null) {
							FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);

							fb = FlowUtils.createSrcIpAddressProtocolDestMacMatchGoTo(new Ipv4Address(srcIp), dstMac,
									port, SdnMudConstants.TCP_PROTOCOL, tableId, BaseappConstants.DROP_TABLE, metadata,
									metadataMask, 1, flowId, flowCookie).build();
							this.flowTable.put(flowIdStr, fb);
						}

						this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

					} else {
						LOG.debug("PacketInDispatcher: SYN flag is OFF. Allowing the flow to pass through");
						String flowIdStr = "/sdnmud/SrcIpAddressProtocolDestMacMatchGoTo:" + srcIp + ":"
								+ dstMac.getValue() + ":" + SdnMudConstants.TCP_PROTOCOL + ":" + port;

						FlowId flowId = new FlowId(flowIdStr);
						Flow fb = this.flowTable.get(flowIdStr);

						if (fb == null) {
							FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(nodeId);
							/*
							 * create a short term pass through flow to allow
							 * packet through. Give it a short timeout.
							 */

							fb = FlowUtils.createSrcIpAddressProtocolDestMacMatchGoTo(new Ipv4Address(srcIp), dstMac,
									port, SdnMudConstants.TCP_PROTOCOL, tableId, metadata, metadataMask, 1, flowId,
									flowCookie).build();
							this.flowTable.put(flowIdStr, fb);
						}

						this.sdnmudProvider.getFlowCommitWrapper().writeFlow(fb, node);

					}
					transmitPacket(notification);

				} else if (protocol == SdnMudConstants.UDP_PROTOCOL) {
					// this is a DH request.
					DhcpPacket dhcpPacket = DhcpPacket.decodeFullPacket(notification.getPayload(), DhcpPacket.ENCAP_L2);

					LOG.info("DHCP packet type = " + dhcpPacket.getClass().getName());

					if (dhcpPacket instanceof DhcpRequestPacket) {
						DhcpRequestPacket dhcpRequestPacket = (DhcpRequestPacket) dhcpPacket;
						String mudUrl = dhcpRequestPacket.getMudUrl();
						if (mudUrl != null) {
							LOG.info("Options 161 request: MUD URL = " + mudUrl);
							try {

								byte[] mudFileChars = new byte[65536];
								int nread = this.doHttpGet(mudUrl, mudFileChars);

								if (nread > 0) {

									LOG.info("read " + nread + " characters");

									assert nread < 65536;

									byte[] mudfileData = Arrays.copyOf(mudFileChars, nread);

									String mudFileStr = new String(mudfileData);

									/* Set up gson to not convert to double */
									Gson gson = new GsonBuilder().setLenient()
											.registerTypeAdapter(new TypeToken<Map<String, LinkedHashMap>>() {
											}.getType(), new MapDeserializerDoubleAsIntFix()).setPrettyPrinting()
											.create();

									/*
									 * Set up gson to preserve the order of
									 * fields
									 */
									Map<?, ?> mudFile = gson.fromJson(mudFileStr,
											new TypeToken<Map<String, LinkedHashMap>>() {
											}.getType());

									Map<?, ?> ietfMud = (Map<?, Object>) mudFile.get("ietf-mud:mud");

									/*
									 * The MUD signature points to the signature
									 * file for this MUD file.
									 */
									String mudSignatureUrl = (String) ietfMud.get("mud-signature");

									LOG.info("mud-signature " + mudSignatureUrl);

									if (mudSignatureUrl != null) {

										byte[] buffer = new byte[65536];
										int bytesRead = this.doHttpGet(mudSignatureUrl, buffer);
										LOG.debug("read " + bytesRead + " bytes");
										byte[] signature = Arrays.copyOf(buffer, bytesRead);
										String manufacturer = InstanceIdentifierUtils.getAuthority(mudUrl);
										if (sdnmudProvider.getSdnmudConfig() == null) {
											LOG.error("Configuration file not found -- not installing MUD profile");
											return;
										}
										String keystoreHome = sdnmudProvider.getSdnmudConfig().getKeystoreHome();
										LOG.debug("keystoreHome = " + keystoreHome);
										FileInputStream is = new FileInputStream(keystoreHome);
										String keyPass = sdnmudProvider.getSdnmudConfig().getKeyPass();
										LOG.debug("keyPass = " + keyPass);
										KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
										LOG.debug("keystore = " + keystore);
										keystore.load(is, keyPass.toCharArray());
										Certificate cert = keystore.getCertificate(manufacturer);
										PublicKey publicKey = cert.getPublicKey();
										String algorithm = publicKey.getAlgorithm();
										Signature sig = Signature.getInstance("SHA256withRSA");
										sig.initVerify(publicKey);
										sig.update(mudfileData);
										LOG.debug("Signature = " + sig + " algorithm = " + algorithm);
										if (!sig.verify(signature)) {
											LOG.error("Signature verification failed");
											return;
										} else {
											LOG.info("Signature verification succeeded");
										}

									}

									String mudStr = gson.toJson(mudFile.get("ietf-mud:mud"));

									this.importDatastore(mudStr, Mud.QNAME);

									String aclStr = gson.toJson(mudFile.get("ietf-access-control-list:acls"));

									this.importDatastore(aclStr, Acls.QNAME);

									MappingBuilder mb = new MappingBuilder();
									ArrayList<MacAddress> macAddresses = new ArrayList<>();
									macAddresses.add(srcMac);
									mb.setDeviceId(macAddresses);
									mb.setMudUrl(new Uri(mudUrl));
									InstanceIdentifier<Mapping> mappingId = InstanceIdentifier.builder(Mapping.class)
											.build();
									ReadWriteTransaction tx = sdnmudProvider.getDataBroker().newReadWriteTransaction();

									tx.merge(LogicalDatastoreType.CONFIGURATION, mappingId, mb.build());
									tx.submit();
								}

							} catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException
									| TransactionCommitFailedException | ReadFailedException | CertificateException
									| SchemaSourceException | YangSyntaxErrorException | SignatureException
									| InvalidKeyException ex) {
								LOG.error("Error fetching MUD file -- not installing", ex);
							}

						}
					}
				}

			}

		}
	}

}
