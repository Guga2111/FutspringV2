package com.futspring.backend.domain.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The Pix key the platform is paid with (BILLING_PIX_*). Without BILLING_PIX_KEY there is no Pix (the creator's
 * screen says to contact support). A key, name or city the BR Code can't carry fails the startup instead of every
 * payment: the key is a phone (digits get +55), an e-mail or a random key; the name inside the code (≤ 25) and the
 * city (≤ 15) lose their accents and go upper case, never truncated.
 */
@Slf4j
@Component
public class BillingPixConfig {

    static final int MERCHANT_NAME_MAX = 25;
    static final int CITY_MAX = 15;

    private static final Pattern EVP =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern PHONE = Pattern.compile("^\\+55\\d{10,11}$");

    private final String key;
    private final String recipient;
    private final String merchantName;
    private final String city;

    public BillingPixConfig(@Value("${app.billing.pix-key:}") String key,
                            @Value("${app.billing.pix-name:}") String name,
                            @Value("${app.billing.pix-merchant-name:}") String merchantName,
                            @Value("${app.billing.pix-city:}") String city) {
        if (key.isBlank()) {
            log.info("BILLING_PIX_KEY is empty: the subscription screen shows no Pix");
            this.key = null;
            this.recipient = null;
            this.merchantName = null;
            this.city = null;
            return;
        }
        if (name.isBlank() || city.isBlank()) {
            throw new IllegalStateException("BILLING_PIX_KEY needs BILLING_PIX_NAME and BILLING_PIX_CITY");
        }
        this.key = normalizeKey(key.strip());
        this.recipient = name.strip();
        this.merchantName = emvText(merchantName.isBlank() ? name : merchantName, MERCHANT_NAME_MAX,
                "BILLING_PIX_MERCHANT_NAME (or BILLING_PIX_NAME)");
        this.city = emvText(city, CITY_MAX, "BILLING_PIX_CITY");
    }

    public boolean configured() {
        return key != null;
    }

    /** The full name shown on screen (the code carries the short one). */
    public Optional<String> recipient() {
        return Optional.ofNullable(recipient);
    }

    /** The BR Code of one payment of {@code amount} for a pelada (txid FUT{peladaId}, so the payment can be traced). */
    public Optional<String> code(BigDecimal amount, Long peladaId) {
        if (!configured()) return Optional.empty();
        return Optional.of(PixBrCode.payload(key, merchantName, city, amount, "FUT" + peladaId));
    }

    static String normalizeKey(String key) {
        if (key.contains("@")) return key.toLowerCase(Locale.ROOT);
        String lower = key.toLowerCase(Locale.ROOT);
        if (EVP.matcher(lower).matches()) return lower;
        String phone = key.replaceAll("[\\s().-]", "");
        if (phone.matches("\\d{10,11}")) {
            phone = "+55" + phone;
        } else if (phone.matches("55\\d{10,11}")) {
            phone = "+" + phone;
        }
        if (!PHONE.matcher(phone).matches()) {
            throw new IllegalStateException(
                    "BILLING_PIX_KEY must be a phone (DDD + number), an e-mail or a random key");
        }
        return phone;
    }

    // Without accents, upper case and within the BR Code's limit
    static String emvText(String value, int max, String variable) {
        String text = Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        if (text.length() > max) {
            throw new IllegalStateException(variable + " must have at most " + max + " characters: " + text);
        }
        return text;
    }
}
