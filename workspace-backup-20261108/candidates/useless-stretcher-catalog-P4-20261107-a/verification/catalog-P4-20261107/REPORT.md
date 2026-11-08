# Catalog P4 离线候选检查

日期：2026-11-07。候选版本：`1.21.1-1.6.4-catalog-P4`。

P4 已冻结为独立测试候选。没有启动 Minecraft、客户端、服务器或模组加载器，没有改动正在运行的整合包，没有提交、推送或发布。整合包真实性能仍待验证。

## 交付

- JAR：`D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4.jar`
- SHA256：`9C57CBB3B1343C8837B773003AC906C630378786A784B7490F1F4E943A5021FC`
- 大小：12,808,495 字节。
- 冻结记录：`delivery\P4-freeze-manifest.json`。
- 包内成员差异：`delivery\P4-members-vs-P3.json`。

相对精确 P3 基线，包内 658 项未变、10 项新增、9 项改变、0 项删除。版本元数据只改候选版本；Mixin 配置只在原 `CatalogJeiTimingMixin` 后增加两个阶段计时钩子。测试类、JUnit/Mockito/ByteBuddy 类、嵌套 JAR 均未进入交付包。7 个 UUID/样板快照相关类保留 P3 原字节。

P3 SHA256 保持 `0D2CCCBD03E95EEC6AC1688F00028372D92313D90189BA8B5EBCA4DC3EE6832A`。生产 JAR 保持审计前后的原哈希。

## 问题证据与实现

已保存原整合包日志为 `input-evidence\night-ui-latest-at-read.log`，SHA256：`DF799180B0869C05D930D04B0616E42104361E041BCB2D20FF221D611350CB61`。日志记录 155024 条配方、9690 次调用、最慢 native 调用 867.0708 ms、最慢 slice 871.1032 ms、总墙钟时间 77136.9193 ms。P3 的固定批量为 16；155024 条恰好对应 9689 次 ADD 加 1 次 publish。4 ms 的协作预算不能抢占批量内部的 JEI 调用。

JEI 19.57.0.451 的离线字节码证据保存在 `input-evidence`。路径为 `RecipeManager.addRecipes → RecipeManagerInternal.addRecipes → IngredientSupplierHelper.getIngredientSupplier → IRecipeCategory.setRecipe → RecipeMap.addRecipe`；配方索引逐条追加，没有因每次 addRecipes 而重建整个目录的证据。

P4 对 ADD/HIDE/UNHIDE 分别维护自适应预算，从单条开始；连续 4 次低于 0.5 ms 的调用后逐步增大，最高 16 条；达到 1 ms 则缩回 1 条，并结合剩余 slice 时间与每条耗时估计约束下一次调用。正预算仍允许至少 1 条，避免昂贵配方饥饿。

保留 P3 的 owner-thread、runtime identity、未知提交不自动重放和回调重入保护。pending 批次在调用 native 回调前被隔离，允许回调取消/失效时安全退出。

新增计时区分 native、分类布局构建、RecipeMap 索引以及 guard，记录最慢 operation、批量大小和配方来源。两个 Redirect 原样传入接收者与参数，不改 JEI 内容；异常仍向原调用路径传播；慢调用日志最多 8 条。

## 实际验证

既有本轮构建记录：`build-result.json`；日志：`unit-tests.log`，SHA256：`3CD7DD2649027585EDBB7560E271ACD2B0648ECB22328245F4823D837B6C28C2`。最终 JUnit 结果为 175 项发现、175 项开始、175 项成功、0 失败、0 跳过、0 中止。冻结时再次核对了测试源码、生产源码、测试日志、已编译生产类及 JAR 内对应成员的哈希；没有重跑无变化的测试。

覆盖完整顺序与防丢失/重复、自适应增长及回退、pending 拆分、失效/异常回调、防重放、runtime 切换、不同 phase 独立预算、off-owner 拒绝、计时参数与异常保真、慢日志限量等行为。

合成检查输出：

```text
P4_ADAPTIVE_FULL_CATALOG entries=155024 calls=9702 max_batch=16 fake_active_ns=155024000
P4_SYNTHETIC_SPLIT entries=160 fixed_max_call_ns=9600000 adaptive_max_call_ns=600000 total_work_ns=96000000
```

这些是确定性的测试时钟数据，不是游戏实测性能。单条 867 ms 情形的测试确认该条仍不可抢占，但后续调用让出到下一 slice。

静态审计为 PASS：`audit-runs\20261107-125805901\audit-patch-report.json`，31 项成员 ABI + 64 项版本 gate，共 95 项检查。确认 JEI 两个 Redirect 的接收者、参数、返回值和唯一命中要求，精确版本 gate、资源范围、测试排除及受保护类不变。

本次收尾只修正审计脚本对 Mixin 数组的顺序预期：原脚本错误地预期在数组末尾追加，改为在原 timing hook 后插入两项并完整比较 JSON；没有放松新增成员或其它配置限制。JAR 字节未变。冻结状态为 `FROZEN_OFFLINE_CHECKS_PASSED`。

## 仍需实测

未验证真实整合包 Mixin 应用、真实 JEI 分类与索引耗时，也没有相同完整输入的 ordered tuple oracle。不能据此声称 0.9 秒卡顿已彻底解决、性能达标或实际完整目录与来源整合包完全一致。

下一次由拥有完整端的任务单独装入此候选，保留相同存档与模组版本，采集完整 publication complete、stages、budget 和最多 8 条慢调用记录，再比较目录条目。重点判断 singleton 是否本身昂贵；若是，继续修对应分类/索引路径，无法靠批次预算抢占它。
