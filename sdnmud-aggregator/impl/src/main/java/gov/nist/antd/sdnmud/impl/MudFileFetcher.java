package gov.nist.antd.sdnmud.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStoreBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.Store;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.Acls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudConfig;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
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

	private boolean verifyCertificateChain(Certificate[] certs) throws CertificateException {

		int n = certs.length;
		for (int i = 0; i < n - 1; i++) {

			X509Certificate cert = (X509Certificate) certs[i];
			cert.checkValidity();
			X509Certificate issuer = (X509Certificate) certs[i + 1];
			if (cert.getIssuerX500Principal().equals(issuer.getSubjectX500Principal()) == false) {
				return false;
			}
			try {
				cert.verify(issuer.getPublicKey());
			} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException e) {
				return false;
			}
		}
		X509Certificate last = (X509Certificate) certs[n - 1];
		// if self-signed, verify the final cert
		// TODO -- check if this is in our CA store.
		if (last.getIssuerX500Principal().equals(last.getSubjectX500Principal())) {
			// Issuer == subject means it is self signed.
			try {
				last.verify(last.getPublicKey());
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init((KeyStore) null);
				X509TrustManager defaultTm = null;
				for (TrustManager tm : tmf.getTrustManagers()) {
					if (tm instanceof X509TrustManager) {
						defaultTm = (X509TrustManager) tm;
						break;
					}
				}
				if (defaultTm == null) {
					LOG.error("Could not find default TM");
					return false;
				}
				boolean verified = false;
				for (Certificate cf : defaultTm.getAcceptedIssuers()) {
					if (cf.equals(last)) {
						LOG.info("Trust chain verified");
						verified = true;
					}
				}
				return verified;
			} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException
					| KeyStoreException e) {
				return false;
			}
		}

		return false;

	}

	private int doHttpGet(String url, byte[] data) throws NoSuchAlgorithmException, KeyStoreException,
			KeyManagementException, ClientProtocolException, IOException {
		SSLContextBuilder builder = new SSLContextBuilder();

		// TODO -- this is for testing purposes.
		X509HostnameVerifier hv;

		if (!sdnmudConfig.isStrictHostnameVerify()) {
			hv = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
		} else {
			hv = SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER;
		}

		if (sdnmudConfig.isTrustSelfSignedCert()) {
			builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		} else {
			TrustStrategy trustStrategy = new TrustStrategy() {
				@Override
				public boolean isTrusted(X509Certificate[] certs, String authType) throws CertificateException {
					return verifyCertificateChain(certs);
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
				int len = (int)response.getEntity().getContentLength();
				LOG.info("content-length = " + len);
				int bytesRead = 0;
				while (bytesRead != len) {
				    int nr = response.getEntity().getContent().read(data, bytesRead, len);
				    if (nr <= 0) {
				    	LOG.error("End of stream detected ");
				    	return bytesRead;
				    } else {
				    	bytesRead += nr;
				    }
				}
				return len;
			} else {
				LOG.error("Could not fetch from " + url + " statusCode = " + response.getStatusLine().getStatusCode());
				throw new IOException(
						"Could not fetch from " + url + " statusCode = " + response.getStatusLine().getStatusCode());
			}
		} finally {
			response.close();
		}

	}

	boolean verifySignature(byte[] mudfileData, byte[] signature)
			throws CMSException, OperatorCreationException, CertificateException {

		CMSProcessableByteArray cmsBa = new CMSProcessableByteArray(mudfileData);
		CMSSignedData cms = new CMSSignedData(cmsBa, signature);
		Store<X509CertificateHolder> store = cms.getCertificates();
		SignerInformationStore signers = cms.getSignerInfos();
		Collection<SignerInformation> c = signers.getSigners();
		Iterator<SignerInformation> it = c.iterator();
		while (it.hasNext()) {
			SignerInformation signer = (SignerInformation) it.next();
			Collection certCollection = store.getMatches(signer.getSID());
			Iterator certIt = certCollection.iterator();
			X509CertificateHolder certHolder = (X509CertificateHolder) certIt.next();
			X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
			cert.checkValidity();
			if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(cert))) {
				LOG.info("verified");
				return true;
			}
		}
		LOG.error("Signature verification failed");
		return false;
	}

	private PublicKey checkCertPath(SignerId signerId, Store<X509CertificateHolder> certs)
			throws IOException, GeneralSecurityException {
		CertStore store = new JcaCertStoreBuilder().setProvider("BC").addCertificates(certs).build();

		CertPathBuilder pathBuilder = CertPathBuilder.getInstance("PKIX", "BC");
		X509CertSelector targetConstraints = new X509CertSelector();

		targetConstraints.setIssuer(signerId.getIssuer().getEncoded());
		targetConstraints.setSerialNumber(signerId.getSerialNumber());

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
		Enumeration<String> aliases = keystore.aliases();
		HashSet<TrustAnchor> trustAnchors = new HashSet<TrustAnchor>();
		while (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
			trustAnchors.add(new TrustAnchor(cert, null));
		}

		PKIXBuilderParameters params = new PKIXBuilderParameters(trustAnchors, targetConstraints);

		params.addCertStore(store);
		params.setRevocationEnabled(false); // TODO: CRLs?

		return pathBuilder.build(params).getCertPath().getCertificates().get(0).getPublicKey();
	}

	private boolean verifySignatureP7S(byte[] mudfileData, byte[] signature)
			throws CMSException, OperatorCreationException, IOException, GeneralSecurityException {

		CMSProcessableByteArray cmsBa = new CMSProcessableByteArray(mudfileData);
		CMSSignedData cms = new CMSSignedData(cmsBa, signature);
		Store<X509CertificateHolder> store = cms.getCertificates();

		SignerInformationStore signers = cms.getSignerInfos();
		Collection<SignerInformation> c = signers.getSigners();
		Iterator<SignerInformation> it = c.iterator();
		while (it.hasNext()) {
			SignerInformation signer = (SignerInformation) it.next();
			Collection<X509CertificateHolder> certCollection = store.getMatches(signer.getSID());
			if (certCollection.size() == 0) {
				LOG.error("Impossible to find a cert using signer ID " + signer.getSID());
				return false;
			}
			Iterator<X509CertificateHolder> certIt = certCollection.iterator();
			X509CertificateHolder certHolder = certIt.next();
			X509Certificate cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
					.getCertificate(certHolder);
			cert.checkValidity();
			SignerId signerId = signer.getSID();
			PublicKey result = this.checkCertPath(signerId, store);

			if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME)
					.build(result))) {
				LOG.info("verification success");
				return true;
			}
		}
		LOG.error("Signature verification failed");
		return false;
	}

	public String fetchAndInstallMudFile(String mudUrl) {
		LOG.info("MudfileFetcher: fetchAndInstall : MUD URL = " + mudUrl);
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
					LOG.info("Found file in mud cache length = " + nread);

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
				Map<?, ?> mudFile = null;

				try {
					mudFile = gson.fromJson(mudFileStr, new TypeToken<Map<String, LinkedHashMap>>() {
					}.getType());
				} catch (Exception ex) {
					LOG.error("Error decoding json ", ex);
					return null;
				}
				
				assert mudFile != null;

				Map<?, ?> ietfMud = (Map<?, Object>) mudFile.get("ietf-mud:mud");

				/*
				 * The MUD signature points to the signature file for this MUD file.
				 */
				String mudSignatureUrl = (String) ietfMud.get("mud-signature");
				String mudUrlFromProfile = (String) ietfMud.get("mud-url");
				int cacheTimeout = ((Long) ietfMud.get("cache-validity")).intValue();

				LOG.info("mud-signature " + mudSignatureUrl);
				if (mudSignatureUrl == null && fileFetchedFromHttps) {
					LOG.error("File verification failed -- no mud signature URL is given protocol " + protocol);
					return null;
				}

				if (mudSignatureUrl != null && fileFetchedFromHttps) {

					// Allocate a buffer to fetch the signature.
					byte[] buffer = new byte[65536];
					int bytesRead = this.doHttpGet(mudSignatureUrl, buffer);
					LOG.debug("read " + bytesRead + " bytes");
					byte[] signature = Arrays.copyOf(buffer, bytesRead);

					if (!verifySignatureP7S(mudfileData, signature)) {
						return null;
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

				Map aclMap = (Map) mudFile.get("ietf-access-control-list:acls");

				// Rewrite the ACL names to allow coexistence with conflicting ACL names from
				// different mud profiles.

				for (Object aclsObj : aclMap.values()) {
					// Get the ACL (there's a list of them). Rename
					ArrayList acls = (ArrayList) aclsObj;
					for (Object aclObj : acls) {
						Map acl = (Map) aclObj;
						String oldName = (String) acl.get("name");
						String newName = mudUrlFromProfile + "/" + oldName;
						LOG.info("renamed ACL " + oldName + " to " + newName);
						acl.put("name", newName);
					}
				}

				String aclStr = gson.toJson(mudFile.get("ietf-access-control-list:acls"));

				datastoreUpdater.writeToDatastore(aclStr, Acls.QNAME);
				return mudUrlFromProfile;

			}

		} catch (IOException | TransactionCommitFailedException | ReadFailedException | SchemaSourceException
				| YangSyntaxErrorException | OperatorCreationException | CMSException | GeneralSecurityException
				| InterruptedException | ExecutionException ex) {
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
