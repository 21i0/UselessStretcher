# 1.6.4 独立诊断：421 模组首次目录及 JEI 初始化

状态：只读诊断完成，优化方案待实施及验证。按本轮委派，小内测不赶 Release。本次没有启动 Minecraft、向正在运行的进程附加工具、修改运行目录内容、构建或替换任何 JAR。1.6.3 已发布字节和硬链接未改动。当前 1.6.4 版本入口变更是独立开发状态，不代表下列优化已经实现。

运行：`D:/AI/sources/DollChronicle/build/mygenesis-longhuan-r6-D`；421 个 mods 目录 JAR，G1、最大堆 8 GiB、4 个 JVM 可用 CPU；run `c63f21e1-8b14-42b8-a4eb-113ad110849c`。本次不启动第二个 MC，不干预来源对话的 session 65157。

## 已定位的主导阶段

JEI 是目前有完整起止计时的最大 Render thread 初始化阶段。合金炉目录的同步构建是另一条明确阻塞路径，但不是全部启动长卡的唯一来源。

下表行号指本目录保存的 `r6-latest-prefix.log`，避免依赖仍在追加的原日志。指纹时间已包含在目录构建时间内，不重复相加；Server thread 的工作也不算进 Render thread 时长。

| 工作 | 线程 | 条数／来源 | 已测耗时 | 证据行 |
| --- | --- | --- | --- | --- |
| 服务端目录 generation 0 | Server | 161,938／39 | 13.190 s，含指纹 4.479 s | 5745–5746 |
| 客户端目录 generation 1 | Render | 25,009／20 | 7.781 s，含指纹 1.041 s | 6154–6156 |
| JEI 初始化 | Render | 整包 | **77.884 s** | 6591、7957 |
| 客户端目录 generation 2 | Render | 162,363／39 | 12.296 s，含指纹 8.001 s | 8313–8314 |

两次客户端目录构建共 20.077 s，其中指纹阶段共 9.042 s。JEI 时间为 10:43:26.953 至 10:44:44.837。仅改善目录不能消除全部 77.884 s 的 JEI 初始化工作。

JEI 已计入总时间的子阶段：注册配方 31.76 s（7871 行），构建 runtime 28.88 s（7928 行，其中 ingredient filter 21.85 s，7922 行），分发 runtime 10.08 s（7956 行）。注册内 Minecraft 插件 12.56 s、无用之物插件 4.176 s、NeoECOAE 0.5523 s；这些也不能重复相加。

旧 6 GiB r5-D 的 JEI 约 73.14 s、两次客户端目录共 18.794 s。旧、新配方数量、环境和统计方式有变化，不能据这两个数断言 8 GiB 改善或恶化。

## 目录完成后还有未分解的工作

第二次目录完成于 10:45:00.861（8314 行）；10:45:31.491／31.552 有 JEI recipe registry plugin 警告，10:45:31.572 才出现 `PERSONAL JEI RECIPES APPLIED`（8318–8320 行）。这约 30.7 s 的日志边界不能追加进已经结束的目录 build，也不能直接全部归给 Route 插件。

精确运行 JAR 的调用链表明：`ClientSetup.onClientTick` 在目录 ready 后立即调用本体 `JEIPlugin.refreshAlloyFurnaceRecipes`，全扫目录 identity，并将新增项一次性提交给 JEI `addRecipes`。JEI 19.57 的该接口强制主线程并同步执行逐配方索引、ingredient supplier 和 focus links；完成 Catalog snapshot 不等于 JEI 已完成这些索引。

创世纪 Route 插件是另一个 Post tick 监听器。`onRuntimeAvailable` 只记录 runtime、创建分类桶；实际 tick 才查询并消费 crafting／crystal aggregator 配方流，随后 hide/unhide，末尾打印完成标记。它没有直接调用本体 refresh。当前日志缺少这些调用的开始计时，尚不能分配上述 30.7 s。

## GC 与现有采样的边界

JVM 已将启动 GC 日志轮转到 `gc.log.0`，只读 `gc.log` 会漏掉启动阶段。本目录保存了准确字节长度快照及 SHA256，见 `gc-startup-summary.json`、`gc-startup-snapshot.log`；范围为 10:26:29.265 至 10:54:11.190，共 74,591 行。

- 整个该快照无 `Pause Full`，最长已记录 GC 暂停 535.943 ms（10:48:03.054，快照 37189 行）。它不属于首次 JEI 阶段。
- JEI 精确时间窗内有 32 次 GC pause，共 **2.547 s**，最长 **206.760 ms**。GC 对该阶段有贡献，但不足以单独解释 77.884 s。
- 10:43:24 至 10:45:32 的近似启动观测窗内 GC pause 总计 5.697 s，最长 245.627 ms。不能把整个进程二十多分钟的 GC 总量当作一次启动停顿。
- 既有 `ThreadDump` 安全点在 JEI 窗约 100 次／秒。该窗全部 safepoint 记录的 Total 共 4.930 s，已含 GC；不能与 2.547 s 再相加。Reaching safepoint 时间也不等于所有线程实际暂停时长。该现象提示现有诊断可能带来扰动，现有文本不足以确定发起方或它的完整 CPU 开销，本次未启动任何此类采样。

`gc-summary.json`／`gc-snapshot.log` 是先保存的 10:54:11 至 10:56:44 后续片段，不覆盖启动；其空启动窗口不能解释为启动没有 GC。启动结论一律使用带 `startup` 的两份文件。

## 当前帧记录能说明什么

保存的前六个性能 JSON 见 `performance/`，汇总见 `performance-first-six-windows.json`。

第一窗 10:43:24.708 至 10:45:31.583，持续 126.875 s，但 world／GUI 均无有效帧对。这不是 126.875 s 最大帧的测量结果。observer 只在 ClientTick.Post 换窗，只记录同一窗口及同一分类中的相邻 RenderFrame.Post；换窗会清掉 lastFrame，跨窗长间隔不能保留。

第二至第六窗全部 world=0、GUI>0；第五窗 GUI 最大间隔为 1629.396 ms。GUI 分类包括 screen、overlay 或 paused；这些数据不能代替裸世界实际游玩的帧验证。旧 r5 的 120061.0765 ms 最大间隔没有逐样本时间戳，不能与本轮窗口硬对齐。等待来源对话实测同一 D 候选的世界帧数据。

## 精确本体路径及必须同时处理的旁路

已核对 r6 实际本体 JAR `useless_mod-1.21.1-2.4.6.2.jar`，SHA256 `9F6F54AF76AA255123B354C8725D55C2ACE4525B8683E1136ACAEBCCC8813B17`。对应现有精确 javap 目录为 `D:/AI/sources/DollChronicle/verification/adventure-20261101/mod-freshness/uselessmod-staging/`。`UFO-main` 是其它项目，旧 2.3.x 源码不能代替此版本。

1. `ClientSetup.onClientTick` 等 tags 或最多 100 tick 后，`beginRecipeCatalogRebuild` 直接 invalidate → awaiting → 同步 prewarm；prewarm → snapshot → build。精确 ClientSetup javap 281–288 行为入口。
2. 整包 `BoundedRecipeFingerprintWork.collect` 第 30 行的 `WORKERS.submit(...).join()` 只限制并行度。调用它的 Render thread 仍在等待，不能将其称为非阻塞。
3. tooltip 的 `resolveMoldTagInputs` 在其它解析失败时，没有 ready gate 就调用 `entries(level)`（精确 ClientSetup javap 548 行）。这会重新同步 build 或等 build lock；只改 prewarm 会留下旁路。
4. 初始 JEI 注册和 refresh 已检查 ready，并复用同一 snapshot；JEI 没有 invalidate 目录。第二次 build 应追 recipe/tag 世代变化，不能宣称是 JEI 重建。
5. 服务端启动及 gameExecutor 上的 reload prewarm 同样同步。客户端／集成服务器按 RecipeManager 对象拥有不同缓存和世代，不能只按全局 generation 合并。
6. 担架 `MoldCatalogService` 读取本体的非阻塞快照，按需构建，已有每 tick 约 1 ms／512 项的模具枚举预算；但 `MoldCatalog.fingerprint(entries)` 仍同步扫描全表。它是可拆分的次级候选，当前没有证据将它归为本次 Render 初始化主因。

## 独立 1.6.4 候选顺序

以下为方案，尚未修改生产实现或声称性能验证通过。

1. **共享构建任务。** 每个 RecipeManager／recipe+tag revision 一份 BuildJob；启动立即返回 building，现有 awaiting／isReady 消费同一任务。收集、转换、指纹、去重排序、索引分别保存游标，按 owner thread tick 的时间预算及数量上限推进；全部完成后一次性发布不可变 snapshot。tooltip、JEI、菜单均不能强制另建。保留等待、阶段和数量进度。
2. **安全边界明确后再外移。** adapter、RecipeManager、level、tag、自定义 ingredient 和各模组配方转换默认留原 owner thread。当前 Fingerprint 会调用 recipe／Ingredient／DataComponent／AEKey codec 和 RegistryOps，不能把完整原生 build 或完整 fingerprint 一概扔给 worker。可在 owner thread 捕获原生编码及 fallback 所需的不可变数据，再让有限 worker 做纯 JSON canonicalize、UTF-8、SHA256、纯数据索引。不得在 Render thread join，不得持 build monitor 等待主线程回调。一个第三方同步回调仍不可抢占，应独立记录其耗时。
3. **精确失效与顺序。** 旧 token 在 reload、断线或换连接后失效；过期结果及计数不得发布。收集保留 adapter 注册顺序和原配方 encounter order，去重保留第一项 sourceId，最后维持原生 recipeId 字符串／fingerprint 排序。不能按 worker 完成顺序合并，也不能按数量猜测相同目录。确实相同 revision 的重复通知才可合并。
4. **拆分目录后 JEI 工作。** 本体全表 identity 扫描可分帧；JEI addRecipes 强制主线程，应使用其支持的接口按有序批次推进并显示进度。需要验证分批加入后的最终数量、顺序和索引与原生一致，不能用空集合伪装 ready。现有 registered map 缺少按世代 remove/reconcile，reload 时必须删除或替换旧 identity，不能只追加。JEI 自己的 77.884 s 首次初始化仍需单独优化其受支持的安全阶段，不能靠一个目录补丁承诺消失。
5. **担架二次 SHA 分帧。** 保持 `sourceId + NUL + recipeId + NUL + native fingerprint + NUL` 的逐字节顺序及 SHA256 算法，仅保存游标和 digest 分 tick 继续；沿用相同存档缓存 key。不得以条数、时间戳或弱哈希替换缓存失效依据。

最小后续计时点：onRecipeCatalogReady 总入口／出口；本体 refresh 的 entries、identity 扫描、additions 数及 addRecipes 前后；Route 每桶 lookup、惰性 stream 实际消费、hide/unhide 前后。先分清目录完成后的约 30.7 s，再选择改动，不扩增当前运行的采样。

候选验收须逐项比较完整有序 `(recipeId, fingerprint, sourceId)`、来源数量、重复项首命中、输出组件、全部 fallback，及 tags／资源 reload、构建中再失效、tooltip、断线重连和同存档重启。帧验收须保留跨窗间隔和 GUI 状态切换信息，并对同一 D 候选实际世界游玩记录。未删配方、未放宽分级、未省略进度。

## 发布字节核验

运行目录担架与 `D:/AI/useless_stretcher-1.21.1-1.6.3.jar` 均为 12,691,712 bytes，SHA256 均为 `00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC`。本轮只新增本诊断目录的脚本、日志前缀快照及汇总；没有发布新版本。
