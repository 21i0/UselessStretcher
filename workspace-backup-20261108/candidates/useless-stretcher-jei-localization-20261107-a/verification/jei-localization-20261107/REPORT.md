# JEI 延迟加载通知本地化：离线候选报告

版本：1.21.1-1.6.4-catalog-P4-assembly-lock-zh。冻结状态：FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED。

JAR：D:\AI\candidates\useless-stretcher-jei-localization-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh.jar

SHA256：7D54D427EE4E8D19B62B94B9B8DA3630FE20D8207D21A872F2101596EC59127D。大小：12810599 字节。

本轮只改两项生产外部类、zh_cn/en_us 两语言资源和版本元数据。语言文件各新增十个 gui.useless_stretcher.jei_publication.* 键，旧翻译保持原值。另有 4 项精确 inner class 只发生调试信息变化，已通过仅移除 SourceFile/SourceDebug/LineNumber/local-variable 调试表的 ASM 归一比较，保留方法描述符、参数元数据、代码、frames、注解和其它结构。其余包内成员保持 P4-lock 原字节。保留 progress()String 诊断 ABI，HUD 使用 progressComponent()Component；失败提示引导查看日志，完整诊断继续留在日志。

本轮执行并通过 18 项 focused checks；没有重跑 184 项完整回归，也没有启动 Minecraft、服务端或模组加载器。生产源码、语言资源、编译参数、编译类、focused 日志/结果和 JAR 成员哈希已关联。静态检查确认两个 API 描述符、Component copy 以及 HUD 的本地化调用。

审计证据：D:\AI\candidates\useless-stretcher-jei-localization-20261107-a\verification\jei-localization-20261107\audit-runs\20261107-142714146\jei-localization-audit-report.json。原 P4-lock/P4/P3/生产 JAR 哈希保持不变。没有提交、推送、发布或改动运行实例。实际 HUD 绘制、运行中语言切换与 JEI 整合包性能仍待客户端测试。

来源任务已经删除 VAT；该操作不是本候选的修改。本包未增加、删除或更新任何整合包模组。后续目录总数取决于当次实际模组和输入，不预设为 155024；不能把删除 VAT 后的输入和包含 VAT 的旧 P3 数据当作单变量性能对比。

本次两个早期审计失败运行分别保存在 `audit-runs\20261107-142533445` 与 `audit-runs\20261107-142616076`，原因是脚本预期数组拼接及未列全 focused-check 测试内类，之后只修正脚本，生产源码、检查源码、JAR 和构建记录均未改。最终冻结采用本报告所列 PASS 运行。
