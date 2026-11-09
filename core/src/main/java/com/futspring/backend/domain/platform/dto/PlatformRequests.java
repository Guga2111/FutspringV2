package com.futspring.backend.domain.platform.dto;

import com.futspring.backend.domain.subscription.SubscriptionPlan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Bodies of the platform admin's commands. */
public final class PlatformRequests {

    private PlatformRequests() {
    }

    public record RecordPayment(
            @NotNull(message = "Escolha o plano") SubscriptionPlan plan,
            // the amount actually charged (a founder's locked price included), NUMERIC(10,2)
            @NotBlank(message = "Informe o valor")
            @Pattern(regexp = "[0-9]{1,8}\\.[0-9]{2}", message = "Informe o valor com duas casas, como 19.90")
            String amount,
            @NotNull(message = "Informe a data do pagamento") LocalDate paidOn,
            @Size(max = 500, message = "Máximo de 500 caracteres") String note) {}

    public enum SubscriptionAction { SET_VALID_UNTIL, EXTEND_DAYS, EXEMPT, CANCEL, SET_FOUNDER }

    // validUntil for SET_VALID_UNTIL, days for EXTEND_DAYS, founder for SET_FOUNDER; note required except SET_FOUNDER
    public record ChangeSubscription(
            @NotNull(message = "Escolha a ação") SubscriptionAction action,
            LocalDate validUntil,
            @Min(value = 1, message = "Mínimo de 1 dia") @Max(value = 366, message = "Máximo de 366 dias") Integer days,
            Boolean founder,
            @Size(max = 500, message = "Máximo de 500 caracteres") String note) {}

    public record VoidPayment(
            @NotBlank(message = "Informe o motivo") @Size(max = 500, message = "Máximo de 500 caracteres")
            String reason) {}
}
