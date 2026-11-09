package com.futspring.backend.domain.subscription;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A static Pix BR Code (EMV QR Code payload, Manual de Padrões para Iniciação do Pix, Bacen): what the QR Code holds
 * and what "Pix Copia e Cola" pastes. Pure encoding: the values are used as given (BillingPixConfig normalizes them).
 */
public final class PixBrCode {

    private static final String GUI = "br.gov.bcb.pix";

    private PixBrCode() {
    }

    /**
     * @param amount null leaves the amount for the payer to type
     * @param txid   up to 25 letters and digits; "***" when there is none
     */
    public static String payload(String key, String merchantName, String city, BigDecimal amount, String txid) {
        StringBuilder payload = new StringBuilder()
                .append(field("00", "01"))
                .append(field("26", field("00", GUI) + field("01", key)))
                .append(field("52", "0000"))
                .append(field("53", "986"));
        if (amount != null) {
            payload.append(field("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
        payload.append(field("58", "BR"))
                .append(field("59", merchantName))
                .append(field("60", city))
                .append(field("62", field("05", txid)))
                .append("6304");
        return payload.append(crc16(payload.toString())).toString();
    }

    /** CRC-16/CCITT-FALSE (polynomial 0x1021, initial 0xFFFF), as four upper-case hex digits. */
    public static String crc16(String value) {
        int crc = 0xFFFF;
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
        }
        return String.format("%04X", crc & 0xFFFF);
    }

    // id + two-digit length + value
    private static String field(String id, String value) {
        if (value.length() > 99) {
            throw new IllegalArgumentException("BR Code field " + id + " longer than 99 characters");
        }
        return id + String.format("%02d", value.length()) + value;
    }
}
