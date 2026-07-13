package com.example.linuxterminal.domains.terminal.service;

import com.example.linuxterminal.domains.terminal.service.TerminalProcessExitHandler;
import com.example.linuxterminal.domains.terminal.domain.TerminalSession;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TerminalProcessWatcherService {

    public Thread waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit) {
        Thread thread = Thread.ofVirtual()
                .name("terminal-process-" + terminalSession.getWebSocketSessionId())
                .unstarted(() -> {
                    try {
                        int exitCode = terminalSession.awaitExit();
                        if (!terminalSession.isClosed()) {
                            log.info("Terminal process exited. sessionId={} exitCode={}",
                                    terminalSession.getWebSocketSessionId(), exitCode);
                            onExit.onExit(exitCode);
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
        thread.start();
        return thread;
    }
}
