package com.jsql.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * SSL certificates are used by https connection. This utility class
 * gets rid of malformed certification chains from bad configured websites
 * in order to ignore connection exception in that specific case.
 */
public class CertificateUtil {
    
    private static final Logger LOGGER = LogManager.getRootLogger();
    
    private SSLContext sslContext = null;

    public CertificateUtil() {
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        
        // Create a trust manager that does not validate certificate chains
        // and ignore exception PKIX path building failed: unable to find valid certification path to requested target
        var trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                @SuppressWarnings("java:S4830")
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // nothing
                }
                @SuppressWarnings("java:S4830")
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // nothing
                }
            }
        };
        
        try {
            this.sslContext = SSLContext.getInstance("TLSv1.2");
            this.sslContext.init(null, trustAllCerts, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.log(LogLevelUtil.CONSOLE_ERROR, "Error ignoring untrusted SSL", e);
        }
    }
    
    public SSLContext getSslContext() {
        return this.sslContext;
    }
}
