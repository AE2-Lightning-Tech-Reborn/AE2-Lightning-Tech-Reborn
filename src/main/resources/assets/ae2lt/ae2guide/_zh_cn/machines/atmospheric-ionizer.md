---
navigation:
  title: 大气电离仪
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# 大气电离仪

<ItemImage id="ae2lt:atmospheric_ionizer" scale="2" float="left" />

**大气电离仪**通过消耗 ME 网络的 AE 能量与对应的**天气凝核**，强制切换当前维度的天气状态。

## 凝核与能耗对照

三种天气凝核均通过工作台合成：

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| 凝核 | 目标天气 | 总 AE 消耗 | 目标天气持续时长（tick） |
|------|----------|-----------:|-------------------------:|
| **晴空凝核** | 晴天 | 500,000 AE | 12,000–180,000 |
| **降雨凝核** | 雨天 | 1,000,000 AE | 12,000–24,000 |
| **雷暴凝核** | 雷暴 | 8,000,000 AE | 3,600–15,600 |

## 运行规则

* **能源机制**：大气电离仪直接消耗 ME 网络中的 **AE 能量**，不需要 FE 缓存，也不消耗闪电库存；无升级卡槽。
* **加工与结算**：在供电充足的 ME 网络下，需要经过 **100 个推进 tick** 完成能量支付，并在天气成功切换时消耗 1 枚凝核。
* **维度与天气检查**：在不支持天气变化的维度（如下界、末地）无法运行；若当前世界已处于目标天气状态，机器会保持等待而不消耗凝核。
* **自然落雷说明**：使用雷暴凝核的作用是为世界创造雷暴天气条件，实际自然落雷的频率与落点仍由 Minecraft 世界机制决定，与大气电离仪本身的 100 tick 加工周期相互独立。
