package net.notplai.mixin;

import net.minecraft.util.Mth;
import net.notplai.config.Config;
import net.notplai.util.FastMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces Minecraft's Mth.sin() and Mth.cos() with high-performance
 * lookup-table versions from FastMath and optimizes floor/ceil operations.
 * <p>
 * All overwrites are conditional: if mathOverwritesEnabled is false in config,
 * they fall back to vanilla behavior.
 * <p>
 * Mth.sin/cos are called millions of times per tick for entity movement,
 * rendering, physics, etc. This is one of the highest-impact optimizations.
 */
@Mixin(Mth.class)
public class MthMixin {

    /**
     * @author NotPlai
     * @reason Replace with fast lookup-table sin (conditional on config)
     */
    @Overwrite
    public static float sin(final double value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (float) Math.sin(value);
        }
        return FastMath.sin(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with fast lookup-table cos (conditional on config)
     */
    @Overwrite
    public static float cos(final double value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (float) Math.cos(value);
        }
        return FastMath.cos(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with branchless fast floor (conditional on config)
     */
    @Overwrite
    public static int floor(final float value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (int) Math.floor(value);
        }
        return FastMath.floor(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with branchless fast floor for double (conditional on config)
     */
    @Overwrite
    public static int floor(final double value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (int) Math.floor(value);
        }
        return FastMath.floor(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with fast long floor (conditional on config)
     */
    @Overwrite
    public static long lfloor(final double value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (long) Math.floor(value);
        }
        return FastMath.lfloor(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with branchless fast ceil for float (conditional on config)
     */
    @Overwrite
    public static int ceil(final float value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (int) Math.ceil(value);
        }
        return FastMath.ceil(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with branchless fast ceil for double (conditional on config)
     */
    @Overwrite
    public static int ceil(final double value) {
        if (!Config.get().mathOverwritesEnabled) {
            return (int) Math.ceil(value);
        }
        return FastMath.ceil(value);
    }
}
