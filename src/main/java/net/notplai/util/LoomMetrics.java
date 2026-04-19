package net.notplai.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Central metrics tracker for Loom optimizations.
 * <p>
 * Thread-safety model:
 * - Write path (server tick thread) acquires write lock briefly to update rolling arrays.
 * - Read path (render thread / F3 screen) uses atomic snapshot references for consistent reads.
 * - Snapshot is published every tick to avoid partial state reads.
 */
public final class LoomMetrics {

    private LoomMetrics() {}

    private static final int SAMPLE_COUNT = 100;

    public static volatile int fastMathTableSize = 0;
    public static volatile long fastMathInitTimeUs = 0;

    public static volatile int dimensionCount = 0;

    private static final Map<String, DimensionStats> dimensionStats = new ConcurrentHashMap<>();

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static final double[] tickChildrenSamples = new double[SAMPLE_COUNT];
    private static int tickChildrenIndex = 0;
    private static double tickChildrenAvgMs = 0.0;
    private static double tickChildrenLastMs = 0.0;
    private static double tickChildrenMaxMs = 0.0;

    private static final double[] blockEntityTickSamples = new double[SAMPLE_COUNT];
    private static int blockEntityTickIndex = 0;
    private static double blockEntityTickAvgMs = 0.0;
    private static double blockEntityTickLastMs = 0.0;
    private static double blockEntityTickMaxMs = 0.0;
    private static int blockEntityCount = 0;

    private static final double[] serverTickSamples = new double[SAMPLE_COUNT];
    private static int serverTickIndex = 0;
    private static double serverTickAvgMs = 0.0;
    private static double serverTickLastMs = 0.0;
    private static double serverTickMaxMs = 0.0;

    private static long totalTicksProfiled = 0;

    private static volatile int executorActiveThreads = 0;
    private static volatile int executorParallelism = 0;
    private static volatile long executorStealCount = 0;
    private static volatile int executorBatchSize = -1;
    private static volatile long circuitBreakerTrips = 0;

    private static final AtomicReference<MetricsSnapshot> currentSnapshot =
            new AtomicReference<>(new MetricsSnapshot());

    /**
     * Immutable metrics snapshot — safe to read from any thread.
     */
    public static final class MetricsSnapshot {
        public final double tickChildrenAvgMs;
        public final double tickChildrenLastMs;
        public final double tickChildrenMaxMs;
        public final double blockEntityTickAvgMs;
        public final double blockEntityTickLastMs;
        public final double blockEntityTickMaxMs;
        public final int blockEntityCount;
        public final double serverTickAvgMs;
        public final double serverTickLastMs;
        public final double serverTickMaxMs;
        public final long totalTicksProfiled;
        public final int dimensionCount;
        public final Map<String, DimSnapshot> dimensions;
        public final int executorActiveThreads;
        public final int executorParallelism;
        public final long executorStealCount;
        public final int executorBatchSize;
        public final long circuitBreakerTrips;

        MetricsSnapshot() {
            this(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), 0, 0, 0, -1, 0);
        }

        MetricsSnapshot(double tcAvg, double tcLast, double tcMax,
                         double beAvg, double beLast, double beMax, int beCount,
                         double stAvg, double stLast, double stMax, long ticks,
                         int dimCount, Map<String, DimSnapshot> dims,
                         int exActive, int exPar, long exSteal, int exBatch, long cbTrips) {
            this.tickChildrenAvgMs = tcAvg;
            this.tickChildrenLastMs = tcLast;
            this.tickChildrenMaxMs = tcMax;
            this.blockEntityTickAvgMs = beAvg;
            this.blockEntityTickLastMs = beLast;
            this.blockEntityTickMaxMs = beMax;
            this.blockEntityCount = beCount;
            this.serverTickAvgMs = stAvg;
            this.serverTickLastMs = stLast;
            this.serverTickMaxMs = stMax;
            this.totalTicksProfiled = ticks;
            this.dimensionCount = dimCount;
            this.dimensions = dims;
            this.executorActiveThreads = exActive;
            this.executorParallelism = exPar;
            this.executorStealCount = exSteal;
            this.executorBatchSize = exBatch;
            this.circuitBreakerTrips = cbTrips;
        }
    }

    public static final class DimSnapshot {
        public final double avgMs;
        public final double lastMs;
        public final double maxMs;

        public DimSnapshot(double avgMs, double lastMs, double maxMs) {
            this.avgMs = avgMs;
            this.lastMs = lastMs;
            this.maxMs = maxMs;
        }
    }

    /**
     * Per-dimension statistics holder.
     */
    public static final class DimensionStats {
        private final double[] samples = new double[SAMPLE_COUNT];
        private int index = 0;
        public volatile double avgMs = 0.0;
        public volatile double lastMs = 0.0;
        public volatile double maxMs = 0.0;

        void record(double ms) {
            lastMs = ms;
            if (ms > maxMs) maxMs = ms;
            int idx = index % SAMPLE_COUNT;
            samples[idx] = ms;
            index++;
            int filled = Math.min(index, SAMPLE_COUNT);
            double sum = 0;
            for (int i = 0; i < filled; i++) sum += samples[i];
            avgMs = sum / filled;
        }
    }


    public static void recordDimensionTick(String dimensionName, double ms) {
        dimensionStats.computeIfAbsent(dimensionName, k -> new DimensionStats()).record(ms);
    }

    public static void recordTickChildrenTime(double ms) {
        lock.writeLock().lock();
        try {
            tickChildrenLastMs = ms;
            if (ms > tickChildrenMaxMs) tickChildrenMaxMs = ms;
            int idx = tickChildrenIndex % SAMPLE_COUNT;
            tickChildrenSamples[idx] = ms;
            tickChildrenIndex++;
            int filled = Math.min(tickChildrenIndex, SAMPLE_COUNT);
            double sum = 0;
            for (int i = 0; i < filled; i++) sum += tickChildrenSamples[i];
            tickChildrenAvgMs = sum / filled;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void recordBlockEntityTick(double ms, int count) {
        lock.writeLock().lock();
        try {
            blockEntityTickLastMs = ms;
            blockEntityCount = count;
            if (ms > blockEntityTickMaxMs) blockEntityTickMaxMs = ms;
            int idx = blockEntityTickIndex % SAMPLE_COUNT;
            blockEntityTickSamples[idx] = ms;
            blockEntityTickIndex++;
            int filled = Math.min(blockEntityTickIndex, SAMPLE_COUNT);
            double sum = 0;
            for (int i = 0; i < filled; i++) sum += blockEntityTickSamples[i];
            blockEntityTickAvgMs = sum / filled;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void recordServerTick(double ms) {
        lock.writeLock().lock();
        try {
            serverTickLastMs = ms;
            if (ms > serverTickMaxMs) serverTickMaxMs = ms;
            int idx = serverTickIndex % SAMPLE_COUNT;
            serverTickSamples[idx] = ms;
            serverTickIndex++;
            totalTicksProfiled++;
            int filled = Math.min(serverTickIndex, SAMPLE_COUNT);
            double sum = 0;
            for (int i = 0; i < filled; i++) sum += serverTickSamples[i];
            serverTickAvgMs = sum / filled;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void recordExecutorStats(int activeThreads, int parallelism, long stealCount, int batchSize) {
        executorActiveThreads = activeThreads;
        executorParallelism = parallelism;
        executorStealCount = stealCount;
        executorBatchSize = batchSize;
    }

    public static void recordCircuitBreakerTrip() {
        circuitBreakerTrips++;
    }

    /**
     * Publish a consistent snapshot. Call once per tick from the server thread.
     * The F3 screen reads from the snapshot atomically.
     */
    public static void publishSnapshot() {
        Map<String, DimSnapshot> dims = new HashMap<>();
        for (var entry : dimensionStats.entrySet()) {
            DimensionStats s = entry.getValue();
            dims.put(entry.getKey(), new DimSnapshot(s.avgMs, s.lastMs, s.maxMs));
        }

        lock.readLock().lock();
        try {
            currentSnapshot.set(new MetricsSnapshot(
                    tickChildrenAvgMs, tickChildrenLastMs, tickChildrenMaxMs,
                    blockEntityTickAvgMs, blockEntityTickLastMs, blockEntityTickMaxMs, blockEntityCount,
                    serverTickAvgMs, serverTickLastMs, serverTickMaxMs, totalTicksProfiled,
                    dimensionCount, Collections.unmodifiableMap(dims),
                    executorActiveThreads, executorParallelism, executorStealCount,
                    executorBatchSize, circuitBreakerTrips
            ));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the current consistent snapshot. Thread-safe, lock-free read.
     */
    public static MetricsSnapshot getSnapshot() {
        return currentSnapshot.get();
    }

    public static void resetPeaks() {
        lock.writeLock().lock();
        try {
            blockEntityTickMaxMs = 0.0;
            serverTickMaxMs = 0.0;
            tickChildrenMaxMs = 0.0;
        } finally {
            lock.writeLock().unlock();
        }
        dimensionStats.values().forEach(s -> s.maxMs = 0.0);
    }


    public static Map<String, DimensionStats> getDimensionStats() { return dimensionStats; }
    public static double getTickChildrenAvgMs() { return tickChildrenAvgMs; }
    public static double getTickChildrenLastMs() { return tickChildrenLastMs; }
    public static double getTickChildrenMaxMs() { return tickChildrenMaxMs; }
    public static double getBlockEntityTickAvgMs() { return blockEntityTickAvgMs; }
    public static double getBlockEntityTickLastMs() { return blockEntityTickLastMs; }
    public static double getBlockEntityTickMaxMs() { return blockEntityTickMaxMs; }
    public static int getBlockEntityCount() { return blockEntityCount; }
    public static double getServerTickAvgMs() { return serverTickAvgMs; }
    public static double getServerTickLastMs() { return serverTickLastMs; }
    public static double getServerTickMaxMs() { return serverTickMaxMs; }
    public static long getTotalTicksProfiled() { return totalTicksProfiled; }
}
