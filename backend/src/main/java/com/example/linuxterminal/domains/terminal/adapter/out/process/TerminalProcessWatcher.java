package com.example.linuxterminal.domains.terminal.adapter.out.process;

import com.example.linuxterminal.domains.terminal.application.port.out.TerminalProcessExitHandler;
import com.example.linuxterminal.domains.terminal.domain.TerminalSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TerminalProcessWatcher {

    public Thread waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit) {
        Thread thread = Thread.ofVirtual()
                .name("terminal-process-" + terminalSession.getWebSocketSessionId())
                .unstarted(() -> {
                    try {
                        int exitCode = terminalSession.getProcess().waitFor();
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
