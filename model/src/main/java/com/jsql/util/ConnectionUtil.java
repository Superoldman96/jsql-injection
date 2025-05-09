package com.jsql.util;

import com.jsql.model.InjectionModel;
import com.jsql.model.bean.util.Header;
import com.jsql.model.bean.util.Interaction;
import com.jsql.model.bean.util.Request;
import com.jsql.model.exception.InjectionFailureException;
import com.jsql.model.exception.JSqlException;
import com.jsql.model.injection.method.AbstractMethodInjection;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class in charge of connection to web resources and management
 * of source page and request and response headers.
 */
public class ConnectionUtil {
    
    private static final Logger LOGGER = LogManager.getRootLogger();
    
    private String urlByUser;  // URL entered by user
    private String urlBase;  // URL entered by user without the query string
    private AbstractMethodInjection methodInjection;  // Method of injection: by query string, request or header
    private String typeRequest = StringUtil.GET;  // Default HTTP method. It can be changed to a custom method
    private final Random randomForUserAgent = new SecureRandom();
    private final InjectionModel injectionModel;
    private final CookieManager cookieManager = new CookieManager();
    
    public ConnectionUtil(InjectionModel injectionModel) {
        this.injectionModel = injectionModel;
    }
    
    public HttpClient.Builder getHttpClient() {
        var httpClientBuilder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(this.getTimeout()))
            .sslContext(this.injectionModel.getMediatorUtils().getCertificateUtil().getSslContext())
            .followRedirects(
                this.injectionModel.getMediatorUtils().getPreferencesUtil().isFollowingRedirection()
                ? HttpClient.Redirect.ALWAYS
                : HttpClient.Redirect.NEVER
            );
        if (this.injectionModel.getMediatorUtils().getPreferencesUtil().isHttp2Disabled()) {
            httpClientBuilder.version(Version.HTTP_1_1);
        }
        if (!this.injectionModel.getMediatorUtils().getPreferencesUtil().isNotProcessingCookies()) {
            httpClientBuilder.cookieHandler(this.cookieManager);
        }
        if (this.injectionModel.getMediatorUtils().getAuthenticationUtil().isAuthentEnabled()) {
            httpClientBuilder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        ConnectionUtil.this.injectionModel.getMediatorUtils().getAuthenticationUtil().getUsernameAuthentication(),
                        ConnectionUtil.this.injectionModel.getMediatorUtils().getAuthenticationUtil().getPasswordAuthentication().toCharArray()
                    );
                }
            });
        }
        return httpClientBuilder;
    }

    public static <T> Map<String, String> getHeadersMap(HttpResponse<T> httpResponse) {
        Map<String, String> sortedMap = ConnectionUtil.getHeadersMap(httpResponse.headers());
        String responseCodeHttp = String.valueOf(httpResponse.statusCode());
        sortedMap.put(":status", responseCodeHttp);
        return sortedMap;
    }
    
    public static Map<String, String> getHeadersMap(HttpHeaders httpHeaders) {
        Map<String, String> unsortedMap = httpHeaders.map()
            .entrySet()
            .stream()
            .sorted(Entry.comparingByKey())
            .map(entrySet -> new AbstractMap.SimpleEntry<>(
                entrySet.getKey(),
                String.join(", ", entrySet.getValue())
            ))
            .collect(Collectors.toMap(
                AbstractMap.SimpleEntry::getKey,
                AbstractMap.SimpleEntry::getValue
            ));
        return new TreeMap<>(unsortedMap);
    }

    /**
     * Check that the connection to the website is working correctly.
     * It uses authentication defined by user, with fixed timeout, and warn
     * user in case of authentication detected.
     */
    public HttpResponse<String> checkConnectionResponse() throws IOException, InterruptedException, JSqlException {
        var queryString = this.injectionModel.getMediatorUtils().getParameterUtil().getQueryStringFromEntries();
        var testUrl = this.getUrlBase().replaceAll("\\?$", StringUtils.EMPTY);

        if (StringUtils.isNotEmpty(queryString)) {
            testUrl += "?"+ queryString;
        }

        String contentTypeRequest = "text/plain";

        var body = this.injectionModel.getMediatorUtils().getParameterUtil().getRawRequest();

        if (this.injectionModel.getMediatorUtils().getParameterUtil().isMultipartRequest()) {
            body = body.replaceAll("(?s)\\\\n", "\r\n");
        } else if (this.injectionModel.getMediatorUtils().getParameterUtil().isRequestSoap()) {
            contentTypeRequest = "text/xml";
        } else if (!this.injectionModel.getMediatorUtils().getParameterUtil().getListRequest().isEmpty()) {
            contentTypeRequest = "application/x-www-form-urlencoded";
        }

        // Test the HTTP connection
        Builder httpRequest = HttpRequest.newBuilder();
        try {
            httpRequest.uri(
                URI.create(
                    testUrl  // Get encoded params without fragment
                    .replace(InjectionModel.STAR, StringUtils.EMPTY)  // Ignore injection point during the test
                )
            );
        } catch (IllegalArgumentException e) {
            throw new JSqlException(e);
        }
        httpRequest.setHeader(HeaderUtil.CONTENT_TYPE_REQUEST, contentTypeRequest)
            .timeout(Duration.ofSeconds(this.getTimeout()));
        
        this.injectionModel.getMediatorUtils().getCsrfUtil().addHeaderToken(httpRequest);
        this.injectionModel.getMediatorUtils().getDigestUtil().addHeaderToken(httpRequest);

        httpRequest.method(this.typeRequest, BodyPublishers.ofString(body));

        // Add headers if exists (Authorization:Basic, etc.)
        for (SimpleEntry<String, String> header: this.injectionModel.getMediatorUtils().getParameterUtil().getListHeader()) {
            HeaderUtil.sanitizeHeaders(httpRequest, header);
        }

        return this.injectionModel.getMediatorUtils().getHeaderUtil().checkResponseHeader(httpRequest, body);
    }

    public void testConnection() throws IOException, InterruptedException, JSqlException {
        // Check connection is working: define Cookie management, check HTTP status, parse <form> parameters, process CSRF
        LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, () -> I18nUtil.valueByKey("LOG_CONNECTION_TEST"));
        this.getCookieManager().getCookieStore().removeAll();
        HttpResponse<String> httpResponse = this.checkConnectionResponse();

        if (
            (httpResponse.statusCode() == 401 || httpResponse.statusCode() == 403)
            && !this.injectionModel.getMediatorUtils().getPreferencesUtil().isNotProcessingCookies()
            && (
                this.injectionModel.getMediatorUtils().getCsrfUtil().isCsrf()
                || this.injectionModel.getMediatorUtils().getDigestUtil().isDigest()
            )
        ) {
            if (this.injectionModel.getMediatorUtils().getPreferencesUtil().isProcessingCsrf()) {
                LOGGER.log(LogLevelUtil.CONSOLE_INFORM, () -> "Testing CSRF handshake from previous connection...");
            } else if (StringUtils.isNotEmpty(this.injectionModel.getMediatorUtils().getDigestUtil().getTokenDigest())) {
                LOGGER.log(LogLevelUtil.CONSOLE_INFORM, () -> "Testing Digest handshake from previous connection...");
            }
            httpResponse = this.checkConnectionResponse();
        }

        if (httpResponse.statusCode() >= 400 && !this.injectionModel.getMediatorUtils().getPreferencesUtil().isNotTestingConnection()) {
            throw new InjectionFailureException(String.format("Connection failed: problem when calling %s", httpResponse.uri().toURL()));
        }
    }

    /**
     * Call a URL and return the source page.
     * @param url to call
     * @return the source page of the URL
     */
    public String getSourceLineFeed(String url) {
        return this.getSource(url, true, false);
    }

    public String getSource(String url) {
        return this.getSource(url, false, false);
    }

    public String getSource(String url, boolean isConnectIssueIgnored) {  // reverse init result can be ignored
        return this.getSource(url, false, isConnectIssueIgnored);
    }

    public String getSource(String url, boolean lineFeed, boolean isConnectIssueIgnored) {
        Map<Header, Object> msgHeader = new EnumMap<>(Header.class);
        msgHeader.put(Header.URL, url);
        
        String pageSource = StringUtils.EMPTY;
        
        try {
            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(this.getTimeout()))
                .build();
            
            HttpHeaders httpHeaders;
            if (lineFeed) {
                HttpResponse<Stream<String>> response = this.getHttpClient().build().send(httpRequest, BodyHandlers.ofLines());
                pageSource = response.body().collect(Collectors.joining("\n"));
                httpHeaders = response.headers();
            } else {
                HttpResponse<String> response = this.getHttpClient().build().send(httpRequest, BodyHandlers.ofString());
                pageSource = response.body();
                httpHeaders = response.headers();
            }
            
            msgHeader.put(Header.RESPONSE, ConnectionUtil.getHeadersMap(httpHeaders));
            msgHeader.put(Header.HEADER, ConnectionUtil.getHeadersMap(httpRequest.headers()));
            
        } catch (IOException e) {
            if (!isConnectIssueIgnored) {  // ignoring reverse connection timeout
                LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
            }
        } catch (InterruptedException e) {
            LOGGER.log(LogLevelUtil.IGNORE, e, e);
            Thread.currentThread().interrupt();
        } finally {
            msgHeader.put(Header.SOURCE, pageSource);
            // Inform the view about the log infos
            var request = new Request();
            request.setMessage(Interaction.MESSAGE_HEADER);
            request.setParameters(msgHeader);
            this.injectionModel.sendToViews(request);
        }
        
        return pageSource.trim();
    }

    public void setCustomUserAgent(Builder httpRequest) {
        if (this.injectionModel.getMediatorUtils().getPreferencesUtil().isUserAgentRandom()) {
            String agents = this.injectionModel.getMediatorUtils().getUserAgentUtil().getCustomUserAgent();
            List<String> listAgents = Stream.of(agents.split("[\\r\\n]+"))
                .filter(q -> !q.matches("^#.*"))
                .collect(Collectors.toList());
            String randomElement = listAgents.get(this.randomForUserAgent.nextInt(listAgents.size()));
            httpRequest.setHeader("User-Agent", randomElement);
        }
    }
    
    
    // Builder

    public ConnectionUtil withMethodInjection(AbstractMethodInjection methodInjection) {
        this.methodInjection = methodInjection;
        return this;
    }
    
    public ConnectionUtil withTypeRequest(String typeRequest) {
        this.typeRequest = typeRequest;
        return this;
    }
    
    
    // Getters and setters
    
    public String getUrlByUser() {
        return this.urlByUser;
    }

    public void setUrlByUser(String urlByUser) {
        this.urlByUser = urlByUser;
    }
    
    public String getUrlBase() {
        return this.urlBase;
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }
    
    public AbstractMethodInjection getMethodInjection() {
        return this.methodInjection;
    }

    public void setMethodInjection(AbstractMethodInjection methodInjection) {
        this.methodInjection = methodInjection;
    }
    
    public String getTypeRequest() {
        return this.typeRequest;
    }

    public void setTypeRequest(String typeRequest) {
        this.typeRequest = typeRequest;
    }

    /**
     * Default timeout used by the jcifs fix. It's the default value used usually by the JVM.
     */
    public Integer getTimeout() {
        return this.injectionModel.getMediatorUtils().getPreferencesUtil().countConnectionTimeout();
    }

    public CookieManager getCookieManager() {
        return this.cookieManager;
    }
}