# 供应器机器适配模式

现有齿轮“高级设置”（用户所说的更多设置）新增独立总开关，默认开启，同时控制 Mekanism 物品槽保护和 Industrial Foregoing（工业先锋）的物品/流体回收保护。仅作用于 AUTO 周期回收和投料前回收；关闭后仍执行通用 AUTO 回收。正常投料、EJECT、原生 MEStorage 优先级及第三方 MachineAdapter 原接口保持原行为。

## 设置与兼容

- `MachineAdaptationEnabled` 随供应器保存；旧存档缺字段时开启，包括复用对象重读旧 NBT。
- 设置变更通知逻辑重排轮询，持久化并发送方块更新。服务端菜单 action 修改设置，`GuiSync(22012)` 同步界面，方块流也包含该字段。
- 记忆卡复制开/关；旧卡没有字段时按现有部分复制惯例保留目标值。
- 两个供应器访问同一目标且开关不同，包装缓存也按开关状态刷新，不能串用。
- Titanium 是 compileOnly 可选依赖。运行时只有同时装有 Industrial Foregoing 与 Titanium 才进入其适配类；不把依赖或 GameTest 打进发布 jar。

## 真实依赖调查

本次检查实际解析到的 jar 字节码，而非按类名或网上示例猜测：Minecraft 1.21.1、NeoForge 21.1.262、Industrial Foregoing `1.21-3.6.39`（Curse 文件 8370717）、Titanium `4.0.50`（8760562），Mekanism `1.21.1-10.7.19.85`，Thunderbolt `2.0.3-beta3`。测试另装 Mekanism Extras 和 More Machine 1.4.0（8659501），实际执行无限加压反应工厂案例。

Titanium `FluidTankComponent` 的 `Type` 是 GUI 大小，`Action` 才限制注入/抽取；默认 `BOTH`，并不代表产物。`MultiTankCapabilityHandler.drain` 逐罐寻找第一个可抽取罐：若同种流体同时位于输入和产物罐，会先抽到输入。

已确认的实际来源包括：

- `DissolutionChamberTile` 的 `input_fluid` 未设置 Action，沿用 BOTH；`output_fluid` 明确 DRAIN。
- `LatexProcessingUnitTile` 的 `latex`、`water` 都沿用 BOTH。
- `EnchantmentApplicatorTile` 的工作用 essence 罐沿用 BOTH。
- `AnimalRancherTile` 的 `fluid_output` 也沿用 BOTH，但它确实是产物，必须单独允许。
- Titanium 物品组件也没有统一的“产物”枚举；如矿物激光基座镜片槽允许抽取（默认侧面关闭，可在机器侧面配置中打开）。不能因为可抽取就视为产物。

## 流体边界

对 IF `ActiveTile` 的已知 Titanium 罐元数据，只允许 Action.DRAIN，以及核对过的动物饲养机 `fluid_output` BOTH 特例。FILL/NONE 和其他不明确的 BOTH 不参与 AUTO 回收。同种流体按罐身份选择，不按流体 key 过滤后再交给总罐抽取。

每次操作重新检查世界实际暴露的侧面能力。只有机器继承未经覆盖的 ActiveTile 流体入口、该能力恰好是 Titanium 标准 MultiTankCapabilityHandler，且与机器该面的原生 handler 是同一对象，才按已验证的组件语义访问产物罐；同时检查实时 FaceMode。缺失能力、不同的权限包装器或自定义实现会对该已识别机器的流体回收关闭访问。绝不改用无面能力，不使用 `drainForced`/`fillForced`。

没有 IF 命名空间、没有 ActiveTile/罐元数据的目标使用通用回收。无法识别用途的 BOTH 罐被保留在机器内，可通过关闭适配恢复通用回收。

## 物品边界

通过具体机器 ID 与核对过的组件名表识别产物；表见 `IndustrialForegoingOutputItemHandler`。常规机器只允许 `output` 组件，另允许污水堆肥机的 `fertilizer`、附魔分类机的 `noEnchanted`/`enchanted`、附魔提取机的两个明确产物组件。只含原料、种子、工作物品或充能物品的已识别机器没有允许的产物组件。

每次操作取当前侧面的 MultiInvCapabilityHandler，由其 `getFromSlot` 定位组件，再委托该实际能力执行操作。因此保留机器的插入/抽取谓词、侧面开关和即时槽位变化，补偿也不能写入输入/镜片槽。

**未列入表的机器保留通用物品回收。** 例如石料工厂 `material_stonework_factory` 的中间阶段可成为配置后的终点，不能只把 `inventoryFour` 当产物；本次不猜测其多阶段配方终点。已识别机器若换成无法识别组件的能力包装器，则其专项物品视图为空，避免绕过包装器权限。原生 MEStorage 或第三方专用适配器仍走原有权威接口，不由此包装重新解释。

## 验证

运行：

```sh
./gradlew --no-configuration-cache \
  -I scripts/provider-machine-adaptation-test.init.gradle \
  -Pae2ltProviderReturnThunderboltJar=/path/to/thunderbolt-2.0.3-beta3.jar \
  test runGameTestServer jar
```

若匹配的 Thunderbolt 已通过正常 Maven 依赖提供，可以省略 jar 参数。

最终实跑结果：190 项相关 JUnit 测试全部通过；26 项带工业先锋的 GameTest 全部通过；无工业先锋/Titanium 的 10 项 GameTest 全部通过。`jar` 构建成功，包内不含 Titanium 类或 GameTest。

推送 alpha 前另以 `fea5eead` 为基线整合复验：保留 alpha 的 NeoForge `21.1.220` 及原有依赖配置，仅增加本功能所需 Titanium compileOnly 依赖；190 项 JUnit、26 项含工业先锋 GameTest、10 项不含工业先锋 GameTest 再次全部通过，jar 构建和资源 JSON 检查通过。alpha 原先尚未包含 Mek 输出保护，因此本次汇总同时纳入该前置改动，没有整合 main 的其他变化。

本次专项验证覆盖：

- 默认开启、保存/加载、缺字段旧 NBT、记忆卡双向复制与旧卡、方块流开/关同步、服务端菜单动作和 GuiSync ID 冲突检查。
- IF 物品/流体分别在相邻/无线、过滤/不过滤四组合中回收；开启→关闭→开启；输入/产物同种；多个产物物品槽及多个同种流体产物罐。
- 真实侧面禁用/启用、抽取/插入谓词、同 tick 访问同目标但设置不同、流体过滤不匹配、输入型机器、无法识别石料阶段的回退。
- 实际网络没有存储单元且回收缓冲已满：产物留在机器，恢复存储后继续回收。
- 实际依赖 handler 配合可控 sink：预先拒收不抽取；模拟可收而实际拒收时补偿仅写合法产物槽；DRAIN-only 罐/禁插产物槽拒绝补偿时交回 overflow 回调，不能灌回同种输入。该测试证明适配层保留/交还全部数量，不声称改写了供应器既有的最终 overflow 策略。
- 正常投料和 EJECT 不主动误抽；既有通用机器回收用例继续通过。
- 无工业先锋/Titanium 运行时，原 `provider-mekanism-return-test.init.gradle` 的 10 项 GameTest 通过。

Mek 组合 I/O 回归：先在原 `aaa9642d` 代码实跑，复现 10 项中 4 项通过、6 项“组合 I/O 后产物未回收”失败。原测试 tick 160 切换侧面、tick 240 断言，少于 `ProviderReturnSweep` 最大 128 tick 空闲间隔。只将断言移动到 tick 320（含缓冲入网余量），保留原有数量与保护断言，6 项均通过；又增加 tick 320 关闭适配后可抽辅助槽的断言。没有更改 Mek 包装器或回收调度以迁就测试。

本次未做图形客户端实际点击与排版验收、专门客户端/服务端跨进程网络包测试、任意第三方权限模组或全部 IF 机器逐台验证。方块流、服务端菜单、实际依赖能力和上述矩阵已执行；其他表项的角色来自真实 jar 静态检查。自定义能力包装器采用保守边界，未声称覆盖所有扩展。未知机器的通用回退仍可能抽取其可抽取的工作物料。
