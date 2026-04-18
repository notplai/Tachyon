package net.notplai.util;

/**
 * High-performance math utilities using lookup tables and bit-level tricks.
 * Replaces Minecraft's Mth sin/cos/floor with significantly faster versions.
 */
public final class FastMath {

    private FastMath() {}

    // --- Sin/Cos lookup table ---
    private static final int SIN_BITS = 16; // 65536 entries — matches vanilla table size
    private static final int SIN_COUNT = 1 << SIN_BITS;
    private static final int SIN_MASK = SIN_COUNT - 1;
    private static final int COS_OFFSET = SIN_COUNT / 4; // 16384
    private static final double SIN_SCALE = SIN_COUNT / (Math.PI * 2.0); // 10430.378...
    private static final float[] SIN_TABLE = new float[SIN_COUNT];

    static {
        for (int i = 0; i < SIN_COUNT; i++) {
            SIN_TABLE[i] = (float) Math.sin(i / SIN_SCALE);
        }
        // Fix critical values to be exact
        SIN_TABLE[0] = 0.0f;                            // sin(0)
        SIN_TABLE[SIN_COUNT / 4] = 1.0f;                // sin(π/2)
        SIN_TABLE[SIN_COUNT / 2] = 0.0f;                // sin(π)
        SIN_TABLE[SIN_COUNT * 3 / 4] = -1.0f;           // sin(3π/2)
    }

    /**
     * Fast Sine using lookup table. Matches vanilla Mth.sin(double) signature.
     * Uses long cast and mask like vanilla but with our corrected table.
     */
    public static float sin(double rad) {
        return SIN_TABLE[(int) ((long) (rad * SIN_SCALE) & SIN_MASK)];
    }

    /**
     * Fast Cosine using lookup table with phase offset. Matches vanilla Mth.cos(double).
     */
    public static float cos(double rad) {
        return SIN_TABLE[(int) ((long) (rad * SIN_SCALE + COS_OFFSET) & SIN_MASK)];
    }

    /**
     * Fast Floor for float. Avoids the double conversion of Math.floor().
     */
    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /**
     * Fast Floor for double.
     */
    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /**
     * Fast Floor returning long for large values.
     */
    public static long lfloor(double value) {
        long l = (long) value;
        return value < l ? l - 1L : l;
    }

    /**
     * Fast Ceil for float.
     */
    public static int ceil(float value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    /**
     * Fast Ceil for double.
     */
    public static int ceil(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    // --- Fast inverse square root (Quake III style, refined) ---

    /**
     * Fast approximate inverse square root (1/√x).
     */
    public static float inverseSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToRawIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x *= (1.5f - xhalf * x * x);
        x *= (1.5f - xhalf * x * x);
        return x;
    }

    /**
     * Fast approximate square root using inverseSqrt.
     */
    public static float sqrt(float x) {
        if (x <= 0f) return 0f;
        return x * inverseSqrt(x);
    }

    // --- Clamping ---

    public static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    public static float clamp(float value, float min, float max) {
        return Math.clamp(value, min, max);
    }

    public static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    // --- Linear interpolation ---

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    // --- Utility ---

    /**
     * Wraps degrees to [-180, 180).
     */
    public static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}

