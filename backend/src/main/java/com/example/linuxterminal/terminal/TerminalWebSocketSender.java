package com.example.linuxterminal.terminal;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class TerminalWebSocketSender {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketSender.class);

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
