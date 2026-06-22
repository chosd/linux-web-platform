package com.example.linuxterminal.terminal.websocket;

import com.example.linuxterminal.terminal.core.WebSocketMessageSender;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class TerminalWebSocketSender implements WebSocketMessageSender {

    @Override
    public boolean sendText(WebSocketSession session, String payload) {
        synchronized (session) {
            if (!session.isOpen()) {
                return false;
            }
            try {
                session.sendMessage(new TextMessage(payload));
                return true;
            } catch (IOException exception) {
                log.warn("Failed to send terminal websocket message. sessionId={}", session.getId(), exception);
                return false;
            }
        }
    }
}
