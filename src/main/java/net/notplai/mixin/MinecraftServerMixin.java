package net.notplai.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * Loom Server Tick Profiling & Optimization.
 *
 * Why NOT parallel dimension ticking:
 *   ServerLevel.tick() calls getChunkSource().tick(haveTime) which schedules
 *   tasks back onto the main server thread. If dimension ticks run on worker
 *   threads while the main thread is blocked waiting (future.get()), we get a
 *   deadlock — chunks never load, and the game freezes on quit.
 *   Minecraft's profiler (Profiler.get()) is also not thread-safe.
 *
 * What we DO:
 *   - Per-dimension tick timing with rolling averages
 *   - Spike detection and logging
 *   - Full tick-children timing (all dimensions + overhead)
 *   - Clean server shutdown handling
 *   - FastMath (via MthMixin) provides real math acceleration
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
     * Wrap each ServerLevel.tick() call to add per-dimension timing.
     * The tick still runs on the server thread (safe), but we measure it.
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

        // Track per-dimension timing
        String dimName = level.dimension().identifier().toString();
        LoomMetrics.recordDimensionTick(dimName, ms);
        loom$dimensionCount++;

        if (ms > 50.0) {
            LOOM_LOGGER.warn("[Loom] Dimension {} took {}ms (>50ms tick budget)",
                    dimName, String.format("%.2f", ms));
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
            LOOM_LOGGER.info("[Loom] FastMath LUT: {} | Profiling: {} | Cores: {}",
                    LoomMetrics.fastMathEnabled ? "ENABLED" : "DISABLED",
                    "ENABLED",
                    Runtime.getRuntime().availableProcessors());
        }
    }

    /**
     * After the levels loop: record total tick-children time.
     */
    @Inject(method = "tickChildren",
            at = @At(value = "CONSTANT", args = "stringValue=connection"))
    private void loom$afterLevelsLoop(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        long elapsed = System.nanoTime() - loom$tickChildrenStart;
        double ms = elapsed / 1_000_000.0;

        LoomMetrics.dimensionCount = loom$dimensionCount;
        LoomMetrics.recordTickChildrenTime(ms);

        if (ms > 50.0) {
            LOOM_LOGGER.warn("[Loom] tickChildren ({} dimensions) took {}ms",
                    loom$dimensionCount, String.format("%.2f", ms));
        }
    }

    /**
     * Clean shutdown — log final stats.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void loom$onServerStop(CallbackInfo ci) {
        LOOM_LOGGER.info("[Loom] Server stopping — final stats:");
        LOOM_LOGGER.info("[Loom]   Total ticks profiled: {}", LoomMetrics.getTotalTicksProfiled());
        LOOM_LOGGER.info("[Loom]   Avg server tick: {}ms", String.format("%.2f", LoomMetrics.getServerTickAvgMs()));
        LOOM_LOGGER.info("[Loom]   Peak server tick: {}ms", String.format("%.2f", LoomMetrics.getServerTickMaxMs()));
        LOOM_LOGGER.info("[Loom]   FastMath enabled: {}", LoomMetrics.fastMathEnabled);
    }
}

