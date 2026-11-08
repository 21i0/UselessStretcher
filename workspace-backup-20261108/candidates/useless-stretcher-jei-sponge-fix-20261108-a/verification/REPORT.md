# JEI Sponge 注入修订

独立候选：`1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1`。

JAR：`D:\AI\candidates\useless-stretcher-jei-sponge-fix-20261108-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1.jar`

SHA256：`3AEB59B7AAD8A2BF50D203BB220192FF45D90D0C50F80F6D8878514249F2F6EF`；12,817,555 字节。

## 根因与修复

runtime-c 的真实日志在第 7154 行报告 `CatalogJeiBuildTimingMixin` 的 `stretcher$timeCategory` 重定向失败，命中 0/1、Scanned 0 targets。日志已复制到本目录 `runtime-evidence`，原运行日志和冻结 9556 包保持原样。

Sponge 0.16.4 在 `SELECT_MEMBER` 模式下将裸方法名默认限制为第一个匹配。JEI 19.70 保留的三参 `getIngredientSupplier` 在方法表中先出现，但已只转发给新增四参实现；实际 `setRecipe` 在四参方法内。旧选择器只选三参，因此找不到注入点。此前在整个类的所有重载中累计调用次数，遗漏了 Sponge 的实际选择规则；该静态审计不能证明注入可成功。

此次仅将选择器改为旧三参和新四参的完整方法描述符，并增加 `allow=1`。原 `require=1`、INVOKE 目标、静态 handler 和计时代码保持不变。两个已核对版本均只重定向一次，保留新 JEI 的第四参数和原生异常处理。

## 实际验证

- 正式 Sponge `0.16.4+mixin.0.8.7` 的真实 `MixinTransformer` 已用旧 9556 重现相同 InjectionError，作为负控。
- 最终修订在 JEI 19.57 和 19.70 下分别成功合并七个目标：四个 JEI 类，以及本体 JEIPlugin、ClientSetup、配方 catalog。对应结果在 `sponge-merge/positive-all-1957-b` 和 `positive-all-1970-b`。
- 新 JEI 再与运行实例中的 AE2Utility、Just Enough Mekanism Multiblocks、NeoEcoAE 三个 RecipesGui Mixin 组合，七目标、十个 Mixin 成功合并，见 `sponge-merge/combined-1970-b`。
- 变换后字节码确认：原生 `setRecipe` 调用从 1 变为 0，替代 handler 调用为 1，仍受原 try/catch 覆盖。两版本原异常处理序列保持；新三参转发入口没有被重复计时。
- 全部七个相关 Mixin 的二十条选择器记录已使用正式 Sponge parser/matcher 复核。旧失败选择器及修复后的两版本匹配证据在 `selector-audit`。
- 对 runtime-c 的 416 个主 JAR、180 个内嵌 JAR、511 份 Mixin 配置做只读扫描。未发现其他模组直接 Mixin 到 IngredientSupplierHelper、RecipeManagerInternal、RecipeLookup 或本体 JEIPlugin；三个外部 RecipesGui Mixin 已按真实字节组合验证。动态生成的目标或任意 ASM 仍须整包运行确认，不能从字面扫描排除。

Sponge 检查使用保留七个生产 Mixin 的独立配置，省略加载器元数据 gate；生产 Mixin 和目标字节没有为测试改写。服务只提供字节码资源，没有定义或初始化游戏目标类，没有启动 Minecraft、服务器或 ModLauncher。版本 gate 的既有专项属于 9556 基线证据，不冒充本轮重跑。

## 产物范围

独立逐文件审计确认：682 个文件成员中只改 `CatalogJeiBuildTimingMixin.class` 与 `META-INF/neoforge.mods.toml`；680 个原字节不变，无新增或删除。剔除方法注解并去调试信息后，Mixin 类完全等同旧类；实际注解只改变两个精确选择器并加 `allow=1`，行号变化为调试信息。测试和检查代码没有进入 JAR。

本轮没有重跑 147/184 项历史检查，没有修改运行实例或 DollChronicle 兼容类，没有提交、推送或发布。9556 的快照、预算、异常隔离、HUD、中文资源等保持原字节。新包仍须由「我的创世纪」在下一独立完整端验证 JEI 启动、目录完整性、实际交互及性能；离线 Sponge 合并不等于整包实测通过。
