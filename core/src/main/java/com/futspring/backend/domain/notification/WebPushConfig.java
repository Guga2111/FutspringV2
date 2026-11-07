package com.futspring.backend.domain.notification;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * The VAPID key pair (P-256, raw base64url as printed by `npx web-push generate-vapid-keys`: a 65-byte uncompressed
 * public point and the 32-byte private scalar). Without VAPID_PRIVATE_KEY push is off (NoopWebPushSender, and
 * GET /push/vapid-public-key answers 204). A half-configured or mismatched pair fails the startup instead of failing
 * every send. Changing the pair invalidates every subscription (the devices subscribe again when the app opens).
 */
@Slf4j
@Configuration
public class WebPushConfig {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushConfig(@Value("${app.push.vapid-public-key:}") String publicKey,
                         @Value("${app.push.vapid-private-key:}") String privateKey,
                         @Value("${app.push.vapid-subject:mailto:contato@futspring.luisgosampaio.com}") String subject) {
        this.publicKey = publicKey.strip();
        this.privateKey = privateKey.strip();
        this.subject = subject.strip();
    }

    public boolean configured() {
        return !privateKey.isEmpty();
    }

    public Optional<String> publicKey() {
        return configured() ? Optional.of(publicKey) : Optional.empty();
    }

    @Bean
    public WebPushSender webPushSender() {
        if (!configured()) {
            log.info("VAPID_PRIVATE_KEY is empty: Web Push is off");
            return new NoopWebPushSender();
        }
        return new VapidWebPushSender(VapidWebPushSender.httpClient(), keyPair(publicKey, privateKey), subject);
    }

    static VAPIDKeyPair keyPair(String publicKeyBase64Url, String privateKeyBase64Url) {
        try {
            byte[] publicBytes = Base64.getUrlDecoder().decode(publicKeyBase64Url);
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECPrivateKey privateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(
                    new BigInteger(1, Base64.getUrlDecoder().decode(privateKeyBase64Url)),
                    parameters.getParameterSpec(ECParameterSpec.class)));
            VAPIDKeyPair pair = VAPIDKeyPairs.of(PrivateKeySources.ofECPrivateKey(privateKey),
                    PublicKeySources.ofUncompressedBytes(publicBytes));
            requireMatching(privateKey, PublicKeySources.ofUncompressedBytes(publicBytes).extract());
            return pair;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Invalid VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY", e);
        }
    }

    // A signature made with the private key must verify with the public one
    private static void requireMatching(ECPrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
        byte[] probe = "futspring-vapid".getBytes(StandardCharsets.US_ASCII);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(probe);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(probe);
        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException("VAPID_PUBLIC_KEY doesn't match VAPID_PRIVATE_KEY");
        }
    }
}
