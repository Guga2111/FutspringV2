package com.futspring.backend.domain.notification;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

// Real P-256 keys in the raw base64url form browsers and `web-push generate-vapid-keys` use
final class PushTestKeys {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private PushTestKeys() {
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String publicKey(KeyPair pair) {
        ECPublicKey key = (ECPublicKey) pair.getPublic();
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
        return B64.encodeToString(out);
    }

    static String privateKey(KeyPair pair) {
        return B64.encodeToString(fixed32(((ECPrivateKey) pair.getPrivate()).getS()));
    }

    // A browser's p256dh key
    static String p256dh() {
        return publicKey(generate());
    }

    // A browser's 16-byte auth secret
    static String auth() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        return B64.encodeToString(secret);
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(32, bytes.length);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }
}
