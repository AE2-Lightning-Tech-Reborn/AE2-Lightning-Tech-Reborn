---
navigation:
  title: 过载机器框架与结构件
  icon: ae2lt:overload_machine_frame
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_machine_frame
  - ae2lt:overload_alloy_anvil
---

# 过载机器框架与结构件

<ItemGrid>
  <ItemIcon id="ae2lt:overload_machine_frame" />
  <ItemIcon id="ae2lt:overload_alloy_anvil" />
</ItemGrid>

## 过载机器框架

**过载机器框架**是本模组加工设备常用的合成结构件。不同机器的具体制作方式（如工作台、闪电装配室等）各自独立，请结合对应机器页面与配方查看器确认具体配方。

<RecipeFor id="ae2lt:overload_machine_frame" />

## 过载合金砧

**过载合金砧**由原版铁砧与过载合金锭合成，保留原版铁砧的重命名、修理、经验消耗与附魔合并机制。方块本身仍具重力特性（可下落），但正常使用时**不会产生磨损或损坏**。在 [天枢合成终端](../terminals/crafting-terminal.md) 中也内置了同名的「过载合金砧」工作页。

<RecipeFor id="ae2lt:overload_alloy_anvil" />

## 硅块

过载处理工厂等配方通过物品标签 `c:storage_blocks/silicon` 接受硅块：

<OptionalSection modId="!extendedae">
当前未安装 ExtendedAE，因此本模组自动注册了自带的硅块（`ae2lt:silicon_block`），可直接由 9 个硅合成并拆解。

<RecipeFor id="ae2lt:silicon_block" />
</OptionalSection>

<OptionalSection modId="extendedae">
当前已安装 ExtendedAE，模组不会重复注册自带硅块，相关配方直接使用 ExtendedAE 提供的硅块。
</OptionalSection>
