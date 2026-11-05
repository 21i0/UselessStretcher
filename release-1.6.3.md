# 万象担架 1.6.3

适用于 Minecraft 1.21.1 / NeoForge，Java 21。需要无用之物 1.21.1-2.4.6+ 和 AE2 19.2+；GuideME、Curios 可选。

- 修复手杖单体、范围加速 Productive Bees Genesis Extra 离心机的调用方式。使用机器原生批处理并合并多来源额外工作，保留机器正常 tick，避免重复完整 ticker 和 AE 输出处理。未安装 JDTE 时使用 PBG 自有接口。
- 修复 MBD2 / LDLib2 2.2.44 / AE2LT 组合的异步节点同步冲突。同步合并后交主线程处理，保留 AE2LT 的线程保护、节点记录和余额；失败完整重试并退避，卸载或停服不会复活旧任务。
- 额外工作受预算限制，旧批次优先处理；第三方回调提交后异常时不自动回退重放，避免重复产出。
- 保留 1.6.2 的胶囊变体选择、邻室保护、地图与陨石修复，以及 2i 玩偶、剧情、手杖和担架功能。GuideME 已同步说明。

**升级：**客户端和服务器一起替换。网络协议保持 23，从 1.6.x 升级无需重置存档。桥接仅对已核对的 LDLib2 2.2.44 启用，未知版本不会盲目注入。

**验证：**官方 PBG 1.0.9 真实 Extra 工厂配方；单体、范围与叠加；持续联网满储、实际部分和零接收、原生 NBT 重载、正常保存后的同存档进程重启；MBD2 真实 dispatcher 的 5000 次合并、原生节点 NBT 与重启内容恢复；普通 AE/AE2LT 加速及可选模组缺席启动。成品不含测试类或第三方接口。

测试使用隔离兼容环境和可观测 AE 测试后端，并非报错服务器的完整存档/整包；不能中断已进入的单次第三方同步回调，也不保证任意网络都不会出现停顿。独立的 13:57 登录超时尚无对应日志定因。

## English

Fix staff acceleration for Productive Bees Genesis Extra centrifuge factories by using native coalesced work instead of replaying complete machine/AE tick paths. Single-target and range contributions share bounded scheduling; unknown partial commits are never automatically replayed.

Fix the MBD2 / LDLib2 2.2.44 / AE2LT managed-node sync conflict by handing the standard sync callback to the server thread. AE2LT's thread guard, persisted node identity and balances remain intact. The optional bridge is gated to the verified LDLib2 version.

Real recipes, connected full storage, partial/rejected fluid inserts, native NBT reload and a separate-process restart of the same save were checked. Update client and server together; protocol remains 23 and existing 1.6.x saves do not need a reset.

File: `useless_stretcher-1.21.1-1.6.3.jar`

SHA256: `00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC`
