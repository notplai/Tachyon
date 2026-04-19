package net.notplai.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.notplai.api.LoomAPI;
import net.notplai.concurrent.TickingExecutor;
import net.notplai.config.LoomConfig;
import net.notplai.util.LoomMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Loom Server Tick Profiling, Executor Lifecycle & Optimization.
 * <p>
 * Manages:
 * - TickingExecutor lifecycle (init on server start, shutdown on server stop)
 * - Per-dimension tick timing with rolling averages
 * - Spike detection and logging
 * - Metrics snapshot publishing for F3 screen
 * - Config-aware warn thresholds
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private static final Logger LOOM_LOGGER = LoggerFactory.getLogger("Loom/Server");

    @Unique
    private long loom$tickChildrenStart;

    @Unique
    private int loom$dimensionCount;

    @Unique
    private boolean loom$loggedStartup = false;

    /**
     * Initialize executor on server start.
     */
    @Inject(method = "runServer", at = @At("HEAD"))
    private void loom$onServerStart(CallbackInfo ci) {
        LoomConfig.load();
        LoomConfig config = LoomConfig.get();

        if (config.parallelizationEnabled) {
            TickingExecutor exec = new TickingExecutor("server");
            LoomAPI.initialize(exec);
            LOOM_LOGGER.info("[Loom] TickingExecutor initialized — parallelism: {}, adaptive: {}",
                    config.maxParallelism, config.adaptiveBatchSizing);
        } else {
            LOOM_LOGGER.info("[Loom] Parallelization disabled by config");
        }
    }


    /**
     * Wrap each ServerLevel.tick() call to add per-dimension timing.
     */
    @WrapOperation(
            method = "tickChildren",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V")
    )
    private void loom$profileDimensionTick(ServerLevel level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        long start = System.nanoTime();

        original.call(level, hasTimeLeft);

        long elapsed = System.nanoTime() - start;
        double ms = elapsed / 1_000_000.0;

        String dimName = level.dimension().identifier().toString();
        LoomMetrics.recordDimensionTick(dimName, ms);
        loom$dimensionCount++;

        double warnMs = LoomConfig.get().dimensionTickWarnMs;
        if (ms > warnMs) {
            LOOM_LOGGER.warn("[Loom] Dimension {} took {}ms (>{}ms budget)",
                    dimName, String.format("%.2f", ms), String.format("%.0f", warnMs));
        }
    }

    /**
     * Before the levels loop: record start time and reset counter.
     */
    @Inject(method = "tickChildren",
            at = @At(value = "CONSTANT", args = "stringValue=levels"))
    private void loom$beforeLevelsLoop(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        loom$dimensionCount = 0;
        loom$tickChildrenStart = System.nanoTime();

        if (!loom$loggedStartup) {
            loom$loggedStartup = true;
            LOOM_LOGGER.info("[Loom] Server tick profiling active — tracking per-dimension timing");
            LOOM_LOGGER.info("[Loom] FastMath: {} | Parallelization: {} | Cores: {}",
                    LoomConfig.get().mathOverwritesEnabled ? "ON" : "OFF",
                    LoomConfig.get().parallelizationEnabled ? "ON" : "OFF",
                    Runtime.getRuntime().availableProcessors());
        }
    }

    /**
     * After the levels loop: record total tick-children time, update executor metrics, publish snapshot.
     */
    @Inject(method = "tickChildren",
            at = @At(value = "CONSTANT", args = "stringValue=connection"))
    private void loom$afterLevelsLoop(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        long elapsed = System.nanoTime() - loom$tickChildrenStart;
        double ms = elapsed / 1_000_000.0;

        LoomMetrics.dimensionCount = loom$dimensionCount;
        LoomMetrics.recordTickChildrenTime(ms);

        // Update executor metrics
        if (LoomAPI.isAvailable()) {
            TickingExecutor ex = LoomAPI.getExecutorInternal();
            LoomMetrics.recordExecutorStats(
                    ex.getCpuPoolActiveThreads(),
                    ex.getCpuPoolParallelism(),
                    ex.getCpuPoolStealCount(),
                    ex.getLastOptimalBatchSize()
            );
        }

        // Publish consistent snapshot for F3 screen
        LoomMetrics.publishSnapshot();

        double warnMs = LoomConfig.get().dimensionTickWarnMs;
        if (ms > warnMs) {
            LOOM_LOGGER.warn("[Loom] tickChildren ({} dimensions) took {}ms",
                    loom$dimensionCount, String.format("%.2f", ms));
        }
    }

    /**
     * Clean shutdown — shutdown executor and log final stats.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void loom$onServerStop(CallbackInfo ci) {
        LoomAPI.shutdown();

        LOOM_LOGGER.info("[Loom] Server stopping — final stats:");
        LOOM_LOGGER.info("[Loom]   Total ticks profiled: {}", LoomMetrics.getTotalTicksProfiled());
        LOOM_LOGGER.info("[Loom]   Avg server tick: {}ms", String.format("%.2f", LoomMetrics.getServerTickAvgMs()));
        LOOM_LOGGER.info("[Loom]   Peak server tick: {}ms", String.format("%.2f", LoomMetrics.getServerTickMaxMs()));
    }
}

