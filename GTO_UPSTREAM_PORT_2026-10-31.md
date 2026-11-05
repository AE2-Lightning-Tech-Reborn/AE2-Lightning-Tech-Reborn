# Forge 1.20.1 上游 2.1.3 → GTO 移植验证

后续 Core `2.0.4-gto.3` 的指定服务端复验已通过，见 [配套验收](GTO_CORE_GTO3_ACCEPTANCE_2026-10-31.md)。本文保留此前各冻结轮次及未完成矩阵的历史记录，不把旧轮次改标为新 Core 执行。

本轮为 LT `2.1.3-gto.1` 与 Core `2.0.4-gto.2` 的本地移植候选，尚未通过整体验收，未提交或推送。以下分别记录完整回归、定向复测和真实运行的精确制品，不用旧版本结果代替当前验收。

## 来源与兼容范围

普通 Forge `1.20.1` 从 `c568e6751b7e78bb556ba2a90dc7b3a736196f1d` 至 `59abcdba32cf0947cb25eb5afd3ad943d1f58e14` 的 17 个提交是本轮来源，覆盖上游正式版 `2.1.3`。不使用 GTL 分支，也没有额外引入 `main`／`alpha` 的跨版本功能。

保留 Forge `47.4.20`、Java 21、Dev23／Dev25、AE2-GTO `15.2610.31`／`15.2610.40` 及既有 NeoECO、JEMI、登录握手、原生 CPU 和大数任务适配。LT 网络协议为 `16`，服务端和客户端须使用配套版本。

主要移植内容：资源归属与回料保护；无线 FE 持久账本、准入、调度和网络管理；天枢统一请求路由、原生规划、上传别名及撤回；Useless 原生万象草稿；JEI 新旧 API 分流；晶簇实际采集产物与条件配方；夜视和移动包装器修复；过载 IO 按实际资源与扣电回执计费。

普通上游旧 CPU 的四个 Mixin 不注册：`ExecutingCraftingJobAccessor`、`CraftingCpuLogicAccessor`、`CraftingCpuLogicMixin`、`ECOCraftingCpuLogicMixin`。继续使用 GTO 原生 key flyweight，普通额外 key reuse Mixin 未启用。夹具仅进入独立 QA 制品；正式包不嵌入 Core、NeoECO 或测试代理。

按照 GTOCore `0.6.0` 固定提交 `b244589d2407362cdc4d60f1fb270db61b0c11b4` 的文档，已缓存反射访问器、采用引用节点索引与原始 int 频率容器，修复实际 GTM 能量 API 及 AE2 客户端双 ABI。依据、边界和验证见 [优化说明](GTOCORE_OPTIMIZATION_2026-10-31.md)。

## 精确制品

r9 为当前正式包；r10 仅修正迁移 QA 的生产字段映射和用例清单，正式生产字节码及冻结正式包哈希不变。r7 保留此前库存 ABI 图形结果，r6 为完整 JUnit 与此前真实专项包。各轮版本号相同，必须按哈希区分。

| 制品 | 版本／轮次 | SHA-256 |
|---|---|---|
| LT 正式包 | `2.1.3-gto.1` r9 | `358ba472560aa2110a25e9592f880072a85d0a900bd378e6733ad747abbd8f7d` |
| LT 附加夹具包 | r9 | `8495d395b838679dd8d85d803ad3d2b226102d2f8f9ee965f19ddc426564df24` |
| 独立 QA | r9 | `e028f4a86c813ea117b41c5514ab96309d45c736c1799160fdbadc3aee2d6438` |
| LT 附加夹具包 | r10，正式包仍为 r9 | `28c4657692e1c863254184f829c522cc541a7ec3d2d0ad7d5e260c0a0fefbe41` |
| 独立 QA | r10 | `2efe8c820ade4c291b44a3e80695e263ac225ea664983da0301089e56fe3fd13` |
| LT 正式包 | `2.1.3-gto.1` r7 | `a1d89d2424174b026da7a6203d285d14fbf66f035a5a249dec75fb08c06950d9` |
| LT 附加夹具包 | r7 | `cd9bcd8d21d42cc31af29ce5427613040fc6b0993b292a3a10395f23e3739bab` |
| 独立 QA | r7 | `121d2bb89c00ed019116ed3413c9e04960d1e0be66c9741ec9e5dd08a1d54dad` |
| LT 正式包 | `2.1.3-gto.1` r6 | `43fa1607363cc88f5de094a29a23ee4423ad806d61560e98e26a568fe35f101d` |
| LT 附加夹具包 | r6 | `9466ea8f9eef97f29e03dabf1a8dfb6e7abb8d531778a27eee4d2cd89980514c` |
| 独立 QA | r6 | `52244a57d3dd8ee6cc3dc64d3ace82e1cf015bf114c131a4163c2e38bf037a70` |
| Thunderbolt Core | `2.0.4-gto.2` | `868167da2d511f3136dc6b72b5619e479117fdf656157055d69f1f33850b06c5` |

Core 来自成员交付提交 `b09da7fdb741d4e96c1d12344735926e5de5b54f`。其 Maven、映射输入与专服实际 JAR 已核对；Core 自身测试报告不代替 LT 联调。此前 `.1` 的结果属于历史候选。

r7 相对 r6 的生产字节码变化限于库存兼容桥、维护终端、模式终端和 JEI 转移处理器及其内部类；2334 个生产类逐字节不变，其他资源仅清单变化。r6 专项仍标记为 r6 实跑，不改写为 r7 已运行。

r9 相对 r7 保留 2347 个相同生产类；改动限于 NeoECO 闪电保护、Mixin 插件及 JEI 父屏桥。资源差异仅清单与移除旧闪电目标后的 refmap。新正式包共 2354 个生产类，与测试副本逐字节一致；此前结果仍分别归属原制品。

## 已执行的验证

| 检查 | 轮次 | 实际结果 |
|---|---|---|
| 新版 NeoECO 字节码、Mixin 门控与 JEI 定向回归 | r9 | 34／34 通过，0 跳过；包含两个实际 NeoECO 发行件 |
| 主源码、QA、客户端探针编译及正式包校验 | r9 | 通过；2354 个生产类、229 个附加 QA 类，1438 个 JSON，Java 类版本不超过 21 |
| Dev25 实际 NeoECO／EAEP／EAE | r9 | 8／8 通过，无超时；真实库存、催化配方及三条闪电转换路径均通过 |
| Dev25 迁移快组 | r9 | 实际 3／13 通过；10 项在夹具玩家字段反射处停止，清单还漏列了已有 NeoECO 用例，原 expected=12 也不符 |
| Dev25 迁移快组 | r10 | 13／13 通过；按实际 SRG 字段登记玩家，并以真实 `getPlayer(UUID)` 核对在线／离线；原物理与资源断言保留 |
| Dev25 迁移慢组 | r10 | 2／2 通过，原 6000 tick 预算不变 |
| Dev25 原生批量派发 | r10 | 0／5 通过；均在 `AEKeyLongMap.addTo(AEKey,long)` 链接处失败，尚未执行其后行为断言，继续定位 |
| Dev23 无线快组 | r9 | 19／19 通过；暂停与恢复检查通过，真正磁盘卸载不在快组计通过 |
| Dev23 无线准入慢组 | r9 | 3／3 通过，含原 1600 tick 内三个真实磁盘卸载／恢复循环；每轮保留旧能力失效、新对象及 FE 守恒断言 |
| Dev23 实际 FX 网络 | r10 QA、r9 正式包 | 通过；8 台 F9 主机、88 个实体 FX，精确 `2^100+37` 次合成、四倍产出，原料余量与重复回执均为 0 |
| Dev25 FX 加装 AppFlux `1.3.7` | r10 QA、r9 正式包 | 启动失败，30.1 秒；AppFlux `FluxKey` 覆盖 AE2 `.40` 的 final `hashCode`，尚未进入 FX 行为；保留失败后按真实 FluxNetworks、无 AppFlux 组合单独复测 |
| Dev25 实际 FX 网络，无 AppFlux | r10a QA、r9 正式包 | 通过，80.6 秒；实际 FluxNetworks、8 台 F9 主机、88 个实体 FX，同样精确 `2^100+37` 次合成及四倍交付，原料余量与重复回执均为 0 |
| Dev23 运行任务跨 JVM 恢复 | r10 QA、r9 正式包 | prepare／resume／verify 三个独立 JVM 均通过；同一任务 UUID，3 份原料消费并交付 3 份输出，第三 JVM 不复活完成任务 |
| Dev25 运行任务跨 JVM 恢复 | r10 QA、r9 正式包 | prepare／resume／verify 三个独立 JVM 均通过；同样验证原 UUID、3 份精确交付及第三 JVM 不复活，不以 Dev23 结果代替 |
| Dev23 双人 TCP 图形与确认页 | r10 | 通过，171.4 秒；两名独立离线身份各实际登录 1 次，服务端收到共 8 个唯一玩家／阶段／状态回执，双客户端与服务端均正常退出 |
| Dev23 双客户端连续登录 | r10 | 整组失败，281.7 秒；首名客户端实际 8／8 次，次名在连接前因 Python 子驱动 GBK 输出异常中断，0／8 次；保留失败收据后以显式 UTF-8 子驱动重跑 |
| Dev23 双客户端连续登录 | r10a，同 r10 制品 | 通过，279.8 秒；服务端各记录 8 次实际登录，共 16 次；两客户端各有 8 次渲染检查、准确最终完成标记和新截图 |
| Dev25 官方 MGU `1.20.1-1.1.0` | r10 | 选中 4 项、实际执行 0 项；QA 准备阶段同步加载区块触发 120 秒看门狗，未进入 MGU 行为断言 |
| 主源码、QA、客户端探针编译及重混淆打包 | r7 | 通过 |
| 完整 JUnit | r6 | 1872 项：1851 通过、7 失败、14 跳过 |
| 自适应批量压力测试 | r6 | 6／6 通过，无跳过 |
| 精确实际 API 审计 | r6 | 20 项：12 通过、8 失败、0 跳过；19 个实际 JAR |
| 优化、GTM 桥、客户端双 ABI 定向 JUnit | r7 | 15／15 通过；不与完整回归重复相加 |
| 正式包和 QA 包校验 | r7 | 通过；2351 个生产类与测试副本逐字节一致，1438 个 JSON，Java 类版本不超过 21 |
| Dev23 管理专项与真实 GTM 能源仓 | r6 | 10／10 通过，原 AppFlux 初始化崩溃已消失 |
| Dev23 实际 AppFlux 能源 API | r6 | 29／29 通过 |
| Dev25 资源专项 | r6 | 15／15 通过；通过真实 FRS 配方索引选择后执行原消耗和产出断言 |
| Dev25 大数整合 exact | r6 | 18／19 通过，Core 规划器键链接失败 |
| Dev25 实际绝对世界路径 reload | r6 | 0／1 通过；原生 loot 也未改变，见下文边界 |
| Dev23 无线 | r6／r7 | 均 18／19 通过；r7 联合卸载断言仍失败，继续诊断 |
| Dev25 EMI 上传别名真实图形 | r7a，同一 r7 包 | 有线／无线完整终态均通过；真实 EMI 来源选择、配方转移和目标选择各 2 次，4 张新截图齐全 |
| Dev25 JEI API 经 JEMI 的图形探针 | r7 | 2／20 后在夹具的 `RecipesGui` 强转失败，后续 18 项未执行，未生成截图 |
| Dev25 JEI API 经 JEMI 的图形探针 | r9 | 3／20 后停止：实际 JEMI 按原始 UID 查找 EMI 分类，JEI `minecraft:furnace` 未打开；后续 17 项未执行，无截图 |
| Dev25 实际 Useless 万象终端图形 | r9 | 在 phase 2 获取 `useless_mod:advanced_alloy_furnace` 的 JEI 分类失败，0／12 完成摘要、无截图；正在核对真实插件注册和依赖 ABI |
| Dev25 实际 NeoECO／EAEP／EAE | r7 | 2／8 通过；3 项因原版配方缺席停止，3 项引用了 NeoECO 已删除的事件入口 |
| 其余图形、双人、FX、跨 JVM 恢复和可选组合 | 当前配套 | 尚未全部执行，不计为通过 |

实际 API 审计的八项失败包括完整 JUnit 同样的七项 Core NeoECO 回归及一项双 AE2 键 ABI 检查，不能重复累计缺陷或当作真实模组行为。完整 JUnit 的十四项跳过包含默认关闭的实包审计和被 GTO 替换的旧映射 CPU 用例；实包审计另行启用。

完整回归任务因失败退出 `1`；后续定向编译、打包与包校验成功不改变这一结果。发行检查覆盖版本和依赖范围、NeoECO 可选声明、许可证与署名、ZIP／JSON／嵌套包、Mixin／refmap、Java 类版本、QA 排除和测试副本生产类一致性。两种 `getAllEntries` JVM 返回描述符已经在 r7 正式字节码中确认。

## 失败与验证边界

Core `NeoEcoGtoBatchAdapter` 的七项契约失败涉及拒绝 API 发现、畸形公开 ABI、合法默认回调、无 job、明确拒绝后的回退和容量，以及非法 admission 的异常类型。默认回调失败使部分后续不确定提交断言未能到达，不能声称已证实所有后续错误。非法数量已在 submit 前拒绝，当前该项仅异常类型不一致，不能单凭它认定资源复制。

Dev25 实际 AE2 `15.2610.40` 的 `AEKey` 继承 `IdentityKey.final hashCode()`，Core 的 `IdOnlyInputGroups$DemandKey` 与 `LateBoundOutputKey` 仍覆盖该方法。实际规划触发 `IncompatibleClassChangeError`；`.31` 允许的实现不能直接用于 `.40`。须由 Core 同时维护两版键语义，不能仅删除 hashCode 却保留不相容的 equals。

r10 原生批量五项均在 Core `ParallelBatchCpuHelper.bulkExtract` 的旧
`AEKeyLongMap.addTo(AEKey,long):long` 调用停止；该组尚未到达 final-hashCode
路径。`.40` 的原生 map 继承以 `IdentityKey` 为参数的方法，两版仍有共同的
`Object` 擦除桥。Core 两个类共有七处相关 `put/addTo` 调用，须一并处理并
保留实际 native map、返回值与账目。冻结正式 LT 的 2354 类没有此类直接调用
或方法句柄；不通过修改 LT 提取资源语义掩盖 Core 链接错误。

额外安装 AppFlux `1.20-1.3.7-forge` 的 Dev25 FX 启动在加载其 `FluxKey`
时触发同类 final `hashCode()` 继承错误，NeoECO 因引用该键而连锁失败。
这是 AppFlux 发行件的另一条 ABI 边界，不能把 Dev23 已过的 GTM 能量桥
当作 `.40` 已兼容；FX 所需的 FluxNetworks 与 AppFlux 分别记录。

r6 无线失败为重载端点未供电。旧夹具仅用临时 chunk 票据；r7 改为持票初始化、等待真实卸载、重新持票加载和实际供电条件推进，总预算仍为 120 tick。r7 的联合断言仍失败。只增加诊断的 r8 保持 r7 正式包和全部断言：首次 `getChunkNow == null` 为第 37 tick，至第 121 tick 仍为 oldRemoved=false、capPresent=true、capInvalidations=0、unloadEvents=0，旧端点投递为 0。这说明在该预算内尚未观察到真正卸载，不能据此认定 GTO 的 capability 清理漏失。r9 按实际 80 tick 卸载节流拆分原快慢项，未改生产缓存、预算或三个循环；快组 19／19、慢组 3／3 实跑通过。历史失败保留，断言分配与日志限制见 [无线 QA](GTO_WIRELESS_CHUNK_QA_2026-10-31.md)。

晶簇 reload 专项已经越过 Windows 相对／绝对路径问题，但期望 quartz×16 时 LT 和原生掉落都仍返回 amethyst_shard。GTOCore 文档 §38 明确固定包、不支持 `/reload`；实际 GTOLib 复用静态 loot 缓存，新 `LootDataManager` 也未重新解析。当前没有 LT 独有故障的证据，不用 LT 全局补丁改变整合包约定；该专项仍记失败。

GTO 实际 `StorageHelper` 的模拟不限制电量，执行后扣电又忽略回执。本轮过载 IO 使用模拟资源和可用电量、实际提取与扣电回执来限制目的插入，并返还或保管余量。目的端执行拒收时 `IEnergySource` 没有通用退款 API，按已支付尝试量扣电，不能宣称能源也无损退款。

图形 r7 首次启动因任务驱动的 Python 输出编码错误中断，业务断言尚未执行；保留原证据后以 UTF-8 和新世界重试，同一包的 r7a 完成整组，先前维护终端 `AbstractMethodError` 已消失。该结果不代替尚未执行的独立 JEI、Useless、双人和其他图形矩阵。

r7 的 JEI 探针另有真实 JEMI 类型处理错误；两版整包都由 JEMI 实现 `IRecipesGui`，本轮改为公开接口浏览、实际页面关闭与原厂 JEI 按钮／服务验证，保留 20 项行为和原预算。JEI 服务路径还暴露别名编辑器父屏识别缺口，生产代码通过 EMI 存在检查和公开 `EmiApi.getHandledScreen()` 桥接，仍检查菜单与玩家身份。此范围不能称为原生 JEI 界面或 EMI 实际按钮路由已测。

r7 实际可选组的失败也确认 LT 闪电保护 Mixin 指向了已删除的 NeoECO 事件类。两版实际 NeoECO 已将转换放入 `LightningBoltMixin.onLightningLand`；修复只包装归属明确的唯一方块替换，保留随机判定、附近转换和闪电生命周期。迁移夹具改用真实 QA 配方，仍要求一原木到四木板、真实编解码和原物理库存断言。r9 实际组 8／8 通过；闪电专项调用真实合并回调以隔离随机数，实体进入世界与 LT pre-tick 仍由各自 GameTest 覆盖，不混称完整实体生命周期专项已跑。

AppGen 尚未找到官方 Forge 1.20.1 发行件。MGU `1.20.1-1.1.0` 已通过 CurseForge 文件 `5131447` 的官方 ForgeCDN 取得，大小与公开元数据一致，ZIP／模组版本／目标类已核对，SHA-256 为 `6e0fa6a245fd56cce99d60ac908b5028bc6392a59833c45e0bc3e27ec7f0a2f9`。r10 已实际加载该包，但在 QA 准备区块时阻塞，4 项行为均未执行；已有界异步准备修正待打包复测，未扩大看门狗或测试预算。没有用跨版本包或合成形状包代替真实模组。EMI 在两个 GTO AE2 版本中均为必需依赖，JEI 验证须保留 EMI 并执行独立 JEI 探针，不能通过禁用 EMI 伪造 JEI-only 环境。

按后续 PR 上传授权，当前候选以单一汇总提交的草稿 PR 供审阅；Core `.3` 的指定服务端结果与本篇历史失败分别报告。图形及其余矩阵尚未完成，不自动合并或发布正式版本。没有修改其他项目或 `AGENTS.md`。
