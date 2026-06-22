package com.example.linuxterminal.terminal.core;

@FunctionalInterface
public interface TerminalProcessExitHandler {

    void onExit(int exitCode);
}

