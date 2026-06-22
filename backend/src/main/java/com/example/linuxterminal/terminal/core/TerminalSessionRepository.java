package com.example.linuxterminal.terminal.core;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface TerminalSessionRepository {

    void save(TerminalSession terminalSession);

    Optional<TerminalSession> findById(String webSocketSessionId);

    Optional<TerminalSession> remove(String webSocketSessionId);

    void forEach(BiConsumer<String, TerminalSession> consumer);
}

