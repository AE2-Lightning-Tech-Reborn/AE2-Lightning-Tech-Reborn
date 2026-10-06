# 附属 API 接入指南（alpha）

本指南描述 AE2LT alpha 新增的附属接口，目标环境为 Minecraft 1.21.1 / NeoForge。该接口批次尚未发布；alpha 后续计划合并 main。旧的已发布 2.1.0 / 2.1.1 JAR 不因为版本字符串相同就具备这些类型。附属应等接口所在构建发布后再提高最低依赖，或在独立的兼容类中隔离旧、新路径。

编译依赖完整 AE2LT 和 AE2 artifact。api 包含委派到内部实现的 façade，不是独立 API-only JAR。不要把实现类或反射构造器当稳定接入契约。

## 注册阶段

在附属的 `FMLCommonSetupEvent.enqueueWork` 中注册，客户端和专用服务端都要执行。注册按物品 ResourceLocation 分派：同一入口重复注册同一物品会抛异常；不同附属的物品互不覆盖。注册表在 AE2LT 的 load-complete 阶段冻结，之后不能动态注册。

```java
event.enqueueWork(() -> {
    CollectorCrystalApi.register(ResourceLocation.parse("overload_sim:simulation_crystal"), crystalBehavior);
    DeviceWorkbenchApi.register(ResourceLocation.parse("overload_sim:resonance_coil"), workbenchDevice);
    DeviceHubApi.register(ResourceLocation.parse("overload_sim:resonance_coil"), hubPage);
});
```

上面三个适配器分别实现下述公开接口。只为需要接入的物品注册；集雷器的空白、普通、完美模拟晶体若都有不同注册 ID，需要逐个注册。无须继承 DeviceItem，也不要覆盖原生 DeviceKind.RAILGUN。

## 集雷器

公开类型：

- `api.lightning.collector.CollectorCrystalApi`
- `api.lightning.collector.CollectorCrystalBehavior`
- `api.event.LightningCaptureCompletedEvent`

`CollectorCrystalBehavior.preview(ItemStack, LightningTier, OutputRange baseOutput)` 在两侧调用，不能修改晶体。返回经过校验的 min/max 范围，GUI 与实际抽取共用该结果。若模拟晶体应该使用基础产量，直接返回 baseOutput。范围允许 0，但上界与跨度必须可用于整数随机抽样。

注册晶体会被插槽接纳，并跳过原生电鸣晶体培养。实际插入量为正时，集雷器先提交冷却和工作状态，再调用：

```java
ItemStack onCaptured(CollectorCrystalBehavior.Capture capture, ItemStack original)
```

capture 提供 level、collectorPos、tier、naturalWeather、requestedAmount、insertedAmount 和当前晶体查询 supplier。original 是当前实际栈；回调可原位更新或返回替换栈（EMPTY 表示移除）。回调期间若别的逻辑已替换原始栈，集雷器不会用返回值覆盖新栈。回调应保留自己的取消和原始栈检查，不应在有副作用后抛异常。

对于现有 CrystalBinding，可把 `capture.installedCrystal()` 传给需要复查当前栈的函数。返回的新晶体由上游安装并标记保存/同步，附属不再需要从 mixin 直接操作集雷器内部库存。

事件顺序：

1. 计算 preview/roll。
2. 原有 `LightningCollectedEvent`，仍可取消或修改待插入量。
3. 实际插入；0 插入立即返回失败。
4. 原生晶体培养（未注册自定义行为时）、冷却及状态提交。
5. 注册晶体的 onCaptured，保存/同步。
6. 不可取消的 `LightningCaptureCompletedEvent`。

取消、零产量和满仓不会调用成功回调或成功事件。部分插入是成功，必须用 insertedAmount 记账。成功事件用于观察；不要在行为回调和事件监听器里重复执行同一次培养。单个集雷器拒绝同 tick 的重复成功和回调重入。

可替代：CollectorInventoryMixin、CollectorMixin。原有 mixin 与新注册实现不要同时处理同一种晶体。

## 工作台

`api.device.WorkbenchDevice` 支持现有 UI 的一个核心槽、模块列表、能量状态和服务器 tick：

- core / canPlaceCore / setCore / mayRemoveCore：核心槽。
- modules / moduleId / maxInstallAmount：模块显示。
- canInstallOne / installOne / uninstallOne / uninstallAll：模块变更。
- storedEnergy / energyCapacity：long 能量值，默认 0。
- onInserted / onModulesChanged：插入与模块变更回调。
- serverTick(ItemStack, Context)：仅在工作台网络激活时执行，Context 包含 ServerLevel、位置和当前 IGrid；返回 true 表示需要保存/同步。

installOne 不得缩减传入的输入物品栈；成功后工作台负责移除输入中的一个单位。uninstall 返回真正从设备组件移出的物品。setCore 必须允许 EMPTY 清槽。具体存储组件仍由附属维护。

例如线圈现有充电逻辑可放进 serverTick：

```java
long before = CoilEnergy.read(device);
CoilEnergy.charge(device, context.grid());
return CoilEnergy.read(device) != before;
```

无需调用上游方块的 setBlockEntity 替换 ticker。原生设备仍走原适配器；精确物品注册不会覆盖原生 RAILGUN 条目。当前只开放已有单核心槽布局，没有承诺任意多槽界面。

可替代：CoilWorkbenchMixin 和 ModContent.setup 中整体替换工作台 ticker 的代码。

## 设备中枢

`api.device.DeviceHubPage` 为手持设备复用现有第五页，支持模块列表和最多 64 项整数/开关设置：

- id()：页面的 ResourceLocation。
- inspect(ServerPlayer, ItemStack)：返回 Status。
- canConfigure(ServerPlayer, ItemStack)：附属权限/核心条件，默认 true。
- setValue(ServerPlayer, ItemStack, ResourceLocation settingId, int value)：通过校验后的服务端更新。

Status 包含 displayName、hasCore、powered、Module 列表和 Setting 列表，不依赖轨道炮专有布尔字段。Module 提供 translationKey/count/enabled。Setting 提供唯一 id、labelTranslationKey、displayValue、value/min/max/editable。开关使用 min=0/max=1；整数设置使用实际范围。标签由客户端翻译，displayValue 是服务端提供的简短显示字符串。单个字符串限制为 256 字符，settingId 限制为 128 字符。

内置客户端按现有滚动布局渲染设置，点击按钮左半部向前循环、右半部向后循环。无需客户端 mixin、私有坐标字段或单独配置包。此批提供声明式设置页，尚未开放任意自绘页面或无限新增页签。

选择顺序为主手优先、副手次之，原生轨道炮与附属设备一致；原有 G 键会识别注册设备。也可服务端调用 `DeviceHubApi.open(player)`。

服务端每次编辑验证：

- 当前服务端线程、原始玩家、存活、非旁观者；
- 当前 containerMenu、containerId 和手持设备页；
- 页面 ID、设备栈对象身份、所在手持槽及随机会话 token；
- canConfigure、设置 ID、editable、客户端预期旧值和 min/max。

换栈、换手持槽、切页会使旧会话失效。每次实际执行前重新 inspect 并核对身份。原有轨道炮设置包不会修改注册附属设备；模块列表在附属页中用于展示，配置请通过 Setting 表达。

可替代：CoilHubHostMixin、CoilHubMenuMixin、CoilHubStatusMixin、CoilHubScreenMixin，以及 CoilStatusFactory 的内部构造器反射。既有 CoilConfiguration.apply 可由 setValue 调用，id 到 action 的映射由附属维护。

## 闪电与设备网络

`api.lightning.LightningApi`：

```java
ILightningEnergyHandler handler = LightningApi.forHost(machine);
ILightningEnergyHandler playerHandler = LightningApi.forHost(machine, IActionSource.ofPlayer(player));
AEKey key = LightningApi.keyOf(LightningTier.HIGH_VOLTAGE);
long refunded = LightningApi.refund(machine, source, LightningTier.HIGH_VOLTAGE, debt);
```

所有调用在服务器线程执行。handler 每次查询当前节点，不缓存 IGrid；普通读写要求节点激活。getStored 是库存快照，getCapacity 可以返回 Long.MAX_VALUE；这都不是本次可提取数量保证。

keyOf 只转换类型，不检查活跃状态和权限。直接用 AE2 storage 的调用方仍需自行遵守节点条件和 IActionSource。机器调用用 ofMachine，玩家动作保留 ofPlayer。

refund 是恢复既有欠款的入口：允许节点存在但尚未激活时插回，不能拿来规避普通生产的活跃限制。没有节点或存储不能完全接收时，调用方保存 debt - refunded，后续只重试余额。它不会凭空恢复一个已销毁的原网络。

`LightningPayment.pay(handler, hv, ehv)` 是尽力执行的双电压扣费，不是原子事务。返回 Result(paid, refundHv, refundEhv)。模拟检查通过不代表真实扣费必然足量；部分扣费失败会尝试退回，并返回尚未退回的分电压欠款。处理器须遵守返回 0..requested 的数量契约。调用方必须持久化欠款并阻止重复退款；处理器异常后不能推断“没有扣款”。需要跨重启的任务 exactly-once 仍由附属负责。

`api.device.DeviceNetworkAccess` 提供 getBoundPos/bind/unbind/resolve，使用既有 AE2 无线绑定数据和距离/跨维策略；Resolution 返回公共 IGrid、IWirelessAccessPoint 和 Failure。resolve 只判连通性，不能代替玩家授权；bind 为受信服务端入口。

## 既有 API 修复

- 频率非正 ID 规范化为 -1，NBT 同样处理；保留正 ID 以便发送端延迟加载恢复。
- FrequencyApi 的 server 参数必须属于当前运行服务器且在其线程，否则返回空/false。
- setFrequency 仍是受信服务端操作；isValidFrequency 不是权限检查。现有 UI 继续检查菜单与授权。
- WirelessPatternProviderHost.getEndpointSnapshots() 返回公共不可变 WirelessEndpoint，旧 getConnections 签名为兼容保留。
- LightningBatchProvider 与 TianshuSynthesizer 先检查线程/能力版本再访问 nonce 历史；相同 nonce 的不同请求被拒绝。
- retryable 且 acceptedAmount=0 的结果不保留最终回执，可以同 nonce 重试；部分成功/成功重复调用只返回已有回执。
- 按输出 long 上限约束批量，避免提交后才发生输出数量乘法溢出。
- 有副作用阶段异常抛 IndeterminateSubmissionException，阻止该 nonce 重放；不能据此自动退款或换 nonce 重发，应核对任务与库存。
- 完成记录至多保留 1024 项，未定结果会固定保留并占容量；容量全被未定记录占用时拒绝新派发。历史不跨卸载/重启持久化，不是永久幂等服务。

## 验证与附属迁移

可运行的最小附属实现位于 `src/jdb/java/com/moakiee/ae2lt/debug/AddonApiGameTests.java`，仅供开发验证，不包含在发布 JAR。它注册普通纸张和木棍，走真实集雷器、AE 存储、工作台 ticker 和设备中枢服务端流程。

```powershell
.\gradlew.bat test jar --offline --console=plain
.\gradlew.bat runAddonApiGameTestServer -Pae2ltAddonApiTestsOnly=true --offline --console=plain
```

服务端测试环境：Java 21、Minecraft 1.21.1、NeoForge 21.1.220、AE2 19.2.17、AE2LT alpha、Thunderbolt Core Reborn 2.0.0。6 项 GameTest 已通过；全量单元测试统计 1394 项，1390 通过、4 跳过、0 失败/错误，发布 JAR 构建通过。真实“闪电科技：模拟”目前声明的最低 NeoForge 为 21.1.252，该附属完整客户端/服务端兼容矩阵仍需要在迁移后另行执行，不能拿这组 fixture 测试代替。

建议按以下顺序迁移并分别验证：

1. 注册三种模拟晶体行为，移除集雷器两个 mixin，验证取消、零入网、部分入网及不同雷击来源。
2. 注册线圈 WorkbenchDevice，移除工作台 mixin 和 ticker 替换，检查充电与核心/模块物品守恒。
3. 注册 DeviceHubPage，移除中枢四个 mixin 和状态反射，验证七项设置、双手选择、旧包拒绝及轨道炮共存。
4. 用 LightningApi / DeviceNetworkAccess 替换内部引用，持久化线圈失败退款余额。
5. 保留 Minecraft / AE2 的三个独立 mixin；它们不是本批 AE2LT API 的替代目标。
6. 对固定 alpha 构建做联合运行验证；确认发布最低版本后更新元数据，合并 main 后复测。
