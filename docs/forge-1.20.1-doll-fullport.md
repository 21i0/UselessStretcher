# Forge 1.20.1 · 20.1.6.2：2i 玩偶与六章剧情全量移植

本轮基线为 `1.20.1` 的 `8d256c9`，对照主线 `af135d4` 的玩偶实现。保留 PR #2、胶囊／加速修复和 Issue #3 的 MEK／扳手兼容；没有改动 1.21.1 工作树。本次对齐的是 2i 与剧情，未将主线后来新增的其他 AE／手杖功能一并宣称为已移植。

## 官方前置

- Minecraft 1.20.1、Forge 47.3.10、Java 17.0.20。
- 无用之物 [官方 20.2.4.6](https://github.com/SorrowMist/UselessMod/releases/tag/20.2.4.6)，源码标签解引用到 `9c10d0e314a21317663eb1b75853dcc2153d5dae`；使用真实发布 JAR，未修改或自行构建本体。
- 官方 JAR：4,231,988 字节，SHA256 `8C7E7A9B30749D313A25D8D56BAF453D0E2F4651285438E77662DEAF3D2ABC8B`。
- AE2 15.4.8、GuideME 20.1.15、JEI 15.20.0.116；可选 Curios 5.14.1。
- 防收容组合另装 Ars Nouveau 4.12.7、Productive Bees 12.6.0、GeckoLib 4.8.2。

Gradle 默认从本体 GitHub Release 下载正式包并映射开发 API；CI 不再检出第三方未发布分支来构建前置。`uselessModDevJar` 仅保留为明确的开发覆盖项。

## 实际补齐和修复

1. 六章长篇分支剧情、有声音字效果、逐字／自动／快进、章节回顾、17 张图片欣赏、星空庭院与邀请开关完整保留；所有玩偶形态共用新皮肤、骨架、尾巴、眨眼和天气动作。
2. 补齐主线 Alex 细臂／袖子、握伞角度、固定鼻根并向下生长的冷天鼻涕。手持、第三人称、头戴、摆件及 Curios 保持一致。
3. 修复 Forge 挖掘实际调用带 `dropXp` 参数的掉落重载，确保真正打树叶触发独立抽奖，取消破坏不抽奖。玩偶默认 0.0001、手杖默认 0.00001；每位玩家每个存档各一次，两种抽中／领取／待领取记录分离。
4. 修复 Productive Bees 1.20 琥珀通过 `entityType` 而非 `id` 保存类型的差异，阻止旧琥珀数据释放副本；直接封装、直接设置容器及 Ars 的各个加载路径均被拦截。
5. 星空奖励使用 AE2 15 正式物品 ID，避免沿用 1.21 的名称后静默找不到奖励。首次八份奖励按玩家存档记录，重进庭院可重播演出而不重复发奖。
6. Forge Curios 5 按实际存在的功能槽取样，去掉新版不存在的启用状态接口及旧空集合分配；装饰槽不计时，隐藏功能饰品仍可陪伴，物品本身不会每刻写入进度。
7. 客户端胶囊检查发现原生异步区块写入时共享 NBT 导致 `ConcurrentModificationException`。现在仅对本模组胶囊维度、Forge 保存事件结束后的写入参数建立快照；不会每刻复制区块。

## 本机游戏回归

原始日志、依赖及截图保存在 `D:/AI/archive/forge1201-doll-fullport-20261024/`，精选证据在仓库 `verification/forge1201-20261024-doll-fullport/`。失败的早期日志保留，不用构建成功替代明确的检查标记。

| 回归 | 明确结果与证据 |
| --- | --- |
| 专用服务端 13 阶段 | `doll-server-v9.log`：13/13 与 `FORGE 2I SERVER CHECKS PASSED`。实际树叶破坏、默认概率、两种独立一次性奖励、满包载体、重连、旧记录迁移、物品／实体／摆件数据、头戴与所有权、赠礼、跟随、天气、禁止召唤和加速、共伞、强制移除、星空往返与八次奖励。 |
| 真实进程重启 | 同一测试世界的后续进程明确 `LEAF RESTART PASSED`、`2I COMPANIONSHIP RESTART PASSED`、`2I CURIOS RESTART PASSED`；检查真实前一进程的世界记录和饰品 NBT。 |
| 剧情及客户端 | `doll-client-final.log`：12:59:09 明确 `STORY CHECKS PASSED`；英文／中文六章各八种视图；逐字、选项暂停、ACK 与 token 幂等、窗口缩放、历史只读、设置、隐藏、超时／返回；96 张章节截图，章节回顾、图鉴和声音检查。 |
| 原生饰品与 UI | 服务端真实 ring 装备同步 UUID／主人，实际 CuriosLayer 渲染；气泡及光标预览有截图与像素变化检查。 |
| 皮肤与尾巴 | `doll-skin-tail-v1.log`：原画皮肤 SHA256、物品与方块粒子全部 4096 像素、完整闭眼；250 条蝴蝶结模型边各有两个面；左右摆尾、跳跃／下落／转身回弹和 96 帧动作截图。2000 次本机姿势取样均值约 13 微秒，不是整合包帧率承诺。 |
| 手持与雨伞 | 1200 次同步无待机重复装备动画，100 次双持交换保留两手动画；144 个握伞姿势和 240 个站／坐／携带冷天鼻涕姿势通过。 |
| 本体与伤害防护 | 实际官方本体杀戮光环、私有 forceDie、强制击杀、存储移除；普通鸡对照仍可伤害和移除。46 类注册伤害及 NaN／无限伤害、跟踪数据污染、实体索引／区段／回调强删通过。未安装另一个 Minecraft 版本的永爱之刃，不宣称已运行该武器。 |
| 实际收容模组 | `doll-server-capture-v3.log`：`FORGE 2I CAPTURE CHECKS PASSED`；Ars 仪式／容器两个写入入口／旧数据读取，以及琥珀蜜蜂封装／容器 setter／旧 `entityType` 数据释放通过，普通容器对照保留。 |
| 胶囊基本流程 | `doll-client-final.log`：12:59:53 明确 `CAPSULE CLIENT ROUND TRIPS PASSED`；实际客户端模组搜索、旋转地图、阶段进度、就绪状态、同一实例两次进入和两次物品返回通过。最终复测无 `ConcurrentModificationException`；另有真实 Forge 保存事件共享 NBT 的磁盘快照回归。没有重跑全部第三方结构。 |
| 原兼容修复复测 | `staff-official-base.log`：13:02:07 明确 `FORGE STAFF COMPAT CHECKS PASSED`；官方本体 20.2.4.6 与 MEK 10.4.15.75，128 次预效果剂量恢复、立即清除、背包／副手与 NBT、九种扳手标签开关通过。 |
| 普通发行构建 | `release-build-v1.log`：不启用游戏测试 fixture 的 `build` 成功；两个 JUnit suite 共 6 项，failures/errors 均为 0。 |
| 双份发行包审计 | `jar-audit-build.json` 与 `jar-audit-delivery.json`：版本、254 个生产 Java、439 个 class、137 个资源及六章剧情一致，测试条目和外部 class 均为 0；MixinExtras Forge 0.5.0 内嵌及保存／时钟映射正确。 |

交付包 `useless_stretcher-20.1.6.2.jar`：13,206,731 字节，SHA256 `3414E5897EEFE20E5B90CA1AA4A8E1C05DE1130B0A6E19A78697F7879D251C73`。精选证据附带 SHA256 清单。发布脚本要求当前提交的 GitHub Actions 构建成功后才允许发布；远端 CI 与 Release 响应保存在本轮交付档案，禁止仅凭退出码判断游戏回归通过。

## 复现

```text
gradlew build
gradlew runDollServerCheck -PdollChecks
gradlew runDollServerCheck -PdollChecks
gradlew runDollClientCheck -PdollChecks
gradlew runDollClientCheck -PdollChecks -PskinTailOnly=true -PdollClientDirectory=run/doll-skin-tail-check
gradlew runDollServerCheck -PdollChecks -PdollCaptureChecks
python docs/audit_forge_release.py build/libs/useless_stretcher-20.1.6.2.jar
```

服务端使用已接受 EULA 的隔离测试世界，连续两个进程检查持久化。不要用正式存档运行自动化场景。

## 已知上游边界

官方无用之物 20.2.4.6 不含 `arsnouveau.EnchantingApparatusRecipeAdapter`，装 Ars 时本体会记录缺失配方适配错误；其 `ProductiveBeesCaptureCompat` 也缺失，安装蜜蜂后普通生物的本体刷怪蛋捕获可能抛错。本次没有伪造这些第三方功能，2i 会在进入这些本体路径前被拦截。基础全量回归与实际 Ars／PB 防收容回归分开运行，报告不能将普通生物的这项上游功能写成通过。

未运行 ATM10 整包；第三方私有维度检查、所有结构变体、所有服务器插件及所有强制击杀模组不能从本次用例推断为全部兼容。1.20.1 版本不能装入 1.21.1。
