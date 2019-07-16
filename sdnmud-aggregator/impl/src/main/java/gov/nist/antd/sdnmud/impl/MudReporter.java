package gov.nist.antd.sdnmud.impl;

import java.io.IOException;
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
import java.util.Collection;
import java.util.HashMap;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.Mud1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.extension.rev190621.mud.reporter.extension.Reporter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Timestamp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.MudReporterBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.reporter.rev190621.mud.reporter.grouping.MudReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class MudReporter implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(MudReporter.class);
	private SdnmudProvider provider;
	private HashMap<Uri, Long> lastUpdated = new HashMap<Uri, Long>();

	public MudReporter(SdnmudProvider provider) {
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

	@Override
	public void run() {
		Collection<Mud> mudFiles = provider.getMudProfiles();

		for (Mud mud : mudFiles) {
			Uri mudUrl = mud.getMudUrl();
			long currentTime = System.currentTimeMillis();

			if (mud.getAugmentation(Mud1.class) != null) {
				Reporter reporter = mud.getAugmentation(Mud1.class).getReporter();
				int intervalMillis = reporter.getFrequency().intValue() * 60 * 1000;
				long currentTimeMilis = System.currentTimeMillis();

				if (!lastUpdated.containsKey(mudUrl)
						|| (currentTimeMilis - lastUpdated.get(mudUrl)) >= intervalMillis) {
					Uri reporterUri = reporter.getReportUri();
					MudReporterBuilder mudReporterBuilder = new MudReporterBuilder();
					mudReporterBuilder.setMudurl(mudUrl);
					MudReportBuilder mudReportBuilder = new MudReportBuilder();
					mudReportBuilder.setTime(new Timestamp (currentTime));
					

				}
			}
		}

	}

}
