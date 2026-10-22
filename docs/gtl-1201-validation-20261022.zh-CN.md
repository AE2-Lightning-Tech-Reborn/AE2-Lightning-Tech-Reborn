# Forge 1.20.1 GTL 更新验收（2026-10-22）

本轮重新抓取普通 `1.20.1`，最新提交仍为 `c568e6751b7e78bb556ba2a90dc7b3a736196f1d`。当前工作区已覆盖此前的同版本更新；此次未引入新的 main/alpha 功能。对照最近七项上游更新的 68 条路径，38 条内容一致，30 条保留本地适配，缺失 0 条。

本次整理的更新包括最终合成派发与精确输出记账、物理 long 边界、材料就绪唤醒、接口导出调度、I/O 重载清理、无限元件摘要读取、相位自由视角隔离、Mekanism/Industrial Foregoing 产物回收，以及 JEI 旧按钮与新 Controller/Service 入口。保留 GTL 规划约束、终端功能和 Forge 1.20.1 的 NBT/网络适配。NeoECO 的适配、依赖、注册、配方和测试夹具继续排除。

此次新增可重复运行的 `scripts/gtl-regression-test.init.gradle`，按六个分组选取夹具、隔离编译输出和世界目录，并在没有测试实际执行或没有全部通过时使任务失败。修复旧移植文档中的遗留冲突标记。

## 本轮验证

环境：Minecraft 1.20.1、Forge 47.1.47、AE2 15.4.10、Java 17。只修改和测试当前 AE2LT 项目；配套核心复用此前已验证的无 NeoECO 构件，未修改其他核心项目或 AGENTS.md。

- 全量 JUnit：1672 项通过，0 失败，0 跳过，包括四项实际 EAEP 构件契约。
- 自适应批处理压力模型：6 项通过。
- 发布脚本单元检查：12 项通过；基准运行器检查：1 项通过；发布仓库原始/映射坐标解析检查通过。
- JEI 15.21.0.144、15.62.0.219、15.74.0.227：各自真实 Forge 客户端探针通过，且各自 32 项兼容测试通过。
- 相位自由视角客户端：本体保护、自主移动、同 UUID 的 LocalPlayer 代理及 RemotePlayer 移动检查通过。未安装 Tweakerge，其自身镜头实现未执行。
- 供应器设置客户端：实际鼠标交互、C2S/S2C 同步、模式切换、重新打开及中英文标签检查通过。

| 服务端分组 | 引擎报告通过 | 可选场景未执行 | 实际执行 |
| --- | ---: | ---: | ---: |
| default | 51 | 0 | 51 |
| io | 74 | 4 | 70 |
| exact | 16 | 0 | 16 |
| machines | 26 | 2 | 24 |
| backport | 128 | 0 | 128 |
| crafting | 50 | 9 | 41 |

分组之间包含部分相同的基础测试，表中数量不代表互不重复的用例。再次验收时发现本表原先高估了实际执行数，现已更正：IO 的四项专项性能场景没有启用；机器组的两项用例需要 Mekanism Extras 的无限反应工厂；合成组缺少 EAEP、AppGen，未启用 KubeJS 脚本夹具，并有六项可选催化配方缺失，共九项直接返回成功。EAEP 的四项单元契约使用实际 JAR 执行通过，与服务端安装后的注册测试不同。新的补测结果见 [再次全面验收](gtl-1201-expanded-validation-20261022.zh-CN.md)。服务端测试使用独立世界，客户端使用自动探针；验证范围不等同于任意完整整合包或长期生产存档。

首次服务端启动发现测试资源目录重复，已更正；补跑启动器对带空格的绝对路径解析失败，改用工作区相对路径后重跑。初始失败日志保留在本地证据目录，未计入通过结果。

发布包审计通过：2273 个 class 均为 Java 17 major 61，1450 个 JSON 可解析；无重复条目，refmap、JarJar 元数据和许可证/署名齐全。开发夹具未进入产物，生产源码和构件未检测到 NeoECO。生产输入与此前验证的移植状态一致。

上述署名检查针对工作区构建产物。再次从汇总提交独立构建时发现，`*.txt` 忽略规则使 `META-INF/recipesearch-attribution.txt` 未进入提交；已补入该文件并修正忽略规则。后续构件与最终提交以再次全面验收报告为准。

## 复现

使用配套核心依赖和 Java 17，在项目根目录运行：

```powershell
.\gradlew.bat test adaptiveBatchStress assemble
foreach ($suite in 'default', 'io', 'exact', 'backport', 'crafting') {
    .\gradlew.bat -I scripts/gtl-regression-test.init.gradle runGameTestServer "-Pae2ltRegressionSuite=$suite" -Pae2ltInscriberFixture=true
}
.\gradlew.bat -I scripts/gtl-regression-test.init.gradle runGameTestServer -Pae2ltRegressionSuite=machines -Penable_industrial_foregoing_runtime=true
```

EAEP 实际构件测试另需向测试 JVM 提供 `ae2lt.eaepTestJar`。客户端使用项目现有的 JEI、phase-freecam 和 provider-advanced 测试初始化脚本。本轮实际执行的命令、完整 XML、日志、截图及机器可读结果保留为本地证据，不加入功能提交。

## 构件

- `ae2lt-forge-1.20.1-gtl-2.1.2-beta2.jar`：SHA-256 `de71488c0114e0947138def66e12c80f4fcdda2c2d7bccbc20b833f16e1cd3a3`。
- 配套无 NeoECO 核心 `thunderbolt-forge-1.20.1-2.0.3-beta3.jar`：SHA-256 `b4b8e379aeb1ea1971062b09daa0ff677bebbbc02c33c4fccbfe606ec395caff`。

开发过程留在本地；验证后的完整移植按单个汇总提交整理到本地 `1.20.1_GTL`。未推送远端。
