---
navigation:
  title: 天枢合成终端
  icon: ae2lt:tianshu_crafting_terminal
  parent: index.md
  position: 48
item_ids:
  - ae2lt:tianshu_crafting_terminal
  - ae2lt:wireless_tianshu_crafting_terminal
---

# 天枢合成终端

<ItemGrid>
  <ItemIcon id="ae2lt:tianshu_crafting_terminal" />
  <ItemIcon id="ae2lt:wireless_tianshu_crafting_terminal" />
</ItemGrid>

**天枢合成终端**提供有线部件版与无线手持版，将五种高频工作站整合到同一个 ME 终端界面中，并支持对接天枢超算的库存维持功能。

<RecipeFor id="ae2lt:tianshu_crafting_terminal" />

<RecipeFor id="ae2lt:wireless_tianshu_crafting_terminal" />

## 五个内置工作页

切换工作页或关闭终端时，当前工作槽中的物品与切石选择等可保存状态会自动保留（有线版保存在终端方块上并由同终端玩家共享，无线版保存在对应终端宿主上）：

| 工作页名称 | 短标签 | 主要功能 |
|------------|--------|----------|
| **合成台** | 合成 | 3×3 合成网格，直接从 ME 网络与玩家背包取料 |
| **锻造台** | 锻造 | 按配方放入锻造模板、基底与添加物 |
| **过载合金砧** | 合金砧 | 修理、合并附魔与重命名，按原版规则消耗经验且无磨损 |
| **切石机** | 切石 | 放入原料，选择目标切石配方并取出成品 |
| **元件工作台** | 元件 | 放入可配置的 ME 存储元件，编辑分区、升级卡槽、模糊选项与复制模式 |

* **清空工作槽**：可将当前工作输入退回至玩家背包或 ME 网络（空间受限时按实际可容纳量处理）。
* **配方填充与补造**：在支持配方填充的工作页中，可使用配方查看器直接填入原料；按住 `Ctrl` 填充时，先填入现有库存，对缺失且网络可合成的材料会弹出合成确认，完成后自动填入。

## 无线版本与联动

* 无线天枢合成终端需绑定 ME 网络并保持有电，具体覆盖范围取决于所绑定的 <ItemLink id="ae2:wireless_access_point" /> 或 [无线过载控制器](../overloaded-network/wireless-network.md)。

<OptionalSection modId="ae2wtlib">
* **AE2WTLib 通用终端**：安装 AE2WTLib 后，无线天枢合成终端支持接入兼容的无线通用终端模块体系。
</OptionalSection>

<OptionalSection modId="jei">
## JEI 无线取料

在「设置 → 天枢设置」中可开启 **JEI 无线取料**（默认关闭；该功能独立于终端内的 Ctrl 补造填充）：

* 只要玩家背包（或安装 Curios 时的饰品栏）中携带已绑定可用 ME 网络且有电的无线天枢终端或兼容通用终端，在打开其他支持 JEI 配方填充的工作台或机器时，点击一次或 `Shift` 批量填充，系统会自动从 ME 网络将缺失原料提取到玩家背包中供 JEI 转移。
* 执行前请确保背包留有空位；若中途失败或关闭界面，已经提取出的物品会安全留在背包中。
</OptionalSection>

<OptionalSection modId="emi">
## EMI 配方转移说明

在安装 EMI 时，天枢合成终端的兼容工作页支持标准 EMI 配方填充与按 `Ctrl` 缺失补造；外部普通工作台的自动无线提取属于上述仅限 JEI 的「JEI 无线取料」功能。
</OptionalSection>

## 库存维持入口

当终端所在网络存在在线的 [天枢超算](../tianshu/tianshu-index.md) 时，可直接在终端内管理库存维持规则（`Shift + 中键` 点击可合成物品或点击「库存维持」总览按钮）：

* 库存严格低于「开始低于」下限时触发补货，达到或超过「停止达到」上限时退出补货；每次固定按「单次合成」数量下单，因此实际库存可能越过上限，且不会取消已经下达的活动任务。
* 全局保留与单条规则附加保留仅约束库存维持的计划取料，不影响玩家手动发起的合成或其他 ME 自动化。详细规则与示例见 [天枢样板编码终端](pattern-encoding-terminal.md#库存维持与库存保留)。
