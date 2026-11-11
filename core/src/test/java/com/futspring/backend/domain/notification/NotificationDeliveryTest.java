package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.notification.email.NotificationEmailDispatcher;
import com.futspring.backend.domain.notification.inbox.NotificationPushService;
import com.futspring.backend.domain.notification.webpush.WebPushDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;

// After the commit (NotificationService registers it), every channel gets the same ids: bell, e-mail, Web Push
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTest {

    @Mock NotificationPushService pushService;
    @Mock NotificationEmailDispatcher emailDispatcher;
    @Mock WebPushDispatcher webPushDispatcher;

    @Test
    void handsTheNotificationsToTheThreeChannels() {
        new NotificationDelivery(pushService, emailDispatcher, webPushDispatcher).deliver(List.of(1L, 2L));

        InOrder order = inOrder(pushService, emailDispatcher, webPushDispatcher);
        order.verify(pushService).push(List.of(1L, 2L));
        order.verify(emailDispatcher).dispatch(List.of(1L, 2L));
        order.verify(webPushDispatcher).dispatch(List.of(1L, 2L));
    }
}
