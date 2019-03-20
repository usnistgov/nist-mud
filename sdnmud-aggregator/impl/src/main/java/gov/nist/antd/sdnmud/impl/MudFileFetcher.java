package gov.nist.antd.sdnmud.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.CharBuffer;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev180427.Acls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180615.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.MappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudConfig;
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
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

/**
 * A Utility class that fetches a MUD file from the manufacturer site, verfies
 * the signature and installs it.
 * 
 * @author mranga
 *
 */

public class MudFileFetcher {

	private static final Logger LOG = LoggerFactory.getLogger(MudFileFetcher.class);
	private SdnmudConfig sdnmudConfig;
	private DatastoreUpdater datastoreUpdater;
	private SdnmudProvider sdnmudProvider;

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
		 * @see https://stackoverflow.com/questions/36508323/how-can-i-prevent-gson-
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

		if (sdnmudConfig.isTrustSelfSignedCert()) {
			builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		} else {

			TrustStrategy trustStrategy = new TrustStrategy() {
				@Override
				public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					LOG.error("VALIDATION CODE SHOULD GO HERE -- this accepts ALL certificates");
					// TODO -- verify the certificate chain.
					return true;
				}
			};
			builder.loadTrustMaterial(null, trustStrategy);
		}

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

	public String fetchAndInstallMudFile(String mudUrl) {
		LOG.info("MUD URL = " + mudUrl);
		try {

			// BUG BUG -- this should be a config parameter
			int max_length = 1024 * 128;

			byte[] mudFileChars = new byte[max_length];

			String[] parts = mudUrl.split(":");

			String protocol = parts[0];

			int nread = -1;
			boolean fileFetchedFromHttps = false;
			if (protocol.equals("http") || protocol.equals("https")) {
				// check if we have the cached file.
				byte[] cachedFile = this.sdnmudProvider.getMudCacheDatastoreListener().getMudFile(mudUrl);
				if (cachedFile == null) {
					nread = this.doHttpGet(mudUrl, mudFileChars);
					fileFetchedFromHttps = true;
				} else {
					nread = cachedFile.length;
					mudFileChars = cachedFile;

				}
			} else if (protocol.equals("file")) {
				// FILE URLs are supported for testing purposes.
				String fileName = System.getProperty("karaf.home") + "/etc/mudprofiles/" + parts[1].substring(2);
				File file = new File(fileName);
				if (file.length() > max_length) {
					LOG.error("MUD File is too large -- exitting");
					return null;
				}

				if (file.exists() && file.isFile()) {
					RandomAccessFile randomAccessFile = new RandomAccessFile(fileName, "r");

					randomAccessFile.readFully(mudFileChars, 0, (int) file.length());
					nread = (int) file.length();
					randomAccessFile.close();
				} else {
					LOG.error("Invalid mud file specified : " + fileName);
					return null;
				}
			} else {
				LOG.error("Unsupported PROTOCOL " + protocol);
				return null;
			}

			if (nread > 0) {

				LOG.info("read " + nread + " characters");

				assert nread < 65536;

				byte[] mudfileData = Arrays.copyOf(mudFileChars, nread);

				String mudFileStr = new String(mudfileData);

				/* Set up gson to not convert to double */
				Gson gson = new GsonBuilder().setLenient()
						.registerTypeAdapter(new TypeToken<Map<String, LinkedHashMap>>() {
						}.getType(), new MapDeserializerDoubleAsIntFix()).setPrettyPrinting().create();

				/*
				 * Set up gson to preserve the order of fields
				 */
				Map<?, ?> mudFile = gson.fromJson(mudFileStr, new TypeToken<Map<String, LinkedHashMap>>() {
				}.getType());

				Map<?, ?> ietfMud = (Map<?, Object>) mudFile.get("ietf-mud:mud");

				/*
				 * The MUD signature points to the signature file for this MUD file.
				 */
				String mudSignatureUrl = (String) ietfMud.get("mud-signature");
				String mudUrlFromProfile = (String) ietfMud.get("mud-url");
				int cacheTimeout = ((Long) ietfMud.get("cache-validity")).intValue();

				LOG.info("mud-signature " + mudSignatureUrl);
				if ( mudSignatureUrl == null && fileFetchedFromHttps) {
					LOG.error("File verification failed -- no mud signature URL is given protocol " + protocol);
					return null;
				}
		
				if (mudSignatureUrl != null && fileFetchedFromHttps) {

					// Allocate a buffer to fetch the signature.
					byte[] buffer = new byte[65536];
					int bytesRead = this.doHttpGet(mudSignatureUrl, buffer);
					LOG.debug("read " + bytesRead + " bytes");
					byte[] signature = Arrays.copyOf(buffer, bytesRead);
					String manufacturer = IdUtils.getAuthority(mudUrl);

					String cacertHome = sdnmudConfig.getCaCerts();
					if (!cacertHome.startsWith("/")) {
						cacertHome = System.getProperty("java.home") + "/" + cacertHome;
					}
					LOG.debug("cacertHome = " + cacertHome);
					FileInputStream is = new FileInputStream(cacertHome);
					String keyPass = sdnmudConfig.getKeyPass();
					LOG.debug("keyPass = " + keyPass);
					KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
					LOG.debug("keystore = " + keystore);
					keystore.load(is, keyPass.toCharArray());
					Certificate cert = keystore.getCertificate(manufacturer);
					if (cert == null) {
						LOG.error("Certificate not found in keystore -- not installing mud profile");
						return null;
					}
					PublicKey publicKey = cert.getPublicKey();
					String algorithm = publicKey.getAlgorithm();
					Signature sig = Signature.getInstance("SHA256withRSA");
					sig.initVerify(publicKey);
					sig.update(mudfileData);
					LOG.debug("Signature = " + sig + " algorithm = " + algorithm);
					if (!sig.verify(signature)) {
						LOG.error("Signature verification failed -- " + " need to alert the admin or block device.");
						return null;
					} else {
						LOG.info("Signature verification succeeded");
					}

				} 

				if (fileFetchedFromHttps) {
					LOG.info("Write to Cache here ");
					this.sdnmudProvider.getMudCacheDatastoreListener().putMudProfileInCache(mudUrl, cacheTimeout,
							mudFileStr);
				}

				String mudStr = gson.toJson(mudFile.get("ietf-mud:mud"));

				// Writing to the datastore will invoke the listener.
				datastoreUpdater.writeToDatastore(mudStr, Mud.QNAME);

				String aclStr = gson.toJson(mudFile.get("ietf-access-control-list:acls"));

				datastoreUpdater.writeToDatastore(aclStr, Acls.QNAME);
				return mudUrlFromProfile;

			}

		} catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException
				| TransactionCommitFailedException | ReadFailedException | CertificateException | SchemaSourceException
				| YangSyntaxErrorException | SignatureException | InvalidKeyException ex) {
			LOG.error("Error fetching MUD file -- not installing", ex);
			return null;
		}
		return null;

	}

	public MudFileFetcher(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
		this.sdnmudConfig = sdnmudProvider.getSdnmudConfig();
		this.datastoreUpdater = sdnmudProvider.getDatastoreUpdater();
	}

}
