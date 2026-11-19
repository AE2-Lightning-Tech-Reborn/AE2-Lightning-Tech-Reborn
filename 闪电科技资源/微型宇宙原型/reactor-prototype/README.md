# 奇点里的宇宙：规则核原型

规则版本 `0.5.0-U5`：原初与凝聚两个时代，精确质量账本、固定 tick、命令行固定策略，外加一个可以手动试玩的网页界面。

设计依据：[核心概念](../方案/增量游戏核心概念-微型宇宙.md)、[内容设计：原初与凝聚时代](../方案/增量游戏内容设计-原初与凝聚时代.md)、[整体玩法方案](../方案/增量游戏整体玩法方案.md)。

## 试玩界面

直接用浏览器打开 `web/reactor.html`，不需要本地服务器。

- 第一轮分 7 步逐步放出面板，每步只有一个目标，目标对应的按钮会高亮。打完一轮后，下一轮开局就显示全部面板。
- 改了 `src` 或 `web/app.js`、`web/style.css` 之后，运行 `node scripts/build-web.js` 重新生成。
- 调试用地址参数：`reactor.html?q=1e7&eta=0.8&all=1&speed=20` 直接开一轮并显示全部面板。
- 界面底部的快进（5×、20×、100×）和“显示全部面板”是原型的试玩工具，不是游戏规则。

## 运行

需要 Node 18 以上，无第三方依赖。

```text
node test/core.test.js      # 规则核，25 项
node test/web.test.js       # 界面逻辑，8 项：按引导用按钮命令手动打完一个教学轮
node test/bundle.test.js    # 单文件版本的冒烟测试，3 项
node src/cli.js run --q 1e6 --k 1.25 --strategy balanced
node src/cli.js sweep
node scripts/compare.js 1e6 1.25   # 逐项改变一个选择，看回收率和轮长怎么变
node scripts/winners.js U4                 # U4基线；分阶段搜索，逐轮保存检查点
node scripts/winners.js U5                 # U5同空间复测；可从检查点续跑
```

测试请按上面的方式直接运行。`node --test` 会另起子进程，在受限沙箱里可能报 `spawn EPERM`。

## U5规则变化

- 成熟花费的特征质量指数仍为 0.25；坍缩阈值改用 `collapseExp`（默认 0.10），随规模增长得更慢。
- 自激允许 `selfexcShare`（默认 50%）的结构质量进入激发态；自动击发无需电容，但每次坍缩仍清空装置。
- 余辉只对过热烧失的质量产势，基准 `afterglowYield` 为每 Q 5000 势，再按成熟特征质量与投入质量之比、共振和暗晕加成。
- `plan()` 可设置规律顺序、首次/全部规律后凝聚结构、1/2/3 格击发、目标成熟度和抽取前/后的结构溶解时机。

## 文件

| 文件 | 内容 |
| --- | --- |
| `src/params.js` | 规则参数表。改动后提升 `version` |
| `src/core.js` | 规则核：状态、动作校验、每 tick 推进、对账、结算、报价 |
| `src/advisor.js` | “下一步造哪种装置最划算”的建议，固定策略和界面的代买共用 |
| `src/strategies.js` | 固定策略，只通过动作操作规则核 |
| `src/runner.js` | 把策略接到规则核上跑到结算，记录事件 |
| `src/cli.js` | 命令行：单轮运行、批量扫参 |
| `web/app.js`、`web/style.css` | 试玩界面：纯函数渲染 + 很薄的 DOM 绑定 |
| `web/reactor.html` | 打包好的单文件版本，由 `scripts/build-web.js` 生成 |
| `scripts/compare.js`、`scripts/winners.js` | 打法比较，用来检查有没有唯一解 |
| `test/` | 规则核、界面逻辑、单文件版本三组测试，共 36 项 |

## 约定

- 质量用 BigInt，最小单位 10^-9 Q，任何时刻 `投入 = 弥散 + 结构 + 抽取口占用 + 已抽取 + 样本胚 + 散失`。
- 势、蓄能、激发态用 Number，不参与对账；通常激发态不超过弥散质量，持有自激时额外计入 `selfexcShare × 结构质量`。
- 规则核不读时钟、不用随机数、不碰文件和网络。同样的输入得到同样的结果。
- 策略、界面和将来的玩家脚本走同一套动作（`act` / `step`），被拒的动作不改变状态。
- 界面上的分步引导、持续投入、代买装置都只是替玩家发出普通动作，不是规则。

## 动作

| 动作 | 参数 |
| --- | --- |
| `strike` | 无。把当前蓄的能击发出去 |
| `setAuto` | `on`, `level`（蓄到几格自动击发） |
| `buyDevice` | `kind`（0 电容 / 1 击发线圈 / 2 约束环）, `count` |
| `mature` | `phi` |
| `collapse` | `law`：`resonate` / `selfexc` / `afterglow` / `conserve` / `crystal` |
| `form` | `kind`：`cloud` / `nucleus` / `halo`；`amount`（Q） |
| `dissolve` | `kind`, `amount`（Q） |
| `buyFlux` | `amount`（Q/秒） |
| `setExtract` | `on`, `blankShare` |
| `setEmergency` | `on` |
| `settle` | 可选 `sampleTier` |

## 尚未包含

恒星、星系、视界时代，更多级样本，增强件，分级支撑，挑战宇宙，玩家脚本的快照协议与沙箱。
