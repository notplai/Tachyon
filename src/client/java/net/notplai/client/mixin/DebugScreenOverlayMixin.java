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
 * Injects between "Section-relative" and "Debug charts" lines.
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
        // Find the "Debug charts" line to insert before it
        int insertIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Debug charts")) {
                insertIdx = i;
                break;
            }
        }

        // Build Loom lines
        List<String> loomLines = new ArrayList<>();
        loomLines.add("\u00A7e[Loom] \u00A77Optimization Profile");
        loomLines.add(String.format("\u00A77FastMath: %d LUT entries", LoomMetrics.fastMathTableSize));
        loomLines.add(String.format("\u00A77Dimensions: \u00A7b%d \u00A77tracked", LoomMetrics.dimensionCount));

        // Per-dimension tick times
        for (Map.Entry<String, LoomMetrics.DimensionStats> entry : LoomMetrics.getDimensionStats().entrySet()) {
            LoomMetrics.DimensionStats stats = entry.getValue();
            String color = stats.avgMs > 45 ? "\u00A7c" : stats.avgMs > 25 ? "\u00A7e" : "\u00A7a";
            loomLines.add(String.format("\u00A77  %s: %s%.1fms\u00A77 avg (%.1fms peak)",
                    entry.getKey(), color, stats.avgMs, stats.maxMs));
        }

        // Block entities
        double beAvg = LoomMetrics.getBlockEntityTickAvgMs();
        String bec = beAvg > 5 ? "\u00A7c" : beAvg > 2 ? "\u00A7e" : "\u00A7a";
        loomLines.add(String.format("\u00A77Block Entities: %s%.1fms\u00A77 avg (%.1fms peak) (%d)",
                bec, beAvg, LoomMetrics.getBlockEntityTickMaxMs(), LoomMetrics.getBlockEntityCount()));

        // Server tick
        double sAvg = LoomMetrics.getServerTickAvgMs();
        String sc = sAvg > 45 ? "\u00A7c" : sAvg > 30 ? "\u00A7e" : "\u00A7a";
        loomLines.add(String.format("\u00A77Tick: %s%.1fms\u00A77 avg (%.1fms peak)",
                sc, sAvg, LoomMetrics.getServerTickMaxMs()));

        loomLines.add("\u00A77Ticks profiled: \u00A7f" + LoomMetrics.getTotalTicksProfiled());
        loomLines.add("");

        // Insert at the right position
        if (insertIdx >= 0) {
            lines.addAll(insertIdx, loomLines);
        } else {
            lines.addAll(loomLines);
        }

        original.call(instance, extractor, lines, rightAligned);
    }
}
