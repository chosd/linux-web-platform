package com.example.linuxterminal.global.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Repository;

@Repository
public class DockerExecRepository {

    private static final long CALLBACK_TIMEOUT_SECONDS = 30L;
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    private final DockerClient dockerClient;

    public DockerExecRepository(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public ExecResult exec(String containerName, String... command) throws IOException {
        return exec(containerName, null, null, command);
    }

    public ExecResult exec(String containerName, String user, InputStream stdin, String... command) throws IOException {
        RawExecResult result = execRaw(containerName, user, stdin, command);
        return new ExecResult(
                result.exitCode(),
                new String(result.stdout(), StandardCharsets.UTF_8),
                new String(result.stderr(), StandardCharsets.UTF_8));
    }

    public RawExecResult execRaw(String containerName, String user, InputStream stdin, String... command) throws IOException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String execId = createExec(containerName, user, stdin != null, true, true, false, command);
        try {
            var execStartCmd = dockerClient.execStartCmd(execId).withTty(false);
            if (stdin != null) {
                execStartCmd.withStdIn(stdin);
            }
            execStartCmd.exec(frameCollector(stdout, stderr))
                    .awaitCompletion(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Integer exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCode();
            return new RawExecResult(
                    exitCode == null ? 1 : exitCode,
                    stdout.toByteArray(),
                    stderr.toByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while executing Docker command.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Docker API exec failed. containerName=" + containerName, exception);
        }
    }

    public InputStream openExecStdoutStream(String containerName, String... command) throws IOException {
        return openExecStdoutStreamAsUser(containerName, null, command);
    }

    public InputStream openExecStdoutStreamAsUser(String containerName, String user, String... command) throws IOException {
        PipedInputStream inputStream = new PipedInputStream(STREAM_BUFFER_SIZE);
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        String execId = createExec(containerName, user, false, true, true, false, command);
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                try {
                    if (frame.getStreamType() == StreamType.STDOUT || frame.getStreamType() == StreamType.RAW) {
                        outputStream.write(frame.getPayload());
                        outputStream.flush();
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to stream Docker exec stdout.", exception);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                closeQuietly(outputStream);
            }

            @Override
            public void onComplete() {
                closeQuietly(outputStream);
            }
        };
        try {
            startExec(execId, null, false, callback);
            return inputStream;
        } catch (RuntimeException exception) {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            throw new IOException("Docker API exec stream failed. containerName=" + containerName, exception);
        }
    }

    public String createExec(
            String containerName,
            String user,
            boolean attachStdin,
            boolean attachStdout,
            boolean attachStderr,
            boolean tty,
            String... command
    ) {
        var execCreateCmd = dockerClient.execCreateCmd(containerName)
                .withAttachStdin(attachStdin)
                .withAttachStdout(attachStdout)
                .withAttachStderr(attachStderr)
                .withTty(tty)
                .withCmd(command);
        if (hasText(user)) {
            execCreateCmd.withUser(user);
        }
        return execCreateCmd.exec().getId();
    }

    public void startExec(
            String execId,
            InputStream stdin,
            boolean tty,
            ResultCallback<Frame> callback
    ) {
        var execStartCmd = dockerClient.execStartCmd(execId).withTty(tty);
        if (stdin != null) {
            execStartCmd.withStdIn(stdin);
        }
        execStartCmd.exec(callback);
    }

    public int inspectExecExitCode(String execId) {
        Integer exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCode();
        return exitCode == null ? 1 : exitCode;
    }

    private ResultCallback.Adapter<Frame> frameCollector(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                try {
                    if (frame.getStreamType() == StreamType.STDERR) {
                        stderr.write(frame.getPayload());
                    } else {
                        stdout.write(frame.getPayload());
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to collect Docker exec frame.", exception);
                }
            }
        };
    }

    private void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Closing Docker exec streams is best effort.
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ExecResult(int exitCode, String stdout, String stderr) {
    }

    public record RawExecResult(int exitCode, byte[] stdout, byte[] stderr) {
        public String stderrText() {
            return new String(stderr, StandardCharsets.UTF_8);
        }
    }
}
