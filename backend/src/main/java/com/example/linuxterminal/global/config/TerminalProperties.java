package com.example.linuxterminal.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "terminal")
public class TerminalProperties {

    @NotBlank
    private String image = "linux-terminal-playground:ubuntu";

    private Duration idleTimeout = Duration.ofMinutes(10);

    private Duration cleanupInterval = Duration.ofMinutes(1);

    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

    private Docker docker = new Docker();

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public Docker getDocker() {
        return docker;
    }

    public void setDocker(Docker docker) {
        this.docker = docker;
    }

    public static class Docker {

        @NotBlank
        private String executable = "docker";

        @NotBlank
        private String cpus = "0.5";

        @NotBlank
        private String memory = "256m";

        @Positive
        private int pidsLimit = 64;

        @NotBlank
        private String network = "none";

        @NotBlank
        private String user = "suser";

        @NotBlank
        private String workdir = "/home/suser";

        private List<String> command = new ArrayList<>(
                List.of("/usr/bin/script", "-qfec", "/bin/bash --login", "/dev/null"));

        @NotBlank
        private String containerNamePrefix = "linux-terminal";

        @NotBlank
        private String allowedVolumeHostPathBase = "/mnt/storage";

        @Positive
        private int containerNameSessionIdLength = 24;

        public String getExecutable() {
            return executable;
        }

        public void setExecutable(String executable) {
            this.executable = executable;
        }

        public String getCpus() {
            return cpus;
        }

        public void setCpus(String cpus) {
            this.cpus = cpus;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public int getPidsLimit() {
            return pidsLimit;
        }

        public void setPidsLimit(int pidsLimit) {
            this.pidsLimit = pidsLimit;
        }

        public String getNetwork() {
            return network;
        }

        public void setNetwork(String network) {
            this.network = network;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getWorkdir() {
            return workdir;
        }

        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        public List<String> getCommand() {
            return command;
        }

        public void setCommand(List<String> command) {
            this.command = command;
        }

        public String getContainerNamePrefix() {
            return containerNamePrefix;
        }

        public void setContainerNamePrefix(String containerNamePrefix) {
            this.containerNamePrefix = containerNamePrefix;
        }

        public String getAllowedVolumeHostPathBase() {
            return allowedVolumeHostPathBase;
        }

        public void setAllowedVolumeHostPathBase(String allowedVolumeHostPathBase) {
            this.allowedVolumeHostPathBase = allowedVolumeHostPathBase;
        }

        public int getContainerNameSessionIdLength() {
            return containerNameSessionIdLength;
        }

        public void setContainerNameSessionIdLength(int containerNameSessionIdLength) {
            this.containerNameSessionIdLength = containerNameSessionIdLength;
        }
    }
}
