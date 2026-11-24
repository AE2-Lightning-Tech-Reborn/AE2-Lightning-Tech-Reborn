---
navigation:
  title: 漂浮物质
  icon: ae2lt:floating_matter
  parent: materials/materials-index.md
item_ids:
  - ae2lt:floating_matter
---

# 漂浮物质

<ItemImage id="ae2lt:floating_matter" scale="2" float="left" />

**漂浮物质**通过拦截潜影贝弹获得，用于高阶材料及飞行相关模块的制作。

## 捕获方法

1. 为 <ItemLink id="ae2:annihilation_plane" />（ME破坏面板）附魔**精准采集**，并接入通电、有空闲物品存储空间的活动 ME 网络。
2. 将破坏面板的**正面**朝向潜影贝弹飞来的方向。
3. 当潜影贝弹正面撞击宿主方块上安装破坏面板的那一面时，网络会支付电力并尝试写入 1 个**漂浮物质**：
   * 写入成功时，潜影贝弹直接消失。
   * 若网络空间不足、未通电或未命中面板所在面，捕获失败，潜影贝弹继续执行常规碰撞与漂浮效果。

> 该机制受服务器配置 `shulkerBulletCollection` 控制。

<OptionalSection modId="apothic_attributes">
## Apothic Attributes 药水酿造

安装 Apothic Attributes 且其对应药水已注册时，可在酿造台中使用**粗制药水**与**漂浮物质**酿造对应药水；具体效果与持续时间以当前版本 Apothic Attributes 注册项及配方查看器为准。
</OptionalSection>
