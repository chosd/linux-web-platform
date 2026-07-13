package com.example.linuxterminal.domains.dashboard.service;

import com.example.linuxterminal.domains.dashboard.dto.HostResourceStatsSample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

import org.springframework.stereotype.Service;

@Service
public class HostResourceStatsService {

    private static final Path PROC_STAT = Path.of("/proc/stat");
    private static final Path PROC_MEMINFO = Path.of("/proc/meminfo");
    private static final long CPU_SAMPLE_INTERVAL_MILLIS = 120L;
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase(Locale.ROOT)
            .contains("win");

    public HostResourceStatsSample readStats() throws IOException {
        if (IS_WINDOWS) {
            return readWindowsStats();
        }

        CpuSnapshot firstCpuSnapshot = readCpuSnapshot();
        sleepForCpuSample();
        CpuSnapshot secondCpuSnapshot = readCpuSnapshot();
        MemorySnapshot memorySnapshot = readMemorySnapshot();
        double cpuPercent = calculateCpuPercent(firstCpuSnapshot, secondCpuSnapshot);
        double memoryUsageMb = memorySnapshot.usedKb() / 1024.0;
        double memoryTotalMb = memorySnapshot.totalKb() / 1024.0;
        double memoryPercent = memorySnapshot.totalKb() > 0
                ? memorySnapshot.usedKb() * 100.0 / memorySnapshot.totalKb()
                : 0.0;

        return new HostResourceStatsSample(
                Instant.now(),
                round(cpuPercent),
                round(memoryUsageMb),
                round(memoryTotalMb),
                round(memoryPercent));
    }

    private HostResourceStatsSample readWindowsStats() {
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double systemCpuLoad = osBean.getSystemCpuLoad();
        if (systemCpuLoad < 0) {
            systemCpuLoad = 0.0;
        }
        double cpuPercent = systemCpuLoad * 100.0;

        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = osBean.getFreeMemorySize();
        long usedMemory = totalMemory - freeMemory;

        double memoryTotalMb = totalMemory / 1024.0 / 1024.0;
        double memoryUsageMb = usedMemory / 1024.0 / 1024.0;
        double memoryPercent = totalMemory > 0 ? (double) usedMemory * 100.0 / totalMemory : 0.0;

        return new HostResourceStatsSample(
                Instant.now(),
                round(cpuPercent),
                round(memoryUsageMb),
                round(memoryTotalMb),
                round(memoryPercent));
    }

    private CpuSnapshot readCpuSnapshot() throws IOException {
        String cpuLine;
        try (Stream<String> lines = Files.lines(PROC_STAT)) {
            cpuLine = lines
                    .filter(line -> line.startsWith("cpu "))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Unable to read aggregate CPU line from /proc/stat."));
        }
        String[] parts = cpuLine.trim().split("\\s+");
        if (parts.length < 5) {
            throw new IOException("Invalid /proc/stat CPU line: " + cpuLine);
        }

        long user = parseLong(parts, 1);
        long nice = parseLong(parts, 2);
        long system = parseLong(parts, 3);
        long idle = parseLong(parts, 4);
        long iowait = parseLong(parts, 5);
        long irq = parseLong(parts, 6);
        long softirq = parseLong(parts, 7);
        long steal = parseLong(parts, 8);
        long idleAll = idle + iowait;
        long nonIdle = user + nice + system + irq + softirq + steal;
        return new CpuSnapshot(idleAll, idleAll + nonIdle);
    }

    private MemorySnapshot readMemorySnapshot() throws IOException {
        List<String> lines = Files.readAllLines(PROC_MEMINFO);
        Map<String, Long> values = new HashMap<>();
        for (String line : lines) {
            String[] keyValue = line.split(":", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String numericValue = keyValue[1].trim().toLowerCase(Locale.ROOT).replace("kb", "").trim();
            values.put(keyValue[0], Long.parseLong(numericValue));
        }

        long totalKb = values.getOrDefault("MemTotal", 0L);
        long availableKb = values.getOrDefault("MemAvailable", values.getOrDefault("MemFree", 0L));
        return new MemorySnapshot(totalKb, Math.max(0L, totalKb - availableKb));
    }

    private long parseLong(String[] parts, int index) {
        if (index >= parts.length) {
            return 0L;
        }
        return Long.parseLong(parts[index]);
    }

    private double calculateCpuPercent(CpuSnapshot first, CpuSnapshot second) {
        long totalDelta = second.total() - first.total();
        long idleDelta = second.idle() - first.idle();
        if (totalDelta <= 0L) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, (totalDelta - idleDelta) * 100.0 / totalDelta));
    }

    private void sleepForCpuSample() throws IOException {
        try {
            Thread.sleep(CPU_SAMPLE_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sampling host CPU usage.", exception);
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record CpuSnapshot(long idle, long total) {
    }

    private record MemorySnapshot(long totalKb, long usedKb) {
    }
}
