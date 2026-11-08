# runtime-e 实机验收补充记录

本记录于 2026-11-08 登记，仅补存来源验收与文件绑定核对结果。未修改冻结 JAR、原报告、manifest、创世纪实例或来源收据；未启动游戏或服务端，未重跑测试。

## 来源及文件绑定

- 实机操作来源：Codex 对话「我的创世纪」，线程 `01a18fa2-fb29-7f13-a025-cb15b908048e`。
- 来源收据：`D:\AI\sources\DollChronicle\verification\mod-updates-20261107\native-rc7\runtime-e-acceptance.json`。
- 收据 SHA256：`2EB11A0F9B41C85D923E20A8AAAF3DAFCB4B1A163CAC2CDF01B86ABD3B340A2E`，本目录副本保持原字节。
- 状态：`RUNTIME_E_ACCEPTED_EXIT_0`，正常退出 `0`，414 个生产 JAR，无测试辅助模组，最大堆 8 GiB。
- 来源列出的 16 个证据文件已逐项核对，SHA256 全部一致。详细路径与哈希见 `local-binding-verification.json`。
- 冻结担架 JAR：`delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1.jar`（相对候选根目录）。
- 担架 JAR SHA256：`3AEB59B7AAD8A2BF50D203BB220192FF45D90D0C50F80F6D8878514249F2F6EF`，12,817,555 字节。
- runtime-e 实例中恰有一个担架 JAR，其 SHA256 与冻结包一致。
- 冻结 manifest SHA256：`A9AD4B06FCE20BDA9FD567177A5AA9DD4B978F654CC0C986B366EB6272703034`，保持不变。

## 来源实机通过范围

以下 GUI 操作由「我的创世纪」执行；本对话核对来源收据、证据哈希及日志，没有重新执行 GUI 测试。

- JEI 19.70.0.460 下目录 42,213 条完成发布；顶部居中中文等待通知可见。
- JEI 界面及四个 Utility 按钮可见并正常打开；未再出现已修复的 RecipeOptionButtons VerifyError 或先前的 NeoForgeGuiPlugin 错误。
- T1、T7、有用线圈键盘 U 分别可见 1255、3920、14971 页，抽查需求等级 1、7、10。
- 普通铁及 T1 线圈的 R 获取页正常打开。
- 既有独立测试存档可进入，正常保存并退出。

## 直接日志核对

日志：`D:\AI\sources\DollChronicle\verification\mod-updates-20261107\runtime-e-production\client\logs\latest.log`。

SHA256：`0B9DD3DEF0CD8C10383C854DF1548FD7BCBA5D0852ACC15205A013C33ED79A1B`。

- 第 7612 行，01:54:32.754：`Cooperative catalog published generation=2 recipes=42213 sources=38`。
- 第 8397 行，01:55:15.376：`Stretcher JEI publication complete epoch=7 planned=42213 submitted=42213 hidden=0 unhidden=0 calls=16137 max_call_ms=128.4187 max_slice_ms=128.4462 wall_ms=42594.0445 max_frame_ms=128.4657`。
- 第 8777–8791 行：逐维度保存区块。
- 第 8888 行，02:23:23.494：Minecraft `Stopping!`；退出码由来源启动收据确认。

## 验收边界

这是内部候选冒烟验收。未逐条加工配方、未穷举路线解锁、未验证 Utility 向真实供应器上传有效样板，亦非完整生存流程、全部任务或长时间性能验收。日志观察到约 128 ms 最大帧片段，不能据此宣称全局流畅或不卡顿。

来源收据中的其他模组检查及整包迁移限制保留于原字节副本，不能扩大为担架全部功能或所有兼容场景已通过。本轮无需为此重复启动测试；没有提交、推送或发布。
