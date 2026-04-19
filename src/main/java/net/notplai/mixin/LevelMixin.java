package net.notplai.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.notplai.config.Config;
import net.notplai.util.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Profiling hook for Level.tickBlockEntities().
 * Optionally enables parallel block entity ticking (Phase 5, experimental).
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Unique
    private static final Logger TACHYON_LOGGER = LoggerFactory.getLogger("Tachyon/LevelMixin");

    @Shadow
    @Final
    public List<TickingBlockEntity> blockEntityTickers;

    @Unique
    private long tachyon$blockEntityTickStart;

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void tachyon$beforeTickBlockEntities(CallbackInfo ci) {
        tachyon$blockEntityTickStart = System.nanoTime();
    }

    @Inject(method = "tickBlockEntities", at = @At("RETURN"))
    private void tachyon$afterTickBlockEntities(CallbackInfo ci) {
        long elapsed = System.nanoTime() - tachyon$blockEntityTickStart;
        double ms = elapsed / 1_000_000.0;
        int count = this.blockEntityTickers.size();

        Metrics.recordBlockEntityTick(ms, count);

        double warnMs = Config.get().blockEntityTickWarnMs;
        if (ms > warnMs) {
            TACHYON_LOGGER.warn("tickBlockEntities took {}ms ({} block entities)",
                    String.format("%.2f", ms), count);
        }
    }
}
