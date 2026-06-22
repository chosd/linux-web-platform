package com.example.linuxterminal.terminal.core;

import java.io.IOException;
import org.springframework.web.socket.WebSocketSession;

public interface TerminalRuntime {

    TerminalSession start(WebSocketSession webSocketSession) throws IOException;

    void remove(TerminalSession terminalSession);

    void streamToClient(TerminalSession terminalSession);

    void waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit);
}

