package com.example.linuxterminal.domains.container.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.linuxterminal.global.config.TerminalProperties;
import org.junit.jupiter.api.Test;

class ContainerNameGeneratorServiceTest {

    @Test
    void forUserCreatesStableSanitizedContainerName() {
        ContainerNameGeneratorService generator = new ContainerNameGeneratorService(new TerminalProperties());

        String containerName = generator.forUser("../User 123!!!");

        assertThat(containerName).isEqualTo("user-123_container");
        assertThat(containerName).matches("[a-z0-9_.-]+");
        assertThat(containerName).doesNotContain(":");
        assertThat(containerName).doesNotContain("/");
    }

    @Test
    void forUserFallsBackWhenUserIdHasNoUsableCharacters() {
        ContainerNameGeneratorService generator = new ContainerNameGeneratorService(new TerminalProperties());

        String containerName = generator.forUser("/////");

        assertThat(containerName).isEqualTo("anonymous_container");
        assertThat(containerName).matches("[a-z0-9_.-]+");
    }
}
