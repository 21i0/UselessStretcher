# 样板总成安装锁定：独立候选验证

日期：2026-11-07。包内版本：`1.21.1-1.6.4-catalog-P4-assembly-lock`。

## 结果

本轮独立候选已完成离线编译、184 项测试、静态目标检查和冻结。没有启动 Minecraft 客户端、服务端、世界或模组加载器；没有提交、推送或发布，没有改动原 P3/P4、生产目录或运行实例。

- 文件：`D:\AI\candidates\useless-stretcher-assembly-lock-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock.jar`
- SHA256：`28317D3E41034B13E1EFFCB75C5C76E71CEFD29AC60437E089DF4E4D5F0A495F`
- 大小：12,809,113 字节。
- 冻结记录：`delivery\assembly-lock-freeze-manifest.json`。
- 成员差异：`delivery\assembly-lock-members-vs-P4.json`。

## 本轮修复

本体 `PatternSlotItemHandler.mayPlace` 对普通已编码样板直接返回 true；旧担架 Mixin 只检查待放入物是否为担架。当总成已装入担架时，编码样板可绕过 handler 的占用校验。后续 handler setter 虽拒绝覆盖，菜单的交换流程却可能已改动玩家光标或快捷栏堆栈，造成物品守恒风险。

新增 `InstalledStretcherPlacementGuard`，在原 `InstalledStretcherSlotMixin` 的 `mayPlace` HEAD 注入中调用：总成的槽位已安装担架时，提前拒绝所有非空待放入物；空槽普通样板保留本体规则，担架仍经已有 handler 校验。空光标取出操作保留可用。

只更改两个生产类：

- 新增 `content/ae/InstalledStretcherPlacementGuard.class`。
- 修改 `mixin/InstalledStretcherSlotMixin.class`。

另外只替换 `META-INF/neoforge.mods.toml` 中的候选版本。对精确 P4 基线，675 项原字节未变、1 项新增、2 项改变、0 项删除。GuideME、UUID 分叉/持久化、样板快照和 JEI 发布相关成员全部保持 P4 原字节。

## 安装快照行为与迁移

继承的总成快照实现已经为每次真实装入生成独立 UUID；源万物之块后续增删或更换样板，不影响总成已装入的样板库。模拟插入不分叉。取出保留快照身份，真正重装时再次分叉，避免另一份拷贝改写当前机器的 UUID。已安装槽需要先取空，才能放入后续物品。小合金炉和被动合成仓继续实时共享源库。

这些行为不是本轮新增改写，原验证见：`D:\AI\sources\UselessStretcher-capsule-variants\verification\assembly-snapshot-1.6.4-20261106\verification.md`。

旧已安装担架只能在首次读取或保存迁移时，从当时仍可读取的源库截取快照；不能恢复历史安装时的样板内容。缺失来源维度或不可解码来源拒绝新安装，旧记录保留，不静默绑定其它库。

样板库存档保存为格式 3，源和快照共享未变化状态，写入时隔离。旧格式 2 与更早 patterns 可读。旧 1.6.3 无法安全读取格式 3；回退需要恢复升级前完整存档备份，不能只换回旧 JAR。

## 实际验证证据

`build-result.json` 记录生产编译、测试编译、测试执行退出码均为 0。`unit-tests.log` SHA256：`D98572E4A5F24CDA504AFD7110B8AB4C39DEB357FDAA96F22C400DCC0CA5F8A7`。

最终 JUnit 汇总：184 项发现、184 项开始、184 项成功；0 失败、0 跳过、0 中止。保留 P4 的 175 项测试，其中包含安装快照的 40 项；新增 9 项 placement guard 测试。

新增测试覆盖占用槽拒绝普通编码样板/同 UUID 与其它担架、空槽普通样板和绑定担架接受、非总成槽保持规则、空 offer/取出不分叉，以及实际原生 `AbstractContainerMenu` 的鼠标 PICKUP 交换、数字键 SWAP 与空光标取出。测试验证被拒绝的交换保留两件物品及锁定 UUID；物品身份和缺席世界服务使用替身，没有应用运行中的 Mixin。

单独负控证据：`negative-control\negative-control-result.json` 为 `EXPECTED_FAILURES_CONFIRMED`。只在测试 classpath 最前覆盖旧行为等价的 guard，184 项开始、181 项成功、3 项失败；失败恰为普通编码样板的提前拒绝、原生鼠标 PICKUP 交换守恒和数字键 SWAP 交换守恒。原有 175 项全部通过。负控前后生产源码、JAR、构建记录及正式测试日志的哈希保持不变；该测试替身未进入 JAR，也不改变已冻结记录。

`audit-and-freeze.ps1 -ExpectedTests 184` 通过。审计报告：`audit-runs\20261107-131214283\assembly-lock-audit-report.json`。审计重新关联构建前 inputs、当前源码及测试资源、javac/java 参数、生产/测试编译类清单、最终测试日志和 JAR 内生产类哈希。白名单为两个精确 class 成员及版本元数据，没有使用目录级放行。

独立 ASM 只读检查确认 Mixin 目标为 `PatternSlotItemHandler`，注入唯一 `mayPlace`、HEAD、cancellable，handler 原描述符保持不变，且只委托一次 public static guard。无用之物 2.4.6 和 2.4.6.2 各存在唯一 `mayPlace(ItemStack)boolean` 目标；对应 javap 与 ASM 输出保存在本次 audit-run 中。测试类、第三方测试库和嵌套 JAR 均未进入交付包。

审计前后原 P4、P3、生产 1.6.3 及两个本体依赖 JAR 的哈希保持各自预期值。冻结状态：`FROZEN_OFFLINE_CHECKS_PASSED`。

## 尚未验证

未启动完整端，未验证实际 Mixin 应用、游戏中的 shift/拖拽/自动化、同存档进程重启或所有第三方样板组件 Codec。离线原生菜单测试不能代替实际游戏操作回归。

本包完整继承 P4 的 JEI 自适应批次与分阶段计时，但没有新增实际整合包帧耗时证据。单条 native JEI 调用仍不可抢占，不能声称原先约 0.9 秒卡顿已完全消除。真实性能及相同输入完整有序目录对照仍待拥有完整测试端的任务执行。
