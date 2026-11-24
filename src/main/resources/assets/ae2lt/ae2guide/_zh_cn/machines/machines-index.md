---
navigation:
  title: 加工机器
  icon: ae2lt:lightning_simulation_room
  parent: index.md
  position: 30
---

# 加工机器

AE2 闪电科技的加工机器围绕 ME 网络中的闪电资源与 FE / AE 能源运转。以下总览各台机器的槽位、缓存、矩阵位与升级卡支持情况：

<SubPages />

## 设备速查表

| 机器 | 输入 / 输出与缓存 | 矩阵槽 | 升级卡 | 能源与闪电 |
|------|-------------------|-------:|--------|------------|
| [闪电模拟室](lightning-simulation-chamber.md) | 3 输入、1 输出（每槽 8,192）；20,000,000 FE | 1 | 最多 4 张加速卡 | FE + 配方闪电 |
| [闪电装配室](lightning-assembly-chamber.md) | 9 输入、1 输出（每槽 8,192）；20,000,000 FE | 1 | 最多 4 张加速卡 | FE + 配方闪电 |
| [过载处理工厂](overload-processing-factory.md) | 9 物品输入、1 物品输出（每槽 16,384）；输入/输出流体槽各 1,024,000 mB；640,000,000 FE | 32 | 最多 4 张加速卡 | FE + 配方闪电 |
| [水晶催化器](crystal-catalyzer.md) | 催化剂槽 256、输出槽 16,384、流体槽 16,000 mB；1,000,000 FE | 1 | 1 张红石卡 | FE + 流体 + 配方闪电 |
| [猪咪水晶催化器](crystal-catalyzer.md) | 催化剂槽 64（需整组）、输出槽 64；仅水 | 无 | 无 | 仅消耗 1,000 mB 水，固定 100 tick |
| [挖掘工厂](mining-factory.md) | 方块槽 4,096、工具槽 1、输出槽 9×4,096；4,000,000 FE | 8 | 无 | FE + 每批 1 高压闪电 |
| [大气电离仪](atmospheric-ionizer.md) | 1 凝核输入槽 | 无 | 无 | 直接消耗 ME 网络 AE（无需 FE 或闪电） |

## 通用供电与自动化说明

* **能源区分**：闪电模拟室、闪电装配室、过载处理工厂、普通水晶催化器与挖掘工厂以 **FE** 驱动加工，并在结算时从 ME 网络扣除**闪电**；**大气电离仪**直接从 ME 网络消耗 **AE**；**猪咪水晶催化器**不需要任何电能或闪电。
* **输出面与自动弹出**：各加工机器可在界面中配置输出面与自动弹出，配合相邻容器或 [过载样板供应器](../overloaded-network/overloaded-pattern-provider.md) 即可接入自动合成。

<OptionalSection modId="appflux">
* **Applied Flux 网络补能**：安装 Applied Flux 后，凡需要 FE 缓存的机器（模拟室、装配室、过载处理工厂、普通水晶催化器、挖掘工厂）在接入存有 FE 的 ME 网络时，均可直接从网络自动补充内部 FE 缓存。
</OptionalSection>
