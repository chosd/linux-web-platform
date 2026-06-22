package com.example.linuxterminal.terminal.repository;

import com.example.linuxterminal.terminal.core.TerminalSession;
import com.example.linuxterminal.terminal.core.TerminalSessionRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTerminalSessionRepository implements TerminalSessionRepository {

    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(TerminalSession terminalSession) {
        sessions.put(terminalSession.getWebSocketSessionId(), terminalSession);
    }

    @Override
    public Optional<TerminalSession> findById(String webSocketSessionId) {
        return Optional.ofNullable(sessions.get(webSocketSessionId));
    }

    @Override
    public Optional<TerminalSession> remove(String webSocketSessionId) {
        return Optional.ofNullable(sessions.remove(webSocketSessionId));
    }

    @Override
    public void forEach(BiConsumer<String, TerminalSession> consumer) {
        sessions.forEach(consumer);
    }
}

