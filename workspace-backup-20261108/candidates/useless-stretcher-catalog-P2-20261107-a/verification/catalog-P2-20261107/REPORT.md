# catalog-P2 独立候选交接

本目录对应 Minecraft 1.21.1 / NeoForge / Java 21 的 `1.21.1-1.6.4-catalog-P2`。
成品：`D:/AI/candidates/useless-stretcher-catalog-P2-20261107-a/delivery/useless_stretcher-1.21.1-1.6.4-catalog-P2.jar`。
大小 12,789,513 字节，SHA256：`161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4`。
101/101 离线测试通过；61 项静态 ABI 与 32 项版本分支审计通过。最终审计 `audit-runs/20261106-221658679/audit-patch-report.json` 为 PASS。
相对精确 P1：624 成员逐字节不变，新增 26、改变 15、删除 0（P1 639 条目，P2 665 条目）。完整带哈希清单在 delivery 的 `P2-members-vs-P1.json`；改动限于目录/JEI 调度、mixin 配置和版本元数据。
成品身份亦记录在同目录 `build-result.json` 和 delivery 的 `P2-freeze-manifest.json`。
冻结核对确认 47 个本轮编译 class 与测试后打入 JAR 的字节一致，源文件及日志未改变；6 个 UUID/样板库相关 class 另外逐一与精确 P1 核对一致（其余非允许改动成员也由全包审计确认不变）。
此候选没有启动 Minecraft、客户端、服务器或向游戏进程附加诊断；没有修改 R 运行目录、整合包、生产包、P1、旧 1.6.4 候选，没有提交、推送或发布。
独立 JVM 编译、单元测试和静态字节码审计不等于整合包实测。

## 精确基底和存档边界

- P1 基底 SHA256：`004AE477920BAA6ADF890417FF11431771A52D2F2733E818B49C446484549BD3`。
- 生产 1.6.3 SHA256：`00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC`。
- 旧 1.6.4 快照候选 SHA256：`AC78F41EAA7883434F6845CF7323829D77E66B994C82EDCF2B81AEC4AFCD9220`。
- P2 在精确 P1 成员上仅覆盖目录及 JEI 调度相关 class、mixin 配置和版本元数据，其它功能必须逐字节保留。实际成员清单由审计 JSON 给出。
- 总成内担架的独立 UUID、装入时样板库快照及 SavedData 格式 3 沿用 1.6.4；后续源万物之块增删样板不影响该快照。P2 不重新编译这些功能。
- 在存档副本测试；回退 1.6.3 使用完整旧备份，不能把格式 3 存档直接视为可逆降级。

## R 的真实失败证据

原日志：`D:/AI/sources/DollChronicle/verification/R-preparation-20261107/R-run-1/client.log`。
摘录保存在 `R-observed-markers.json`。下列是 R 实际观察值，不是 P2 预期或验收数量。

| 指标 | Server | Client |
| --- | ---: | ---: |
| recipes / sources | 162601 / 39 | 162581 / 39 |
| published wall_ms | 341325 | 291115 |
| ticks | 5031 | 5679 |
| max_tick_ms | 5242.6948 | 4625.0322 |
| ADAPTERS active_ms | 6643.1734 | 5980.9452 |
| CONVERTED active_ms | 2563.9785 | 2451.9231 |
| FINGERPRINT active_ms | 9511.7382 | 11714.0441 |

JDTE generated 单次原生回调：Server 5242.4544 ms、Client 4624.7429 ms。
Client EnderIO Vat `convertAll` 最大 939.6335 ms；SoulBinding 最大 296.7314 ms。
最后 ready callback 29585.1133 ms，其中一次 JEI `addRecipes` 29496.0112 ms、refresh 总计 29579.955 ms。
P1 虽然能发布目录，性能并未通过；P2 不把目录条数或已出现 published 当作性能通过。

## P2 改动

1. 服务端每 tick 协作预算由 2 ms 提高为 8 ms；客户端改为每 RenderFrame 4 ms，ClientTick 仅负责同步屏障及完成回调。目录和 JEI 在同一 frame 二选一推进，避免叠加两份预算。移除独立的每 tick 128 条指纹限制，保留 deadline 和 maxSteps。
2. 精确 JDTE Infusion adapter 在真实 item × fluid 候选、瓶装及蛋 entry 之间让出 owner；复用原生私有构造 helper、ID、组件、缓存和先见去重规则。完成 base 扫描后才写原生缓存。
3. EnderIO Vat 的标签去重、正反组合及输出逐项推进。继续使用原生 helper 构造配方；倍数和数量全同的压缩、反向去重及变体 ID 保持原生行为。ABI 探测失败回原生转换。
4. 转换异常丢弃整个转换缓冲；生成后半段失败回滚该 adapter 已收集的生成结果，保持原生局部失败语义，防止半条转换或半批生成留在目录。
5. JEI 在 Render thread 按稳定顺序分批，每批最多 16 项，协作预算 4 ms。原生初次 category/catalyst 注册保留，大目录延后至 runtime 可用。保存成功返回批次的原 Entry 对象，取消重建后不重复添加；移除项按批隐藏，再出现的已知项按批恢复。
6. JEI 没有原子事务/删除 API。同步期间目标 `RecipeLookup.get()`（包括 includeHidden）返回 pending 空结果，配方 GUI 的显式打开、历史、已有界面和直接 setScreen 受屏障控制。没有调用无所有权的 category hide/unhide，不改变其它调用方的类别隐藏状态。其它 RecipeType 查询保持原生。最终全部批次正常返回后才放开屏障，并执行本体 ready callback，保留手持伤害刷新。
7. JEI 回调可能先接收部分内容再抛异常。此时停止并锁住该 runtime，不因重建或普通 invalidate 自动重放；换成新的 JEI runtime 才可恢复。回调前发现过期任务，或整个旧 runtime 已被替换，则安全取消。回调重入失效/新建任务也有独立测试。
8. INDEX 阶段逐条记录完整有序 `(recipeId, fingerprint, sourceId)` 摘要，保持原生转换、指纹、identity、遇到顺序、首来源去重、排序和最终原生索引。只有纯字符串排序和可选纯字符串证据写盘进入后台。

## 有序 tuple 验收方法与 oracle

P2 默认输出 `catalog-tuples-v1`：完整有序总 SHA256，以及每个 source 的有序 count/SHA256。编码使用版本头、UTF-8 长度前缀和总数结束标记，避免连接歧义。

需要逐项比较时，P2 JVM 加：

```text
-Duseless_stretcher.catalogTupleDirectory=D:/AI/verification/S-P2-tuples
```

每个 owner/generation 的日志会给出 UUID 独立文件名。导出只传不可变字符串，单 worker、有界队列，不覆盖旧文件。失效会撤销 token；文件提交前检查 token，但检查和文件改名之间仍有极窄竞态，可能留下带独立代号的过时证据文件，不影响目录发布。选择与成功 published 对应的日志文件，不根据文件最新时间猜测。

`p1-evidence/` 准备了独立诊断源码及 `build-p1-evidence.ps1`：未插桩 P1 源重编译出的 9 个目录 class 与精确 P1 全部相同；诊断副本只增加 INDEX 证据累计及成功发布后的摘要/可选导出，保留 P1 2 ms / 8192 steps / 128 指纹限制。它仅用于同输入比对，不能作为性能基线。没有修改原 P1，准备阶段没有构建/运行该诊断游戏包。
P1 诊断导出参数为 `-Duseless_stretcher.catalog.p1EvidenceDirectory=D:/AI/verification/S-P1-tuples`。

在相同模组、数据包、配置、配方同步及同一侧上下文下采集 P1-evidence 与 P2，再执行：

```powershell
& 'D:/AI/candidates/useless-stretcher-catalog-P2-20261107-a/verification/catalog-P2-20261107/compare-tuples.ps1' -Left 'D:/AI/verification/S-P1-tuples/实际文件.tuples' -Right 'D:/AI/verification/S-P2-tuples/实际文件.tuples' -Report 'D:/AI/verification/S-tuple-compare.json'
```

比较器流式重算摘要、验证连续序号及每项三元组，报告首个差异和来源差异；不能只比较总数。Server 与 Client 分别和同侧基线比较。
R 没有导出完整 tuple，因此 **R 的完整有序摘要目前不可得，不能倒推**；同输入 P1/P2 全量一致性仍待采集。离线原生 helper/Codec 与目录 fixture 的逐项比较是已完成的独立 oracle，不能代替整个 R/S 整合包。

## 日志指标

- `Cooperative catalog queued`：generation、owner；`published`：recipes、sources、wall_ms、ticks、max_tick_ms。
- `Cooperative catalog phase`：各阶段 active_ms；`detached sorting`：排序 wall_ms。
- `atomic operation`：单 owner 步骤 max_operation_ms。
- `adapter`：operation、calls、active_ms、max_callback_ms；分片名包含 JDTE/Vat 的具体子阶段。
- `generated slices`：source、attempted pairs、steps、outputs。
- `ordered evidence`：有序总摘要和每来源摘要；`tuple export`：实际证据路径。
- `Stretcher JEI publication complete`：epoch、planned、submitted、hidden、unhidden、calls、max_call_ms、max_slice_ms、wall_ms、max_frame_ms。
- `Stretcher catalog ready callback`：revision、elapsed_ms；此回调位于最终 JEI 提交之后。
- `JEI publication stopped without replay`：异常和已正常返回批次数量。

`submitted` 仅指正常返回批次的项目数。JEI 内部也可能自行跳过 broken recipe，不能据此宣称全部配方已被真实接收。S 还应检查最终查询、界面、重复/漏项与整包日志。

## 已完成验证与仍待实测

最终离线测试汇总在 `unit-tests.log`；源文件、编译输出和测试日志身份在 `build-result.json`。
覆盖旧 UUID/快照和持久化测试、原生指纹/首来源/排序/索引、同步 gate、真实 JDTE helper/组件/输出顺序、真实 Vat Codec NBT 逐项比较、tuple 导出/比较器、协作预算与发布 ledger、JEI 生命周期及失败防重放。
静态 ABI 和精确成员差异由 `audit-patch.ps1` 产生独立 `audit-runs` 记录；它不启动模组加载器。
首轮离线测试缺 NeoForge fluid_type registry 的失败证据保留；fixture 已通过 NeoForge 原 DeferredRegister/真实 RegisterEvent 初始化原生 water/lava/empty，不用 mock 替换被验证的 CommonHooks/JDTE helper。

目录替换只对已核对本体 1.21.1-2.4.6 / 1.21.1-2.4.6.2 启用；JEI 新分批/lookup/GUI 屏障只对 19.57.0.451 启用。其它 JEI 版本保留原生注册路径，不声称获得本次分批优化。
本轮参考 JDTE 0.6.5.4 和 EnderIO 8.2.12-beta。可选模组没有进入成品。

尚未启动 S，没有新的整合包墙钟、最大回调或 JEI 最终注册实测，不能宣布性能通过。
单次第三方 capability/codec、JDTE spawn-egg map 首次获取、base cache 最后冻结、Vat tag 展开等仍是不可抢占回调。SoulBinding 及其它未专门拆分 adapter 保留原生回调，其耗时会继续暴露。
GUI/lookup 屏障不是对 JEI 内部所有对象的全局事务；第三方直接持有 Entry 并调用内部布局等路径需要整包验证。海洋 core 修复由来源任务另做，本候选不混入该修改。

S 需同时验收墙钟、最大单回调、客户端最大 frame、服务端 tick、JEI 最后注册、完整有序 tuple、取消重建及最终正常保存退出；目录 ready 条数只作观察值。
