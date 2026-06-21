package com.example.linuxterminal.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DockerTerminalServiceTest {

    @Test
    void createContainerNameSanitizesWebSocketSessionId() {
        DockerTerminalService service = dockerTerminalService();

        String containerName = service.createContainerName("../ABC:raw session id!!!");

        assertThat(containerName).startsWith("linux-terminal-abc-raw-session-id-");
        assertThat(containerName).matches("[a-z0-9_.-]+");
        assertThat(containerName).doesNotContain(":");
        assertThat(containerName).doesNotContain("/");
    }

    @Test
    void createContainerNameFallsBackWhenSessionIdHasNoUsableCharacters() {
        DockerTerminalService service = dockerTerminalService();

        String containerName = service.createContainerName("/////");

        assertThat(containerName).startsWith("linux-terminal-session-");
        assertThat(containerName).matches("[a-z0-9_.-]+");
    }

    private DockerTerminalService dockerTerminalService() {
        TerminalProperties properties = new TerminalProperties();
        return new DockerTerminalService(
                properties,
                new DockerCommandFactory(properties),
                new TerminalWebSocketSender());
    }
}
