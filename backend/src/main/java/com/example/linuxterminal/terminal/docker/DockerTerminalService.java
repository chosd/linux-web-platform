package com.example.linuxterminal.terminal.docker;

import com.example.linuxterminal.terminal.core.TerminalProcessExitHandler;
import com.example.linuxterminal.terminal.core.TerminalRuntime;
import com.example.linuxterminal.terminal.core.TerminalSession;
import com.example.linuxterminal.terminal.io.TerminalProcessWatcher;
import com.example.linuxterminal.terminal.io.TerminalStreamForwarder;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
public class DockerTerminalService implements TerminalRuntime {

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerNameGenerator containerNameGenerator;
    private final TerminalStreamForwarder terminalStreamForwarder;
    private final TerminalProcessWatcher terminalProcessWatcher;

    public DockerTerminalService(
            DockerCommandFactory dockerCommandFactory,
            ContainerNameGenerator containerNameGenerator,
            TerminalStreamForwarder terminalStreamForwarder,
            TerminalProcessWatcher terminalProcessWatcher
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerNameGenerator = containerNameGenerator;
        this.terminalStreamForwarder = terminalStreamForwarder;
        this.terminalProcessWatcher = terminalProcessWatcher;
    }

    @Override
    public TerminalSession start(WebSocketSession webSocketSession) throws IOException {
        String containerName = containerNameGenerator.create(webSocketSession.getId());
        List<String> command = dockerCommandFactory.runCommand(containerName);
        log.info("[COMMAND][{}]", command);
        Process process = new ProcessBuilder(command).start();
        return new TerminalSession(webSocketSession.getId(), webSocketSession, containerName, process);
    }

    @Override
    public void remove(TerminalSession terminalSession) {
        if (!terminalSession.markClosed()) {
            return;
        }

        terminalSession.closeInput();
        Process process = terminalSession.getProcess();
        if (process.isAlive()) {
            process.destroy();
        }

        List<String> command = dockerCommandFactory.removeCommand(terminalSession.getContainerName());
        try {
            Process rmProcess = new ProcessBuilder(command).start();
            int exitCode = rmProcess.waitFor();
            if (exitCode != 0) {
                log.warn("Docker container cleanup finished with non-zero exitCode. containerName={} exitCode={}",
                        terminalSession.getContainerName(), exitCode);
            }
        } catch (IOException exception) {
            log.warn("Failed to start docker cleanup process. containerName={}",
                    terminalSession.getContainerName(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while cleaning docker container. containerName={}",
                    terminalSession.getContainerName(), exception);
        }
    }

    @Override
    public void streamToClient(TerminalSession terminalSession) {
        terminalStreamForwarder.forward(terminalSession, terminalSession.getProcess().getInputStream(), "stdout");
        terminalStreamForwarder.forward(terminalSession, terminalSession.getProcess().getErrorStream(), "stderr");
    }

    @Override
    public void waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit) {
        terminalProcessWatcher.waitForExit(terminalSession, onExit);
    }
}
