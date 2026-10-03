/*
 * Copyright © 2026 37 Audits (thiago.moreira@37audits.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thirtysevenaudits.crawler;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.authlete.hms.ComponentIdentifier;
import com.authlete.hms.ComponentValueProvider;
import com.authlete.hms.SignatureBaseBuilder;
import com.authlete.hms.SignatureField;
import com.authlete.hms.SignatureInputField;
import com.authlete.hms.SignatureMetadata;
import com.authlete.hms.SignatureMetadataParameters;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;

/**
 * Signs outbound requests with Web Bot Auth (RFC 9421 HTTP Message Signatures
 * over Ed25519) so bot-management services such as Cloudflare can verify the
 * crawler against the key directory published at
 * {@code <signatureAgent>/.well-known/http-message-signatures-directory}.
 *
 * <p>
 * Only {@code @authority} and {@code signature-agent} are covered, so one
 * signature is valid for any method and path on that host until it expires.
 * </p>
 */
public final class WebBotAuthSigner {

    public static final String ENV_JWK = "WEB_BOT_AUTH_JWK";
    public static final String ENV_SIGNATURE_AGENT = "WEB_BOT_AUTH_SIGNATURE_AGENT";
    public static final String DEFAULT_SIGNATURE_AGENT = "https://www.37audits.com";
    public static final Duration DEFAULT_VALIDITY = Duration.ofMinutes(10);

    static final String LABEL = "sig1";
    static final String TAG = "web-bot-auth";
    static final String ALG = "ed25519";

    private static final Logger LOGGER = LoggerFactory.getLogger(WebBotAuthSigner.class);
    // PKCS#8 PrivateKeyInfo prefix for an Ed25519 key: the raw 32-byte seed follows it
    private static final byte[] PKCS8_ED25519_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class Default {
        private static final Optional<WebBotAuthSigner> SIGNER = fromEnvironment();
    }

    private final OctetKeyPair key;
    private final String keyId;
    private final PrivateKey privateKey;
    private final String signatureAgent;
    private final Duration validity;
    private final Clock clock;

    private WebBotAuthSigner(OctetKeyPair key, String signatureAgent, Duration validity, Clock clock) {
        this.key = key;
        this.keyId = thumbprint(key);
        this.privateKey = toPrivateKey(key);
        this.signatureAgent = signatureAgent;
        this.validity = validity;
        this.clock = clock;
    }

    public static WebBotAuthSigner of(OctetKeyPair key, String signatureAgent) {
        return of(key, signatureAgent, DEFAULT_VALIDITY, Clock.systemUTC());
    }

    public static WebBotAuthSigner of(OctetKeyPair key, String signatureAgent, Duration validity, Clock clock) {
        if (!Curve.Ed25519.equals(key.getCurve())) {
            throw new IllegalArgumentException("Web Bot Auth requires an Ed25519 key, got " + key.getCurve());
        }
        if (!key.isPrivate()) {
            throw new IllegalArgumentException("Web Bot Auth requires the private JWK (missing 'd')");
        }
        return new WebBotAuthSigner(key, signatureAgent, validity, clock);
    }

    /**
     * The signer configured through {@value #ENV_JWK} (a private OKP JWK) and
     * optionally {@value #ENV_SIGNATURE_AGENT}; empty when the key is not set
     * or cannot be parsed, so unconfigured environments keep sending
     * unsigned requests.
     */
    public static Optional<WebBotAuthSigner> defaultSigner() {
        return Default.SIGNER;
    }

    /**
     * Headers from {@link #defaultSigner()} for a request to {@code url}, or an
     * empty map when no key is configured — for code that builds requests
     * outside an auditor (visitors, static helpers).
     */
    public static Map<String, String> defaultHeaders(String url) {
        return defaultSigner().map(signer -> signer.headers(url)).orElse(Map.of());
    }

    static Optional<WebBotAuthSigner> fromEnvironment() {
        return fromEnvironment(System.getenv(ENV_JWK), System.getenv(ENV_SIGNATURE_AGENT));
    }

    static Optional<WebBotAuthSigner> fromEnvironment(String jwk, String signatureAgent) {
        if (jwk == null || jwk.isBlank()) {
            return Optional.empty();
        }
        try {
            String agent = (signatureAgent == null || signatureAgent.isBlank()) ? DEFAULT_SIGNATURE_AGENT
                    : signatureAgent.trim();
            return Optional.of(of(OctetKeyPair.parse(jwk.trim()), agent));
        } catch (Exception e) {
            LOGGER.warn("Ignoring {}: {}", ENV_JWK, e.getMessage());
            return Optional.empty();
        }
    }

    public String keyId() {
        return keyId;
    }

    public String signatureAgent() {
        return signatureAgent;
    }

    /** The public half, in the shape the key directory serves. */
    public JWK publicJwk() {
        return key.toPublicJWK();
    }

    /**
     * The {@code Signature-Agent}, {@code Signature-Input} and {@code Signature}
     * headers for a request to {@code target}.
     */
    public Map<String, String> headers(URI target) {
        String agent = "\"" + signatureAgent + "\"";
        Instant created = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        var context = new ComponentValueProvider().setTargetUri(target).setHeaders(
                Map.of("signature-agent", List.of(agent)));
        var parameters = new SignatureMetadataParameters().setCreated(created).setExpires(created.plus(validity))
                .setKeyid(keyId).setAlg(ALG).setNonce(nonce()).setTag(TAG);
        var metadata = new SignatureMetadata(
                List.of(new ComponentIdentifier("@authority"), new ComponentIdentifier("signature-agent")),
                parameters);

        try {
            byte[] signature = new SignatureBaseBuilder(context).build(metadata).sign(this::sign);

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Signature-Agent", agent);
            headers.put("Signature-Input", new SignatureInputField(Map.of(LABEL, metadata)).serialize());
            headers.put("Signature", new SignatureField(Map.of(LABEL, signature)).serialize());
            return headers;
        } catch (SignatureException e) {
            throw new IllegalStateException("Unable to sign request to " + target, e);
        }
    }

    public Map<String, String> headers(String url) {
        return headers(URI.create(url));
    }

    // HttpSigner backed by the JDK's Ed25519 provider (the library's JOSE signer would need Google Tink)
    private byte[] sign(byte[] signatureBase) throws SignatureException {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(signatureBase);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new SignatureException(e);
        }
    }

    /** RFC 7638 JWK thumbprint, the {@code keyid} verifiers look up in the key directory. */
    static String thumbprint(JWK key) {
        try {
            return key.computeThumbprint().toString();
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Unable to compute the JWK thumbprint", e);
        }
    }

    private static PrivateKey toPrivateKey(OctetKeyPair key) {
        byte[] seed = key.getDecodedD();
        byte[] der = new byte[PKCS8_ED25519_PREFIX.length + seed.length];
        System.arraycopy(PKCS8_ED25519_PREFIX, 0, der, 0, PKCS8_ED25519_PREFIX.length);
        System.arraycopy(seed, 0, der, PKCS8_ED25519_PREFIX.length, seed.length);
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid Ed25519 private key", e);
        }
    }

    private static String nonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
