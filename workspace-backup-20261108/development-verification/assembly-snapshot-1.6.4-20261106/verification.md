# 1.6.4 样板总成安装快照验证

用户确认：不同步，保留装入时的样板库。仅样板总成改为独立快照；小合金炉、被动合成仓保留实时共享。

## 已完成

- Java 21 生产源码编译通过，`compile-java21.log`；后续修改再次随测试和最终构建编译。
- `unit-tests-bindings.log`：40 项纯 JVM 测试通过，0 失败、0 错误。JUnit XML 保存在 `unit-results/`。
  - `AssemblyStretcherInventoryTest` 12 项：实际 NeoForge/本体库存操作、模拟不分叉、占用槽防覆盖、余量守恒、取出重装、镜像同步、迁移和递归保存。仅物品身份及世界服务采用替身。
  - `InstalledStretcherBindingTest` 6 项：真实 ItemStack/CustomData、UUID 与维度解析、重装隔离和不可读来源拒绝；世界查找采用替身。
  - `InstalledPatternSnapshotTest` 16 项：真实样板库 COW、增删、清空、Rewrite CAS、顺序与监听隔离；AE key/纯变换采用替身。
  - `PatternSnapshotPersistenceTest` 6 项：真实 vanilla ItemStack/AEItemKey Codec、非空重读、格式 2 和更早 patterns 迁移、坏索引/缺少物品 Codec 保留原 NBT、未来格式保护。
- `compile-regression-source.log`：含更新后的真实游戏回归源码编译通过，未执行运行任务。
- `build-candidate.log`：普通 `build` 通过，未启用游戏测试源码集合；同一已通过的测试任务为 UP-TO-DATE。
- 对无用之物 2.4.6 和实际 2.4.6.2 做了只读字节码核对：总成构造中唯一的 RecoverableItemStackHandler 创建点及三参签名匹配；原生恢复直接写库存，不经过安装 setter。

## 独立测试环境

仅启动 Gradle/JUnit JVM，没有创建 Minecraft 客户端、服务端、世界或启动 ModLauncher。
`VanillaTestRegistries` 在 bootstrap 期间提供空模组列表；测试资源中的空英文语言表只用于 vanilla 静态初始化，均不会进入生产 JAR。此处不能验证真实模组注册时序。

本机依赖仓库解析缓慢，使用 `local-test-runtime.gradle` 引入 `test-libraries/` 中的 12 个官方 Maven Central 测试依赖。下载来源和 SHA256 见 `test-libraries-manifest.json`，仅作用于 test 配置。复现：

```powershell
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
.\gradlew.bat test --offline --no-configuration-cache --max-workers=2 --console=plain '-Dorg.gradle.jvmargs=-Xmx1G' -I 'verification\assembly-snapshot-1.6.4-20261106\local-test-runtime.gradle'
```

早期失败日志全部保留：依赖缺失、init 路径、FML 静态列表及缺少 vanilla 语言资源的失败属于独立测试环境初始化问题；后续已修正，没有删除失败证据。

## 使用及迁移边界

每次实际装入总成创建新 UUID；模拟、读取已迁移槽及原生 NBT 恢复不创建新 UUID。旧 live link 只能在首次读取/保存时截取当前仍能读取的源库，无法恢复最初装入时的历史内容。

样板库存档升级为格式 3，源和快照共享未变状态以避免反复复制大库；后续写入隔离。格式 2 和旧 patterns 可读。不可解码状态保留 UUID 与原 NBT；新安装提前拒绝不可读来源。

请使用完整存档副本测试。旧 1.6.3 无法安全读取新格式；回退必须恢复升级前完整备份。

## 尚未验证

本轮没有启动第二个 Minecraft 实例，未执行真实 Mixin 启动、客户端/终端点击、自动化交互、整机创造复制或新版本的同存档进程重启回归。独立单测及 ABI 审查不能替代这些验证，也不覆盖所有第三方组件 Codec。

421 mod 启动性能诊断单独保留在 `../startup-catalog-1.6.4-20261106/diagnosis.md`，本轮没有把诊断方案实施为性能修复。

最终包条目审计、版本、旧发布哈希与交付路径见本目录审计及交付 JSON。1.6.4 为本地候选包，尚未提交、推送或发布；已发布 1.6.3 保留。
