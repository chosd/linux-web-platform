package com.example.linuxterminal.domains.terminal.adapter.out.process;

import com.example.linuxterminal.domains.terminal.domain.TerminalSession;
import com.example.linuxterminal.domains.terminal.application.port.out.WebSocketMessageSender;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TerminalStreamForwarder {

    private final WebSocketMessageSender webSocketMessageSender;

    public TerminalStreamForwarder(WebSocketMessageSender webSocketMessageSender) {
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public Thread forward(TerminalSession terminalSession, InputStream inputStream, String streamName) {
        Thread thread = Thread.ofVirtual()
                .name("terminal-" + streamName + "-" + terminalSession.getWebSocketSessionId())
                .unstarted(() -> forwardStream(terminalSession, inputStream));
        thread.start();
        return thread;
    }

    private void forwardStream(TerminalSession terminalSession, InputStream inputStream) {
        char[] buffer = new char[2048];
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read);
                webSocketMessageSender.sendText(terminalSession.getWebSocketSession(), chunk);
            }
        } catch (IOException exception) {
            if (!terminalSession.isClosed()) {
                log.warn("Failed to forward terminal stream. sessionId={}",
                        terminalSession.getWebSocketSessionId(), exception);
            }
        }
    }
}
