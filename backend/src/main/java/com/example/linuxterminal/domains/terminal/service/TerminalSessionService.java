package com.example.linuxterminal.domains.terminal.service;

import java.io.IOException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

public interface TerminalSessionService {

    void create(WebSocketSession webSocketSession) throws IOException;

    void write(String webSocketSessionId, String payload) throws IOException;

    void close(String webSocketSessionId, String reason);

    void close(String webSocketSessionId, String reason, boolean closeWebSocket);

    void close(String webSocketSessionId, String reason, CloseStatus closeStatus);

    void cleanupIdleSessions();
}
