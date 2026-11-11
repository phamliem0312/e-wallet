package com.ewallet.identity.infrastructure;

import com.ewallet.identity.application.MessageSender;
import com.ewallet.platform.core.Sensitive;
import com.ewallet.platform.core.provider.FakeProvider;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bản giả: giữ tin đã "gửi" trong bộ nhớ để dev và test đọc lại (ví dụ lấy OTP). Không có ở profile prod, và
 * {@code FakeProviderGuard} của platform chặn khởi động nếu nó lọt vào prod.
 */
@Component
@FakeProvider
@Profile("!prod")
public class InMemoryMessageSender implements MessageSender {

    /** {@code code} chỉ có với tin OTP. */
    public record Message(String type, String recipient, String code, String purpose, Map<String, String> params,
            Instant at) {}

    private static final int CAPACITY = 1000;

    private final Deque<Message> messages = new ArrayDeque<>();
    private volatile boolean failing;

    @Override
    public synchronized void sendOtp(UUID challengeId, String phone, Sensitive<String> code, String purpose,
            Instant expiresAt) {
        if (failing) throw new IllegalStateException("fake sender is set to fail");
        add(new Message("OTP", phone, code.value(), purpose, Map.of(), Instant.now()));
    }

    @Override
    public synchronized void sendNotice(String type, String phone, Map<String, String> params) {
        if (failing) throw new IllegalStateException("fake sender is set to fail");
        add(new Message(type, phone, null, null, Map.copyOf(params), Instant.now()));
    }

    /** Tin gửi tới một SĐT, mới nhất trước. */
    public synchronized List<Message> messagesTo(String phone) {
        return messages.stream().filter(m -> m.recipient().equals(phone)).toList();
    }

    /** Cho test: giả lập dịch vụ thông báo lỗi. */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    private void add(Message message) {
        messages.addFirst(message);
        if (messages.size() > CAPACITY) messages.removeLast();
    }

    /** Trang nội bộ của dev để xem tin đã gửi (requirements: fake NotificationSender). Không có ở prod. */
    @RestController
    @Profile("!prod")
    static class DevMessagesController {
        private final InMemoryMessageSender sender;

        DevMessagesController(InMemoryMessageSender sender) {
            this.sender = sender;
        }

        @GetMapping("/internal/dev/messages")
        List<Message> messages(@RequestParam String recipient) {
            return sender.messagesTo(recipient);
        }
    }
}
