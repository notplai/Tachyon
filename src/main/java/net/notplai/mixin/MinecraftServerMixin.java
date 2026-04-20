package net.notplai.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.notplai.api.TachyonAPI;
import net.notplai.concurrent.TickingExecutor;
import net.notplai.config.Config;
import net.notplai.util.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Tachyon Server Tick Profiling, Executor Lifecycle & Optimization.
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
    private static final Logger TACHYON_LOGGER = LoggerFactory.getLogger("Tachyon/Server");

    @Unique
    private long tachyon$tickChildrenStart;

    @Unique
    private int tachyon$dimensionCount;

    @Unique
    private boolean tachyon$loggedStartup = false;

    /**
     * Initialize executor on server start.
     */
    @Inject(method = "runServer", at = @At("HEAD"))
    private void tachyon$onServerStart(CallbackInfo ci) {
        Config.load();
        Config config = Config.get();

        if (config.parallelizationEnabled) {
            TickingExecutor exec = new TickingExecutor("server");
            TachyonAPI.initialize(exec);
            TACHYON_LOGGER.info("TickingExecutor initialized. parallelism: {}, adaptive: {}",
                    config.maxParallelism, config.adaptiveBatchSizing);
        } else {
            TACHYON_LOGGER.info("Parallelization disabled by config");
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
    private void tachyon$profileDimensionTick(ServerLevel level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        long start = System.nanoTime();

        original.call(level, hasTimeLeft);

        long elapsed = System.nanoTime() - start;
        double ms = elapsed / 1_000_000.0;

        String dimName = level.dimension().identifier().toString();
        Metrics.recordDimensionTick(dimName, ms);
        tachyon$dimensionCount++;

        double warnMs = Config.get().dimensionTickWarnMs;
        if (ms > warnMs) {
            TACHYON_LOGGER.warn("Dimension {} took {}ms (>{}ms budget)",
                    dimName, String.format("%.2f", ms), String.format("%.0f", warnMs));
        }
    }

    /**
     * Before the levels loop: record start time and reset counter.
     */
    @Inject(method = "tickChildren",
            at = @At(value = "CONSTANT", args = "stringValue=levels"))
    private void tachyon$beforeLevelsLoop(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        tachyon$dimensionCount = 0;
        tachyon$tickChildrenStart = System.nanoTime();

        if (!tachyon$loggedStartup) {
            tachyon$loggedStartup = true;
            TACHYON_LOGGER.info("Server tick profiling active. Tracking per-dimension timing");
            TACHYON_LOGGER.info("FastMath: {}, Parallelization: {}, Cores: {}",
                    Config.get().mathOverwritesEnabled ? "ON" : "OFF",
                    Config.get().parallelizationEnabled ? "ON" : "OFF",
                    Runtime.getRuntime().availableProcessors());
        }
    }

    /**
     * After the levels loop: record total tick-children time, update executor metrics, publish snapshot.
     */
    @Inject(method = "tickChildren",
            at = @At(value = "CONSTANT", args = "stringValue=connection"))
    private void tachyon$afterLevelsLoop(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        long elapsed = System.nanoTime() - tachyon$tickChildrenStart;
        double ms = elapsed / 1_000_000.0;

        Metrics.dimensionCount = tachyon$dimensionCount;
        Metrics.recordTickChildrenTime(ms);

        // Update executor metrics
        if (TachyonAPI.isAvailable()) {
            TickingExecutor ex = TachyonAPI.getExecutorInternal();
            Metrics.recordExecutorStats(
                    ex.getCpuPoolActiveThreads(),
                    ex.getCpuPoolParallelism(),
                    ex.getCpuPoolStealCount(),
                    ex.getLastOptimalBatchSize()
            );
        }

        // Publish consistent snapshot for F3 screen
        Metrics.publishSnapshot();

        double warnMs = Config.get().dimensionTickWarnMs;
        if (ms > warnMs) {
            TACHYON_LOGGER.warn("tickChildren ({} dimensions) took {}ms",
                    tachyon$dimensionCount, String.format("%.2f", ms));
        }
    }

    /**
     * Clean shutdown — shutdown executor and log final stats.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void tachyon$onServerStop(CallbackInfo ci) {
        TachyonAPI.shutdown();

        TACHYON_LOGGER.info("Server stopping. final stats:");
        TACHYON_LOGGER.info("   Total ticks profiled: {}", Metrics.getTotalTicksProfiled());
        TACHYON_LOGGER.info("   Avg server tick: {}ms", String.format("%.2f", Metrics.getServerTickAvgMs()));
        TACHYON_LOGGER.info("   Peak server tick: {}ms", String.format("%.2f", Metrics.getServerTickMaxMs()));
    }
}

