package com.codereview.aicodereviewer.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Date;
import java.util.Map;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Service to authenticate as a GitHub App and generate installation tokens
 */
@Service
@Slf4j
public class GitHubAppAuthService {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
        log.info("🕐 FORCED TIMEZONE TO UTC");
    }

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private String privateKeyPath;

    private final RestTemplate restTemplate;

    public GitHubAppAuthService() {
        try {
            // 1. Create a socket factory with explicit TLS 1.2 and the modern Hostname Verifier
            final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(SSLContexts.createDefault())
                    .setTlsVersions(TLS.V_1_2)
                    .setHostnameVerifier(new DefaultHostnameVerifier()) // Use 'new DefaultHostnameVerifier()'
                    .build();

            // 2. Build the client with the forced strategy
            final CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslSocketFactory)
                            .build())
                    .disableAutomaticRetries() // Stop handshake retry loops
                    .build();

            // 3. Connect it to Spring
            this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
            log.info("✅ RestTemplate engine swapped to Apache HttpClient 5 (TLS 1.2 + SNI Fixed)");

        } catch (Exception e) {
            log.error("❌ Failed to initialize secure RestTemplate: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private PrivateKey privateKey;

    /**
     * Generate a JWT (JSON Web Token) for GitHub App authentication
     */
    public String generateJWT() {
        try {
            if (privateKey == null) {
                privateKey = loadPrivateKey();
            }

            Date now = new Date();
            Date expiration = new Date(now.getTime() + 600000); // 10 minutes

            // ===== EXTENSIVE DEBUG LOGGING =====
            log.info("==================== JWT DEBUG START ====================");
            log.info("🔑 App ID from config: {}", appId);
            log.info("🔑 Private Key Algorithm: {}", privateKey.getAlgorithm());
            log.info("🔑 Private Key Format: {}", privateKey.getFormat());
            log.info("🔑 Private Key Class: {}", privateKey.getClass().getName());
            log.info("⏰ Server Time (now): {}", now);
            log.info("⏰ Server Timezone: {}", TimeZone.getDefault().getID());
            log.info("⏰ JWT Expiration: {}", expiration);
            log.info("⏰ Time diff (ms): {}", (expiration.getTime() - now.getTime()));

            String jwt = Jwts.builder()
                    .setIssuedAt(now)
                    .setExpiration(expiration)
                    .setIssuer(appId)
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();

            log.info("✅ JWT Generated Successfully");
            log.info("📝 JWT Length: {}", jwt.length());
            log.info("📝 JWT First 50 chars: {}", jwt.substring(0, Math.min(50, jwt.length())));
            log.info("📝 JWT Last 50 chars: {}", jwt.substring(Math.max(0, jwt.length() - 50)));
            log.info("==================== JWT DEBUG END ====================");

            return jwt;

        } catch (Exception e) {
            log.error("❌❌❌ Error generating JWT: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    /**
     * Get an installation access token
     */
    public String getInstallationToken(long installationId) {
        try {
            String jwt = generateJWT();

            String url = String.format(
                    "https://api.github.com/app/installations/%d/access_tokens",
                    installationId
            );

            log.info("==================== API REQUEST DEBUG START ====================");
            log.info("🌐 Target URL: {}", url);
            log.info("🌐 Installation ID: {}", installationId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github+json");
            headers.set("Authorization", "Bearer " + jwt);
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            log.info("📤 Request Headers: {}", headers);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("🚀 Sending request to GitHub...");

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            log.info("✅ Response Status: {}", response.getStatusCode());
            log.info("==================== API REQUEST DEBUG END ====================");

            if (response.getBody() != null) {
                String token = (String) response.getBody().get("token");
                log.info("✅ Generated installation token for installation: {}", installationId);
                return token;
            }

            throw new RuntimeException("Failed to get installation token");

        } catch (Exception e) {
            log.error("❌❌❌ Installation Token Request Failed");
            log.error("Error Type: {}", e.getClass().getName());
            log.error("Error Message: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused By: {}", e.getCause().getClass().getName());
                log.error("Cause Message: {}", e.getCause().getMessage());
            }
            log.error("Full Stack Trace:", e);
            throw new RuntimeException("Failed to get installation token", e);
        }
    }

    /**
     * Load private key from PEM file - Handles ALL PEM formats
     */
    private PrivateKey loadPrivateKey() throws Exception {
        try {
            log.info("==================== PRIVATE KEY LOAD DEBUG START ====================");

            String pemContent;
            String source;

            // Priority 1: Check base64 environment variable (Cloud Run)
            String base64PrivateKey = System.getenv("GITHUB_APP_PRIVATE_KEY_BASE64");
            if (base64PrivateKey != null && !base64PrivateKey.isEmpty()) {
                log.info("✅ Loading private key from base64 environment variable");
                log.info("✅ Found GITHUB_APP_PRIVATE_KEY_BASE64 env var");
                log.info("📏 Base64 Length: {}", base64PrivateKey.length());
                log.info("📝 Base64 First 50 chars: {}", base64PrivateKey.substring(0, Math.min(50, base64PrivateKey.length())));
                log.info("📝 Base64 Last 50 chars: {}", base64PrivateKey.substring(Math.max(0, base64PrivateKey.length() - 50)));

                byte[] decoded = Base64.getDecoder().decode(base64PrivateKey);
                pemContent = new String(decoded, StandardCharsets.UTF_8);
                source = "base64 environment variable";

                log.info("✅ Base64 decoded successfully");
                log.info("📏 Decoded PEM Length: {}", pemContent.length());
                log.info("📝 PEM starts with: {}", pemContent.substring(0, Math.min(50, pemContent.length())));
            }
            // Priority 2: Check plain text environment variable (fallback)
            else {
                String envPrivateKey = System.getenv("GITHUB_APP_PRIVATE_KEY");
                if (envPrivateKey != null && !envPrivateKey.isEmpty()) {
                    log.info("✅ Loading private key from environment variable");
                    pemContent = envPrivateKey;
                    source = "environment variable";
                }
                // Priority 3: Local development (file path)
                else {
                    log.info("✅ Loading private key from file: {}", privateKeyPath);
                    pemContent = Files.readString(Paths.get(privateKeyPath));
                    source = "file: " + privateKeyPath;
                }
            }

            log.info("📄 PEM Content loaded from: {}", source);
            log.info("📄 PEM Content contains 'BEGIN': {}", pemContent.contains("BEGIN"));
            log.info("📄 PEM Content contains 'END': {}", pemContent.contains("END"));
            log.info("📄 PEM Content contains 'RSA': {}", pemContent.contains("RSA"));

            PEMParser pemParser = new PEMParser(new StringReader(pemContent));
            Object object = pemParser.readObject();

            if (object == null) {
                log.error("❌ PEM parser returned NULL");
                throw new RuntimeException("PEM parser returned null - invalid key format");
            }

            log.info("✅ PEM parsed successfully");
            log.info("📦 Parsed object type: {}", object.getClass().getName());

            pemParser.close();

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKey key;

            if (object instanceof PrivateKeyInfo) {
                // PKCS#8 format: -----BEGIN PRIVATE KEY-----
                key = converter.getPrivateKey((PrivateKeyInfo) object);
                log.info("✅ Loaded PKCS#8 private key");

            } else if (object instanceof PEMKeyPair) {
                // PKCS#1 format: -----BEGIN RSA PRIVATE KEY-----
                PEMKeyPair keyPair = (PEMKeyPair) object;
                PrivateKeyInfo privateKeyInfo = keyPair.getPrivateKeyInfo();
                key = converter.getPrivateKey(privateKeyInfo);
                log.info("✅ Loaded PKCS#1 (RSA) private key");

            } else if (object instanceof KeyPair) {
                // KeyPair object
                key = ((KeyPair) object).getPrivate();
                log.info("✅ Loaded private key from KeyPair");

            } else {
                throw new RuntimeException("Unsupported PEM format: " + object.getClass().getName());
            }

            log.info("✅ Successfully loaded GitHub App private key from: {}", source);
            return key;

        } catch (Exception e) {
            log.error("Failed to load private key: {}", e.getMessage());
            throw new RuntimeException("Could not load GitHub App private key", e);
        }
    }

    public String getAppId() {
        return appId;
    }
}