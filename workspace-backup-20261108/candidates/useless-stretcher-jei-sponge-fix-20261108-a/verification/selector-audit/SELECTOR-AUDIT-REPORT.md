# Sponge 0.16.4 选择器补审

旧 9556 候选的真实故障已复现到选择器层：裸 `getIngredientSupplier` 经 Sponge 的 `SELECT_MEMBER` 配置变成最多匹配一个方法，按 class 文件顺序选中原三个参数的转发入口。该方法不再直接调用 `setRecipe`，因此实际注入点为零。之前跨全部同名重载累计调用的静态检查漏掉了选择器限制。

新修复保留同一静态 Redirect handler 和原始 INVOKE，明确列出三个参数、四个参数两个完整描述符，并保持 `require=1, allow=1`。实际加载 Sponge 0.16.4 的 MemberInfo parser 与 ElementNode matcher 验证：

| JEI | 三参数入口的 INVOKE | 四参数入口的 INVOKE | 合计 |
| --- | ---: | ---: | ---: |
| 19.57.0.451 | 1 | 不存在 | 1 |
| 19.70.0.460 | 0 | 1 | 1 |

七个 mixin 的其余 19 条 selector/At 记录全部在实际选中方法中命中；`invalidate(Level)` 的两个返回点均保留。9556 旧报告、旧故障字节码和新修复编译收据独立保存。

这是实际 Sponge parser/matcher 检查，没有启动 Mixin transformer、ModLauncher 或 Minecraft；完整变换执行由另一份 transformer harness 验证。本补充报告修正旧审计在选择器层的缺口，不能将原来的 13 项静态通过视为 Sponge 注入成功。
