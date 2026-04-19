package net.notplai.test;
import net.notplai.util.FastMath;
/**
 * Unit tests for FastMath. Run as standalone: java net.notplai.test.FastMathTest
 */
public class FastMathTest {
    private static int passed = 0, failed = 0;
    static void main(String[] args) {
        System.out.println("=== FastMath Unit Tests ===");
        testSinAccuracy();
        testCosAccuracy();
        testCriticalValues();
        testFloorCeil();
        testNaNSafety();
        testInfinitySafety();
        testClamp();
        testBenchmark();
        System.out.printf("%n=== %d passed, %d failed ===%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
    static void testSinAccuracy() {
        double max = 0;
        for (double d = -12; d < 12; d += 0.001) {
            max = Math.max(max, Math.abs(FastMath.sin(d) - (float) Math.sin(d)));
        }
        check("sin max error < 0.001", max < 0.001);
    }
    static void testCosAccuracy() {
        double max = 0;
        for (double d = -12; d < 12; d += 0.001) {
            max = Math.max(max, Math.abs(FastMath.cos(d) - (float) Math.cos(d)));
        }
        check("cos max error < 0.001", max < 0.001);
    }
    static void testCriticalValues() {
        check("sin(0)==0", FastMath.sin(0) == 0f);
        check("sin(pi/2)==1", FastMath.sin(Math.PI / 2) == 1f);
        check("cos(0)==1", FastMath.cos(0) == 1f);
    }
    static void testFloorCeil() {
        check("floor(1.7)==1", FastMath.floor(1.7) == 1);
        check("floor(-1.5)==-2", FastMath.floor(-1.5) == -2);
        check("ceil(1.1)==2", FastMath.ceil(1.1) == 2);
        check("ceil(-1.5)==-1", FastMath.ceil(-1.5) == -1);
    }
    static void testNaNSafety() {
        check("sin(NaN)==0", FastMath.sin(Double.NaN) == 0f);
        check("cos(NaN)==1", FastMath.cos(Double.NaN) == 1f);
        check("floor(NaN)==0", FastMath.floor(Double.NaN) == 0);
        check("clamp(NaN,0,1)==0", FastMath.clamp(Float.NaN, 0f, 1f) == 0f);
    }
    static void testInfinitySafety() {
        check("sin(Inf)==0", FastMath.sin(Double.POSITIVE_INFINITY) == 0f);
        check("cos(Inf)==1", FastMath.cos(Double.POSITIVE_INFINITY) == 1f);
    }
    static void testClamp() {
        check("clamp(5,0,10)==5", FastMath.clamp(5, 0, 10) == 5);
        check("clamp(-1,0,10)==0", FastMath.clamp(-1, 0, 10) == 0);
        check("clamp(15,0,10)==10", FastMath.clamp(15, 0, 10) == 10);
    }
    static void testBenchmark() {
        int n = 10_000_000;
        float s = 0;
        for (int i = 0; i < n; i++) s += FastMath.sin(i * 0.001);
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) s += FastMath.sin(i * 0.001);
        long fast = System.nanoTime() - t0;
        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) s += (float) Math.sin(i * 0.001);
        long slow = System.nanoTime() - t0;
        double speedup = (double) slow / fast;
        System.out.printf("  sin speedup: %.1fx (sink=%f)%n", speedup, s);
        check("FastMath.sin >= 1.5x faster", speedup >= 1.5);
    }
    static void check(String n, boolean c) {
        if (c) { passed++; System.out.println("  OK " + n); }
        else { failed++; System.out.println("  FAIL " + n); }
    }
}
