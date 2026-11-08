# 最终候选的依赖与 ABI 审计

结论：静态成员解析和精确注入点检查通过。验证对象为最终候选 `9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7`，旧 HUD 基线 `E0E15336F1FFA8D1F9AC1839B7A29509BB2D6ECDC03C502AE92B1B4251048FB6` 的审计独立保留。

| 目标 | 成员调用解析 | 聚焦检查 |
| --- | ---: | ---: |
| 无用之物 2.4.6.5 | 216 / 216 | 15 / 15 |
| JEI 19.70.0.460 | 11 / 11 | 13 / 13 |
| AE2 19.2.18 | 150 / 150 | — |
| 本体调用 DATA 3.4.0 | 45 / 45 | — |

最终包新增的 `AdapterGuards.call/callEach/guard/isDisabled/rememberSource` 五个调用均与官方方法描述符匹配。担架实际引用的 13 个 JEI 类全部存在，直接及反射类型字符串中没有 DATA 类引用。目标命名空间缺失成员时禁止通过旧 JAR 或旧散装 class 补齐。

JEI 的配方构建调用已经移到新增的四参数 helper，三个参数的原入口保留并转发；两个入口合计仅一个 `setRecipe` 注入点。索引入口仍只有一个匹配的 `RecipeMap.addRecipe`。两个调用均保留原生的 RuntimeException 和 LinkageError 捕获范围。Lookup 字段及 get 方法、七个 GUI HEAD 入口仍匹配；原生新增加的网格、滚动条与键盘选区逻辑不被替换。

DATA 3.4.0 的 `PatternProviderMetadata` 构造参数为 `ObjectList,ObjectList`，本体 2.4.6.5 的调用已经匹配。旧的 List 参数扩宽补丁不能套到这一组合。DATA 的元数据要求 LDLib2 ≥2.2.44、AE2 ≥19.2.18，并将 AE2WTLib 设为必需依赖；EAEP 1.6.4.1 要求已安装的 JEI ≥19.70.0.460。

JDTE 生成配方适配器的精确 SHA 未变化，因此原来的切片白名单继续命中。VAT 两个类的完整字节存在差异，去除 debug/frame 后各方法逐指令一致；该切片实现没有精确 SHA 门控。

完整输入路径、SHA256、官方 TOML、每个解析项、28 项精确检查和限制保存在同名 JSON。`final-candidate-ref-audit` 中是最终包的独立报告，先前 HUD 基线报告未覆盖。

本轮没有启动 MC 或加载模组类。这里证明的是静态 ABI 与注入点形状；不替代实际 Mixin 变换、客户端、服务器或整包性能验证。根代理报告的 147 项检查未在本审计中重复执行。原生修复会跳过物理槽中的担架物品，不能据此声称担架内部的旧失效样板已自动修好。
