# Forge 1.20.1 · 20.1.6.2 验证证据

本地精选证据；完整通过、早期失败日志及依赖保留在 `<WORKSPACE>/archive/forge1201-doll-fullport-20261024/`。
游戏场景必须检查对应 PASSED 标记，不能只看 Gradle 退出码。最终客户端没有再出现区块保存并发异常。
JUnit 共六项，failures/errors 均为零。发行 JAR 已分别对构建和交付副本审计，测试类没有混入发行包。
`manifest.json` 给出本目录原始日志、JUnit、审计和关键截图的大小及 SHA256。
所有 96 张剧情章节截图与其余动作帧保留在隔离 run 目录，本目录仅保存精选。

## 发布结果

提交与标签均为 `fc0dda8fa01eb4f17ac3e3c4557798d5c17b83d5`。
[GitHub CI](https://github.com/21i0/UselessStretcher/actions/runs/40065075500) 已成功，直接使用官方本体发布包。
[正式 Release](https://github.com/21i0/UselessStretcher/releases/tag/v20.1.6.2) 已公开，非草稿、非预发布；不替换主线 latest。
`release-verified.json` 记录四个线上附件逐项大小及 SHA256 校验通过；说明附件改用英文名以避免 GitHub 中文文件名归一化冲突。
