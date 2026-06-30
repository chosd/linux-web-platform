package com.example.linuxterminal.domains.container.adapter.out.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.linuxterminal.global.config.TerminalProperties;
import org.junit.jupiter.api.Test;

class ContainerNameGeneratorTest {

    @Test
    void forUserCreatesStableSanitizedContainerName() {
        ContainerNameGenerator generator = new ContainerNameGenerator(new TerminalProperties());

        String containerName = generator.forUser("../User 123!!!");

        assertThat(containerName).isEqualTo("user-123_container");
        assertThat(containerName).matches("[a-z0-9_.-]+");
        assertThat(containerName).doesNotContain(":");
        assertThat(containerName).doesNotContain("/");
    }

    @Test
    void forUserFallsBackWhenUserIdHasNoUsableCharacters() {
        ContainerNameGenerator generator = new ContainerNameGenerator(new TerminalProperties());

        String containerName = generator.forUser("/////");

        assertThat(containerName).isEqualTo("anonymous_container");
        assertThat(containerName).matches("[a-z0-9_.-]+");
    }
}
