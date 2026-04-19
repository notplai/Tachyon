package net.notplai.api;

import net.notplai.concurrent.TickingExecutor;
import net.notplai.config.Config;
import net.notplai.util.FastMath;
import net.notplai.util.Metrics;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Public API for Tachyon.
 * <p>
 * Other mods can use this to:
 * - Access the shared TickingExecutor safely
 * - Use FastMath directly without relying on Mixin overwrites
 * - Read consistent performance metrics
 * - Submit work using the Snapshot -> Process -> Apply pattern
 * <p>
 * All methods are safe to call from any thread unless documented otherwise.
 *
 * @since 1.0.0
 */
public final class TachyonAPI {

    private TachyonAPI() {}

    private static volatile TickingExecutor executor;

    /**
     * Called internally by Tachyon on server start. Do NOT call from other mods.
     */
    public static void initialize(TickingExecutor exec) {
        executor = exec;
    }

    /**
     * Called internally by Tachyon on server stop. Do NOT call from other mods.
     */
    public static void shutdown() {
        TickingExecutor ex = executor;
        if (ex != null) ex.shutdown ();
        executor = null;
    }

    /**
     * Internal: get the raw executor. Not part of public API contract.
     */
    public static TickingExecutor getExecutorInternal() {
        return executor;
    }



    /**
     * Check if the Tachyon executor is available and running.
     *
     * @return true if parallelization is enabled and the server is running
     */
    public static boolean isAvailable() {
        TickingExecutor ex = executor;
        return ex != null && !ex.isShutdown();
    }

    /**
     * Run a list of items in parallel using virtual threads (I/O-bound work).
     * Falls back to sequential execution if Tachyon is unavailable or disabled.
     *
     * @param items  items to process
     * @param action action to perform on each item
     * @param <T>    item type
     */
    public static <T> void runParallel(List<T> items, Consumer<T> action) {
        TickingExecutor ex = executor;
        if (ex != null && !ex.isShutdown()) ex.runParallel(items, action);
        else items.forEach(action);
    }

    /**
     * Run a list of items in batched parallel using the CPU pool (CPU-bound work).
     * Falls back to sequential execution if Tachyon is unavailable or disabled.
     *
     * @param items  items to process
     * @param action action to perform on each item
     * @param <T>    item type
     */
    public static <T> void runBatched(List<T> items, Consumer<T> action) {
        TickingExecutor ex = executor;
        if (ex != null && !ex.isShutdown()) {
            ex.runBatched(items, action);
        } else {
            items.forEach(action);
        }
    }

    /**
     * Submit an async I/O task. Returns a Future.
     *
     * @param task the callable to execute
     * @param <R>  result type
     * @return Future with the result
     * @throws IllegalStateException if Tachyon is not available
     */
    public static <R> Future<R> submitAsync(Callable<R> task) {
        TickingExecutor ex = executor;
        if (ex == null || ex.isShutdown()) {
            throw new IllegalStateException("Tachyon executor is not available");
        }
        return ex.submitAsync(task);
    }

    /**
     * Submit a CPU-bound task. Returns a Future.
     *
     * @param task the callable to execute
     * @param <R>  result type
     * @return Future with the result
     * @throws IllegalStateException if Tachyon is not available
     */
    public static <R> Future<R> submitCpu(Callable<R> task) {
        TickingExecutor ex = executor;
        if (ex == null || ex.isShutdown()) {
            throw new IllegalStateException("Tachyon executor is not available");
        }
        return ex.submitCpu(task);
    }

    /**
     * Submit work following the Snapshot -> Process -> Apply pattern.
     * <p>
     * The snapshot supplier runs on the calling thread (main thread).
     * The process function runs on a worker thread.
     * The returned Future should be polled/applied on the main thread next tick.
     *
     * @param snapshot supplier that captures immutable state (runs on caller thread)
     * @param process  function that processes the snapshot (runs on worker thread)
     * @param <S>      snapshot type
     * @param <R>      result type
     * @return Future containing the processed result
     * @throws IllegalStateException if Tachyon is not available
     */
    public static <S, R> Future<R> submitSnapshotTask(Supplier<S> snapshot, Function<S, R> process) {
        TickingExecutor ex = executor;
        if (ex == null || ex.isShutdown()) {
            throw new IllegalStateException("Tachyon executor is not available");
        }
        return ex.submitSnapshotTask(snapshot, process);
    }



    /** @see FastMath#sin(double) */
    public static float sin(double rad) { return FastMath.sin(rad); }

    /** @see FastMath#cos(double) */
    public static float cos(double rad) { return FastMath.cos(rad); }

    /** @see FastMath#floor(double) */
    public static int floor(double value) { return FastMath.floor(value); }

    /** @see FastMath#ceil(double) */
    public static int ceil(double value) { return FastMath.ceil(value); }

    /** @see FastMath#sqrt(float) */
    public static float sqrt(float x) { return FastMath.sqrt(x); }

    /** @see FastMath#clamp(double, double, double) */
    public static double clamp(double value, double min, double max) { return FastMath.clamp(value, min, max); }

    /** @see FastMath#lerp(double, double, double) */
    public static double lerp(double delta, double start, double end) { return FastMath.lerp(delta, start, end); }



    /**
     * Get a thread-safe, consistent snapshot of all Tachyon metrics.
     * Safe to call from any thread (render thread, server thread, etc.).
     *
     * @return immutable metrics snapshot
     */
    public static Metrics.MetricsSnapshot getMetrics() {
        return Metrics.getSnapshot();
    }



    /**
     * Check if parallelization is enabled in config.
     */
    public static boolean isParallelizationEnabled() {
        return Config.get().parallelizationEnabled;
    }

    /**
     * Check if FastMath overwrites are enabled in config.
     */
    public static boolean isMathOverwritesEnabled() {
        return Config.get().mathOverwritesEnabled;
    }
}

