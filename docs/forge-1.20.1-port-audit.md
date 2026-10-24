# Forge 1.20.1 移植审计与胶囊回归

本报告的胶囊和加速运行记录来自 2026-10-20。2026-10-24 的后续修复见 [Issue #3 报告](forge-1.20.1-issue-3.md)：采用辐射防护与扳手标签补丁，并在两版 Mekanism 上独立验证。PR #2 及原修复分支已合入 `1.20.1`，下面的对比基线继续保留用于追溯。

对比基线：主线 `ad0a63e945f04286dab58a5991998082c73e401d` 与 PR #2
`2d2e00611b7dee86d86e709cb729b695e1317802`。本报告区分代码保留、API 移植和实际运行验证。

## 原 PR 覆盖范围

| 内容 | 实际对比 |
| --- | --- |
| 生产 Java | 主线 247 个、PR 251 个；主线文件没有删除 |
| 资源 | 两边均 137 个；28 个路径按 1.20.1 的复数目录和 Forge 元数据重命名 |
| 2i | 实体、物品、摆件、手持/头戴/Curios、尾巴、眨眼、雨伞、剧情及配套资源保留 |
| 手杖/模具 | 主体源码保留；组件、事件、能力、网络和 AE2 15 接口改为 Forge 桥接 |
| 胶囊 | 原生结构计划、空白镜像维度、战利品/考古、分批放置、地图、进度、返回点和存档全部保留 |
| 专用兼容缺口 | OccultismAccelerationCompat、RftoolsBuilderAccelerationCompat 被明确排除编译和打包，不能算可用 |
| 原场景回归 | regression 40、storyTest 14、guideTest 5 个源文件保留，但没有接入运行；部分仍使用 1.21 API |
| 原 PR 的 check | 三个 JUnit 测试，不能证明结构生成、客户端通信或跨维度往返可用 |

大批删除来自历史测试资产和捆绑依赖，不能按删除行数推断功能丢失；同样不能把源码保留说成全部功能已经验证。

## 本轮补充

- 胶囊 SavedData 读取使用明确传入的服务器，写出数据不再访问全局 ServerLifecycleHooks。
- 镜像维度按需补齐，覆盖 AE2 在 `createLevels` 返回后注册的空间维度。
- 原世界选址与两百万格外的胶囊存放坐标分离。真实回归中，原算法的末地城 1024 次规划全部失败；1.20 的末地岛密度会在远坐标整数平方处溢出。新算法从源世界出生点附近稳定选址，仍使用原生结构集、群系谓词和生成器，接受后才平移原生部件图。
- 已装配结构按实际边界保留清理间距。原来的“相邻但不重叠”判断允许不足 16 格的间距，后来房间清理会损坏已完成邻居；新实现直接平移已接受计划，避免重新规划同一偏移而循环。
- 清理及收尾保护邻居建筑和入口区域，避免删方块、实体及邻居的计划 tick。保护列表按任务和附近范围缓存，普通不相邻扫描不遍历所有房间。
- 收尾版本升级为 5：旧完成实例只重铺基岩和收尾，保留已有建筑及战利品；基岩阶段中断后先完成铺底，不再直接跳到收尾。
- 为 Forge 47 内置 MixinExtras 0.5.0，原 PR 的 compileOnly 不会给最小安装提供 WrapMethod 运行库。
- 补回 Occultism 1.158.0、RFToolsBuilder 6.0.10 的专用适配及后者的旧版 `scan` 字段访问；统一走有模组加载判断的直接调用，修正仪式时钟的原版方法重混淆映射。
- 无效结构、缺失目标世界及队列满的响应保留请求实例，避免客户端误显示默认实例或无反馈。
- 配套本体未安装 JEI 时，客户端配方目录刷新跳过 JEI 插件调用，保留玩家属性刷新；普通手杖交互也不再误调用本体未打包的 Occultism 刷怪蛋适配器。
- 增加检查点保存/重载、旧记录迁移和相邻实例幂等性的 JUnit 测试。
- 增加 `-PcapsuleChecks` 的专用 Forge 服务端回归入口，测试代码从发布 JAR 排除。
- 游戏回归设计为三个真实进程：`checkpoint` 在部分放置后保存停服；`resume` 重启续建并生成其余结构；`verify` 再次重启检查完成实例和两次往返。

测试目标为沙漠神殿、丛林神庙、沼泽小屋、掠夺者前哨、下界要塞、堡垒遗迹、古迹废墟及末地城。检查原生 piece 计划、源环境、基岩兜底、战利品表、刷怪笼、考古、完整地图及返回点。

本轮已复现并修复末地城选址、邻居清理、重启收尾、动态维度和可选适配的问题。最终通过范围以以下日志为准，不能外推成所有整合包结构的保证。

## 复现命令

先用 Java 17 构建本体 PR #45 的 `88bded31e1b722f407cbfbaa4686738d14af31e9`，取得 `build/devlibs/useless_mod-20.2.4.6.jar`。附属命令为：

```text
gradlew build -PuselessModDevJar=<本体开发包绝对路径>
gradlew runCapsuleCheck -PcapsuleChecks -PcapsuleCheckMode=checkpoint -PuselessModDevJar=<本体开发包绝对路径>
gradlew runCapsuleCheck -PcapsuleChecks -PcapsuleCheckMode=resume -PuselessModDevJar=<本体开发包绝对路径>
gradlew runCapsuleCheck -PcapsuleChecks -PcapsuleCheckMode=verify -PuselessModDevJar=<本体开发包绝对路径>
```

运行目录 `run/capsule-check` 必须是专用测试世界，三次运行使用同一个世界。检查日志中的 `FORGE CAPSULE ... PASSED` 及所有失败标记，不能仅看 Gradle 退出码。

## 本机验证状态

环境为 Forge 47.3.10 / Java 17 / 无用之物 20.2.4.6 / AE2 15.4.8 / GuideME 20.1.15。测试代码与自定义测试维度不进入发布包。

| 实际游戏回归 | 结果与原始日志 |
| --- | --- |
| 部分放置后保存并停止进程 | 13:13:00 `FORGE CAPSULE CHECKPOINT PASSED`；`forge1201-capsule-checkpoint-v5.log` |
| 重启续建及 10 个实例生成、内容、地图、每个实例两次往返 | 13:30:04 `FORGE CAPSULE resume CHECKS PASSED`；`forge1201-capsule-resume-v4.log` |
| 第三进程读取原有实例并再次检查内容与往返 | 13:31:16 `FORGE CAPSULE verify CHECKS PASSED`；`forge1201-capsule-verify-v1.log` |
| 客户端实际目录/搜索网络包、鼠标操作、各阶段进度截图、完成通知、4096 像素地图、窗口布局、地图旋转、两次进入和物品使用返回 | 13:34:53 `CAPSULE CLIENT ROUND TRIPS PASSED`；`forge1201-capsule-client-v2.log` |
| Occultism 1.158.0 黄金碗制作次元矿井 | 13:42:09 `FORGE OPTIONAL ACCELERATION CHECKS PASSED`；`forge1201-acceleration-v8.log` |
| RFToolsBuilder 6.0.10 采掘和建造 × 单体/范围 | 同上日志；普通推进 8、加速推进 36，逐块检查物料、能耗、缺能和红石禁用 |
| 基岩阶段实际中断、另进程读取与完整铺底恢复 | 13:43:13 检查点为 `bedrockChunk=1`，13:44:29 `FORGE CAPSULE floorresume CHECKS PASSED`；`forge1201-capsule-floor-checkpoint-v2.log`、`forge1201-capsule-floor-resume-v1.log` |
| 未装 JEI 的客户端完整胶囊流程 | 13:46:23 `CAPSULE CLIENT ROUND TRIPS PASSED`；`forge1201-capsule-client-nojei-v1.log` |
| 普通无场景参数构建和 6 项 JUnit | `forge1201-addon-build-final-v1.log` 与 `junit/` XML；6 通过，0 失败/错误/跳过 |
| 构建包与根目录交付包独立审计 | 均 `PASSED`；253 生产 Java、438 class、137 资源，测试条目和误打包依赖类均为 0，仅内置 MixinExtras Forge 0.5.0 |

黄金碗使用真实原生仪式与物品激活：100 个真实 tick 的普通进度为 5，单体普通、永久、范围三路 x2 的进度均为 15；x1024 在后续 63 tick 内完成。另验证先空闲 220 tick 后激活、完成后反复脏标记仍休眠、再次启动、原生 NBT 重载，最终每路恰好获得两个矿井。旧测试世界残留标记已在 fixture 中清理，并断言一次点击仅有一个标记，避免误报倍率。

10 个实例包括 8 种原版结构、相邻的第二座沙漠神殿，以及自定义下界环境中的要塞。沙漠神殿保留 4 箱、9 TNT 与 7 个考古方块；古迹废墟保留 79 个考古方块；末地城为 99 部件、6 个战利品容器；自定义下界要塞为 153 部件、10 个战利品容器、2 个刷怪笼。检查的是原生战利品表/NBT 和结构计划，未将开箱后的随机战利品误当作固定内容。

原始日志保存在 `D:/AI/archive/`；通过日志、JUnit XML、JAR 审计和两套客户端截图已复制至本工作树 `verification/forge1201-20261020/`。失败证据也保留：`forge1201-capsule-resume-v1.log` 是旧末地选址 1024 次失败，`forge1201-capsule-resume-v2.log` 是旧邻居/铺底问题，`forge1201-capsule-client-v1.log` 是配套本体未装 JEI 的崩溃。一次测试进程并发重编译导致的类文件缺失记录在 `forge1201-capsule-floor-checkpoint-v1.log`，不计为产品回归结果。

当前交付文件（含 2026-10-24 Issue #3 修复）`D:/AI/useless_stretcher-20.1.6.1.jar`，13,205,359 字节，SHA256：
`828602DFD03E73849E310F8C9F46A37D834487F32054A1729F475AB370817F85`。
包内版本为 `20.1.6.1`，Minecraft 范围 `[1.20.1,1.20.2)`，Forge 范围 `[47.3.10,48)`。
配套本体为 PR #45 指定提交的源码构建包，不是新建官方 Release。原移植修复最初在独立修复分支交付，现已与 PR #2 一起合入 `1.20.1`；本次 Issue 修复不创建 Release 或移动标签。

## 验证边界

- 本轮没有运行 ATM10 整包，也没有验证 1.20.1 的 AllTheModium、闪电科技或所有第三方结构。原版和自定义下界回归通过不代表这些模组已通过。
- 镜像保留源维度类型、群系源、建筑原生计划、数据包处理器和结构内刷怪规则；镜像拥有独立维度 ID，不能保证第三方硬编码维度 ID 或外部世界状态检查自动等价。
- 结构计划、箱子战利品表、考古与刷怪笼已实测；没有穷举所有随机变体，也没有覆盖所有模组自己的考古处理器。
- 2i、剧情、尾巴、Curios 等源码和资源完整保留，本轮未重新运行其所有场景测试。
- 本体 PR #45 还缺少其标记 Occultism 刷怪蛋适配类。本轮只保护普通物品交互，不声称补全本体的自动驯服刷怪蛋功能。
- 生成继续分批执行并显示进度，第三方原生单次回调不能抢占，不承诺任意大小结构零卡顿。自定义要塞的本轮最大原生回调为 12.18ms，不能当作整包最坏上界。
