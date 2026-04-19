package net.notplai.concurrent;

import net.notplai.config.LoomConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A high-performance parallel executor using Java 25 Virtual Threads for I/O-bound work
 * and a bounded ForkJoinPool for CPU-bound tasks.
 * <p>
 * Lifecycle: Created on server start, shut down on server stop.
 * Includes circuit-breaker timeouts and adaptive batch sizing.
 */
public final class TickingExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Loom/TickingExecutor");

    private final String name;
    private final ExecutorService virtualExecutor;
    private final ForkJoinPool cpuPool;
    private volatile boolean shutdown = false;

    // Adaptive batch sizing state
    private volatile int lastOptimalBatchSize = -1;
    private volatile double lastTaskAvgNanos = 0;

    public TickingExecutor(String name) {
        this.name = name;
        LoomConfig config = LoomConfig.get();

        // Virtual threads for I/O-bound and waiting tasks
        this.virtualExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("loom-" + name + "-vt-", 0).factory()
        );

        // Bounded ForkJoinPool for CPU-bound tasks (pathfinding, heavy math)
        int parallelism = config.maxParallelism;
        this.cpuPool = new ForkJoinPool(
                parallelism,
                pool -> {
                    ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    t.setName("loom-" + name + "-cpu-" + t.getPoolIndex());
                    t.setDaemon(true);
                    return t;
                },
                (t, e) -> LOGGER.error("[Loom/{}] Uncaught exception in CPU pool thread {}", name, t.getName(), e),
                true // async mode
        );

        LOGGER.info("[Loom/{}] Executor initialized: VirtualThreads + ForkJoinPool(parallelism={})", name, parallelism);
    }

    /**
     * Execute a collection of tasks in parallel using virtual threads.
     * Best for I/O-bound or waiting tasks.
     * Includes circuit-breaker timeout.
     */
    public <T> void runParallel(List<T> items, Consumer<T> action) {
        if (shutdown || items.isEmpty()) return;

        LoomConfig config = LoomConfig.get();
        if (!config.parallelizationEnabled || items.size() <= config.parallelThreshold) {
            runSequential(items, action);
            return;
        }

        long timeoutMs = config.workerTimeoutMs;
        List<Future<?>> futures = new ArrayList<>(items.size());
        for (T item : items) {
            futures.add(virtualExecutor.submit(() -> {
                try {
                    action.accept(item);
                } catch (Exception e) {
                    LOGGER.error("[Loom/{}] Exception ticking item: {}", name, item, e);
                }
            }));
        }

        awaitAll(futures, timeoutMs);
    }

    /**
     * Execute tasks in parallel using the CPU-bound ForkJoinPool with batching.
     * Best for CPU-heavy tasks (pathfinding, math-intensive operations).
     * Supports adaptive batch sizing.
     */
    public <T> void runBatched(List<T> items, Consumer<T> action) {
        if (shutdown || items.isEmpty()) return;

        LoomConfig config = LoomConfig.get();
        if (!config.parallelizationEnabled || items.size() <= config.parallelThreshold) {
            runSequential(items, action);
            return;
        }

        int size = items.size();
        int batchSize = computeBatchSize(size, config);

        if (size <= batchSize) {
            runSequential(items, action);
            return;
        }

        long timeoutMs = config.workerTimeoutMs;
        List<Future<?>> futures = new ArrayList<>();
        long batchStartNanos = System.nanoTime();

        for (int start = 0; start < size; start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, size);
            futures.add(cpuPool.submit(() -> {
                for (int i = from; i < to; i++) {
                    try {
                        action.accept(items.get(i));
                    } catch (Exception e) {
                        LOGGER.error("[Loom/{}] Exception ticking item: {}", name, items.get(i), e);
                    }
                }
            }));
        }

        awaitAll(futures, timeoutMs);

        // Update adaptive metrics
        if (config.adaptiveBatchSizing) {
            long elapsed = System.nanoTime() - batchStartNanos;
            lastTaskAvgNanos = (double) elapsed / size;
            lastOptimalBatchSize = batchSize;
        }
    }

    /**
     * Submit a single async task to the virtual thread executor.
     * Returns a Future for the Snapshot -> Process -> Apply pattern.
     */
    public <R> Future<R> submitAsync(Callable<R> task) {
        if (shutdown) throw new RejectedExecutionException("Executor is shut down");
        return virtualExecutor.submit(task);
    }

    /**
     * Submit a CPU-bound task to the ForkJoinPool.
     */
    public <R> Future<R> submitCpu(Callable<R> task) {
        if (shutdown) throw new RejectedExecutionException("Executor is shut down");
        return cpuPool.submit(task);
    }

    /**
     * Submit work following the Snapshot -> Process -> Apply pattern.
     *
     * @param snapshot Function to create an immutable snapshot (runs on caller thread)
     * @param process  Function to process the snapshot (runs on worker thread)
     * @return Future containing the processed result to be applied on main thread
     */
    public <S, R> Future<R> submitSnapshotTask(java.util.function.Supplier<S> snapshot, Function<S, R> process) {
        if (shutdown) throw new RejectedExecutionException("Executor is shut down");
        // Take snapshot on calling thread (main thread)
        S snap = snapshot.get();
        // Process on worker
        return cpuPool.submit(() -> process.apply(snap));
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public String getName() {
        return name;
    }

    public int getCpuPoolParallelism() {
        return cpuPool.getParallelism();
    }

    public int getCpuPoolActiveThreads() {
        return cpuPool.getActiveThreadCount();
    }

    public long getCpuPoolStealCount() {
        return cpuPool.getStealCount();
    }

    public int getLastOptimalBatchSize() {
        return lastOptimalBatchSize;
    }

    /**
     * Shutdown gracefully. Called on server stop.
     */
    public void shutdown() {
        shutdown = true;
        virtualExecutor.shutdown();
        cpuPool.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
                LOGGER.warn("[Loom/{}] Virtual executor forced shutdown", name);
            }
            if (!cpuPool.awaitTermination(5, TimeUnit.SECONDS)) {
                cpuPool.shutdownNow();
                LOGGER.warn("[Loom/{}] CPU pool forced shutdown", name);
            }
        } catch (InterruptedException e) {
            virtualExecutor.shutdownNow();
            cpuPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[Loom/{}] Executor shut down cleanly", name);
    }


    private <T> void runSequential(List<T> items, Consumer<T> action) {
        for (T item : items) {
            try {
                action.accept(item);
            } catch (Exception e) {
                LOGGER.error("[Loom/{}] Exception ticking item: {}", name, item, e);
            }
        }
    }

    private void awaitAll(List<Future<?>> futures, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (Future<?> future : futures) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    LOGGER.warn("[Loom/{}] Circuit breaker: timeout after {}ms, cancelling remaining tasks",
                            name, timeoutMs);
                    futures.forEach(f -> f.cancel(true));
                    break;
                }
                future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOGGER.warn("[Loom/{}] Task timed out, triggering circuit breaker", name);
                futures.forEach(f -> f.cancel(true));
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                LOGGER.error("[Loom/{}] Unexpected execution exception", name, e);
            } catch (CancellationException ignored) {
            }
        }
    }

    private int computeBatchSize(int totalItems, LoomConfig config) {
        if (config.adaptiveBatchSizing && lastTaskAvgNanos > 0) {
            // Target: each batch should take ~2ms of work for good load balancing
            double targetBatchNanos = 2_000_000.0;
            int adaptive = Math.max(1, (int) (targetBatchNanos / lastTaskAvgNanos));
            return Math.min(adaptive, totalItems);
        }
        // Default: split evenly across CPU pool parallelism
        int parallelism = config.maxParallelism;
        return Math.max(1, (totalItems + parallelism - 1) / parallelism);
    }
}
