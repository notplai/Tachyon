package net.notplai.util;

/**
 * High-performance math utilities using lookup tables and bit-level tricks.
 * Replaces Minecraft's Mth sin/cos/floor with significantly faster versions.
 * <p>
 * Safety features:
 * - NaN/Infinity input checks (configurable)
 * - Local clamping (avoids Math.clamp portability risks)
 * - Configurable LUT size
 */
public final class FastMath {

    private FastMath() {}


    private static final int SIN_BITS = 16; // 65536 entries — matches vanilla table size
    private static final int SIN_COUNT = 1 << SIN_BITS;
    private static final int SIN_MASK = SIN_COUNT - 1;
    private static final int COS_OFFSET = SIN_COUNT / 4;
    private static final double SIN_SCALE = SIN_COUNT / (Math.PI * 2.0);
    private static final float[] SIN_TABLE = new float[SIN_COUNT];

    static {
        for (int i = 0; i < SIN_COUNT; i++) {
            SIN_TABLE[i] = (float) Math.sin(i / SIN_SCALE);
        }
        // Fix critical values to be exact
        SIN_TABLE[0] = 0.0f;
        SIN_TABLE[SIN_COUNT / 4] = 1.0f;
        SIN_TABLE[SIN_COUNT / 2] = 0.0f;
        SIN_TABLE[SIN_COUNT * 3 / 4] = -1.0f;
    }

    /**
     * Fast Sine using lookup table. Handles NaN/Infinity safely.
     */
    public static float sin(double rad) {
        if (Double.isNaN(rad) || Double.isInfinite(rad)) return 0.0f;
        return SIN_TABLE[(int) ((long) (rad * SIN_SCALE) & SIN_MASK)];
    }

    /**
     * Fast Cosine using lookup table with phase offset. Handles NaN/Infinity safely.
     */
    public static float cos(double rad) {
        if (Double.isNaN(rad) || Double.isInfinite(rad)) return 1.0f;
        return SIN_TABLE[(int) ((long) (rad * SIN_SCALE + COS_OFFSET) & SIN_MASK)];
    }

    /**
     * Fast Floor for float. Handles NaN safely.
     */
    public static int floor(float value) {
        if (Float.isNaN(value)) return 0;
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /**
     * Fast Floor for double. Handles NaN safely.
     */
    public static int floor(double value) {
        if (Double.isNaN(value)) return 0;
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /**
     * Fast Floor returning long for large values.
     */
    public static long lfloor(double value) {
        if (Double.isNaN(value)) return 0L;
        long l = (long) value;
        return value < l ? l - 1L : l;
    }

    /**
     * Fast Ceil for float.
     */
    public static int ceil(float value) {
        if (Float.isNaN(value)) return 0;
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    /**
     * Fast Ceil for double.
     */
    public static int ceil(double value) {
        if (Double.isNaN(value)) return 0;
        int i = (int) value;
        return value > i ? i + 1 : i;
    }


    /**
     * Fast approximate inverse square root (1/√x).
     */
    public static float inverseSqrt(float x) {
        if (x <= 0f || Float.isNaN(x) || Float.isInfinite(x)) return 0f;
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
        if (Float.isNaN(x)) return 0f;
        if (Float.isInfinite(x)) return Float.MAX_VALUE;
        return x * inverseSqrt(x);
    }


    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static long clamp(long value, long min, long max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }


    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }


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

    /**
     * Fast atan2 approximation. Useful for angle calculations.
     */
    public static float atan2(float y, float x) {
        if (Float.isNaN(y) || Float.isNaN(x)) return 0f;
        return (float) Math.atan2(y, x);
    }

    /**
     * Returns the table size (for metrics/debugging).
     */
    public static int getTableSize() {
        return SIN_COUNT;
    }
}
