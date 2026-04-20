package net.notplai;

import net.fabricmc.api.ModInitializer;
import net.notplai.config.Config;
import net.notplai.util.FastMath;
import net.notplai.util.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
    public static final String MOD_ID = "tachyon";
    public static final Logger LOGGER = LoggerFactory.getLogger("Tachyon");

    @Override
    public void onInitialize() {
        // Load config
        Config.load();
        Config config = Config.get();

        LOGGER.info("""
                        Tachyon here! Informed Runtime:
                           Java version: {}
                           Available processors: {}
                           Max memory: {}MB
                           Parallelization: {}
                           Math Overwrites: {}
                           Max Parallelism: {}""",
                Runtime.version(),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / 1024 / 1024,
                config.parallelizationEnabled,
                config.mathOverwritesEnabled,
                config.maxParallelism
        );

        // Warm up and verify FastMath lookup tables
        long warmStart = System.nanoTime();
        float sinZero = FastMath.sin(0.0);
        float cosZero = FastMath.cos(0.0);
        float sinHalfPi = FastMath.sin(Math.PI / 2.0);
        float cosHalfPi = FastMath.cos(Math.PI / 2.0);
        // Test NaN safety
        float sinNaN = FastMath.sin(Double.NaN);
        float cosInf = FastMath.cos(Double.POSITIVE_INFINITY);
        long warmElapsed = System.nanoTime() - warmStart;

        Metrics.fastMathTableSize = FastMath.getTableSize();
        Metrics.fastMathInitTimeUs = warmElapsed / 1000;

        LOGGER.info("""
                    [FastMath] Initialized ({} entries) in {}µs:
                        sin(0)={}, cos(0)={}, sin(π/2)={}, cos(π/2)={}
                        sin(NaN)={}, cos(∞)={}""",
                FastMath.getTableSize(), warmElapsed / 1000,
                sinZero, cosZero, sinHalfPi, cosHalfPi,
                sinNaN, cosInf
        );
    }
}
