# Tachyon

> **Tachyon** is a high-performance Fabric mod designed to optimize Minecraft by aggressively offloading heavy computational workloads to concurrent execution contexts. By leveraging modern Java concurrency models—specifically virtual threads and worker thread pools—and providing low-latency mathematical utilities, Tachyon significantly reduces main-thread stalls and enhances overall application responsiveness.

## Architecture & Features

Tachyon serves as a robust, low-overhead performance toolkit tailored for Fabric-based mods and servers. Its architecture revolves around decoupling CPU-intensive systems from the primary game thread.

### Feature Overview

| Component | Technical Description | Performance Benefit |
| :--- | :--- | :--- |
| **Virtual Thread Support** | Native support for Project Loom's virtual threads. Tasks are seamlessly scheduled off the main thread using lightweight, high-throughput execution contexts. | Massively scalable concurrency with minimal memory overhead per thread, ideal for blocking or I/O-bound tasks. |
| **Worker Thread Pools** | Traditional worker thread dispatchers for computationally intensive tasks. | Reduces main-thread blocking, smoothing out tick durations and improving server/client responsiveness. |
| **FastMath Utilities** | Optimized mathematical operations (e.g., fast `sin`, `cos`, `floor`, `ceil`) utilizing pre-computed lookup tables. | Significantly lower latency in tight loops and heavily mathematical sub-systems (like world generation or rendering). |
| **Runtime Metrics** | Built-in diagnostics for tracking thread utilization, initialization timings, and runtime behavior. | Enables developers to precisely monitor performance bottlenecks via F3 or custom diagnostic outputs. |

## Getting Started (For Developers)

Integrating Tachyon into your development environment is straightforward using standard Fabric Loom tooling.

### Building from Source

1. Clone the repository and navigate to the project root.
2. Build the project using the Gradle wrapper:

```bash
./gradlew build
```

3. The compiled mod artifact will be available in the `build/libs/` directory.

### Integration

To depend on Tachyon in your own Fabric mod, include the compiled JAR in your development environment and reference the Tachyon concurrency and `FastMath` APIs within your performance-critical code paths.

## Frequently Asked Questions (FAQ)

**Q: Are virtual threads required to use Tachyon?**
> **A:** No. While Tachyon heavily emphasizes the use of modern virtual threads for optimal scalability, it safely falls back to standard worker thread pools depending on the underlying JVM environment and configuration.

**Q: Can I use FastMath alongside standard `java.lang.Math`?**
> **A:** Yes. `FastMath` is provided as a drop-in utility class. For highly sensitive operations requiring strict IEEE 754 precision, standard `Math` may be preferable, but `FastMath` is highly recommended for game loops where speed is prioritized over micro-precision.

**Q: How does Tachyon prevent race conditions when offloading tasks?**
> **A:** Developers must still ensure thread safety when dispatching tasks. Tachyon provides the execution contexts, but game state mutation should ideally be synchronized or marshalled back to the main thread during safe tick phases.

## Contributing Guidelines

We welcome contributions from the community to help make Tachyon even faster and more robust!

### How to Contribute
1. **Fork the Repository:** Create your own fork of the Tachyon repository.
2. **Create a Feature Branch:** Branch off of `main` for your work (e.g., `git checkout -b feature/optimize-fastmath`).
3. **Write Clean, Documented Code:** Ensure any new concurrency patterns are well-documented and do not introduce regressions.
4. **Test Thoroughly:** Run the game and verify your changes under heavy load. Ensure no race conditions or thread deadlocks have been introduced.
5. **Submit a Pull Request:** Open a PR detailing the technical changes made, the rationale, and any performance benchmarks you have collected.

## Contacts

For technical inquiries, architectural discussions, or metadata changes, please reach out:

*   **Email:** me@notplai.net
*   **Discord:** notplai
*   **Instagram:** notplai

## License

This project is released under **CC0-1.0** (Creative Commons Zero v1.0 Universal). See the `LICENSE` file for more details.
