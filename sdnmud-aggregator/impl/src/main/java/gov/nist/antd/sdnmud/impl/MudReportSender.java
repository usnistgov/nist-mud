package gov.nist.antd.sdnmud.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimerTask;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.Mud1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.mud.reporter.extension.Reporter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Timestamp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.IetfMudReporterData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporterBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporterGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.stream.JsonWriter;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class MudReportSender extends TimerTask {

	private static final Logger LOG = LoggerFactory.getLogger(MudReportSender.class);
	private SdnmudProvider provider;
	private HashMap<Uri, Long> lastUpdated = new HashMap<Uri, Long>();

	public MudReportSender(SdnmudProvider provider) {
		this.provider = provider;
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

	private void doPost(String reporterUri, String report) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException,
			ClientProtocolException, IOException {
		
		X509HostnameVerifier hv;
		SdnmudConfig sdnmudConfig = provider.getSdnmudConfig();
		SSLContextBuilder builder = new SSLContextBuilder();

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

		HttpPost httpPost = new HttpPost(reporterUri);
		HttpEntity entity = new ByteArrayEntity(report.getBytes("UTF-8"));
		httpPost.setEntity(entity);

		CloseableHttpResponse response = httpclient.execute(httpPost);
		if ( response.getStatusLine().getStatusCode() != 200) {
			LOG.error("Error posting log record");
		}
	}

	private String doConvert(SchemaPath schemaPath, NormalizedNode<?, ?> data, JSONCodecFactory codecFactory) {
		final StringWriter writer = new StringWriter();
		final JsonWriter jsonWriter = JsonWriterFactory.createJsonWriter(writer);
		final NormalizedNodeStreamWriter jsonStream;
		if (data instanceof MapEntryNode) {
			jsonStream = JSONNormalizedNodeStreamWriter.createNestedWriter(codecFactory, schemaPath, null, jsonWriter);
		} else {
			jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(codecFactory, schemaPath, null,
					jsonWriter);
		}
		final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
		try {
			nodeWriter.write(data);
			nodeWriter.flush();
			String jsonValue = writer.toString();
			return jsonValue;
		} catch (IOException e) {
			LOG.error("Error converting normalized ndoe to string", e);
			return null;
		}
	}

	@Override
	public void run() {
		Collection<Mud> mudFiles = provider.getMudProfiles();

		for (Mud mud : mudFiles) {
			Uri mudUrl = mud.getMudUrl();
			if (mud.getAugmentation(Mud1.class) != null) {
				Reporter reporter = mud.getAugmentation(Mud1.class).getReporter();
				if (reporter != null) {
					// int intervalMillis = reporter.getFrequency().intValue() *60* 1000;
					int intervalMillis = provider.getSdnmudConfig().getReporterFrequency().intValue() * 1000;
					long currentTimeMilis = System.currentTimeMillis();
					if (!lastUpdated.containsKey(mudUrl)
							|| (currentTimeMilis - lastUpdated.get(mudUrl)) >= intervalMillis) {
						MudReporterBuilder mudReporterBuilder = new MudReporterBuilder();
						List<MudReport> mudReportList = new ArrayList<MudReport>();

						for (String switchId : provider.getCpeSwitches()) {
							MudReport mudReport = new MudReportGenerator(provider).getMudReport(mud, switchId);
							mudReportList.add(mudReport);

						}
						mudReporterBuilder.setMudReport(mudReportList);
						mudReporterBuilder.setMudurl(mudUrl);
						MudReporter mudReporter = mudReporterBuilder.build();
						InstanceIdentifier<MudReporter> ii = InstanceIdentifier.create(MudReporter.class);
						BindingNormalizedNodeSerializer codec = provider.getBindingNormalizedNodeSerializer();
						Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> e = codec.toNormalizedNode(ii, mudReporter);
						YangInstanceIdentifier yii = e.getKey();
						NormalizedNode<?, ?> nn = e.getValue();
						SchemaContext sc = provider.getDomSchemaService().getGlobalContext();
						JSONCodecFactory codecFactory = JSONCodecFactory.createLazy(sc);
						SchemaPath schemaPath = SchemaPath.create(true, MudReporter.QNAME);
						SchemaPath parent = schemaPath.getParent();
						LOG.info("MUD REPORT = " + doConvert(parent, nn, codecFactory));

					}
				}
			}
		}

	}

}
