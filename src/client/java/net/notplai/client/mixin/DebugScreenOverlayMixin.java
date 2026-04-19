package net.notplai.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.notplai.util.LoomMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adds Loom performance metrics to the F3 debug screen.
 * Uses the atomic snapshot API for thread-safe reads.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @WrapOperation(
            method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V",
                    ordinal = 0)
    )
    private void loom$addLeftSideMetrics(DebugScreenOverlay instance, GuiGraphicsExtractor extractor,
                                          List<String> lines, boolean rightAligned, Operation<Void> original) {
        // Read consistent snapshot (thread-safe, no partial state)
        LoomMetrics.MetricsSnapshot snap = LoomMetrics.getSnapshot();

        int insertIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Debug charts")) {
                insertIdx = i;
                break;
            }
        }

        List<String> loomLines = new ArrayList<>();
        loomLines.add("\u00A7e[Loom] \u00A77Performance Dashboard");
        loomLines.add(String.format("\u00A77FastMath: %d LUT entries | Init: %dµs",
                LoomMetrics.fastMathTableSize, LoomMetrics.fastMathInitTimeUs));
        loomLines.add(String.format("\u00A77Dimensions: \u00A7b%d \u00A77tracked", snap.dimensionCount));

        // Per-dimension tick times from snapshot
        for (Map.Entry<String, LoomMetrics.DimSnapshot> entry : snap.dimensions.entrySet()) {
            LoomMetrics.DimSnapshot stats = entry.getValue();
            String color = stats.avgMs > 45 ? "\u00A7c" : stats.avgMs > 25 ? "\u00A7e" : "\u00A7a";
            loomLines.add(String.format("\u00A77  %s: %s%.1fms\u00A77 avg (%.1fms peak)",
                    entry.getKey(), color, stats.avgMs, stats.maxMs));
        }

        // Block entities
        String bec = snap.blockEntityTickAvgMs > 5 ? "\u00A7c" : snap.blockEntityTickAvgMs > 2 ? "\u00A7e" : "\u00A7a";
        loomLines.add(String.format("\u00A77Block Entities: %s%.1fms\u00A77 avg (%.1fms peak) (%d)",
                bec, snap.blockEntityTickAvgMs, snap.blockEntityTickMaxMs, snap.blockEntityCount));

        // Server tick
        String sc = snap.serverTickAvgMs > 45 ? "\u00A7c" : snap.serverTickAvgMs > 30 ? "\u00A7e" : "\u00A7a";
        loomLines.add(String.format("\u00A77Tick: %s%.1fms\u00A77 avg (%.1fms peak)",
                sc, snap.serverTickAvgMs, snap.serverTickMaxMs));

        // Executor stats
        if (snap.executorParallelism > 0) {
            loomLines.add(String.format("\u00A77Executor: \u00A7b%d/%d\u00A77 active | steals: %d | batch: %s",
                    snap.executorActiveThreads, snap.executorParallelism, snap.executorStealCount,
                    snap.executorBatchSize >= 0 ? String.valueOf(snap.executorBatchSize) : "auto"));
            if (snap.circuitBreakerTrips > 0) {
                loomLines.add(String.format("\u00A7c  Circuit breaker trips: %d", snap.circuitBreakerTrips));
            }
        }

        loomLines.add("\u00A77Ticks profiled: \u00A7f" + snap.totalTicksProfiled);
        loomLines.add("");

        if (insertIdx >= 0) {
            lines.addAll(insertIdx, loomLines);
        } else {
            lines.addAll(loomLines);
        }

        original.call(instance, extractor, lines, rightAligned);
    }
}
