# 本体 2.4.6.5 / JEI 19.70 独立候选

版本：`1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970`。

JAR：`D:\AI\candidates\useless-stretcher-upstream-2465-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970.jar`。

SHA256：`9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7`；12,817,487 字节。冻结状态：`FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED`。

## 实现

精确组合为本体 2.4.6.5、JEI 19.70.0.460、EAEP 1.6.4.1、DATA 正式 3.4.0，依赖路径和 SHA 见 `dependencies.json`。保留经审查的旧本体与 JEI 组合，不开放任意未来版本。EAEP 的 JEI 下限与 DATA 新增的 AE2/LDLib2/AE2WTLib 约束由来源任务统筹。

除两个版本 gate 外，本轮将官方新增 AdapterGuards 接回被分时目录替代的调用：`getAllRecipes/getGeneratedRecipes/getRecipeClass` 采用官方结构性隔离；切片 `convertAll` 采用逐条隔离。生成切片后段失败回滚当前生成段的全部已收集产物；转换后段失败清空当前 holder 全部输出。转换 RuntimeException 允许下条继续，LinkageError 禁用同适配器类/动作，互不污染其它动作或适配器。登记 source 继续使用官方诊断归属。

AE2LT 使用官方 `AE2LT JEI recipes` scope；整表使用 `alloy-furnace recipe catalog build` scope。scope 失败保留原三次重试及失败 UI，查询的空 fallback 不写 CACHE，完成结果仍只在 generation 检查后的 PUBLISH 阶段发布。正常目录顺序、owner 线程和预算保持原流程。新接口封装在惰性 Native 内类中；真实旧本体缺少 AdapterGuards 时直接执行原 work.get，保留原 catch 行为。

独立生产复审为 PASS，核对了 action/scope、生成 rollback、每 holder 的异常口径、正常顺序、旧本体惰性链接、owner/预算/重试与 CACHE 路径。非切片列表 iterator 的后段异常仍走官方同样的外层 scope，未扩大为其它动作的会话禁用。

## 实际验证

本轮实际运行并全部通过 **147 组专项检查**：门控 127 组（1131 断言）、真实新本体保护 14 组、真实旧本体缺类环境 6 组。新/旧保护在独立 JVM 中执行；审计实际检查了新 JVM classpath 唯一官方 .5 及旧 JVM 确实没有 AdapterGuards、新本体已被移除。专项日志中的 ERROR/WARN 是主动注入 RuntimeException/LinkageError 的预期输出，不是游戏运行报错。

旧 184 项全套回归没有重跑。HUD 21 组几何/文本检查属于先前已冻结基线；本候选的 HUD 类和语言资源为该基线原字节，没有冒充新的实机显示验证。

最终包独立静态 ABI 报告确认：本体 216/216、JEI 11/11、AE2 150/150 次成员引用可解析，本体调用 DATA 45/45 匹配，另有 28 项精确检查。JEI 配方构建的 `setRecipe` 调用移到四参 helper，三参入口仍转发，合计唯一注入点；索引目标唯一，两处原生 RuntimeException/LinkageError 捕获保留。没有用旧 JEI/旧 AE2 补齐缺失成员。新 combo 不需要旧 DATA List-widen 补丁。JDTE 适配器精确 SHA 没有变，切片白名单继续命中。

固定证据：

- `build-result.json`：编译、实际命令/classpath、147 组结果及输入/类/日志哈希。
- `audit-runs\20261107-152449858\upstream-candidate-audit-report.json`：最终精确审计。
- `delivery\upstream-freeze-manifest.json` 与 `upstream-members-vs-HUD.json`。
- `D:\AI\candidates\useless-stretcher-upstream-abi-20261107-a\FINAL-COMBO-ABI-REPORT.json`，SHA256 `E0FEAACEA05441761E7B0E190A4A7777EA4758618C39C92C05F8A24F484AD9C9`。
- 同目录 `FINAL-COMBO-ABI-RECEIPT.json`，SHA256 `CCD9206009E991D101B97A4A51C3E10B0801F3E0C30999DF28EA93FDC6339F40`。
- `HANDOFF-TO-GENESIS.md`：来源任务的统一实机验证交接。

## 精确差异和边界

相对精确 HUD 基线，2 个新增、12 个改变、0 个删除、668 个成员原字节不变。新增为 NativeAdapterGuards outer/Native；实际业务改变为两个 gates、Budgeted outer/Build 及版本元数据。另 7 个 Budgeted 既有内类仅调试信息改变，保留 frames/注解/方法参数等结构的 ASM 去 debug 比较相等。两个 gate 类在允许的 gate 方法之外同样归一相等。HUD、Publication、NativeCatalogAccess、总成 UUID/快照、语言与其它未授权内容全部原字节不变。测试/专项检查类及依赖未入包。

首次审计 `audit-runs\20261107-152313818` 因脚本将含重复 CSV 的有序参数误用集合比较而失败；之后仅修脚本，源码、JAR 和构建记录未变。后续 PASS 记录及外部 ABI 关联均保留。

未启动 Minecraft 客户端、服务端、ModLauncher 或实际世界；未改运行实例，未提交、推送或发布。新组合真实 Mixin 变换、整包加载、目录完整性、同存档总成交互和性能仍待来源唯一客户端验证。原生样板修复会跳过担架物品，不能声称已经修复其内部旧失效样板。

VAT 由来源任务删除，不是本候选修改。目录总数随实际输入变化，不固定 155024；不能以不同 VAT/本体/JEI 输入的旧 P3 结果作单变量性能对比。样板库格式 3 的旧边界保持：回退到 1.6.3 需升级前完整存档备份。
