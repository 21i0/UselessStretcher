# runtime-client-d 独立只读复核

日期：2026-11-14（Asia/Shanghai）。未运行 Java，未修改生产 JAR、原始日志、既有测试结果或历史失败证据。根已单独审阅固定 hand 身份边界并放行该 SHA 的 GitHub 发布；本报告不宣称远端发布完成。

## 结果

runtime-d 为 `PASS_WITH_SEPARATE_IDENTITY_OBSERVATION`：18 项普通 PASS，另有 1 项 `OPEN_UI_LIVE_HAND_SWAP_IDENTITY` 的 `OBSERVED_FIXED_HAND_IDENTITY_DRIFT`。后者保持独立观察，未改写成通过。客户端 exit 0，37.64 秒，未超时、未强杀、resource_stop=false；293 fixture ticks，真实集成服务端。结果原文件的 production_acceptance=false、fullpack_acceptance=false 保持不变，专项结论不能扩大为整包验收。

G 的 7 项覆盖所列主副手布局、原生 layout 请求应答和模式包；X 的 5 项覆盖实际生产输入事件、selectSpeed 动作及服务端组件变化。另有切到非手杖的服务端守卫、关窗 F 换手重开和关窗快捷槽切换重开。G 并未遍历本体所有工具模式。JEI 检查实际 runtime、原生合金分类非空配方和实际 ingredient filter 中的手杖；未逐页验证全部获取/用途界面。

维度阶段实际 teleport 到下界再回主世界；客户端等待目标维度和新 ACK，重开 G 等原生 layout 后再打开 X。源码 323–327 行以不同 expectedSpeed=16/64 调用生产动作，并在延后 3 服务端 tick 后检查服务端实际组件；第二程不重装物品。因此两次 ACK 是真实变化，非仅固定日志文字。该检查仍非零 tick UI race 的证明。

先关闭窗口、换手/换槽后再重新打开的用法已验证；打开窗口期间直接换手的身份漂移仍是已知边界。使用 NeoForge 生产输入事件分派，未注入原生 GLFW 回调；未覆盖远端真实多人、全部结构、全部玩偶或整合包性能。

## 运行与绑定

mods 中实际 8 个 JAR 的完整集合和全部 SHA 与 watchdog-binding 一致；fixture-binding 的夹具源包路径和 hash 对应最终 fixture-c/attempt-b。最终源码 SHA 为 `bec2bbf17bd3a70a102c5ce28530ff51037e967a6790af03d9614f25bbdc8cbc`。生产字节未改变，生产夹具未混入正式包。

日志记录 `Stopping!`、`Stopping server`、玩家和世界保存及 `All dimensions are saved`。有一次 `Can't keep up!` 约 2295ms/45 ticks，不能据此宣称无停顿。client-result 的 post_guard（根称 rootpost）在 UTC 2026-11-14T02:05:39.287739+00:00 采样 passed=true、other_java=[]、unknown_java_command_line=false；这是退出后该采样时点的零 Java 结果。

runtime-a 仍为 DEFERRED_NO_JAVA；runtime-b 和 runtime-c 的 fixture_status 仍为 FAIL，原文件未删改。d 的结论不回填旧运行。

## 关键完整 SHA256

- `D:\AI\verification\stretcher-release-1.6.4-20261114\production\staging\useless_stretcher-1.21.1-1.6.4.jar`：`29a4ece1dea89e19778370e87c7af35e1a789d29f0e88ee213ae4bc2a9402fbe`。
- `D:\AI\useless_stretcher-1.21.1-1.6.4.jar`：`29a4ece1dea89e19778370e87c7af35e1a789d29f0e88ee213ae4bc2a9402fbe`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\runtime-client-d\launch-evidence\staff-compat-result.json`：`4f80aca3ff821d9d6b59bb7f1e9422d379331a53a55c3518266cd3af80d51e94`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\runtime-client-d\launch-evidence\client-result.json`：`7ecb028bbf27ffd56bada5ced39fe88fc6a1adc3b5e5751ba391726cac2248fa`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\runtime-client-d\launch-evidence\fixture-binding.json`：`adc18bfc34218aca67ff76a5f87248055a5f8d30473cf01052837097c2d49157`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\runtime-client-d\launch-evidence\watchdog-binding.json`：`a473b03e145a362991998ff0b6d7417e650a47a0125aa516b07bb9cc4e6ee31b`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\runtime-client-d\logs\latest.log`：`3e8e754b1f3377348b411eea08277b16f2a74367cd0b3ac3bd3ff60fe324e5e4`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\fixture-c\src\main\java\com\twoi\stretcher\verification\StaffCompatSmoke.java`：`bec2bbf17bd3a70a102c5ce28530ff51037e967a6790af03d9614f25bbdc8cbc`。
- `D:\AI\verification\stretcher-release-1.6.4-20261114\fixture-c\attempt-b\stretcher-staff-smoke-private.jar`：`2f5e774d65bbd8538d1252890b1ae4d222c95a1f0c6d317ce4e765a03dfe78a8`。
- `D:\AI\sources\DollChronicle\verification\update-041-20261114\runtime-agent\watchdog\build-a\resource-stop-watchdog-PRIVATE.jar`：`3bf08c04f0aee9318418fc25a036f31048c6f37cdb3618c23a4d468948698150`。

全部运行 mods：

- `appliedenergistics2-19.2.19.jar`：`7db959595ca8734acccac51c963567460e7db9f31b145b23dea72778b692777b`。
- `guideme-21.1.22.jar`：`108337ff98c706b18ed2fbec077b3337ba72c08f8b46a273534886280e89ef0d`。
- `jei-1.21.1-neoforge-19.70.0.469.jar`：`9ab82d9bdbc8d9a08b80bc2023e1d886f6335b92f937d3afac878c9ea995dc33`。
- `mezz_config-1.21.1-neoforge-0.7.1-standalone.jar`：`c42510938584eda0aae134003e367d745fd32ceac1836221cecac4cbc7bfa3a1`。
- `resource-stop-watchdog-PRIVATE.jar`：`3bf08c04f0aee9318418fc25a036f31048c6f37cdb3618c23a4d468948698150`。
- `stretcher-staff-smoke-private.jar`：`2f5e774d65bbd8538d1252890b1ae4d222c95a1f0c6d317ce4e765a03dfe78a8`。
- `useless_mod-1.21.1-2.4.6.6.jar`：`fbadd572c3cafb27c4bee7397b57eaa2edb2278b2bca9335b91e74197f1e0d65`。
- `useless_stretcher-1.21.1-1.6.4.jar`：`29a4ece1dea89e19778370e87c7af35e1a789d29f0e88ee213ae4bc2a9402fbe`。

逐项收据：`RUNTIME-D-REVIEW.json`。GitHub 与 CurseForge 状态须由实际发布回执更新；当前只登记 GitHub 发布准备，CF 仍阻塞。
