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

/**
 * Service to authenticate as a GitHub App and generate installation tokens
 */
@Service
@Slf4j
public class GitHubAppAuthService {

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private String privateKeyPath;

    private final RestTemplate restTemplate = new RestTemplate();
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

            return Jwts.builder()
                    .setIssuedAt(now)
                    .setExpiration(expiration)
                    .setIssuer(appId)
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();

        } catch (Exception e) {
            log.error("Error generating JWT: {}", e.getMessage(), e);
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

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github+json");
            headers.set("Authorization", "Bearer " + jwt);
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getBody() != null) {
                String token = (String) response.getBody().get("token");
                log.info("✅ Generated installation token for installation: {}", installationId);
                return token;
            }

            throw new RuntimeException("Failed to get installation token");

        } catch (Exception e) {
            log.error("Error getting installation token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get installation token", e);
        }
    }

    /**
     * Load private key from PEM file - Handles ALL PEM formats
     */
    private PrivateKey loadPrivateKey() throws Exception {
        try {
            String pemContent;
            String source;

            // Priority 1: Check base64 environment variable (Cloud Run)
            String base64PrivateKey = System.getenv("GITHUB_APP_PRIVATE_KEY_BASE64");
            if (base64PrivateKey != null && !base64PrivateKey.isEmpty()) {
                log.info("✅ Loading private key from base64 environment variable");
                byte[] decoded = Base64.getDecoder().decode(base64PrivateKey);
                pemContent = new String(decoded, StandardCharsets.UTF_8);
                source = "base64 environment variable";
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

            PEMParser pemParser = new PEMParser(new StringReader(pemContent));
            Object object = pemParser.readObject();

            if (object == null) {
                throw new RuntimeException("PEM parser returned null - invalid key format");
            }

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