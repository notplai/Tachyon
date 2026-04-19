package net.notplai.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.notplai.config.Config;
import net.notplai.util.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * ServerLevel optimizations:
 * <ul>
 *   <li>Per-tick profiling for metrics/F3 screen</li>
 *   <li>Explosion throttling — caps explosions per tick to prevent massive lag spikes
 *       from large TNT chains. Overflow is queued and processed in subsequent ticks.
 *       This is the same approach used by Paper/Spigot servers.</li>
 * </ul>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Unique
    private static final Logger TACHYON_LOGGER = LoggerFactory.getLogger("Tachyon/ServerLevel");

    @Unique
    private long tachyon$tickStartNanos;

    // =========================================================================
    // Explosion throttle state
    // =========================================================================

    /** Number of explosions processed in the current tick */
    @Unique
    private int tachyon$explosionsThisTick = 0;

    /** Queued explosions that exceeded the per-tick limit */
    @Unique
    private final Deque<Runnable> tachyon$explosionQueue = new ArrayDeque<>();

    // =========================================================================
    // Tick hooks
    // =========================================================================

    @Inject(method = "tick", at = @At("HEAD"))
    private void tachyon$onTickStart(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        tachyon$tickStartNanos = System.nanoTime();

        // Reset per-tick explosion counter
        tachyon$explosionsThisTick = 0;

        // Process queued explosions from previous tick(s)
        Config config = Config.get();
        int maxPerTick = config.maxExplosionsPerTick;
        if (maxPerTick <= 0) maxPerTick = Integer.MAX_VALUE;

        while (!tachyon$explosionQueue.isEmpty() && tachyon$explosionsThisTick < maxPerTick) {
            Runnable queued = tachyon$explosionQueue.poll();
            if (queued != null) {
                queued.run();
                tachyon$explosionsThisTick++;
            }
        }

        if (!tachyon$explosionQueue.isEmpty()) {
            TACHYON_LOGGER.debug("[Tachyon] {} explosions still queued for next tick", tachyon$explosionQueue.size());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void tachyon$onTickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        long elapsed = System.nanoTime() - tachyon$tickStartNanos;
        double ms = elapsed / 1_000_000.0;

        Metrics.recordServerTick(ms);

        if (ms > 50.0) {
            ServerLevel self = (ServerLevel) (Object) this;
            TACHYON_LOGGER.warn("ServerLevel.tick() took {}ms — dimension: {}",
                    String.format("%.2f", ms), self.dimension());
        }
    }

    // =========================================================================
    // Explosion throttle
    // =========================================================================

    /**
     * Intercept the main explode() method. If we've exceeded the per-tick limit,
     * queue the explosion for the next tick instead of running it immediately.
     * This converts a massive single-tick lag spike into many smaller ticks.
     */
    @Inject(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void tachyon$throttleExplosion(
            Entity source, DamageSource damageSource, ExplosionDamageCalculator damageCalculator,
            double x, double y, double z, float r, boolean fire,
            Level.ExplosionInteraction interactionType,
            ParticleOptions smallParticles, ParticleOptions largeParticles,
            WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> sound,
            CallbackInfo ci
    ) {
        Config config = Config.get();
        int maxPerTick = config.maxExplosionsPerTick;

        // 0 or negative = no limit (vanilla behavior)
        if (maxPerTick <= 0 || !config.parallelizationEnabled) {
            tachyon$explosionsThisTick++;
            return;
        }

        if (tachyon$explosionsThisTick < maxPerTick) {
            // Under limit — let it through
            tachyon$explosionsThisTick++;
            return;
        }

        // Over limit — queue for next tick
        ServerLevel self = (ServerLevel) (Object) this;
        tachyon$explosionQueue.add(() -> {
            self.explode(source, damageSource, damageCalculator, x, y, z, r, fire,
                    interactionType, smallParticles, largeParticles, blockParticles, sound);
        });

        ci.cancel();
    }
}
