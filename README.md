# Tachyon
<p>
  <img alt="GitHub License" src="https://img.shields.io/github/license/notplai/Tachyon?style=for-the-badge">
  <img alt="GitHub branch check runs" src="https://img.shields.io/github/check-runs/notplai/Tachyon%2F26.1/indev?style=for-the-badge">
  <img alt="GitHub last commit (branch)" src="https://img.shields.io/github/last-commit/notplai/Tachyon%2F26.1/indev?style=for-the-badge">
  <img alt="GitHub forks" src="https://img.shields.io/github/forks/notplai/Tachyon?style=for-the-badge">
</p>

**Tachyon** is a high performance mod designed to optimize Minecraft by aggressively offloading heavy computational workloads to concurrent execution contexts. By leveraging modern Java concurrency models specifically virtual threads and worker thread pools and providing low latency mathematical utilities, Tachyon significantly reduces main thread stalls and enhances overall application responsiveness.

## Architecture & Features

Tachyon serves as a robust, low-overhead performance toolkit tailored for Fabric based mods and servers. Its architecture revolves around decoupling CPU intensive systems from the primary game thread.

### Feature Overview

| Component                  | Technical Description                                                                                                                                           | Performance Benefit                                                                                                   |
|:---------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------|
| **Virtual Thread Support** | Native support for Project Tachyon's virtual threads. Tasks are seamlessly scheduled off the main thread using lightweight, high-throughput execution contexts. | Massively scalable concurrency with minimal memory overhead per thread, ideal for blocking or I/O-bound tasks.        |
| **Worker Thread Pools**    | Traditional worker thread dispatchers for computationally intensive tasks.                                                                                      | Reduces main thread blocking, smoothing out tick durations and improving server/client responsiveness.                |
| **FastMath Utilities**     | Optimized mathematical operations (e.g., fast `sin`, `cos`, `floor`, `ceil`) utilizing pre-computed lookup tables.                                              | Significantly lower latency in tight loops and heavily mathematical sub-systems (like world generation or rendering). |

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

To depend on Tachyon in your own Fabric mod, include the compiled JAR in your development environment and reference the Tachyon concurrency and `FastMath` APIs within your performance critical code paths.

## Contributing Guidelines

We welcome contributions from the community to help make Tachyon even faster and more robust!

### How to Contribute
1. **Fork the Repository:** Create your own fork of the Tachyon repository.
2. **Create a Feature Branch:** Branch off of `main` for your work (e.g., `git checkout -b feature/optimize-fastmath`).
3. **Submit a Pull Request:** Open a PR detailing the technical changes made, the rationale, and any performance benchmarks you have collected.

## License

This project is licensed under the [**Apache License 2.0**](LICENSE).
