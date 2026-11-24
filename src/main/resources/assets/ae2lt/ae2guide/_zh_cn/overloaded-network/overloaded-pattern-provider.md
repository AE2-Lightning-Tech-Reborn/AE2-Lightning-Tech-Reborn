---
navigation:
  title: 过载样板供应器
  icon: ae2lt:overloaded_pattern_provider
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_pattern_provider
  - ae2lt:overloaded_pattern_provider_upgrade
  - ae2lt:overload_pattern
  - ae2lt:overload_pattern_encoder
  - ae2lt:overloaded_wireless_connect_tool
---

# 过载样板供应器

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_pattern_provider" />
  <ItemIcon id="ae2lt:overloaded_pattern_provider_upgrade" />
  <ItemIcon id="ae2lt:overload_pattern_encoder" />
  <ItemIcon id="ae2lt:overload_pattern" />
  <ItemIcon id="ae2lt:overloaded_wireless_connect_tool" />
</ItemGrid>

**过载样板供应器**提供 **36 个样板槽**，支持相邻发配与同维度无线面发配、主动产物回收、自适应倍增以及忽略 NBT 的过载样板。对已放置的兼容样板供应器使用**过载样板供应器升级**可原地升级并保留支持的库存与设置。

<RecipeFor id="ae2lt:overloaded_pattern_provider_upgrade" />

## 过载无线连接工具

**过载无线连接工具**同时适用于过载样板供应器、过载ME接口与过载供电仪：

1. 右击源设备（过载样板供应器）将其选定为源端。
2. 右击目标机器的具体朝向面，记录一条无线连接；再次右击已绑定的目标面可解除连接。
3. **Ctrl + 右击**可从目标出发，批量切换相邻连通的同种方块连接。
4. 规则与上限：工具路径**仅限同维度**，默认最大距离 **128 格**（服务器可修改，设为 `0` 表示不限距离）。单台过载样板供应器最多记录 **1,024** 个目标面连接。

## 发配模式与策略

* **普通模式与无线模式**：普通模式向相邻有效目标送入材料；无线模式向已绑定的目标面分派。同一样板的单次执行输入会发往同一目标；若某机器要求多面分别输入不同原料，请配合支持多面定向的样板或兼容路径。
* **阻挡与分配策略**：
  * 普通阻挡会检查目标是否已存在对应输入。
  * 允许同一样板继续推送时，会比对上一次成功接收的样板。
  * 分配策略决定多目标时的轮询或选择顺序。
* **自适应倍增**：针对兼容的样板与目标，根据当前合成请求量与目标实际可接收容量动态探测可行批量，减少高频小单派发。

## 产物回收与输入过滤

* **回收方式**：界面可选择关闭、自动提取，或仅接收目标机器主动弹出的成品。
* **主动回收轮询时序**：相邻目标与无线目标共用同一退避轮询机制——活跃时约每 **20 tick** 扫描一次，连续空闲时依次退避至 **40 → 80 → 128 tick**；一旦发生新发配或检测到可回收成品，轮询周期立即重置回 **20 tick**。在自动回收模式下，每次向目标发配材料前也会优先检查并回收已完成产物。
* **输入过滤**：开启后，主动回收与被动接收仅按当前已装载样板声明的产物匹配（遵守过载样板的匹配模式）；关闭后则允许回收未声明的副产物及目标面可提取的其他资源。若需限制只接收指定方向，可使用扳手指定朝向或明确绑定面。

<OptionalSection modId="mekanism">
* **Mekanism 化学品适配**：在安装 Mekanism 的环境中，供应器可按机器有效端口与适配规则一并处理物品、流体与化学品输入输出。
</OptionalSection>

<OptionalSection modId="appflux">
* **Applied Flux 感应卡供电**：在供应器中安装感应卡后，可向所连机器输出 FE。注意：此处的“供应器向机器输出 FE”与过载频率卡的“设备侧接收 FE”属于独立设置。
</OptionalSection>

## 过载样板与过载样板编码器

当加工配方涉及随机耐久、附魔或其他变动组件（NBT）时，可使用**过载样板**按槽位控制是否忽略组件匹配：

1. 将任意可解析的**处理样板**（或已有过载样板）放入**过载样板编码器**的源样板槽（物料与数量直接继承自源样板）。
2. 在预览区**逐个点击**需要调整的输入或输出槽，在「忽略 NBT：关」（严格匹配完整组件）与「忽略 NBT：开」（仅按物品 ID 匹配）之间切换。
3. 执行编码即可生成**过载样板**。此外，在 [天枢样板编码终端](../terminals/pattern-encoding-terminal.md) 中也可于处理配方上直接进行过载编码。
