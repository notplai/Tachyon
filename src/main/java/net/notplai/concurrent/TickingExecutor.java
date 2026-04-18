package net.notplai.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A high-performance parallel executor using Java 21+ Virtual Threads.
 * Designed for splitting Minecraft tick workloads across multiple threads safely.
 *
 * Virtual threads are extremely lightweight (~few KB stack) so we can spawn
 * thousands without the overhead of platform threads.
 */
public final class TickingExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Loom/TickingExecutor");

    private final String name;
    private final ExecutorService executor;

    public TickingExecutor(String name) {
        this.name = name;
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("loom-" + name + "-", 0).factory()
        );
    }

    /**
     * Execute a collection of tasks in parallel, waiting for all to complete.
     * Each task processes one item from the list.
     * Exceptions in individual tasks are caught and logged, not propagated,
     * to prevent one failing block entity from crashing the server.
     *
     * @param items    the items to process
     * @param action   the action to perform on each item
     * @param <T>      the item type
     */
    public <T> void runParallel(List<T> items, Consumer<T> action) {
        if (items.isEmpty()) return;

        // For very small lists, don't bother with parallelism overhead
        if (items.size() <= 4) {
            for (T item : items) {
                try {
                    action.accept(item);
                } catch (Exception e) {
                    LOGGER.error("[Loom/{}] Exception ticking item: {}", name, item, e);
                }
            }
            return;
        }

        List<Future<?>> futures = new ArrayList<>(items.size());
        for (T item : items) {
            futures.add(executor.submit(() -> {
                try {
                    action.accept(item);
                } catch (Exception e) {
                    LOGGER.error("[Loom/{}] Exception ticking item: {}", name, item, e);
                }
            }));
        }

        // Wait for all tasks to complete before returning
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("[Loom/{}] Interrupted while waiting for parallel tasks", name);
                break;
            } catch (ExecutionException e) {
                LOGGER.error("[Loom/{}] Unexpected execution exception", name, e);
            }
        }
    }

    /**
     * Execute tasks in parallel using batched chunking for reduced overhead.
     * Items are split into N chunks (based on available processors) and each
     * chunk is processed sequentially within its virtual thread.
     *
     * Best for large lists of lightweight tasks (e.g., entity ticking).
     *
     * @param items    the items to process
     * @param action   the action to perform on each item
     * @param <T>      the item type
     */
    public <T> void runBatched(List<T> items, Consumer<T> action) {
        if (items.isEmpty()) return;

        int size = items.size();
        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors());
        int batchSize = Math.max(1, (size + parallelism - 1) / parallelism);

        if (size <= batchSize) {
            // Single batch — run inline
            for (T item : items) {
                try {
                    action.accept(item);
                } catch (Exception e) {
                    LOGGER.error("[Loom/{}] Exception ticking item: {}", name, item, e);
                }
            }
            return;
        }

        List<Future<?>> futures = new ArrayList<>(parallelism);
        for (int start = 0; start < size; start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, size);
            futures.add(executor.submit(() -> {
                for (int i = from; i < to; i++) {
                    try {
                        action.accept(items.get(i));
                    } catch (Exception e) {
                        LOGGER.error("[Loom/{}] Exception ticking item: {}", name, items.get(i), e);
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                LOGGER.error("[Loom/{}] Unexpected execution exception", name, e);
            }
        }
    }

    /**
     * Shutdown the executor gracefully.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOGGER.warn("[Loom/{}] Executor did not terminate in time, forced shutdown", name);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

