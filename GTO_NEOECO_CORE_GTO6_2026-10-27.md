# NeoECO 与 Core gto.6 配套验证（2026-10-27）

Core 的 NeoECO coremod 先将远程 UI 的两处客户端世界读取转换为 `NeoEcoClientLevel.current()`，LT 原转换器随后仍要求找到两处旧读取，抛出 `found 0` 并阻止专服启动。LT 现识别并原样保留完整的 Core 转换，也支持自身转换重复应用；数量、方法描述符、静态调用、辅助类归属或残留客户端引用不符合约定时仍拒绝，不会部分修改字节码。旧正式 NeoECO 的转换路径和上游客户端保护分支均保留。

验证环境为 Minecraft 1.20.1、Java 21.0.10、Forge 47.4.20、GTO 0.6.0-dev-23、GTOCore 26.10.10、AE2-GTO 15.2610.31、LT 2.1.2-gto.9、Core 2.0.3-gto.6，以及未修改的正式 NeoECO 20.5.0-gto.1。

| 检查 | 通过 | 跳过 | 失败/错误 |
| --- | ---: | ---: | ---: |
| JUnit，共 1580 项 | 1575 | 5 | 0 |
| 自适应批量压力模型 | 6 | 0 | 0 |
| 发行流程契约 | 12 | 0 | 0 |
| 安装 NeoECO 的 GameTest | 122 | 0 | 0 |

JUnit 跳过项为 4 项未配置 EAEP 实际包夹具的用例和 1 项需 ModLauncher 环境启用的原生 CPU 用例。远程 UI 定向回归先在旧实现上出现 2 项失败，修复后 10/10 通过。

| GameTest 套件 | 通过/预期 |
| --- | ---: |
| NeoECO 专项 | 2/2 |
| 基础 CPU、时轮与种子循环 | 11/11 |
| 精确数量 | 19/19 |
| 批量适配 | 5/5 |
| 候选索引、零 tick 与延迟回料派发 | 20/20 |
| 权限 | 5/5 |
| 接口公平分配 | 6/6 |
| 过载 IO | 40/40 |
| 无限存储 | 1/1 |
| 内容与配方 | 13/13 |

各组均使用隔离专服，满足 CPU、STORAGE、PLANNER 探针通过、预期的非零测试数量、零失败与正常保存退出。NeoECO 专项覆盖真实 F4 主机批量接收、工作线程 NBT 往返及取消回收，以及正式 facade 的 101 位数量保留、同步产物回流和拒收。

正式 LT 主包分别在安装 NeoECO、保存后重新启动同一世界、未安装 NeoECO 三种情况下通过加载与桥接探针，并正常退出。这些启动检查的 `total=0` 不计入 GameTest 数量，也不表示正在执行的合成任务已通过跨 JVM 恢复测试。

行为测试使用附加一次性夹具的 QA JAR；其中 2138 个生产类与正式 JAR 逐字节一致。正式 JAR 的依赖元数据、4 份 Mixin 配置及其类/refmap、1484 份 JSON/mcmeta、许可证和嵌套 JAR 检查通过，且不包含一次性 QA 夹具。与修复前主包相比，仅远程 UI 转换类与构建清单发生变化，未增删归档项。

| 当前验证制品 | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.9.jar` | `d8b23834008c172bb0662b4c5f15b2c6ae21c2363e03fd9a54ef63d3e7ca794d` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.6.jar` | `85ae8b28afacd951d19af79a1ad8708d01f29e834e17d7e0448772ed4bbddd1f` |
| NeoECO 正式包 | `24f60175359c65d688f24ec65feb2316ced73800486127f77bcec97d392b77b4` |

上述 LT/Core 与历史记录使用相同版本号，须按哈希区分。构建执行了 `build adaptiveBatchStress jarJar gtoQaJar gtoQaLtJar -PgtoQa`，另运行发行流程契约；使用现有离线依赖缓存，未改动依赖仓库配置。原始证据保留在本地 `build/ae2lt-gto-neoeco-fix-20261027/`，包含逐组日志/JSON、实际加载制品哈希、冻结制品和源码补丁；不将日志、存档或本机路径加入仓库。

图形客户端远程 UI、独立客户端多人联机及真实 FX 虚拟网络尚未覆盖。冻结整合包未安装 Industrial Foregoing、Mekanism 或 Useless Mod，未执行对应可选机器套件。第三方 Mixin 缺少 minVersion、LowDragLib 编辑器注解扫描触及客户端类等非致命诊断仍保留在日志中；本轮加载与行为断言通过不代表日志完全无报错，也不代表所有可选附属均已验证。
