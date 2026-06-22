package com.example.linuxterminal.terminal.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.linuxterminal.terminal.config.TerminalProperties;
import org.junit.jupiter.api.Test;

class ContainerNameGeneratorTest {

    @Test
    void createSanitizesWebSocketSessionId() {
        ContainerNameGenerator generator = new ContainerNameGenerator(new TerminalProperties());

        String containerName = generator.create("../ABC:raw session id!!!");

        assertThat(containerName).startsWith("linux-terminal-abc-raw-session-id-");
        assertThat(containerName).matches("[a-z0-9_.-]+");
        assertThat(containerName).doesNotContain(":");
        assertThat(containerName).doesNotContain("/");
    }

    @Test
    void createFallsBackWhenSessionIdHasNoUsableCharacters() {
        ContainerNameGenerator generator = new ContainerNameGenerator(new TerminalProperties());

        String containerName = generator.create("/////");

        assertThat(containerName).startsWith("linux-terminal-session-");
        assertThat(containerName).matches("[a-z0-9_.-]+");
    }
}

