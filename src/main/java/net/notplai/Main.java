package net.notplai;

import net.fabricmc.api.ModInitializer;
import net.notplai.util.FastMath;
import net.notplai.util.LoomMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
    public static final String MOD_ID = "loom";
    public static final Logger LOGGER = LoggerFactory.getLogger("Loom");

    @Override
    public void onInitialize() {
        LOGGER.info("""
                        Loom here! Informed Runtime:
                           Java version: {}
                           Available processors: {}
                           Max memory: {}MB""",

                Runtime.version(),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / 1024 / 1024
        );

        // Warm up and verify FastMath lookup tables
        long warmStart = System.nanoTime();
        float sinZero = FastMath.sin(0.0);
        float cosZero = FastMath.cos(0.0);
        float sinHalfPi = FastMath.sin(Math.PI / 2.0);
        float cosHalfPi = FastMath.cos(Math.PI / 2.0);
        long warmElapsed = System.nanoTime() - warmStart;

        // Record metrics for F3 display
        LoomMetrics.fastMathEnabled = true;
        LoomMetrics.fastMathTableSize = 1 << 16;
        LoomMetrics.fastMathInitTimeUs = warmElapsed / 1000;

        LOGGER.info("""
                    [FastMath] Initialized ({} entries) in {}µs:
                        sin(0)={}, cos(0)={}, sin(π/2)={}, cos(π/2)={}
                        floor(-1.5)={}, ceil(-1.5)={}, floor(1.7)={}, ceil(1.7)={}""",

                1 << 16, warmElapsed / 1000,
                sinZero,
                cosZero,
                sinHalfPi,
                cosHalfPi,
                FastMath.floor(-1.5),
                FastMath.ceil(-1.5),
                FastMath.floor(1.7),
                FastMath.ceil(1.7)
        );

        LOGGER.info("[Profiling] Per-dimension tick profiling: ENABLED");
        LOGGER.info("[Profiling] Block entity tick profiling: ENABLED");
        LOGGER.info("[Profiling] F3 debug overlay: ENABLED");
        LOGGER.info("[System] Available CPU cores: {}", Runtime.getRuntime().availableProcessors());
    }
}
