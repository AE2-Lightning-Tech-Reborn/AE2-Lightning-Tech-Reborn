---
navigation:
  title: 过载供电仪
  icon: ae2lt:overloaded_power_supply
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_power_supply
---

# 过载供电仪

<OptionalSection modId="!appflux">
**过载供电仪**仅在安装 **Applied Flux** 模组时注册并启用。当前未检测到 Applied Flux，因此该方块未在游戏中注册。安装 Applied Flux 后即可使用过载供电仪从 ME 网络向同维度机器集中无线供能。
</OptionalSection>

<OptionalSection modId="appflux">
<ItemImage id="ae2lt:overloaded_power_supply" scale="2" float="left" />

**过载供电仪**从已连接的 ME 网络（Applied Flux 能量存储）中抽取 FE，并通过 [过载无线连接工具](overloaded-pattern-provider.md) 向同维度内的多台机器无线供电。

> 适用范围区分：**过载供电仪**通过无线连接工具绑定最多 **64 个**同维度目标面（默认距离上限 128 格），适合高密度设备群集中供电；若需要跨维度或突破距离上限无线供 FE，请使用绑定在高级无线过载控制器上的 [过载频率卡](overloaded-frequency-card.md)。

## 两种工作模式

| 模式 | 能量元件要求 | 供电方式与电力成本 |
|------|--------------|--------------------|
| **普通模式** | 无需放入能量元件 | 由共享调度器持续从 ME 网络抽取并分配 FE，按常规 1∶1 支付网络 FE |
| **过载模式** | 需放入能量元件作为高速缓冲 | 先执行首次探测，在活跃输出阶段对每个活跃目标单 tick 最多执行 **64 次**连续供电调用；电力支出按 **2 倍**计算 |

## 使用要点

1. 将过载供电仪接入存有 Applied Flux FE 的 ME 网络。
2. 用**过载无线连接工具**先右击过载供电仪，再右击目标机器允许输入 FE 的具体面（最多 64 个目标面，支持 Ctrl + 右击批量绑定连通同类机器）。
3. 实际输送速率取决于目标机器单次可接收上限、网络可用 FE 以及过载模式下的缓冲能力；使用过载模式前请确保网络 FE 储量足以承担双倍功耗与高频调用。
</OptionalSection>
