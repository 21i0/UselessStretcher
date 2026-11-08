# Forge 1.20.1 release evidence backup

This is a sanitized text backup prepared on 2026-11-08 before local build cleanup.
Production release: 20.1.6.5, source d344267a4036b6dcb01a43f4d96d4c5d664798fe.
Official dependency: Useless Mod 20.2.4.9; AE2 15.4.8 and matching GuideME 20.x.

The release build and 27 fresh JUnit tests ran on 2026-11-02. Real Forge server,
restart, UI, and meteorite round-trip checks ran during the 2026-10-29 PR review.
The two release JARs have identical uncompressed content; ZIP timestamps differ.
Historical folders document older versions and must not be reported as new tests.
No Minecraft instance or repeated regression test was started during cleanup.

CurseForge file 9404126 was already released on 2026-11-02. The 2026-11-08
download verification matches the local formal JAR. Its missing changelog,
required AE2/GuideME relations, and server environment label were repaired.

Local usernames/home paths, workspace paths, IP addresses, UUIDs, email addresses,
and sensitive query parameters are redacted. No player saves, tokens, accounts,
third-party binaries, dependency caches, or generated worlds are included.
The manifest records original and redacted-file hashes for traceability.

Reproduction: use Java 17 and the README's Gradle tasks. Build with `gradlew build`;
server checks use `-PupstreamChecks runUpstreamServerCheck`, and repeat with the
same dedicated test directory to verify persisted installed-pattern state.
Never point these scenarios at a player's production world.

Coverage excludes exhaustive third-party structures, the full modpack, and all
forced-kill weapons. The separate Ever Love Sword 2.2 run failed to start and
must not be described as passing protection tests.
