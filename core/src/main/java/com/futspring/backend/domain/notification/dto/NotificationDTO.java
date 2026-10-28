package com.futspring.backend.domain.notification.dto;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationAward;
import com.futspring.backend.domain.notification.NotificationCategory;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

// The references a notification's text is rendered from (by the client); public fields only
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {

    private Long id;
    private NotificationType type;
    private NotificationCategory category;
    private NotificationAward award;
    private Instant createdAt;
    private Instant readAt;
    private PeladaRef pelada;
    private DailyRef daily;
    private PublicUserDTO actor;
    // only on TEAMS_SORTED: the recipient's team in that session
    private TeamRef team;

    public static NotificationDTO from(Notification n, TeamRef team) {
        Pelada pelada = n.getPelada();
        Daily daily = n.getDaily();
        return NotificationDTO.builder()
                .id(n.getId())
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeladaRef {
        private Long id;
        private String name;
        private String image;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRef {
        private Long id;
        private LocalDate date;
        private String time;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamRef {
        private String name;
        private String color;
    }
}
