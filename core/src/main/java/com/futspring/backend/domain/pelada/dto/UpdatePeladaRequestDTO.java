package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

// Partial update: null fields are left unchanged
@Builder
public record UpdatePeladaRequestDTO(
        @Size(min = 1, max = 100, message = "O nome deve ter entre 1 e 100 caracteres")
        String name,

        @Pattern(regexp = DayOfWeekPattern.REGEX, message = DayOfWeekPattern.MESSAGE)
        String dayOfWeek,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "Horário inválido (HH:mm)")
        String timeOfDay,

        @Positive(message = "A duração deve ser maior que zero")
        Float duration,

        @Size(max = 255, message = "Máximo de 255 caracteres")
        String address,

        @Size(max = 255, message = "Máximo de 255 caracteres")
        String reference,

        Boolean autoCreateDailyEnabled,

        // IANA zone (America/Sao_Paulo); unknown → 400
        @Size(min = 1, max = 64, message = "O fuso horário deve ter entre 1 e 64 caracteres")
        String timeZone,

        // The new capacity applies to the sessions already scheduled too: nobody is removed, a session with more
        // confirmed players than spots is just over the limit
        @Min(value = 2, message = "Mínimo de 2 times")
        @Max(value = 10, message = "Máximo de 10 times")
        Integer numberOfTeams,

        @Min(value = 2, message = "Mínimo de 2 jogadores por time")
        @Max(value = 20, message = "Máximo de 20 jogadores por time")
        Integer playersPerTeam,

        // between the current number of mensalistas and the session capacity, else 409
        @Min(value = 0, message = "O limite de mensalistas não pode ser negativo")
        Integer maxMensalistas) {
}
