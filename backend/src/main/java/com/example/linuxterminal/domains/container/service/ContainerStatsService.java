package com.example.linuxterminal.domains.container.service;

import com.example.linuxterminal.domains.container.dto.ContainerStatsSample;
import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.global.docker.DockerStatsRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContainerStatsService {

    private static final long STREAM_INTERVAL_MILLIS = 2000L;

    private final DockerStatsRepository dockerStatsRepository;
    private final ContainerService containerService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ContainerStatsService(
            DockerStatsRepository dockerStatsRepository,
            ContainerService containerService
    ) {
        this.dockerStatsRepository = dockerStatsRepository;
        this.containerService = containerService;
    }

    public SseEmitter streamStats(String userId, String containerName) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean active = new AtomicBoolean(true);
        emitter.onCompletion(() -> active.set(false));
        emitter.onTimeout(() -> active.set(false));
        emitter.onError(error -> active.set(false));

        executorService.submit(() -> {
            while (active.get()) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("container-stats")
                            .data(readStats(containerName)));
                    Thread.sleep(STREAM_INTERVAL_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    active.set(false);
                } catch (Exception exception) {
                    active.set(false);
                    if (isClientDisconnect(exception)) {
                        log.debug("Container stats stream closed by client. containerName={}", containerName);
                        return;
                    }
                    log.warn("Failed to stream container stats. containerName={}", containerName, exception);
                    emitter.completeWithError(exception);
                }
            }
            if (active.get()) {
                emitter.complete();
            }
        });

        return emitter;
    }

    public ContainerStatsSample readStats(String containerName) throws IOException {
        return dockerStatsRepository.readStats(containerName);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private boolean isClientDisconnect(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("broken pipe")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
