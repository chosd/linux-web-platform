package com.example.linuxterminal.terminal.io;

import com.example.linuxterminal.terminal.core.TerminalProcessExitHandler;
import com.example.linuxterminal.terminal.core.TerminalSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
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
