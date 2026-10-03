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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.authlete.hms.ComponentValueProvider;
import com.authlete.hms.SignatureBaseBuilder;
import com.authlete.hms.SignatureField;
import com.authlete.hms.SignatureInputField;
import com.authlete.hms.SignatureMetadata;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;

class WebBotAuthSignerTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    // SubjectPublicKeyInfo prefix for an Ed25519 key: the raw 32-byte public key follows it
    private static final byte[] SPKI_ED25519_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    /** Generates an Ed25519 JWK with the JDK (Nimbus's own generator needs Google Tink). */
    static OctetKeyPair newKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        byte[] pkcs8 = pair.getPrivate().getEncoded();
        byte[] x = Arrays.copyOfRange(spki, spki.length - 32, spki.length);
        byte[] d = Arrays.copyOfRange(pkcs8, pkcs8.length - 32, pkcs8.length);
        return new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(x)).d(Base64URL.encode(d)).build();
    }

    private static WebBotAuthSigner newSigner(OctetKeyPair key) {
        return WebBotAuthSigner.of(key, "https://www.37audits.com", Duration.ofMinutes(10), CLOCK);
    }

    @Test
    void keyId_isTheRfc7638Thumbprint_rfc8037Vector() throws Exception {
        var key = new OctetKeyPair.Builder(Curve.Ed25519, new Base64URL("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"))
                .build();

        assertThat(key.computeThumbprint().toString()).isEqualTo("kPrK_qmxVWaYVA9wwBF6Iuo3vVzz7TxHCTwXBygrS4k");
    }

    @Test
    void keyId_isTheRfc7638Thumbprint_cloudflareDocsVector() throws Exception {
        var key = new OctetKeyPair.Builder(Curve.Ed25519, new Base64URL("JrQLj5P_89iXES9-vFgrIy29clF9CC_oPPsw3c5D0bs"))
                .build();

        assertThat(key.computeThumbprint().toString()).isEqualTo("poqkLGiymh_W0uP6PZFw-dvez3QJT5SolqXBCW38r0U");
    }

    @Test
    void headers_carryWebBotAuthParameters() throws Exception {
        var key = newKey();
        var signer = newSigner(key);

        Map<String, String> headers = signer.headers(URI.create("https://Example.com/some/path?q=1"));

        assertThat(headers).containsOnlyKeys("Signature-Agent", "Signature-Input", "Signature");
        assertThat(headers.get("Signature-Agent")).isEqualTo("\"https://www.37audits.com\"");
        assertThat(headers.get("Signature-Input")).startsWith("sig1=(\"@authority\" \"signature-agent\");")
                .contains("created=1700000000").contains("expires=1700000600").contains("alg=\"ed25519\"")
                .contains("tag=\"web-bot-auth\"").contains("keyid=\"" + key.computeThumbprint() + "\"")
                .contains("nonce=\"");
        assertThat(headers.get("Signature")).matches("sig1=:[A-Za-z0-9+/]+=*:");
        assertThat(signer.keyId()).isEqualTo(key.computeThumbprint().toString());
        assertThat(signer.publicJwk().isPrivate()).isFalse();
    }

    @Test
    void headers_verifyAgainstThePublicKey() throws Exception {
        var key = newKey();
        var signer = newSigner(key);
        var target = URI.create("https://example.com/");

        Map<String, String> headers = signer.headers(target);

        // Rebuild the signature base the way a verifier would and check it with the JDK's Ed25519 verifier
        SignatureMetadata metadata = SignatureInputField.parse(headers.get("Signature-Input")).get("sig1");
        byte[] signature = SignatureField.parse(headers.get("Signature")).get("sig1");
        var context = new ComponentValueProvider().setTargetUri(target)
                .setHeaders(Map.of("signature-agent", List.of(headers.get("Signature-Agent"))));
        String base = new SignatureBaseBuilder(context).build(metadata).serialize();

        assertThat(base).startsWith("\"@authority\": example.com\n\"signature-agent\": \"https://www.37audits.com\"\n"
                + "\"@signature-params\": ");
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey(key));
        verifier.update(base.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void headers_includeNonDefaultPortInAuthority() throws Exception {
        var signer = newSigner(newKey());

        Map<String, String> headers = signer.headers("http://localhost:8080/page");

        SignatureMetadata metadata = SignatureInputField.parse(headers.get("Signature-Input")).get("sig1");
        var context = new ComponentValueProvider().setTargetUri(URI.create("http://localhost:8080/page"))
                .setHeaders(Map.of("signature-agent", List.of(headers.get("Signature-Agent"))));
        String base = new SignatureBaseBuilder(context).build(metadata).serialize();

        assertThat(base).startsWith("\"@authority\": localhost:8080\n");
    }

    @Test
    void headers_useAFreshNoncePerRequest() throws Exception {
        var signer = newSigner(newKey());

        var first = signer.headers("https://example.com/");
        var second = signer.headers("https://example.com/");

        assertThat(first.get("Signature-Input")).isNotEqualTo(second.get("Signature-Input"));
    }

    @Test
    void of_rejectsPublicOnlyKeys() throws Exception {
        var publicOnly = newKey().toPublicJWK();

        assertThatThrownBy(() -> WebBotAuthSigner.of(publicOnly, "https://www.37audits.com"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("private");
    }

    @Test
    void fromEnvironment_isEmptyWithoutAKey() {
        assertThat(WebBotAuthSigner.fromEnvironment(null, null)).isEmpty();
        assertThat(WebBotAuthSigner.fromEnvironment("  ", null)).isEmpty();
    }

    @Test
    void fromEnvironment_isEmptyForAMalformedKey() {
        assertThat(WebBotAuthSigner.fromEnvironment("not-a-jwk", null)).isEmpty();
        assertThat(WebBotAuthSigner.fromEnvironment("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"abc\"}", null))
                .isEmpty();
    }

    @Test
    void fromEnvironment_parsesTheJwkAndDefaultsTheSignatureAgent() throws Exception {
        var key = newKey();

        var signer = WebBotAuthSigner.fromEnvironment(key.toJSONString(), null);

        assertThat(signer).isPresent();
        assertThat(signer.get().keyId()).isEqualTo(key.computeThumbprint().toString());
        assertThat(signer.get().signatureAgent()).isEqualTo(WebBotAuthSigner.DEFAULT_SIGNATURE_AGENT);
        assertThat(WebBotAuthSigner.fromEnvironment(key.toJSONString(), " https://bot.example ").get().signatureAgent())
                .isEqualTo("https://bot.example");
    }

    private static PublicKey publicKey(OctetKeyPair key) throws Exception {
        byte[] x = key.getDecodedX();
        byte[] der = new byte[SPKI_ED25519_PREFIX.length + x.length];
        System.arraycopy(SPKI_ED25519_PREFIX, 0, der, 0, SPKI_ED25519_PREFIX.length);
        System.arraycopy(x, 0, der, SPKI_ED25519_PREFIX.length, x.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }
}
