package com.example.linuxterminal.global.docker;

public record DockerCommandResult(
        int exitCode,
        String stdout,
        String stderr
) {
}
