# LoomProject

LoomProject is a Fabric mod that improves Minecraft performance by offloading heavy work to concurrent threads (including support for modern virtual threads) and by providing high-performance math utilities (FastMath) to reduce CPU overhead for common math operations.

Summary
- Offloads CPU-intensive systems to worker threads / virtual threads to reduce main-thread stalls and improve responsiveness.
- Provides FastMath utilities (fast sin/cos, floor/ceil, etc.) backed by lookup tables for lower-latency math operations.
- Aims to be a low-overhead, modular performance toolkit for Fabric-based mods and servers.

Features
- Worker and virtual-thread helpers to schedule and run tasks off the main game thread.
- FastMath: lookup-table-based implementations for common math operations to speed tight loops.
- Metrics for monitoring initialization and runtime behavior (useful for F3 / diagnostics).

Getting started
1. Build with Gradle (standard Fabric Loom project):

```bash
./gradlew build
```

2. Put the generated mod jar from `build/libs/` into your Minecraft `mods/` folder.

Contacts
- Email: me@notplai.net
- Discord: notplai
- Instagram: notplai

License
This project is released under CC0-1.0. See the `LICENSE` file for details.

If you find any issues or need changes to naming/metadata, contact me at the addresses above.
