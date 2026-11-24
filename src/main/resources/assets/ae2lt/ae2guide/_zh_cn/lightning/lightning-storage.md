---
navigation:
  title: 闪电存储元件
  icon: ae2lt:lightning_storage_component_i
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:lightning_storage_component_i
  - ae2lt:lightning_storage_component_ii
  - ae2lt:lightning_storage_component_iii
  - ae2lt:lightning_storage_component_iv
  - ae2lt:lightning_storage_component_v
  - ae2lt:bulk_lightning_storage_component
  - ae2lt:bulk_lightning_cell_component
  - ae2lt:lightning_item_cell_housing
  - ae2lt:infinite_storage_cell
  - ae2lt:lightning_cell_component_i
  - ae2lt:lightning_cell_component_ii
  - ae2lt:lightning_cell_component_iii
  - ae2lt:lightning_cell_component_iv
  - ae2lt:lightning_cell_component_v
---

# 闪电存储元件

<ItemGrid>
  <ItemIcon id="ae2lt:lightning_item_cell_housing" />
  <ItemIcon id="ae2lt:lightning_cell_component_i" />
  <ItemIcon id="ae2lt:lightning_storage_component_i" />
  <ItemIcon id="ae2lt:lightning_storage_component_iii" />
  <ItemIcon id="ae2lt:lightning_storage_component_v" />
  <ItemIcon id="ae2lt:bulk_lightning_storage_component" />
</ItemGrid>

闪电资源在 ME 网络中以专用存储元件的形式存放在 <ItemLink id="ae2:drive" /> 或 <ItemLink id="ae2:chest" /> 中。

`利用末影的空间力量与过载水晶天然的闪电亲和力，将高能的闪电约束在小小的元件之中。`

## 组件、外壳与成品元件

制作标准与大宗闪电存储元件时，请注意区分「组件」与「元件」：

* **闪电存储组件（I–V） / 大宗闪电存储组件**：制作元件用的核心部件，不能直接放入驱动器。
* **闪电存储元件外壳**：与对应组件合成即可组装为成品元件（部分等级亦支持直接有序合成）。
* **闪电存储元件（I–V） / 大宗闪电存储元件**：组装后的成品，放入 ME驱动器即可存储高压闪电与极高压闪电。

<RecipeFor id="ae2lt:lightning_item_cell_housing" />

<RecipeFor id="ae2lt:lightning_storage_component_i" />

## 标准闪电存储元件（I–V）

每枚标准闪电存储元件均可同时存储高压闪电与极高压闪电两种资源。闪电每单位占用 1 字节，元件注册字节容量已为两种资源预留 AE2 类型开销（单种存放时的实际可用量会受类型开销影响）：

| 元件名称 | 两种闪电同时使用时的有效容量 | 空闲耗电 |
|----------|----------------------------:|---------:|
| 闪电存储元件 I | 256 | 32 AE/t |
| 闪电存储元件 II | 1,024 | 128 AE/t |
| 闪电存储元件 III | 4,096 | 512 AE/t |
| 闪电存储元件 IV | 16,384 | 2,048 AE/t |
| 闪电存储元件 V | 65,536 | 8,192 AE/t |

## 大宗闪电存储元件

**大宗闪电存储元件**由**大宗闪电存储组件**与**闪电存储元件外壳**合成，专为大规模后期产线设计：

<RecipeFor id="ae2lt:bulk_lightning_storage_component" />

* **空闲耗电**：32 AE/t。
* **存储上限**：高压闪电与极高压闪电各自拥有独立的有符号 64 位计数（上限 `9,223,372,036,854,775,807`），达到上限后拒绝超额写入。

## 无限存储元件

<ItemImage id="ae2lt:infinite_storage_cell" scale="2" float="left" />

**无限存储元件**属于按实际写入记录的多类型存储元件，支持多种资源共存并提供极高容量上限，不受常规存储元件的字节与种类数约束。

## 附录

> 扫描日志末尾夹着一条旁注：“有一枚元件不在登记库中，它只能被读取一次，不可写入，其余一切正常。”来源与解读均未记录。
