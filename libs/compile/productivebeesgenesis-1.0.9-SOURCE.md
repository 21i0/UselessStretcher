# Productive Bees Genesis 1.0.9 compile-only fixture

The unmodified official JAR is used to compile optional regression checks against the real batching interface. Production integration uses reflection and has no hard runtime dependency on PBG. This dependency is compileOnly and is not included in the distributed Stretcher JAR.

- Source: https://github.com/Ayoshiko/productive-bees-genesis
- Official artifact: https://github.com/Ayoshiko/productive-bees-genesis/releases/download/v1.0.9/productivebeesgenesis-1.0.9.jar
- Size: 2,793,872 bytes
- SHA256: `CBD6280828A5AF08A92A9F857FB886B4CBC3E1C05773765AE80A12675E1FA2E5` (matches official release digest)
- License: MIT, Copyright (c) 2026 Ayoshiko. The complete original license is preserved inside the JAR as `LICENSE_productivebeesgenesis`.

The regression runtime uses separately recorded official dependency fixtures. See `verification/1.6.3-watchdog-compat-20261105.md`.
