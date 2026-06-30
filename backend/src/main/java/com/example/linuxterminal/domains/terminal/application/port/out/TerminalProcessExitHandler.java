package com.example.linuxterminal.domains.terminal.application.port.out;

@FunctionalInterface
public interface TerminalProcessExitHandler {

    void onExit(int exitCode);
}

