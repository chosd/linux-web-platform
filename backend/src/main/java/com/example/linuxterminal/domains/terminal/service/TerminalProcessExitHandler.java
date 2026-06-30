package com.example.linuxterminal.domains.terminal.service;

@FunctionalInterface
public interface TerminalProcessExitHandler {

    void onExit(int exitCode);
}

