# 2i 素材与参考记录

- 皮肤：用户提供 `D:/AI/玩偶/皮肤.png`，实体统一使用 `assets/useless_stretcher/textures/entity/two_i.png`。
- 剧情立绘/CG：用户提供 `D:/AI/玩偶/图片 压缩后`，使用压缩原图，没有扩图。
- 互动体验参考：用户提供 Poppet Daily 0.9.0d（Breezeth，元数据声明 All Rights Reserved）。仅以“动态小玩偶、摸头反馈、手持也活动”等体验为参考；当前模型由 Minecraft PlayerModel 的标准 Steve UV 和本项目自行编写的大头短肢变换构成。走路支撑脚、十二种坐姿待机、皮肤外层、摸头压扁回弹均为 Java 程序动画，不使用 Poppet 的模型、骨骼/动画 JSON、私有运行库、代码包或嘴巴资源；不把它或 GeckoLib 列为依赖。
- 三段摸头音效：`two_i_pat_1/2/3.ogg` 已完全替换为本项目原创的双音/三音短旋律。仅由正弦谐波、包络、轻微滑音与固定种子的低通噪声数学合成，不读取、截取、变调或混入任何参考模组音频。历史本地测试包曾使用 Poppet 的三个音效，不能把历史包当作已清理的当前构建发布。
- 剧情短音节：`two_i_voice`（自然）、`two_i_voice_soft`（害羞/柔和）、`two_i_voice_bright`（开心/俏皮）及独立的 `player_voice` 均为原创数学合成。2i 的变体不仅改变音高，还改变谐波、音头和力度；角色台词的实际逐字时刻、情绪起伏与停顿由游戏内独立控制。未使用《逆转裁判》或任何模组的声音素材。
- 音频复现：`generate_dialogue_blips.py`，固定随机种子 `2026101002`，依赖 NumPy / SoundFile；相同依赖版本可复现。声音事件及原始音频均属于本项目，不需要安装 Poppet。脚本另在 `build/audio-preview` 生成 WAV 试听文件，该目录不进入正式 JAR。
- 对白同步：`TwoIStoryTimeline` 统一输出揭字与声音 Cue，`TwoIStoryDirection` 为六章 90 句对白/玩家选项分别配置情绪，并对六句安排中英繁语义转折。`render_dialogue_voice_preview.py` 可把客户端测试导出的实际 Cue 合成试听，不另写一套演示节奏；快进/关闭不留声音队列。
- 音频清理核验（2026-10-10）：替换前，三个摸头文件分别与参考包 `assets/poppet/sounds/toy_squeak_1/2/3.ogg` 的 SHA-256 完全一致；替换后，将当前全部 96 个生产资源文件与参考包 549 个非空条目逐一比对，完全相同文件数为 0。7 个新 OGG 连续生成两次的 SHA-256 一致，且全部能正常解码。哈希核验仅证明未保留相同字节文件，不替代对模型、代码或其他用户提供素材的来源审查。

2026-10-12：读取用户目录 Poppet 的模型尺寸及 Kaleidoscope Doll Workshop 的坐姿比例作视觉参考。当前九像素躯干、统一四肢、坐姿落地、惯性、第三人称掌心、十二种待机和天气动作均为本项目 Java 实现；伞及鼻涕纹理由本项目程序绘制，眨眼由用户皮肤眼部像素生成，不复制参考模组资产。
