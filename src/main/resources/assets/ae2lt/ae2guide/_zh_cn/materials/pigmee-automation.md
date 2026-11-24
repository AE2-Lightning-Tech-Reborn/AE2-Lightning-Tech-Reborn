---
navigation:
  title: 猪咪起步自动化
  icon: ae2lt:pigmee_core
  parent: materials/materials-index.md
  position: 65
item_ids:
  - ae2lt:pigmee_fumo
  - ae2lt:pigmee_core
  - ae2lt:pigmee_storage_component
  - ae2lt:pigmee_item_cell_housing
  - ae2lt:pigmee_storage_cell
  - ae2lt:pigmee_pattern_provider
  - ae2lt:pigmee_molecular_assembler
  - ae2lt:pigmee_mentalmath_unit
  - ae2lt:pigmee_synthesis_station
---

# 猪咪起步自动化

<ItemGrid>
  <ItemIcon id="ae2lt:pigmee_fumo" />
  <ItemIcon id="ae2lt:pigmee_core" />
  <ItemIcon id="ae2lt:pigmee_storage_cell" />
  <ItemIcon id="ae2lt:pigmee_synthesis_station" />
  <ItemIcon id="ae2lt:pigmee_pattern_provider" />
  <ItemIcon id="ae2lt:pigmee_molecular_assembler" />
  <ItemIcon id="ae2lt:pigmee_mentalmath_unit" />
</ItemGrid>

**猪咪**系列提供了一套低门槛的起步存储、手动合成与轻量自动合成设备，同时串联起 [猪咪水晶催化器](../machines/crystal-catalyzer.md)、[彩虹猪咪染色](rainbow-pigmee.md) 与 [猪咪建材](pigmee-building-materials.md)。

## 获取猪咪与猪咪核心

* **猪咪**：默认情况下，玩家首次进入世界时会自动获赠一只**猪咪**（优先放入快捷栏或背包，空间不足时掉落在身旁，每名玩家仅发放一次）。将猪咪佩戴在头部时，世界中的自然闪电会呈现粉色，便于在雷暴中观察自然落雷。
* **猪咪核心**：让一只**成年猪**站在**过载水晶块**上方，并受到下落铁砧的砸击伤害，即可转化为 1 枚**猪咪核心**。猪咪核心是本系列多款设备的可重复使用合成原料（具体配方见配方查看器）。

## 起步设备一览

| 设备 / 物品 | 功能与参数 |
|-------------|------------|
| **猪咪存储组件 / 猪咪元件外壳 / 猪咪存储元件** | 将**猪咪存储组件**与**猪咪元件外壳**合成为**猪咪存储元件**（拆解可取回材料）。成品遵循 AE2 物品元件规则：**256 字节**、最多 **16 种**物品、每类占用 8 字节开销，待机耗电 **0.5 AE/t**。 |
| **猪咪合成站** | 自带 3×3 合成网格，可实时聚合相邻可访问的**非 ME 存储容器**供存取与合成，手动合成**不消耗 AE**；周围容器变动会自动同步，无可用相邻容器时界面会给出提示。 |
| **猪咪样板供应器** | 提供 **3 个样板槽**，按方块朝向或各相邻面向兼容目标派发样板材料，并通过专用回收库存尽量将成品导回 ME 网络。 |
| **猪咪装配室** | 接收并执行兼容的合成样板，配合猪咪样板供应器构成紧凑的起步自动合成单元。 |
| **猪咪口算单元** | 轻量合成 CPU：提供 **256 字节**合成存储、每 tick **1 次**派发能力，占用 **1 频道**，待机耗电 **1 AE/t**。 |
| **[猪咪水晶催化器](../machines/crystal-catalyzer.md)** | 放入整组 64 个催化剂，仅消耗 1,000 mB 水即可每 100 tick 产出配方基础产量，无需电能与闪电。 |
