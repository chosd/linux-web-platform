package com.example.linuxterminal.terminal;

@FunctionalInterface
public interface TerminalProcessExitHandler {

    void onExit(int exitCode);
}
