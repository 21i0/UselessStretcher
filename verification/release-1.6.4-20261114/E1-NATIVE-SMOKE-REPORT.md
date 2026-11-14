# E1 单房原生 smoke 结果窄核

2026-11-11（北京时间）。根对话持唯一 Java 槽完成一次运行；本线程只读核实际结果和日志，没有另启 Java、修改冻结源或重跑。

**本轮限定结论：一间新 AE2 陨石房、受控服务端身份的两次生产进入/返回已完成；结果与冻结源码的通过条件相符。** 不继承 C 的其他成绩，不代表真人客户端或整包验收。

运行目录：`D:\AI\verification\capsule-gravity-runtime-e\entry\e1entry-20261111-a\server`。

- 实际 E1 主包 SHA256：`58dad32e8644a8612ef1ed30d6088531f16cf9849df6f844f1e27534865f63c3`，包内版本及四个关键类的加载资源 SHA 均匹配。加载资源字节检查不声称测量了 mixin 变换后的运行时字节码。
- 独立夹具 SHA256：`ccbe6a439327ec29c18a52ac2282c7799605eb64c96cfafd2090394056cfc92a`，49,046 bytes、10 个 class；生产 JAR 没有混入夹具。
- 新世界 seed `6111025`，唯一房间 `instanceId=0 / slot=0`，81 个区块，35,136 个原生砂砾 pins。1,024 个真实重力排程已入队/出队，观察 80 个实际 server tick；falling join 和生成/ready 峰值均为 0。
- 完整结构及支撑层扫描 373,248 个位置，结构上下半部天陨石均存在，记录神秘立方体 1、石英块 15、方块实体 1。进入前及两次往返后的完整内容/方块实体摘要一致；这不是对所有结构内容或战利品的通用验收。
- 两次分别从 side 0/1 走生产 `action=2` / `PendingEntry`，并通过真实胶囊 item service 返回，同一 key/slot/plan；实际入口与预期位置距离平方均为 0。两次均回到主世界 `(1.5, 82, -2.5)`，yaw/pitch `(0, 0)`。4 次传送包均由原监听器接受延后一 tick 的受控 ACK，待 ACK 和原生 entry 队列结束。
- 原观测 force flags 81/81 释放；返回后的独立内容复查 flags 合计 162/162 释放，最终为 0。回程坐标和朝向恢复，六个回程标签全部删除是绑定夹具源码的必经断言；未另读取盘上的玩家 NBT 作独立确认。
- 控制器总耗时 193.797 秒，JVM/Job 耗时约 184.359 秒；exit 0、无超时/软停止、owned tree 空、cleanupErrors 空。控制器退出资源快照 `Processes=[]`。日志 12:44:39 先记录本轮 PASS，再停止，12:44:40 记录全部维度已保存。

内容 SHA256：`67a716c2ddb693f3efc2bb55e57b1706a4881ba3afb3e27442fae1eaf45187a9`。方块实体 SHA256：`1ede819fc40a3dbb29c229d41143c43782e560bad93c7cc51477135ee531e7d7`。pin 坐标/材质摘要：`ea193fe009f0ed797eadc4bbc37ee0904388c67ad352406841a7403e7bfdda0f`。

| 实际证据 | SHA256 |
| --- | --- |
| `plans/e1entry-20261111-a/E1-ENTRY-COMPILE-RESULT.json` | `d291e9e9aab988b4029dbf523f41ffe4e3de268215ff86906ec280ea8308ca36` |
| `plans/e1entry-20261111-a/E1-ENTRY-RUNTIME-RESULT.json` | `8e4193d7f772b9724b3da602606719c4c18504bfbf9f9a9b09714f5c77e12f77` |
| runtime `evidence/n1-result.json` | `dd0ce2a0a64c6714d37b31b5737a4ab57c7a0c45da227107aafe8044a3d2aaaa` |
| runtime `evidence/n1-room-0.json` | `6f0e3f9e7fd67dcd8aefc933676da429993d1d16ad3fe1ac87d13c42ab1a1c3a` |
| runtime `logs/server-e1entry.log` | `6f6d3df9cafa520fd9608d3dad90e873e4b0825531b48f0558d47d89f6d9e22d` |

原生记录与独立房间 JSON 完整相等，房间内外 entrySmoke 相等，每轮 81 个 pin 区块复查完整；9 份完整房间 NBT 文本的 SHA 独立重算一致。本线程重新调用冻结控制器的只读 `audit_native` 核对通过，另由子代理核嵌套每次往返/内容/身份记录；原始 JSON/日志均保留。

未覆盖：真人登录/客户端 UI/完成通知、同档重启、实际卸载重载、盘上 region 内容独立检查、损坏 legacy journal 的 ready 入口例外、多房/多 owner、旧场景实体恢复、全部结构/变体及整包性能。169 条数据检查和 C 历史结果不算本轮原生成绩。

日志另保留三条 `useless_mod:blocks/useless_compact_l9/f9/c9` 战利品表引用未知同名物品的 ERROR；本轮没有调查或修改它们，不能描述为全日志无错误或所有战利品均验收通过。
