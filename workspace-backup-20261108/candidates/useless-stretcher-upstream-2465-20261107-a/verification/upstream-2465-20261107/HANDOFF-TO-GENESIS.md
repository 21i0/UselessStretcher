# 交给「我的创世纪」统一实机验证

本候选以已冻结的中文 HUD 包为精确基线，合入无用之物 2.4.6.5 和 JEI 19.70.0.460 的最小兼容修改。没有启动 Minecraft、服务器或 ModLauncher，没有编辑整合包实例或 DollChronicle 的兼容类，没有提交、推送或发布。

产物：`D:\AI\candidates\useless-stretcher-upstream-2465-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970.jar`

SHA256：`9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7`；12,817,487 字节。

精确新组合：无用之物 2.4.6.5、JEI 19.70.0.460、EAEP 1.6.4.1、DATA 正式 3.4.0；另按来源固定 AE2 19.2.18、AppliedFlux 2.1.7，核入 LDLib2 2.2.44、AE2WTLib 19.5.1。完整路径和 SHA 见本目录 `dependencies.json`，没有使用未完成的 DATA 下载。

## 改动和证据

- 目录预算、JEI 分批发布、读取/界面保护和计时注入均已对新精确版本放行。新 JEI 的四处注入逐点审查：`setRecipe` 移至新增四参 helper，但重定向保留原生新增参数；目标调用各唯一。没有用旧 JEI 或旧 AE2 补齐被移除成员。
- 将原生 `AdapterGuards` 接回分时收集及切片转换。结构性失败隔离对应适配器动作；转换的普通异常只丢当前配方，链接错误禁用同类同动作的后续调用。生成后段失败丢弃该段已产出内容，转换后段失败丢弃整个当前配方缓存。新接口位于惰性内层，旧本体不链接缺失类。
- 保留 owner 线程、原预算、最多三次重试及失败提示。查询失败的空快照不写入原生 CACHE；完整结果仍只在 generation 检查后的 PUBLISH 阶段发布。
- HUD 继承上方居中、最多两行及省略的冻结实现；总成 UUID 锁定和装入时的独立样板快照保持基线。
- 新原生样板修复会跳过担架物品，不会改装入 UUID；它也不会修复担架内部的旧失效样板。JDTE Infusion 适配器仍为原精确 SHA，生成切片不因升级而退回原生大调用；VAT helper 算法去调试信息后相同。

本轮实际运行 147 组专项检查：门控 127 组 / 1131 断言、新原生保护 14 组、真实旧本体缺少新类时 6 组，全部通过。门控调用的类 CodeSource 指向本轮编译目录。旧本体检查使用独立 JVM，运行 classpath 已移除新本体。日志中的故意异常是故障注入，不能当作游戏报错。

HUD 的 21 组检查属于先前精确基线的证据；本轮没有重跑 184 项全套回归。新组合的整包启动、实际显示、目录完整性、卡顿数据和游戏内总成保存/重进仍由来源的唯一客户端验证，离线结果不代表这些已通过。VAT 输入已变化，不能拿目录数量不同的旧 P3 数据作单变量性能比较。

构建及每项检查的命令、实际 classpath、输入/产物/日志 SHA 见 `build-result.json`。官方接口审计证据位于 `D:\AI\candidates\useless-stretcher-upstream-abi-20261107-a`，最终成品引用快扫在其 `final-candidate-ref-audit`；原生 216、JEI 11、AE2 150 次引用全部可解析。

DATA 3.4.0 的 `PatternProviderMetadata` 真实构造为 `(ResourceLocation, ProviderIdentityDescriptor, ObjectList, ObjectList)V`，本体 .5 的原调用匹配，不需机械套用旧 List-widen 补丁。EAEP 1.6.4.1 已安装 JEI 下限 19.70.0.460；DATA 新依赖包括 AE2≥19.2.18、LDLib2≥2.2.44 和必需 AE2WTLib。核心兼容是否转换由创世纪按真实 ABI 决定。

既有样板库格式 3 的迁移说明继续适用：回退到 1.6.3 应恢复升级前完整备份。此候选保持既有快照实现，不将已完成存档重新生成或补发内容。
