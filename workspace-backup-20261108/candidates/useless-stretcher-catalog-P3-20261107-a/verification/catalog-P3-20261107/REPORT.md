# catalog-P3：JEI 重入与部分提交异常

此候选基于冻结的 `1.21.1-1.6.4-catalog-P2`，只修正 JEI 发布控制器的组合边界。
最终 JAR、精确 SHA256、成员差异和测试总数以 `build-result.json`、最终审计及 delivery 的 `P3-freeze-manifest.json` 为准。
已冻结普通 JAR：`D:/AI/candidates/useless-stretcher-catalog-P3-20261107-a/delivery/useless_stretcher-1.21.1-1.6.4-catalog-P3.jar`。
大小 **12,792,091 字节**，SHA256：`0D2CCCBD03E95EEC6AC1688F00028372D92313D90189BA8B5EBCA4DC3EE6832A`。
最终 **149/149** 全量离线测试通过；其中 JEI 生命周期 **60/60**。29 项成员 ABI 和 32 项版本检查通过，最终审计为 `audit-runs/20261106-224959577/audit-patch-report.json`。
相对精确 P2：661 成员逐字节不变、新增 2、修改 4、删除 0（665 → 667 成员）。仅控制器类族和版本元数据改变。7 个 UUID/样板库相关 class 另行核对一致，测试类/测试库/第三方类没有进入 JAR。
没有启动 Minecraft、客户端、服务器或向游戏附加诊断；没有修改 P2、S、生产包、P1 或旧 1.6.4 候选，没有提交、推送、发布。
离线检查通过不等于 S 已可开测，也不代表整包性能通过。

## 已确认的 P2 缺陷

精确 P2 JAR SHA256：`161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4`。
实际 `CatalogJeiPublication.class` SHA256：`BF7B3FD6E16B84F375539E2D5306944E60AB74B4A080A6A4B767C4CA3BB77208`。
不是只看源码：冻结包的 javap 显示 offset 177 调 `advance`，185 比较全局 task，205 提前 return，209 才读取 failure。
`p2-evidence/frozen-P2-jei-publication-javap.txt` 保存了完整字节码证据。

第三方 add 已部分修改 JEI，然后重入 `invalidate()`，可再 `request()` 创建新 task，最后抛异常。Owner helper 将异常记在旧 advancing 中；P2 因全局 task 已变化直接返回，没处理旧异常。未写入 canonical ledger 的未知部分可能在下一 frame 再发起。

来源任务提供的独立最小反例已只读检查并复制到 `p2-evidence/source-independent-repro/`：

- receipt SHA256：`AF378EAB9E499D5C2CA449DDCDDEEBD738A5E3C937159370B3123B0A75B6C35B`。
- 两例均出现 `known=0 / unsafeFailure=false`，之后 calls 从 1 到 2；expected 3、accepted 4，首个原 Entry 身份计数 2，错误到达 ready。
- 该夹具使用真实 P2 patch class 和外围 ABI stub，证明控制器重复发起逻辑，不能当作 native JEI 全路径实测。

本候选也从冻结 P2 JAR 加载控制器运行扩展回归；P2 的原始 101 项结果仍保留，既没有改成失败描述，也没有拿原通过结果覆盖新增反例。
原 P2 的 101 项日志 SHA256 仍为 `C2765BB3690765647C10B88FB3FB62BDEE9F063E793058D8E8ACC83CF7B1BC3E`。

## P3 的处理

- task 创建时固定其 `taskRuntime`。`advance` 返回后，先读取该旧 advancing 的异常，再判断它是否仍是全局 task。
- 故障按真正被调用的 target runtime 对象身份记录。弱引用列表通过 `==` 比较，值只保留独立错误文本，不通过 Throwable、manager、Entry 或闭包反向强引用运行时。
- 同 runtime 重入后抛异常时，立即禁止重放并取消该 runtime 中新排的任务；已经换上的其它 runtime 不因旧 runtime 的异常被封锁。
- reset、普通 invalidate 和切换到新 runtime 不清除旧故障。A → B → A 时，只要旧 A 仍存在，其未知批次就不能重放；已经回收的对象记录可在 owner 上清理。
- 每次 request 都依据 JEI 实际当前对象重选故障状态，覆盖“native 当前对象已经变了，但控制器还没观察到”的重入次序。不会仅依赖全局 bool 或是否调用过 observe。
- `frameInProgress` 阻止回调中嵌套推进新的任务。回调可请求新任务，但必须先结算旧调用的账本或故障。
- pre-call `StaleRevision` 表示尚未调用 native mutation，可安全取消。native 回调直接抛出的异常保持故障属性，不因为 epoch/runtime 已切换而降为安全过期。
- native 正常返回后，若 runtime/注册状态已变，使用单独 `RuntimeChangedAfterCall`；旧调用已执行而 canonical ledger 未完成，保守封住旧 target，防止后来返回旧 runtime 时重复发起或污染已清空的共享账本。
- `registrationEpoch` 在 reset 或观察到 runtime 更换时前进。普通 invalidate 不改变它，因此普通重入且正常返回时，原 Entry 的成功返回批次仍能正常记账。

没有改动 `OwnerRecipePublication`、目录生成/转换/指纹/identity/排序/索引、tuple 编码、推进预算、同步 gate、JEI mixin 和界面屏障。P2 里这些成员继续保留原字节。
1.6.4 的独立 UUID、装入时样板库快照和 SavedData 格式 3 沿用 P2；源万物之块后续增删不影响装入时快照。

## 验证与复现

`verification/jei-publication/run-tests.ps1` 提供两种模式：

```powershell
# 控制器及 Owner 从精确冻结 P2 JAR 加载；新增反例应失败。
& ./verification/jei-publication/run-tests.ps1 -Controller P2Baseline
# 从当前 P3 源码重新编译，再运行同一夹具。
& ./verification/jei-publication/run-tests.ps1 -Controller P3Source
```

独立 run 保存 controller 的 class origin、源码和 class SHA、classpath、编译参数、完整 JUnit 输出和退出码。
最终生命周期证据：`verification/jei-publication/runs/P3Source-20261106-224715819/tests.log`，源码 SHA256 `03D9F3E41087BBA6CA41513554C0E5BD6F15DA205FB81A8FEE5BD49862B716EC`；实际 controller class SHA256 `6AFA6EF41769F06725B6B62F3E3252F45C8955D972EBFB65F1FE39BC8A7C692E`。
最终测试源码 SHA256 `7AB3C4B42FF102FACA193F27D7F835FDFE1739B377CC7394778CACED12171977`。
原 P2 的 54 项控制器对照有 36 项复现失败；新增 6 项针对“native 当前对象与已观察对象不同”的反例，也在保留的旧 P3 6EF 实际 class 上复现 6 项失败。最终 03D9 对应的纯回调 60 项全部通过，未以反射写故障状态来制造最终反例。
用例覆盖 add/hide/unhide 的部分变更后 throw、失效后 task 为 null/已替换、正常返回、reset 清 canonical map、运行时更换及回切、嵌套 frame、native 当前对象与已观察对象次序不同。
所有这些是独立 JVM 中的 mock runtime/controller 回归，没有启动 native JEI 或游戏。

全量构建继续运行 P2 原有目录、真实 native helper/Codec、tuple、同步 gate、UUID/快照、存档持久化等测试，并加入扩展生命周期用例。
最终汇总以 `unit-tests.log` 的最后完整 JUnit summary 为准；冻结工具校验所有测试启动成功、无失败/跳过/中止，并匹配测试日志和构建源文件哈希。
最终汇总为 149 found / 149 started / 149 successful，0 failed / skipped / aborted；日志 SHA256 `550D1C3277FD7DF2222DCECB2EFC50369ECC2C488F5C30DE47E5811FB0678D6C`。
失败的原 P2 及中间候选结果按独立 run 保存，不覆盖原证据。

`audit-patch.ps1` 以精确 P2 为基底，仅允许控制器类族及版本元数据改动。其余所有成员必须逐字节一致，另检查测试/第三方类排除、UUID/样板快照、29 项成员 ABI 与 32 项版本分支。
`freeze-candidate.ps1` 检查源文件未在构建后改变、打包 class 等于已测试编译输出、精确 JAR 哈希、原包不变和最终测试摘要。

## 兼容与验收边界

版本为 `1.21.1-1.6.4-catalog-P3`；Minecraft 1.21.1、NeoForge、Java 21，客户端和服务端使用同一候选。
启用边界沿用 P2：目录补丁针对无用之物 `1.21.1-2.4.6` / `1.21.1-2.4.6.2`；JEI 分批、lookup/GUI 屏障针对 `19.57.0.451`。其它版本未新增承诺。
本次普通 JAR 不带测试夹具、测试库或第三方 class。P3 没有混入来源任务的海洋 core 修改。

性能尚待整包实测：本次没有新的墙钟、单回调、最大 frame/tick、最终 JEI 查询或 UI 实测结果。修复防重放不证明所有 native JEI 路径或任意第三方回调均通过。
`submitted` 仍仅表示正常返回批次的项目数，不是 JEI 确认接收数量。
来源任务保留 S 为 PREPARED_NOT_LAUNCHED；本候选的离线冻结状态不授权或代替其开测决定。

有序 tuple 编码、默认摘要和导出参数仍与 P2 完全相同：

```text
-Duseless_stretcher.catalogTupleDirectory=D:/AI/verification/S-P3-tuples
```

`compare-tuples.ps1` 随本候选保留；P1-evidence 诊断源码/脚本继续位于原 P2 的 `verification/catalog-P2-20261107/p1-evidence/`，本轮没有修改它。
R 没有完整 tuple，仍不能倒推出其真实摘要或用条数证明等价；同输入完整有序比较及整包性能验收仍待执行。
