# 附属 API 接入指南（alpha）

本次面向 Minecraft 1.21.1 / NeoForge，只开放集雷器晶体、工作台设备和基础闪电访问三个明确边界。接口仍处于本地 alpha，尚未发布，后续计划合并 main。已发布的 2.1.0 / 2.1.1 JAR 不因为版本字符串相同就拥有这些新增接口。

编译依赖完整 AE2LT 和 AE2 artifact。公共 façade 可以委派内部实现，不是独立 API-only JAR。附属专属的界面、绑定策略和任务退款流程不在本次稳定契约内。

## 接入范围

| 附属现有实现 | 建议 |
| --- | --- |
| CollectorInventoryMixin、CollectorMixin | 使用晶体行为接口替代；保留附属自己的配方、绑定条件及培养规则。 |
| CoilWorkbenchMixin、整体替换工作台 ticker | 使用按物品注册与 serverTick 回调替代。 |
| LightningNetwork、SimulationGridBridge、CoilLightning 的内部 LightningKey 引用 | 可改用 LightningApi.keyOf；适合通过节点操作的普通 I/O 可使用 forHost。 |
| CoilHubHostMixin、CoilHubMenuMixin、CoilHubStatusMixin、CoilHubScreenMixin | 保留在附属兼容层，按支持版本验证，不要求改成通用页面 API。 |
| CoilMiningCommitMixin、CoilAEWrenchMixin、CoilMouseScrollMixin | 保留；属于 Minecraft/AE2 行为，不是本次扩展点。 |
| 设备网络绑定、付款和退款任务 | 由附属兼容层管理；没有通用 DeviceNetworkAccess、LightningPayment 或专用 refund API。 |

未发布的 DeviceHubApi/DeviceHubPage、配套配置协议、DeviceNetworkAccess、LightningPayment 和 LightningApi.refund 已撤回。原有中枢实现恢复，未要求附属修改其界面或语言文件以适配新页面协议。

## 注册阶段

在附属的 FMLCommonSetupEvent.enqueueWork 中注册，客户端和专用服务端都执行。注册按物品 ResourceLocation 分派；同一个入口重复注册同一物品会抛异常，不同附属的物品互不覆盖。注册表在 load-complete 阶段冻结。

```java
event.enqueueWork(() -> {
    CollectorCrystalApi.register(ResourceLocation.parse("overload_sim:simulation_crystal"), crystalBehavior);
    DeviceWorkbenchApi.register(ResourceLocation.parse("overload_sim:resonance_coil"), workbenchDevice);
});
```

空白、普通、完美模拟晶体若都需要接入，应分别注册其物品 ID。只注册要接入的物品，不覆盖 DeviceKind.RAILGUN。

## 集雷器晶体与成功事件

公开类型在 com.moakiee.ae2lt.api.lightning.collector：

- CollectorCrystalApi.register(itemId, behavior)
- CollectorCrystalBehavior.preview(ItemStack, LightningTier, OutputRange baseOutput)
- CollectorCrystalBehavior.onCaptured(Capture, ItemStack original)

preview 在两侧调用且不能修改晶体。返回基础产量 baseOutput 即可维持模拟晶体的基础输出；GUI 预览和实际抽样共用结果。OutputRange 会检查非负数、顺序和整数抽样范围。

注册行为统一负责插槽准入和输出，并跳过原生电鸣晶体培养。实际入网成功后才调用 onCaptured；Capture 提供 level、collectorPos、tier、naturalWeather、requestedAmount、insertedAmount 和当前晶体查询 supplier。

original 是活栈。回调可原位修改或返回替换栈，EMPTY 表示移除。回调期间若其他逻辑换了原栈，上游不会用返回值覆盖新栈。现有 CrystalBinding 可继续使用 capture.installedCrystal() 复查身份。配方选择、结构绑定、培养条件和取消事件仍归附属。

事件顺序：

1. 计算 preview/roll。
2. 原有 LightningCollectedEvent，可取消或修改待入网数量。
3. 实际插入，0 插入立即失败。
4. 原生培养（没有注册行为时）、冷却及工作状态提交。
5. 自定义 onCaptured，保存/同步。
6. api.event.LightningCaptureCompletedEvent，不可取消的成功观察事件。

取消、零产量和满仓不触发成功回调。部分插入算成功，记账使用 insertedAmount。不要在行为回调和成功事件中重复培养。同 tick 重复成功及同一集雷器的回调重入会被拒绝。

## 工作台

api.device.DeviceWorkbenchApi.register(itemId, WorkbenchDevice) 按物品注册，不需要继承 DeviceItem。WorkbenchDevice 表达现有工作台的一个核心槽、模块列表和生命周期：

- core / canPlaceCore / setCore / mayRemoveCore：核心槽。
- modules / moduleId / maxInstallAmount：模块显示。
- canInstallOne / installOne / uninstallOne / uninstallAll：模块变更。
- storedEnergy / energyCapacity：能量显示。
- onInserted / onModulesChanged：插入和模块变化回调。
- serverTick(ItemStack, Context)：激活工作台的服务端回调，Context 提供 ServerLevel、位置和当前 IGrid；变化后返回 true，让上游保存/同步。

模块组合、核心要求、能量上限、充电速率和实际组件存储由附属实现。installOne 不得缩减输入栈；成功后工作台负责扣一个输入单位。uninstall 返回实际从设备移出的物品，setCore 必须允许 EMPTY。

线圈充电可直接调用自己的逻辑：

```java
long before = CoilEnergy.read(device);
CoilEnergy.charge(device, context.grid());
return CoilEnergy.read(device) != before;
```

不要再整体替换上游方块 ticker。原生设备仍走原适配器；此接口不承诺任意多槽布局或通用设备模块系统。

## 基础闪电访问

api.lightning.LightningApi 只提供普通访问和公共 AEKey 转换：

```java
ILightningEnergyHandler storage = LightningApi.forHost(machine);
ILightningEnergyHandler playerStorage = LightningApi.forHost(machine, IActionSource.ofPlayer(player));
AEKey hv = LightningApi.keyOf(LightningTier.HIGH_VOLTAGE);
```

服务器线程调用。forHost 每次读取当前节点，普通读写要求节点激活；getStored 是缓存快照，getCapacity 可返回 Long.MAX_VALUE，simulate 不提供预留或原子保证。

keyOf 只返回 AEKey，不检查活跃状态或权限。直接使用 AE2 storage 的附属仍负责节点条件及 IActionSource。玩家操作保留 ofPlayer，机器操作用 ofMachine。

付款、失败退款、未退余额持久化、原网络归属与重启恢复继续由附属负责。尤其不要直接把附属的离线退款路径替换为要求节点激活的普通 handler.insert；可通过 keyOf 使用已有 AE2 存储路径，并保留实际回插量和剩余债务。当前节点所属网络可能发生变化，附属需要按自己的任务语义决定退款目的地。

## 中枢 mixin 与语言文件

中枢四个 mixin 可以继续存在。专属显示、操作、状态适配与版本反射集中在附属兼容层，并验证其支持的每个 AE2LT 版本；不要假定内部类永远不变。

已核对附属 zh_cn.json / en_us.json：各 131 个键，键集合和格式占位符一致。原界面仍需保留：

- efficiency_level / fortune_level 的等级参数与上限。
- on / off 和 wrench_mode.0 至 wrench_mode.7 的客户端翻译。
- no_mekanism 的条件工具提示。

前期页面草案缺少翻译参数、可翻译值和 tooltip，是暂缓通用页面 API 的原因之一。草案已撤回，不再要求附属新增无参数标签或改写现有语言键来迁就它。后续保持中英文的设置、模式名和提示验证即可。

## 已有正确性修复

批处理回执的线程校验、nonce 请求一致性、临时拒绝重试、输出数量边界与不确定结果处理，以及频率 ID 规范化和 server 归属校验继续保留。它们不代表附属需要采用批处理接口。

WirelessPatternProviderHost.getEndpointSnapshots() 的公共只读快照及原有方法兼容也保留，不属于本附属的迁移要求。

## 验证

可运行的最小集雷器/工作台接入示例位于 src/jdb/java/com/moakiee/ae2lt/debug/AddonApiGameTests.java，仅在指定开发运行中注册，不打入发布 JAR。

```powershell
.\gradlew.bat test jar --offline --console=plain
.\gradlew.bat runAddonApiGameTestServer -Pae2ltAddonApiTestsOnly=true --offline --console=plain
```

本轮收窄后重新验证：单元测试共 1388 项，1384 通过、4 跳过、0 失败/错误；5 项服务端 GameTest 全通过，其中 4 项覆盖集雷器和工作台，1 项覆盖保留的批处理线程修复。JAR 构建通过，确认没有撤回的 API/协议或测试夹具残留。原有中枢代码与页面框架加入前一致。

环境：Java 21、Minecraft 1.21.1、NeoForge 21.1.220、AE2 19.2.17、Thunderbolt Core Reborn 2.0.0。附属最低 NeoForge 21.1.252 的真实客户端/服务端联合验证仍应在迁移后执行。

新注册行为和旧 mixin 不要同时处理同一功能。按集雷器、工作台、基础闪电访问逐项迁移，确认接口首发构建再调整最低依赖；alpha 合并 main 后复核。
