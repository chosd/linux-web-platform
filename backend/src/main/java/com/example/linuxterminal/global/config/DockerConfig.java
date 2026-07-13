package com.example.linuxterminal.global.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerConfig {

    @Bean(destroyMethod = "close")
    DockerClient dockerClient(TerminalProperties terminalProperties) {
        String dockerHost = terminalProperties.getDocker().getHost();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            if (dockerHost.startsWith("unix://")) {
                dockerHost = "npipe:////./pipe/docker_engine";
            }
        }
        
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
                
        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

}
