package com.example.linuxterminal.terminal.core;

import org.springframework.web.socket.WebSocketSession;

public interface WebSocketMessageSender {

    boolean sendText(WebSocketSession session, String payload);
}

