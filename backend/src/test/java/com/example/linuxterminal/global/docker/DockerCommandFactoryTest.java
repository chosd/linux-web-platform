package com.example.linuxterminal.global.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.container.dto.VolumeMount;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.global.config.TerminalProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockerCommandFactoryTest {

    @Test
    void runDetachedCommandIncludesVolumeMounts() {
        DockerCommandFactory factory = new DockerCommandFactory(new TerminalProperties());

        List<String> command = factory.runDetachedCommand(
                "test_container",
                new ResourceLimits(0.5, 256),
                List.of(new PortBinding(18080, 8080, PortBinding.Protocol.TCP)),
                List.of(new VolumeMount("/mnt/storage/test2", "/workspace")),
                null);

        assertThat(command).containsSubsequence("-v", "/mnt/storage/test2:/workspace");
        assertThat(command).containsSubsequence("-p", "18080:8080/tcp");
    }
}
