# Local test note for the final candidate

Candidate: `useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1.jar`

SHA-256: `3AEB59B7AAD8A2BF50D203BB220192FF45D90D0C50F80F6D8878514249F2F6EF`.

The candidate changes the JEI category timing injection selector and displayed mod version. It preserves all 680 other JAR members byte for byte, including the assembly UUID lock and installed snapshot implementation. Offline true Sponge checks cover both JEI variants and the known three external `RecipesGui` Mixins; full modpack startup and live UI behavior still require a game test.

For the game test, replace the old Stretcher candidate so only one Stretcher JAR is installed. Keep the intended Useless Mod 2.4.6.5 / JEI 19.70.0.460 combination and the existing modpack dependencies. Retain the previous logs before launching.

Verify that startup passes the former `CatalogJeiBuildTimingMixin` zero-target crash, then open a world and use JEI recipe display, category changes, back/forward, and the Stretcher catalog loading UI. Save the new `latest.log` and any crash report with this JAR's hash. Check the visible version ends in `-sponge1`.

For the assembly lock requirement, an existing installed stretcher must retain its installed UUID and pattern library after another stretcher is presented or its source Myriad Block changes. This revision does not alter that logic; preservation of bytes is not a substitute for a new live behavior check.

No commit, push, Release, or CurseForge upload is part of this candidate audit.
