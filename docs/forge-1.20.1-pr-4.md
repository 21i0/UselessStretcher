# Forge 1.20.1 · PR #4 与无用之物 20.2.4.7 兼容验证

2026-10-25 审查 [PR #4](https://github.com/21i0/UselessStretcher/pull/4)，作者 AyaYumi，精确提交 `318ba12dc7b5acd13b6da9d83a9b35c4ff6272d7`，基线 `fc0dda8`。代码审查与下列本机回归未发现阻止合并的问题。保留作者提交，用合并提交接入 `1.20.1`。

## 采用与补充

- 采用 MEK／Generators 10.4.16.80、Applied Mekanistics 1.4.3 编译依赖；胶囊环境、后台恢复、进度与慢回调改为 debug，真正生成失败的警告仍保留。模具枚举异常在每个目录构建最多打印五条，跳过问题条目继续构建的行为保留。
- 采用清理过时换肤说明与忽略本地迁移残留的修改，不改动玩偶、剧情、存档、网络协议或获取概率。
- 补充将默认本体坐标、CI 说明改为官方 20.2.4.7，手杖回归的默认运行 MEK 版本也对齐 10.4.16.80，避免仅修改编译接口而运行旧版。
- 修正 README 与 GuideME：Forge SERVER 配置属于当前世界的 `serverconfig/useless_stretcher-server.toml`；专用服务器世界目录遵从 `level-name`。`defaultconfigs/` 是新世界模板，不应让玩家误改全局 `config/` 后以为已覆盖现有世界。
- 版本递增至 **20.1.6.3 待发布版**，已发布的 20.1.6.2 和旧标签不修改。本轮用户自行发布，不创建或更新 Release。

## 真实前置

Minecraft 1.20.1、Forge 47.3.10、Java 17.0.20；AE2 15.4.8、GuideME 20.1.15、JEI 15.20.0.116、Curios 5.14.1。使用[官方无用之物 20.2.4.7](https://github.com/SorrowMist/UselessMod/releases/tag/20.2.4.7)原始发布 JAR，无第三方源码自建包或二进制修改。

本体标签对象 `fb97fca76eef485a352bd98f9f2ac9c487372b80` 解引用至 `e25d17a8f8722e7e73619fd6b9c581f8b0bbdfa2`；JAR 4,209,822 字节，SHA256 `8FDAE841D6736B81E1D30B23811B470CF6014F94C8168CBAB239B10841A80B55`。新增的三份 MEK／AppMek 依赖与 Modrinth 官方元数据 SHA512 一致，见本轮 `dependency-manifest.json`。

## 本机结果

| 检查 | 实测结果及原始日志 |
| --- | --- |
| 玩偶服务端 | `doll-server-first-v1.log`、`doll-server-restart-v1.log`：两个独立进程各 13/13，均明确 `FORGE 2I SERVER CHECKS PASSED`；包括真实树叶掉落、独立一次性和满包待领取、旧数据迁移、所有权、礼物、跟随、共伞、防召唤／加速、46 类伤害与强删、星空往返／八份奖励。 |
| 真实重启 | 第二进程明确 `LEAF RESTART PASSED`、`2I COMPANIONSHIP RESTART PASSED`、`2I CURIOS RESTART PASSED`。实际读取前一进程世界与饰品数据。 |
| 三种新依赖运行 | `staff-pack-v1.log` 实装 MEK／Generators 10.4.16.80、AppMek 1.4.3；`FORGE STAFF PACK CATALOG PASSED`，通过本体原生配方适配和附属增量目录读到 AppMek 20、MEK 7、Generators 1 个不同模具条目。这是模具数，不是全部配方数或所有机器验证。 |
| 辐射／扳手 | 同一真实服务端明确三项手杖 PASSED；生存模式剂量暴露对照、128 次原生更新前清除、背包／副手／NBT 和九类标签开关通过。 |
| 防收容 | `capture-client-v1.log`：实装 Ars Nouveau 4.12.7、Productive Bees 12.6.0、GeckoLib 4.8.2；`FORGE 2I CAPTURE CHECKS PASSED`，包含旧容器数据防复制和普通容器对照。 |
| 完整剧情客户端 | 同一日志明确 `STORY CHECKS PASSED`，中英文六章各八种视图（96 张章节截图），逐字、自动／快进选项暂停、ACK/token、历史／设置、回顾／图库和声音检查；双持／握伞／鼻涕检查通过。 |
| 饰品／胶囊客户端 | 同一日志明确 `2I CURIOS CLIENT PASSED`、`CAPSULE CLIENT CHECKS PASSED`、`CAPSULE CLIENT ROUND TRIPS PASSED`；原生饰品同步、气泡／光标预览、搜索／旋转地图／阶段进度／就绪、同一实例两次进入及两次返回通过。未出现此前的异步保存并发异常。 |
| 普通发行构建 | `build-final-v1.log`：不带场景开关重新构建，六项 JUnit 均通过，failures/errors/skipped 为零。 |
| 双份 JAR 审计 | 构建与交付包 SHA256 相同；生产源码和资源齐全，六章故事、版本、refmap、内嵌 MixinExtras 均通过；测试条目和外部 class 均为零。 |

## 交付与复现

文件：`D:/AI/useless_stretcher-20.1.6.3.jar`，13,206,826 字节。SHA256 `FC09162716BE42B2BE7563AFE5DE495A764429AA09470B80BE441D6B0EB196A0`。两 TXT、交接和 GuideME 已更新；本次没有发布 Release。GitHub 对合并后提交的构建结果单独保存在本轮交付记录。

```text
gradlew build
gradlew runDollServerCheck -PdollChecks -PdollServerDirectory=run/pr4-doll-server
gradlew runDollServerCheck -PdollChecks -PdollServerDirectory=run/pr4-doll-server
gradlew runStaffCompatCheck -PcapsuleChecks -PstaffCompatChecks -PstaffPackChecks -PstaffCompatDirectory=run/pr4-staff-check
gradlew runDollServerCheck runDollClientCheck -PdollChecks -PdollCaptureChecks -PdollServerDirectory=run/pr4-capture-server -PdollClientDirectory=run/pr4-client-check
python docs/audit_forge_release.py build/libs/useless_stretcher-20.1.6.3.jar
```

游戏回归使用接受 EULA 的隔离目录，不运行正式存档。须核对具体 PASSED 和失败标记，不能仅凭 Gradle 退出码。完整原始记录在 `D:/AI/archive/forge1201-pr-review-20261025/`，精选证据及哈希在 `verification/forge1201-20261025-pr4/`。

## 范围与上游边界

20.2.4.7 对缺失的可选适配增加探测、跳过和回退，不等于补齐所有被排除的适配功能；Ars 配方及 PB 普通生物捕获等上游边界不能因日志安静就宣称已解决。本轮 2i 防收容路径已独立通过。

隔离开发运行仍有未安装可选模组的目标类警告、开发 refmap／Windows Log4j URI 提示，以及首建超平坦测试世界的 `No key layers` 提示；没有将这些写成“日志零警告”。本次排除了场景失败、Mixin 应用／注入失败和保存并发异常。

没有运行完整整合包、全部第三方结构、所有机器加速或全部扳手交互；此前黄金碗／建造机和胶囊多进程压力证据保留，不能冒充本轮重测。20.2.4.6 兼容范围保持原声明，本次实际运行的是 20.2.4.7。
