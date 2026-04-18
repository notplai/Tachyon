package net.notplai.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central metrics tracker for Loom optimizations.
 * Uses volatile fields for thread-safe reads from the render thread
 * while being updated from the server tick thread.
 *
 * Tracks rolling averages over the last N samples to smooth out spikes.
 */
public final class LoomMetrics {

    private LoomMetrics() {}

    // --- Configuration ---
    private static final int SAMPLE_COUNT = 100; // rolling average over 100 ticks (~5 seconds)

    // --- FastMath stats (set once at startup) ---
    public static volatile boolean fastMathEnabled = false;
    public static volatile int fastMathTableSize = 0;
    public static volatile long fastMathInitTimeUs = 0;

    // --- Dimension ticking ---
    public static volatile int dimensionCount = 0;

    // --- Per-dimension tick timing ---
    private static final Map<String, DimensionStats> dimensionStats = new ConcurrentHashMap<>();

    // --- Tick-children (all dimensions combined) timing ---
    private static final double[] tickChildrenSamples = new double[SAMPLE_COUNT];
    private static volatile int tickChildrenIndex = 0;
    private static volatile double tickChildrenAvgMs = 0.0;
    private static volatile double tickChildrenLastMs = 0.0;
    private static volatile double tickChildrenMaxMs = 0.0;

    // --- Block entity tick timing ---
    private static final double[] blockEntityTickSamples = new double[SAMPLE_COUNT];
    private static volatile int blockEntityTickIndex = 0;
    private static volatile double blockEntityTickAvgMs = 0.0;
    private static volatile double blockEntityTickLastMs = 0.0;
    private static volatile double blockEntityTickMaxMs = 0.0;
    private static volatile int blockEntityCount = 0;

    // --- Server level tick timing ---
    private static final double[] serverTickSamples = new double[SAMPLE_COUNT];
    private static volatile int serverTickIndex = 0;
    private static volatile double serverTickAvgMs = 0.0;
    private static volatile double serverTickLastMs = 0.0;
    private static volatile double serverTickMaxMs = 0.0;

    // --- Tick counter ---
    private static volatile long totalTicksProfiled = 0;

    /**
     * Per-dimension statistics holder.
     */
    public static final class DimensionStats {
        private final double[] samples = new double[SAMPLE_COUNT];
        private volatile int index = 0;
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

    /**
     * Record a per-dimension tick duration.
     */
    public static void recordDimensionTick(String dimensionName, double ms) {
        dimensionStats.computeIfAbsent(dimensionName, k -> new DimensionStats()).record(ms);
    }

    /**
     * Record the total tickChildren duration (all dimensions + overhead).
     */
    public static void recordTickChildrenTime(double ms) {
        tickChildrenLastMs = ms;
        if (ms > tickChildrenMaxMs) tickChildrenMaxMs = ms;
        int idx = tickChildrenIndex % SAMPLE_COUNT;
        tickChildrenSamples[idx] = ms;
        tickChildrenIndex++;
        int filled = Math.min(tickChildrenIndex, SAMPLE_COUNT);
        double sum = 0;
        for (int i = 0; i < filled; i++) sum += tickChildrenSamples[i];
        tickChildrenAvgMs = sum / filled;
    }

    /**
     * Record a block entity tick duration.
     */
    public static void recordBlockEntityTick(double ms, int count) {
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
    }

    /**
     * Record a server level tick duration.
     */
    public static void recordServerTick(double ms) {
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
    }

    /**
     * Reset peak tracking.
     */
    public static void resetPeaks() {
        blockEntityTickMaxMs = 0.0;
        serverTickMaxMs = 0.0;
        tickChildrenMaxMs = 0.0;
        dimensionStats.values().forEach(s -> s.maxMs = 0.0);
    }

    // --- Getters for the F3 screen ---

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
