package net.notplai.mixin;

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
 * Profiling for ServerLevel tick processing.
 * Only adds timing — does NOT modify tick behavior.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Unique
    private static final Logger LOOM_LOGGER = LoggerFactory.getLogger("Loom/ServerLevel");

    @Unique
    private long loom$tickStartNanos;

    @Inject(method = "tick", at = @At("HEAD"))
    private void loom$onTickStart(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        loom$tickStartNanos = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void loom$onTickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        long elapsed = System.nanoTime() - loom$tickStartNanos;
        double ms = elapsed / 1_000_000.0;

        // Push to metrics for F3 screen
        LoomMetrics.recordServerTick(ms);

        if (ms > 50.0) {
            ServerLevel self = (ServerLevel) (Object) this;
            LOOM_LOGGER.warn("ServerLevel.tick() took {}ms — dimension: {}",
                    String.format("%.2f", ms), self.dimension());
        }
    }
}
