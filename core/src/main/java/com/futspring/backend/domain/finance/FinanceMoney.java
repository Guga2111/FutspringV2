package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class FinanceMoney {
    static final BigDecimal ZERO = new BigDecimal("0.00");
    static final BigDecimal MAX = new BigDecimal("999999999999.99");
    private FinanceMoney() {}
    static BigDecimal parse(String value) {
        if (value == null || !value.matches("-?[0-9]{1,12}\\.[0-9]{2}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Informe um valor decimal com duas casas");
        }
        return check(new BigDecimal(value));
    }
    static BigDecimal check(BigDecimal amount) {
        if (amount.abs().compareTo(MAX) > 0) throw new AppException(HttpStatus.BAD_REQUEST, "Valor acima do limite financeiro");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    static BigDecimal positive(String value) {
        BigDecimal amount = parse(value);
        if (amount.signum() <= 0) throw new AppException(HttpStatus.BAD_REQUEST, "O valor deve ser maior que zero");
        return amount;
    }
    static String text(BigDecimal amount) { return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
}
