package com.example.linuxterminal.terminal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DockerCommandFactory {

    private final TerminalProperties properties;

    public DockerCommandFactory(TerminalProperties properties) {
        this.properties = properties;
    }

    public List<String> runCommand(String containerName) {
        TerminalProperties.Docker docker = properties.getDocker();
        List<String> command = new ArrayList<>();
        command.add(docker.getExecutable());
        command.add("run");
        command.add("-i");
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
        command.addAll(docker.getCommand());
        return command;
    }

    public List<String> removeCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "rm", "-f", containerName);
    }
}
