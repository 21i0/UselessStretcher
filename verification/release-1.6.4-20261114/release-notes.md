# 万象担架 1.6.4 · Minecraft 1.21.1 / NeoForge

正式文件名：`useless_stretcher-1.21.1-1.6.4.jar`。客户端与服务端须同时更新。

- 适配无用之物 `1.21.1-2.4.6.6`、AE2 `19.2.19` 与 JEI `19.70.0.469`，补齐新版本的目录预算和 JEI 集成门控。
- 样板总成装入担架后保存独立的 UUID 与样板库快照，后续担架或源万物之块修改不会覆盖已经装入的库。
- 万象目录分批收集、转换与发布，加入适配器故障隔离；目录 HUD 调整为中文提示，避免重叠。
- 胶囊新生成结构保留重力方块的稳定状态，避免陨石中的砂砾集中变为下落实体。相关持久化记录按房间隔离，并保留异常状态以供检查。
- 同一胶囊实例继续使用已有建筑与战利品状态；生成相邻实例才创建新建筑。升级不会自动覆盖已经完成的旧房间或玩家改建。

升级前请备份整个存档。1.6.4 的样板库与胶囊持久化更新不支持用旧版 JAR 原地降级；需要回退时一并恢复升级前的完整备份。

真实最小客户端已通过 18 项普通检查：本体 G、担架 X、主副手与混持、正常 F 换手和热键切槽后重开、实际 JEI 目录，以及下界往返后的布局同步和服务端倍率修改（分别改为 16、64）。测试使用本页列出的新依赖，正常保存退出。胶囊沿用未改动 E1 代码的一间完整陨石和两次生产入口往返证据。

已知边界：设置窗口按打开时的手定位；窗口打开期间若第三方从服务端强制替换同一手中的另一把同类杖，设置会作用于当前该手里的杖。换手或切换物品后请关闭并重新打开设置。本轮没有覆盖零 Tick 输入竞态、远程多人或全部模组结构，也没有将完整源码重新 clean build；精确继承和增量编译记录见源码中的发布验证资料。

---

Official file: `useless_stretcher-1.21.1-1.6.4.jar`. Update both client and server.

- Adds exact-version support for Useless Mod `1.21.1-2.4.6.6` and JEI `19.70.0.469`, alongside AE2 `19.2.19`.
- An inserted stretcher keeps its own assembly identity and pattern snapshot.
- Budgets catalog collection, conversion and publication, and isolates failing adapters.
- Stabilizes gravity blocks in newly generated capsule structures and separates recovery records per room.
- Keeps completed structures, edits and loot state when revisiting an existing instance.

Back up the entire world before upgrading. To downgrade, restore the complete backup as well as the older JAR.

The actual integrated-client smoke passed 18 ordinary checks, including G/X controls, mixed hands, swapping, hotbar changes, JEI and both dimension transfers. This does not claim remote-multiplayer or arbitrary full-pack coverage. Close and reopen settings after changing held items: an artificially replaced same-type staff in an already-open hand-bound screen receives that hand's edits.
