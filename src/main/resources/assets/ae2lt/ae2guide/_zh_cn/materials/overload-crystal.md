---
navigation:
  title: 过载水晶
  icon: ae2lt:overload_crystal
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_crystal
  - ae2lt:overload_crystal_dust
  - ae2lt:overload_crystal_block
  - ae2lt:flawless_budding_overload_crystal
  - ae2lt:flawed_budding_overload_crystal
  - ae2lt:cracked_budding_overload_crystal
  - ae2lt:damaged_budding_overload_crystal
  - ae2lt:small_overload_crystal_bud
  - ae2lt:medium_overload_crystal_bud
  - ae2lt:large_overload_crystal_bud
  - ae2lt:overload_crystal_cluster
---

# 过载水晶

<ItemGrid>
  <ItemIcon id="ae2lt:overload_crystal" />
  <ItemIcon id="ae2lt:overload_crystal_dust" />
  <ItemIcon id="ae2lt:overload_crystal_block" />
  <ItemIcon id="ae2lt:flawless_budding_overload_crystal" />
</ItemGrid>

**过载水晶**是 AE2 闪电科技最基础的材料，同时具备主动召唤人工闪电的特性。

## 人工引雷机制

* **手持 / 快捷栏携带**：默认将过载水晶放在快捷栏任意槽位或副手，约每 **200 tick（10 秒）** 会在玩家所在位置触发一次人工闪电。旁观模式下不触发；将水晶移出检测位置会清空当前计时（服务器配置可调整是否检测主背包或关闭快捷栏/副手触发）。
* **地面掉落物**：将过载水晶丢在地上时，掉落物实体独立计时，同样约每 200 tick 在物品自身位置召唤一次人工闪电。
* **无露天限制**：上述两种人工引雷触发条件均**不检查是否露天**。若需自然雷暴落雷，仍需将避雷针暴露于天空。

## 母岩转化结构

将 AE2 赛特斯石英母岩置于 3×3 同高度结构的中心，四条正边（东、西、南、北）各放 1 个 <ItemLink id="ae2:fluix_block" />，中心母岩正上方放置 1 个 <ItemLink id="minecraft:lightning_rod" />，四角根据路线选择不同方块：

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

| 路线 | 四角方块 | 所需雷击 | 中心输入（赛特斯石英母岩） | 转化结果（过载水晶母岩） |
|------|----------|----------|----------------------------|--------------------------|
| **精制结构** | 过载水晶块 ×4 | 仅自然闪电 | 无瑕 / 有瑕 / 开裂 / 损坏 | 同级转化为 无瑕 / 有瑕 / 开裂 / 损坏 |
| **简化结构** | 赛特斯石英块 ×4 | 任意闪电（人工或自然） | 无瑕 → 有瑕；有瑕 → 开裂；开裂 → 损坏 | 降一级转化（**不接受损坏的赛特斯石英母岩**） |

雷击命中避雷针并成功转化后，外圈 8 个方块会被消耗，中心母岩就地转换，避雷针保留。

## 生长、衰减与采集掉落

过载水晶母岩在随机刻向相邻空气面生长水晶芽，依次经历小型 → 中型 → 大型 → **过载水晶簇**四个阶段；四级母岩均受 AE2 <ItemLink id="ae2:growth_accelerator" /> 加速。

* **自然生长衰减**：**无瑕的过载水晶母岩**不会因生长而衰减；有瑕、开裂、损坏三个等级每次自然生长成功后均有 **1/12** 的概率衰减一级（损坏级衰减后变为过载水晶块）。
* **水晶芽与簇的采集**：

| 方块 | 普通挖掘掉落 | 精准采集掉落 |
|------|--------------|--------------|
| 小型 / 中型 / 大型过载水晶芽 | 过载水晶粉 | 对应阶段的水晶芽本身 |
| 过载水晶簇 | 4 个过载水晶（受时运均匀加成） | 过载水晶簇 |

* **母岩的采集**：

| 母岩等级 | 普通挖掘掉落 | 精准采集掉落 |
|----------|--------------|--------------|
| 无瑕的过载水晶母岩 | 有瑕的过载水晶母岩 | 有瑕的过载水晶母岩（无法直接挖取无瑕级） |
| 有瑕的过载水晶母岩 | 开裂的过载水晶母岩 | 有瑕的过载水晶母岩 |
| 开裂的过载水晶母岩 | 损坏的过载水晶母岩 | 开裂的过载水晶母岩 |
| 损坏的过载水晶母岩 | 过载水晶块 | 损坏的过载水晶母岩 |

> 后期可将过载水晶块或无瑕的过载水晶母岩投入 [水晶催化器](../machines/crystal-catalyzer.md)，以稳定高速地量产过载水晶与过载水晶粉。
