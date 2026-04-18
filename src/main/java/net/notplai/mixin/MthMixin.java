package net.notplai.mixin;

import net.minecraft.util.Mth;
import net.notplai.util.FastMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces Minecraft's Mth.sin() and Mth.cos() with high-performance
 * lookup-table versions from FastMath and optimizes floor/ceil operations.
 * <p>
 * Mth.sin/cos are called millions of times per tick for entity movement,
 * rendering, physics, etc. This is one of the highest-impact optimizations.
 */
@Mixin(Mth.class)
public class MthMixin {

    /**
     * @author NotPlai
     * @reason Replace it with fast lookup-table sin (corrected critical values)
     */
    @Overwrite
    public static float sin(final double value) {
        return FastMath.sin(value);
    }

    /**
     * @author NotPlai
     * @reason Replace it with fast lookup-table cos (corrected critical values)
     */
    @Overwrite
    public static float cos(final double value) {
        return FastMath.cos(value);
    }

    /**
     * @author NotPlai
     * @reason Replace it with a branchless fast floor (avoids Math.floor double conversion)
     */
    @Overwrite
    public static int floor(final float value) {
        return FastMath.floor(value);
    }

    /**
     * @author NotPlai
     * @reason Replace it with a branchless fast floor for double
     */
    @Overwrite
    public static int floor(final double value) {
        return FastMath.floor(value);
    }

    /**
     * @author NotPlai
     * @reason Replace it with a fast long floor
     */
    @Overwrite
    public static long lfloor(final double value) {
        return FastMath.lfloor(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with branchless fast ceil for float
     */
    @Overwrite
    public static int ceil(final float value) {
        return FastMath.ceil(value);
    }

    /**
     * @author NotPlai
     * @reason Replace with branchless fast ceil for double
     */
    @Overwrite
    public static int ceil(final double value) {
        return FastMath.ceil(value);
    }
}
