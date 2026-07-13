package com.example.linuxterminal.global.docker;

import com.example.linuxterminal.domains.container.dto.ContainerStatsSample;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.Statistics;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Repository;

@Repository
public class DockerStatsRepository {

    private static final long CALLBACK_TIMEOUT_SECONDS = 30L;

    private final DockerClient dockerClient;

    public DockerStatsRepository(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public ContainerStatsSample readStats(String containerName) throws IOException {
        try {
            Statistics statistics = new BlockingStatsCallback().read(dockerClient.statsCmd(containerName).withNoStream(true));
            return toStatsSample(statistics);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading Docker stats.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to read stats. containerName=" + containerName, exception);
        }
    }

    private ContainerStatsSample toStatsSample(Statistics statistics) {
        return new ContainerStatsSample(
                Instant.now(),
                cpuPercent(statistics),
                bytesToMb(statistics.getMemoryStats() == null ? null : statistics.getMemoryStats().getUsage()),
                bytesToMb(statistics.getMemoryStats() == null ? null : statistics.getMemoryStats().getLimit()),
                bytesToMb(sumNetwork(statistics, true)),
                bytesToMb(sumNetwork(statistics, false)),
                bytesToMb(sumBlockIo(statistics, "read")),
                bytesToMb(sumBlockIo(statistics, "write")));
    }

    private double cpuPercent(Statistics statistics) {
        CpuStatsConfig cpuStats = statistics.getCpuStats();
        CpuStatsConfig preCpuStats = statistics.getPreCpuStats();
        if (cpuStats == null || preCpuStats == null
                || cpuStats.getCpuUsage() == null || preCpuStats.getCpuUsage() == null) {
            return 0.0;
        }
        long cpuDelta = longValue(cpuStats.getCpuUsage().getTotalUsage())
                - longValue(preCpuStats.getCpuUsage().getTotalUsage());
        long systemDelta = longValue(cpuStats.getSystemCpuUsage()) - longValue(preCpuStats.getSystemCpuUsage());
        long onlineCpus = longValue(cpuStats.getOnlineCpus());
        if (onlineCpus <= 0 && cpuStats.getCpuUsage().getPercpuUsage() != null) {
            onlineCpus = cpuStats.getCpuUsage().getPercpuUsage().size();
        }
        if (cpuDelta <= 0 || systemDelta <= 0 || onlineCpus <= 0) {
            return 0.0;
        }
        return (double) cpuDelta / systemDelta * onlineCpus * 100.0;
    }

    private long sumNetwork(Statistics statistics, boolean rx) {
        Map<String, StatisticNetworksConfig> networks = statistics.getNetworks();
        if (networks == null || networks.isEmpty()) {
            networks = statistics.getNetwork();
        }
        if (networks == null) {
            return 0L;
        }
        return networks.values().stream()
                .mapToLong(network -> rx ? longValue(network.getRxBytes()) : longValue(network.getTxBytes()))
                .sum();
    }

    private long sumBlockIo(Statistics statistics, String operation) {
        if (statistics.getBlkioStats() == null
                || statistics.getBlkioStats().getIoServiceBytesRecursive() == null) {
            return 0L;
        }
        return statistics.getBlkioStats().getIoServiceBytesRecursive().stream()
                .filter(entry -> operation.equalsIgnoreCase(entry.getOp()))
                .mapToLong(entry -> longValue(entry.getValue()))
                .sum();
    }

    private double bytesToMb(Long bytes) {
        return bytesToMb(longValue(bytes));
    }

    private double bytesToMb(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    private long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private static class BlockingStatsCallback extends ResultCallback.Adapter<Statistics> {
        private Statistics statistics;

        Statistics read(com.github.dockerjava.api.command.StatsCmd statsCmd)
                throws InterruptedException {
            statsCmd.exec(this).awaitCompletion(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return statistics == null ? new Statistics() : statistics;
        }

        @Override
        public void onNext(Statistics statistics) {
            this.statistics = statistics;
        }
    }
}
