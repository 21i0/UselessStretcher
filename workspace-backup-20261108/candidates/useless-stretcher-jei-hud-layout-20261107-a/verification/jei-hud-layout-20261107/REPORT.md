# JEI HUD 布局局部候选：离线检查
版本：1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud。冻结状态：FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED。
JAR：D:\AI\candidates\useless-stretcher-jei-hud-layout-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar
SHA256：E0E15336F1FFA8D1F9AC1839B7A29509BB2D6ECDC03C502AE92B1B4251048FB6。大小：12813852 字节。

本轮只改变通知布局：在上方居中，按逻辑GUI尺寸限制宽高，支持两行与省略。仅新增Overlay及Layout record、修改Lifecycle.progress和版本；Lifecycle其余类结构和方法在去调试信息后相等。Publication、语言、调度预算、样板总成及其它业务成员全部保持中文基线原字节。

本轮 21 项专项离线检查通过，输入/编译类/检查日志/结果/JAR哈希已关联。静态确认一次委托渲染、原角落绘制移除、正常与异常退出都恢复scissor，不调用Xaero或世界方法。没有重跑184项，没有启动Minecraft或更改运行实例。实际GUI4、窄屏和小地图重叠仍需客户端确认；本包不含性能修复。

VAT由来源任务删除，不是本候选修改。目录总数随输入变化，不固定155024，不能用VAT输入不同的旧P3作单变量性能比较。
审计：D:\AI\candidates\useless-stretcher-jei-hud-layout-20261107-a\verification\jei-hud-layout-20261107\audit-runs\20261107-145346981\jei-hud-layout-audit-report.json

实际几何检查包括 1280×960 下 GUI2/GUI4、10000 个视口/字体组合、极小高度/宽度、整数极值，以及真实公开 StringSplitter 的中英文长文本、两行省略、emoji 代理对、样式保留和窄宽矩阵。GUI2 的逻辑框为 x=160、y=6、宽320、高28；GUI4 为 x=80、y=6、宽160、高28（测试字体行高9）。

小地图不相交检查只针对来源提供的矩形 (12,43)–(254,283)，分别按物理像素和逻辑GUI坐标解释验证；没有覆盖任意 Xaero 位置、尺寸、自定义字体或其它覆盖层，也没有真实渲染截图证据。

本包相对精确中文基线新增2个、修改2个、删除0个成员，676项原字节不变。首次审计运行 `audit-runs\20261107-145331355` 因独立ASM辅助程序的局部变量推断类型导致编译失败；只修正审计脚本后在上述PASS运行冻结，生产源码、检查源码、JAR和构建记录均未改动。
