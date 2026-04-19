package net.notplai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime configuration for Tachyon.
 * Stored as JSON in config/tachyon.json.
 * All fields are hot-readable (volatile backing) but only written on load/save.
 */
public final class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger("Tachyon/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "tachyon-config.json";

    // Singleton
    private static volatile Config INSTANCE;


    /** Master toggle for all parallelization features */
    public boolean parallelizationEnabled = true;

    /** Maximum number of platform threads for the CPU-bound ForkJoinPool */
    public int maxParallelism = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    /** Whether FastMath overwrites are active (Mth sin/cos/floor replacements) */
    public boolean mathOverwritesEnabled = true;

    /** Size of the sin/cos LUT as a power of 2 (e.g., 16 means 2^16 = 65536 entries) */
    public int sinTableBits = 16;

    /** NaN/Infinity safety checks in FastMath */
    public boolean mathSafetyChecks = true;

    /** Logging threshold: only log dimension ticks exceeding this many ms */
    public double dimensionTickWarnMs = 50.0;

    /** Logging threshold: only log block entity ticks exceeding this many ms */
    public double blockEntityTickWarnMs = 5.0;

    /** Minimum items before parallelization kicks in */
    public int parallelThreshold = 4;

    /** Circuit breaker: max ms to wait for a worker task before timing out */
    public long workerTimeoutMs = 100;

    /** Enable adaptive batch sizing based on live metrics */
    public boolean adaptiveBatchSizing = true;

    /** Enable parallel block entity ticking (Phase 5 - experimental) */
    public boolean parallelBlockEntityTicking = false;

    /** Enable optimized explosion calculations (block state caching + pre-computed rays) */
    public boolean optimizedExplosions = true;

    /** Max explosions processed per tick per dimension. Overflow queued for next tick.
     *  0 or negative = unlimited (vanilla behavior). 100 works well for most servers. */
    public int maxExplosionsPerTick = 100;

    /** Enable async chunk I/O offloading */
    public boolean asyncChunkIO = true;

    /** Metrics snapshot interval in ticks (how often to publish a consistent snapshot) */
    public int metricsSnapshotIntervalTicks = 1;


    public static Config get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static synchronized void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(FILE_NAME);

        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                INSTANCE = GSON.fromJson(json, Config.class);
                LOGGER.info("Config loaded from {}", configFile);
            } catch (Exception e) {
                LOGGER.error("Failed to load config, using defaults", e);
                INSTANCE = new Config();
            }
        } else {
            INSTANCE = new Config();
            save(); // Write defaults
        }
    }

    public static synchronized void save() {
        if (INSTANCE == null) INSTANCE = new Config();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDir);
            Files.writeString(configFile, GSON.toJson(INSTANCE));
            LOGGER.info("[Tachyon] Config saved to {}", configFile);
        } catch (IOException e) {
            LOGGER.error("[Tachyon] Failed to save config", e);
        }
    }

    public static void reload() {
        load();
    }
}

