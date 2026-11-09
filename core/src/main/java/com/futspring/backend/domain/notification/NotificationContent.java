package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.AttendanceWindow;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.subscription.SubscriptionService;
import com.futspring.backend.domain.subscription.SubscriptionState;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * The text of a notification, plain, for the e-mail (NotificationEmail escapes it) and the Web Push (WebPushDispatcher:
 * title and body). The same sentences the client shows in
 * the bell (utils/notifications.ts). The switch has no default, so a new NotificationType doesn't compile until it
 * gets its text here. The notification must have its pelada, daily and actor loaded.
 *
 * @param ctaPath path in the web app the button opens ("/daily/12")
 */
public record NotificationContent(String subject, String title, String body, String ctaLabel, String ctaPath) {

    private static final String SEE_SESSION = "Ver a sessão";
    private static final String SEE_PELADA = "Ver a pelada";
    static final int PUSH_BODY_MAX = 180;
    private static final DateTimeFormatter BILLING_DAY = DateTimeFormatter.ofPattern("dd/MM")
            .withZone(SubscriptionService.BILLING_ZONE);

    // The body of a system notification: push services and lock screens show little, so it is cut at 180 characters
    public String pushBody() {
        if (body.length() <= PUSH_BODY_MAX) {
            return body;
        }
        int end = PUSH_BODY_MAX - 1;
        if (Character.isHighSurrogate(body.charAt(end - 1))) {
            end--; // never split an emoji
        }
        return body.substring(0, end).stripTrailing() + "…";
    }

    public static NotificationContent of(Notification n) {
        String pelada = n.getPelada() != null ? n.getPelada().getName() : "pelada";
        String actor = n.getActor() != null ? n.getActor().getUsername() : "Um admin";
        Daily daily = n.getDaily();
        String date = daily != null ? AttendanceWindow.formatDate(daily) : "";
        String dateTime = daily != null ? AttendanceWindow.formatStart(daily) : "";
        String session = daily != null ? "/daily/" + daily.getId() : peladaPath(n);
        String peladaPath = peladaPath(n);

        return switch (n.getType()) {
            case DIARISTA_BUMPED -> new NotificationContent(
                    "Você perdeu a vaga na " + pelada,
                    "Você perdeu a vaga",
                    actor + " é mensalista e confirmou com a sessão lotada, então sua vaga na " + pelada + " ("
                            + dateTime + ") ficou com ele. Se abrir outra vaga, é só confirmar de novo.",
                    SEE_SESSION, session);
            case SESSION_CANCELED -> new NotificationContent(
                    "Sessão cancelada: " + date,
                    "Sessão cancelada",
                    "A sessão de " + dateTime + " da " + pelada + " foi cancelada.",
                    SEE_SESSION, session);
            case ATTENDANCE_REMOVED_BY_ADMIN -> new NotificationContent(
                    "Sua presença foi removida: " + date,
                    "Sua presença foi removida",
                    actor + " removeu sua presença na sessão de " + dateTime + " da " + pelada + ".",
                    SEE_SESSION, session);
            case ATTENDANCE_CONFIRMED_BY_ADMIN -> new NotificationContent(
                    "Sua presença foi confirmada: " + date,
                    "Sua presença foi confirmada",
                    actor + " confirmou sua presença na sessão de " + dateTime + " da " + pelada + ".",
                    SEE_SESSION, session);
            case SESSION_REMINDER -> new NotificationContent(
                    "Confirme sua presença: " + date,
                    "Você ainda não confirmou",
                    "A sessão de " + dateTime + " da " + pelada + " está chegando e você ainda não confirmou.",
                    "Confirmar presença", session);
            case SESSION_CREATED -> new NotificationContent(
                    "Nova sessão na " + pelada + ": " + date,
                    "Nova sessão",
                    "Nova sessão na " + pelada + ": " + dateTime + ".",
                    SEE_SESSION, session);
            case DIARISTA_WINDOW_OPEN -> new NotificationContent(
                    "Confirmações abertas: " + date,
                    "As confirmações abriram",
                    "Já dá para confirmar presença na sessão de " + dateTime + " da " + pelada + ".",
                    "Confirmar presença", session);
            case TEAMS_SORTED -> new NotificationContent(
                    "Times definidos: " + date,
                    "Times definidos",
                    "Os times da sessão de " + dateTime + " da " + pelada + " foram definidos.",
                    "Ver os times", session);
            case SESSION_FINISHED -> new NotificationContent(
                    "Sessão finalizada: " + date,
                    "Sessão finalizada",
                    "A sessão de " + date + " da " + pelada + " foi finalizada. Veja a tabela, os jogos e os prêmios.",
                    "Ver o resultado", session);
            case PELADA_INVITATION -> new NotificationContent(
                    "Convite para a " + pelada, "Você recebeu um convite",
                    actor + " convidou você para participar da " + pelada + ". O convite vale por 7 dias.",
                    "Ver convite", "/invitations/" + n.getInvitationId());
            case MEMBER_JOINED -> new NotificationContent(
                    actor + " entrou na " + pelada, "Novo participante",
                    actor + " aceitou participar da " + pelada + ".", SEE_PELADA, peladaPath);
            case ADDED_TO_PELADA -> new NotificationContent(
                    "Você entrou na " + pelada,
                    "Bem-vindo à " + pelada,
                    n.getActor() != null ? actor + " adicionou você à " + pelada + "." : "Você entrou na " + pelada + ".",
                    SEE_PELADA, peladaPath);
            case REMOVED_FROM_PELADA -> new NotificationContent(
                    "Você foi removido da " + pelada,
                    "Você saiu da pelada",
                    "Você foi removido da " + pelada + ". Suas estatísticas das sessões jogadas continuam no seu perfil.",
                    "Abrir o Futspring", "/home");
            case ADMIN_GRANTED -> new NotificationContent(
                    "Você agora é admin da " + pelada,
                    "Você agora é admin",
                    actor + " fez de você admin da " + pelada + ": você pode criar sessões, confirmar jogadores e sortear os times.",
                    SEE_PELADA, peladaPath);
            case ADMIN_REVOKED -> new NotificationContent(
                    "Você não é mais admin da " + pelada,
                    "Você não é mais admin",
                    "Você não é mais admin da " + pelada + ".",
                    SEE_PELADA, peladaPath);
            case MENSALISTA_GRANTED -> new NotificationContent(
                    "Você agora é mensalista da " + pelada,
                    "Você agora é mensalista",
                    "Você agora é mensalista da " + pelada + ": confirme sua presença a qualquer momento, sem esperar a "
                            + "abertura das confirmações.",
                    SEE_PELADA, peladaPath);
            case MENSALISTA_REVOKED -> new NotificationContent(
                    "Você agora é diarista da " + pelada,
                    "Você agora é diarista",
                    "Você agora é diarista da " + pelada + ": a confirmação abre 24 horas antes de cada sessão.",
                    SEE_PELADA, peladaPath);
            case PELADA_TRANSFERRED -> new NotificationContent(
                    actor + " transferiu a " + pelada + " para você",
                    "A pelada agora é sua",
                    actor + " transferiu a " + pelada + " para você. Agora você é o criador da pelada.",
                    SEE_PELADA, peladaPath);
            case AWARD_WON -> award(n, pelada, date, session);
            case SUBSCRIPTION_ENDING -> new NotificationContent(
                    "A assinatura da " + pelada + " vence em " + billingDay(n.getValidUntil()),
                    "Assinatura vencendo",
                    "O plano da " + pelada + " vence em " + billingDay(n.getValidUntil())
                            + ". Assine para a pelada não travar.",
                    "Ver assinatura", subscriptionPath(n));
            case SUBSCRIPTION_PAST_DUE -> new NotificationContent(
                    "Pagamento pendente: " + pelada,
                    "Pagamento pendente",
                    "O plano da " + pelada + " venceu em " + billingDay(n.getValidUntil()) + ". Renove até "
                            + billingDay(lockDate(n.getValidUntil())) + " para a pelada não travar.",
                    "Renovar", subscriptionPath(n));
            case SUBSCRIPTION_PIX_CLAIMED -> new NotificationContent(
                    "Pix informado: " + pelada,
                    "Pix informado",
                    (n.getActor() != null ? n.getActor().getUsername() : "O criador") + " informou o Pix da assinatura da "
                            + pelada + ". Confira e registre o pagamento.",
                    "Abrir no painel", n.getPelada() != null ? "/admin/peladas/" + n.getPelada().getId() : "/admin");
        };
    }

    private static NotificationContent award(Notification n, String pelada, String date, String session) {
        String label = n.getAward() != null ? n.getAward().label() : "prêmio";
        String sentence = n.getAward() == NotificationAward.WILTBALL
                ? "Você levou a " + label + " de " + date + " na " + pelada + "."
                : n.getAward() == NotificationAward.PUSKAS
                        ? "Você ganhou o " + label + " de " + date + " na " + pelada + " 🏆"
                        : "Você foi o " + label + " de " + date + " na " + pelada + " 🏆";
        return new NotificationContent(label + " de " + date, label, sentence, "Ver a sessão", session);
    }

    // "07/12" in the billing zone (the validity ends at 23:59:59 there)
    private static String billingDay(Instant instant) {
        return instant != null ? BILLING_DAY.format(instant) : "";
    }

    // A paid subscription locks once the grace after valid_until is over
    private static Instant lockDate(Instant validUntil) {
        return validUntil != null ? validUntil.plus(SubscriptionState.GRACE) : null;
    }

    private static String subscriptionPath(Notification n) {
        return n.getPelada() != null ? "/pelada/" + n.getPelada().getId() + "/assinatura" : "/home";
    }

    private static String peladaPath(Notification n) {
        return n.getPelada() != null ? "/pelada/" + n.getPelada().getId() : "/home";
    }
}
