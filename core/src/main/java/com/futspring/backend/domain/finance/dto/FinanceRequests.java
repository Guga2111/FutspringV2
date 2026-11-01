package com.futspring.backend.domain.finance.dto;

import com.futspring.backend.domain.finance.FinanceTypes.*;
import jakarta.validation.Valid;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceRequests {
    private FinanceRequests() {}
    public record Command(@NotNull @Min(0) Long revision, @NotNull UUID requestKey,
                          @Size(max = 1000) String reason) {}
    public record Component(@NotBlank @Size(max = 100) String name,
                            @NotNull @Pattern(regexp = "[0-9]{1,12}\\.[0-9]{2}") @JsonDeserialize(using = DecimalStringDeserializer.class) String amount) {}
    public record Settings(@NotNull @Valid Command command, @NotNull Visibility visibility,
                           @Size(max = 200) String pixKey, @Size(max = 200) String pixRecipient, @Positive Long qrId) {}
    public record Managers(@NotNull @Valid Command command,
                           @NotNull @Size(max = 100) List<@NotNull @Positive Long> userIds) {}
    public record Template(@NotNull @Valid Command command, @NotBlank @Size(max = 200) String name,
                           @NotNull Classification classification, @Size(max = 2000) String description,
                           @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Component> components,
                           @Size(max = 200) String pixKey, @Size(max = 200) String pixRecipient,
                           @Positive Long qrId, @NotNull Boolean archived) {}
    public record Collection(@NotNull @Valid Command command, @NotBlank @Size(max = 200) String name,
                             @NotNull Classification classification, @Size(max = 2000) String description,
                             LocalDate dueDate, @Positive Long dailyId,
                             @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Component> components,
                             @Size(max = 200) String pixKey, @Size(max = 200) String pixRecipient,
                             @Positive Long qrId) {}
    public record Charge(@NotNull @Valid Command command, @Positive Long userId, @Positive Long personId,
                         @Size(max = 200) String guestName,
                         @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Component> components,
                         @NotNull @Pattern(regexp = "-?[0-9]{1,12}\\.[0-9]{2}") @JsonDeserialize(using = DecimalStringDeserializer.class) String adjustment,
                         LocalDate dueDate) {}
    public record Participants(@NotNull @Valid Command command,
                               @NotNull @Size(max = 100) List<@NotNull @Positive Long> userIds,
                               @NotNull @Size(max = 100) List<@NotBlank @Size(max = 200) String> guestNames) {}
    public record Receipt(@NotNull @Valid Command command,
                          @Pattern(regexp = "[0-9]{1,12}\\.[0-9]{2}") @JsonDeserialize(using = DecimalStringDeserializer.class) String amount,
                          LocalDate effectiveDate, @NotNull PaymentMethod method) {}
    public record Confirmation(@NotNull @Valid Command command, LocalDate effectiveDate,
                               @NotNull PaymentMethod method) {}
    public record Refund(@NotNull @Valid Command command,
                         @NotNull @Pattern(regexp = "[0-9]{1,12}\\.[0-9]{2}") @JsonDeserialize(using = DecimalStringDeserializer.class) String amount,
                         LocalDate effectiveDate, @NotNull PaymentMethod method,
                         @NotNull Boolean reduceObligation) {}
    public record Cash(@NotNull @Valid Command command, @NotNull MovementKind kind,
                       @NotNull @Pattern(regexp = "-?[0-9]{1,12}\\.[0-9]{2}") @JsonDeserialize(using = DecimalStringDeserializer.class) String amount,
                       @NotBlank @Size(max = 500) String description, @Size(max = 100) String category,
                       LocalDate effectiveDate, @NotNull PaymentMethod method,
                       @Positive Long listId, @Positive Long dailyId) {}
}
