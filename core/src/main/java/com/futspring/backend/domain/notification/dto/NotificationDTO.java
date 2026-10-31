package com.futspring.backend.domain.notification.dto;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationAward;
import com.futspring.backend.domain.notification.NotificationCategory;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

// The references a notification's text is rendered from (by the client); public fields only
@Builder
public record NotificationDTO(
        Long id,
        Long invitationId,
        NotificationType type,
        NotificationCategory category,
        NotificationAward award,
        Instant createdAt,
        Instant readAt,
        PeladaRef pelada,
        DailyRef daily,
        PublicUserDTO actor,
        // only on TEAMS_SORTED: the recipient's team in that session
        TeamRef team) {

    public static NotificationDTO from(Notification n, TeamRef team) {
        Pelada pelada = n.getPelada();
        Daily daily = n.getDaily();
        return NotificationDTO.builder()
                .id(n.getId())
                .invitationId(n.getInvitationId())
                .type(n.getType())
                .category(n.getType().category())
                .award(n.getAward())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .pelada(pelada == null ? null : new PeladaRef(pelada.getId(), pelada.getName(), pelada.getImage()))
                .daily(daily == null ? null : new DailyRef(daily.getId(), daily.getDailyDate(), daily.getDailyTime()))
                .actor(n.getActor() == null ? null : PublicUserDTO.from(n.getActor()))
                .team(team)
                .build();
    }

    public record PeladaRef(
            Long id,
            String name,
            String image) {
    }

    public record DailyRef(
            Long id,
            LocalDate date,
            String time) {
    }

    public record TeamRef(
            String name,
            String color) {
    }
}
