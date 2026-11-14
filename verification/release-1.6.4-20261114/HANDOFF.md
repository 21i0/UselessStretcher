# 2026-11-14 万象担架 1.6.4 发布准备前言

本段是接在旧交接文档之前的 1.6.4 实际专项检查与发布准备记录。旧文中的“当前包”“当前交付”“已发布”及旧 SHA 仅属于对应历史记录；本段没有登记发布成功。日期统一为 Asia/Shanghai。

## 当前生产身份

- 本地交付：`D:/AI/useless_stretcher-1.21.1-1.6.4.jar`；冻结 staging 副本同 SHA。
- 正式文件名：`useless_stretcher-1.21.1-1.6.4.jar`；包内版本 `1.21.1-1.6.4`，网络协议 `23`。
- 大小：15,106,028 字节；SHA256：`29a4ece1dea89e19778370e87c7af35e1a789d29f0e88ee213ae4bc2a9402fbe`。
- 基线 E1 SHA256：`58dad32e8644a8612ef1ed30d6088531f16cf9849df6f844f1e27534865f63c3`。最终候选只改变已重新编译的两个版本 gate class 与版本元数据，786 个文件条目原字节不变；共 789 条目，测试夹具未入生产包。
- 新组合：无用之物 `1.21.1-2.4.6.6`（SHA `fbadd572c3cafb27c4bee7397b57eaa2edb2278b2bca9335b91e74197f1e0d65`）、AE2 `19.2.19`、JEI `19.70.0.469`、mezz_config `0.7.1`。JAR 原有依赖下限保持不变，不能据此声称任意未来版本兼容。

## 最终行为，优先于旧说明

样板总成每次实际装入担架时创建独立 UUID 与样板库快照，后续原万物之块增删不再同步影响。占有担架的槽位必须先取出才能换装，后来的担架或普通样板不能直接覆盖。小合金炉和被动合成仓仍实时共享绑定库。旧总成迁移只能截取当时仍可读的库，不能恢复最初装入时已经被修改的历史；来源不可读保留数据，不静默改绑。

目录按 owner 线程、世代和工作预算分阶段构建/发布；JEI 使用自适应批次，保留失败与未知提交的隔离。新版本体 AdapterGuards 已接入目录查询、生成与转换；新增精确 `.6` / `.469` gate，防止新版组合绕过补丁。单次第三方同步调用仍不可抢占。

胶囊新结构的重力保护与恢复记录按房间、区块持久化，抑制陨石砂砾集中生成下落实体。同一实例仍是原房间和战利品状态，生成相邻实例才新建。升级不自动重放已完成房间或覆盖玩家改建。

样板库存储格式为 3。旧 1.6.3 无法安全读取；回退必须恢复升级前整个存档备份及旧 JAR。不得照抄旧文“只换回旧包即可”的含义。

## 实际检查与证据边界

最终候选静态检查记录为 `FINAL-1.6.4-API-STATIC.json`：本体 169 个直接常量池成员引用、JEI 9 个、AE2 111 个均解析，未用旧目标 JAR 补齐缺失成员。这些计数不是逐指令计数，不能与旧 216/11/150 报告直接相加。

本体 `.5`→`.6` 的十个关键手杖、G 键、工具转换、AE 绑定及网络类原字节相同。JEI `IngredientSupplierHelper` 整类与 `.460` 相同；`RecipeManagerInternal.addRecipe` 的目标方法符号指令相同，两个 Redirect 目标均唯一且仍位于原生 RuntimeException/LinkageError 捕获范围内。静态通过不等于实际 Mixin、客户端或网络通过。

E1 可复用的真实服务端证据：`D:/AI/candidates/useless-stretcher-capsule-gravity-pause-shards-20261111-e/verification/native-entry-e1/E1-NATIVE-SMOKE-REPORT.md`，SHA `1767cd60f6558a764f3f6c0a7dda2e3e0a2055a1d7305bf8e67ddfe0d9b7993b`。覆盖一间新 `ae2:meteorite`、81 区块、35,136 个砂砾保护位置、1,024 个唯一真实调度、80 个活动 tick，falling joins/生成与峰值为 0；两次使用生产胶囊入口及 PendingEntry 并返回，内容摘要保持一致，强加载全部释放。E1 冻结代码片段在正式候选中未改动，可复用该有限结论。

E1 证据本身不覆盖本轮新依赖组合客户端；本轮 runtime-d 的专项结果另列下文。未覆盖真人 UI、同存档重启、多房/多所有者、全部结构、全部玩偶或整包性能。更早 C 候选的失败/INVALID 证据继续保留，不能迁移成 E1 通过。

## runtime-d 实际专项检查与发布边界

最终生产 SHA 绑定的 runtime-d 专项检查已完成：18 项普通 PASS，另有 1 项打开 UI 期间换手的固定 hand 身份观察，未将其算作通过。关窗后 F 换手或切快捷槽再重开，以及下界/主世界两次往返后重开 UI 和服务端实际倍率 16/64 修改均通过。JEI 实际 runtime、原生合金分类非空配方及手杖 ingredient filter 通过；未遍历 G 全部工具模式或 JEI 每个获取/用途页面。客户端 37.64 秒正常保存退出，exit 0、无强杀、resource_stop=false，退出后守卫采样零 Java。运行是集成服务端与 NeoForge 输入事件，未覆盖远端多人、原生 GLFW 输入和零 tick UI 换手竞争。

结果 SHA256：`4f80aca3ff821d9d6b59bb7f1e9422d379331a53a55c3518266cd3af80d51e94`；夹具 SHA256：`2f5e774d65bbd8538d1252890b1ae4d222c95a1f0c6d317ce4e765a03dfe78a8`。完整独立复核见 `publication/RUNTIME-D-REVIEW.md` 与 JSON。runtime-a 的资源拒绝和 runtime-b/c 的失败原证据保留。根已单独接受已知身份观察并放行 GitHub；没有将原始 production_acceptance=false/fullpack_acceptance=false 改成整体验收通过。

换手或切槽应先关窗，完成后重新打开；零 tick UI race 未覆盖。GitHub 尚未登记成功，CurseForge 当前阻塞，下一步由根按实际发布回执更新。

生产编译与打包材料在 `production/`；独立测试夹具和计划在 `fixture/`；本轮中文文档在 `publication/docs/`。这些路径均相对 `D:/AI/verification/stretcher-release-1.6.4-20261114/`。

用户已授权公开发布，但本文未填写 CF 文件 ID、远端下载、GitHub 发布成功或整包 0.4.1 接入状态。发布后另存实际回执，再把候选路径/哈希与回执逐项绑定。旧仓库大量未提交和未跟踪改动保持，不 reset、不覆盖冻结源码或历史报告；不要为更新文档修改已经绑定的最终 JAR。
