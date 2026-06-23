package com.example.linuxterminal.terminal.docker;

import com.example.linuxterminal.terminal.config.TerminalProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DockerCommandFactory {

    private final TerminalProperties properties;

    public DockerCommandFactory(TerminalProperties properties) {
        this.properties = properties;
    }

    public List<String> runDetachedCommand(String containerName) {
        TerminalProperties.Docker docker = properties.getDocker();
        List<String> command = new ArrayList<>();
        command.add(docker.getExecutable());
        command.add("run");
        command.add("-d");
        command.add("--name");
        command.add(containerName);
        command.add("--cpus=" + docker.getCpus());
        command.add("--memory=" + docker.getMemory());
        command.add("--pids-limit=" + docker.getPidsLimit());
        command.add("--network=" + docker.getNetwork());
        command.add("--user");
        command.add(docker.getUser());
        command.add("--workdir");
        command.add(docker.getWorkdir());
        command.add(properties.getImage());
        command.add("tail");
        command.add("-f");
        command.add("/dev/null");
        return command;
    }

    public List<String> execCommand(String containerName) {
        List<String> command = new ArrayList<>();
        command.add(properties.getDocker().getExecutable());
        command.add("exec");
        command.add("-i");
        command.add(containerName);
        command.addAll(properties.getDocker().getCommand());
        return command;
    }

    public List<String> inspectStatusCommand(String containerName) {
        return List.of(
                properties.getDocker().getExecutable(),
                "inspect",
                "-f",
                "{{.State.Status}}",
                containerName);
    }

    public List<String> startCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "start", containerName);
    }

    public List<String> stopCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "stop", containerName);
    }

    public List<String> restartCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "restart", containerName);
    }

}
