package org.unwind.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class SagaWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(SagaInstance saga) {
        try {
            String json = mapper.writeValueAsString(new SagaView(saga));
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            System.out.println("broadcast failed: " + e.getMessage());
        }
    }

    public record SagaView(
            String id,
            String fromAccount,
            String toAccount,
            java.math.BigDecimal amount,
            String state,
            String failStep,
            String detail,
            String updatedAt
    ) {
        SagaView(SagaInstance s) {
            this(s.getId(), s.getFromAccount(), s.getToAccount(), s.getAmount(),
                    s.getState().name(), s.getFailStep().name(),
                    s.getDetail(), String.valueOf(s.getUpdatedAt()));
        }
    }
}