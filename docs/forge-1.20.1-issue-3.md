# Forge 1.20.1 Issue #3：辐射防护与扳手标签

来源：[ICEty66 提交的 Issue #3](https://github.com/21i0/UselessStretcher/issues/3)。
审阅基线为 `1.20.1` 分支的 `825763107997f707deaf5da53b03b38a8c43ccfa`。
该基线已经合入 PR #2 及胶囊、加速修复；本次反馈是 Issue，未创建新的 PR。

## 采用的修复

- Mekanism 1.20.1 的剂量实现是 `DefaultRadiationEntity`，更新入口是 `update(LivingEntity)`。原移植保留的类名、无参更新方法和实体字段来自另一版本。现在在原生更新开始时，将开启手杖防护的玩家剂量归到基线，先于伤害和饥饿效果。
- 完整辐射抗性注入改为 `RadiationManager.getRadiationResistance(LivingEntity)` 的实例方法，阻止原生管理器新增暴露。两个注入均要求实际命中；可选模组加载判断继续保留。
- 手杖动态扳手标签补齐 Forge、c、GT 的别名，九条路径统一遵从现有 G 菜单开关。保留手杖身份和原有保存格式，不改变普通物品。

采用附件的三份 Java 修复，保留作者归属。没有采用附件中仅供其本地验证的 Forge 47.4.10 升级及 `-compat.1` 版本后缀。
本分支仍为 `20.1.6.1`，最低 Forge 仍为 47.3.10；1.21.1 主线未改动。

## 本机独立验证

环境：Minecraft 1.20.1、Forge 47.3.10、Java 17.0.20、无用之物 20.2.4.6（本体 `88bded31` 开发包）、AE2 15.4.8、GuideME 20.1.15、JEI 15.20.0.116。
专用回归 `ForgeStaffCompatChecks` 在真实 Forge 服务端中，使用生存模式 ServerPlayer、Mekanism 原生玩家能力、原生管理器及已应用 Mixin 的 ItemStack。

| 运行依赖 | 明确通过标记 | 原始日志 |
| --- | --- | --- |
| Mekanism 10.4.15.75 | 2026-10-24 09:20:40，辐射、扳手及总检查 PASSED | `staff-compat-mek101575-v1.log` |
| Mekanism 10.4.16.80 | 2026-10-24 09:24:20，辐射、扳手及总检查 PASSED | `staff-compat-mek101680-v2.log` |

辐射用例包含：默认关闭的无护甲暴露对照、开启时立即清除旧剂量、真实私有实例抗性方法返回 1、原生管理器暴露被阻止、128 次极高存量剂量在原生 update 前清除且生命值/饥饿消耗不变、随身背包携带、副手、物品 NBT 保存读取，以及关闭后恢复暴露和剂量更新。普通木棍不能通过此开关获得防护。

扳手用例覆盖以下九条路径的关闭→开启→关闭、每条路径的物品 NBT 往返、手杖身份保留，以及普通木棍标签行为不变：

```text
forge:tools/wrench       forge:tools/wrenches       forge:wrenches
c:tools/wrench           c:tools/wrenches           c:wrenches
gtceu:crafting_tools/wrench  gtceu:tools/crafting_wrenches
mekanism:configurators
```

10.4.15.75 原始 JAR SHA1：`a969ed0007ddee5bd9c93c66eb735849cf8e5224`。
10.4.16.80 从 Mekanism 官方 ModMaven 取得，原始 JAR SHA1：`b7f12ae710888b01746507a0aa8cb375f5897158`，与 Modrinth 发布记录相同。
游戏内模组版本显示为 `10.4.15`、`10.4.16`，完整构建号以这些原始依赖及校验值为准。

常规在线环境可复现：

```text
gradlew runStaffCompatCheck -PcapsuleChecks -PstaffCompatChecks -PuselessModDevJar=<本体 build/devlibs 开发包>
gradlew runStaffCompatCheck -PcapsuleChecks -PstaffCompatChecks -PmekanismCheckVersion=uxe1WQp4 -PstaffCompatDirectory=run/staff-compat-check-mek1680 -PuselessModDevJar=<本体开发包>
```

运行目录必须是已接受 EULA 的专用测试世界。此回归完成后主动停服，必须检查 `FORGE STAFF RADIATION PASSED`、`FORGE STAFF WRENCH TAGS PASSED`、`FORGE STAFF COMPAT CHECKS PASSED`，并排除 FAILED 标记；Gradle 返回 0 本身不构成通过证据。

本机第二次运行使用校验过的原始官方 JAR/POM 组成离线 Maven 目录，通过 `-PmekanismCheckRepository=<目录>` 及默认 Maven 版本 `1.20.1-10.4.16.80` 运行。没有重写第三方 JAR；临时仓库、网络缓存与依赖不提交本仓库。

## 构建与交付

两版服务端检查后，使用不带任何场景参数的普通 `gradlew build` 重编译生产代码并重新运行 JUnit。`forge1201-issue3-build-v1.log` 为 `BUILD SUCCESSFUL`；两个 JUnit XML 共六项通过，失败、错误和跳过均为 0。
构建包和交付包分别通过 `docs/audit_forge_release.py`：253 个生产 Java、438 个 class、137 个资源全部匹配；测试类/测试数据包和误打包第三方类均为 0，仅内置 MixinExtras Forge 0.5.0。

交付：`D:/AI/useless_stretcher-20.1.6.1.jar`，13,205,359 字节。
SHA256：`828602DFD03E73849E310F8C9F46A37D834487F32054A1729F475AB370817F85`。
包内版本 `20.1.6.1`，Minecraft 范围 `[1.20.1,1.20.2)`、Forge 范围 `[47.3.10,48)`；客户端和服务器须同时换成此次构建。
更新说明、使用说明、交接文档及本报告同步到 `D:/AI` 的 Forge 1.20.1 专用文件，不覆盖 1.21.1 文档或 JAR。源码提交到 `1.20.1` 分支，不创建 Release 或移动标签。

## 证据和验证边界

原始 Issue、附件及本机日志保留于 `D:/AI/archive/forge1201-issue3-20261024/`；本机通过日志和构建审计副本放在 `verification/forge1201-20261024-issue3/`。
`staff-compat-mek101680-v1.log` 是测试依赖以本地文件传入时被 Gradle 拒绝的记录，未启动游戏，不计为通过证据；v2 改用标准 Maven 模块后通过。

Issue 附件中的 20 项 GameTest 是提交者的验证记录，测试源未随附件提供，不能称为本机重跑的结果。本机新增场景以本报告列明的原生服务端检查为准。
没有穷举所有机器的扳手交互，没有本轮重跑胶囊全流程、全部 2i/剧情或完整整合包；此前胶囊和加速证据见 [移植审计报告](forge-1.20.1-port-audit.md)。
