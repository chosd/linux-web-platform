package com.example.linuxterminal.domains.terminal.application.port.out;

import org.springframework.web.socket.WebSocketSession;

public interface WebSocketMessageSender {

    boolean sendText(WebSocketSession session, String payload);
}

