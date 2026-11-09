package com.futspring.backend.domain.subscription;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixBrCodeTest {

    @Test
    void reproducesTheExampleOfTheBacenManual() {
        // Manual de Padrões para Iniciação do Pix, static QR Code with a random key and no amount
        assertThat(PixBrCode.payload("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "BRASILIA", null, "***"))
                .isEqualTo("00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR"
                        + "5913Fulano de Tal6008BRASILIA62070503***63041D3D");
    }

    @Test
    void crc16_isCcittFalse() {
        assertThat(PixBrCode.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void amountAndTxid_goInTheirFields_andTheCrcClosesThePayload() {
        String code = PixBrCode.payload("+5511999990000", "FUTSPRING", "SAO PAULO", new BigDecimal("19.9"), "FUT7");

        assertThat(code).startsWith("000201").contains("0114+5511999990000").contains("540519.90")
                .contains("5909FUTSPRING").contains("6009SAO PAULO").contains("62080504FUT7");
        String body = code.substring(0, code.length() - 4);
        assertThat(body).endsWith("6304");
        assertThat(code.substring(code.length() - 4)).isEqualTo(PixBrCode.crc16(body));
    }

    @Test
    void aFieldLongerThan99Characters_fails() {
        assertThatThrownBy(() -> PixBrCode.payload("k", "N".repeat(100), "C", null, "***"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
