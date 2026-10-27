# NeoECO GTO 远程 UI 补丁更新（2026-10-26）

本页为 2026-10-26 的历史验证记录。当前 LT 还支持 Core gto.6 已执行的远程 UI 转换，最新修复、配套哈希及测试结果见 [2026-10-27 配套验证](GTO_NEOECO_CORE_GTO6_2026-10-27.md)。

上游 `1.20.1-GTO` 从 `21faad48` 更新至 [cca7fbb](https://github.com/Yang120231/NeoECOAEExtension/commit/cca7fbb71b9dc922029880e25fc35f65ceb71fc5)，将远程 UI 的客户端世界访问移入独立辅助类，并在共享工厂中按物理运行侧分支。版本号仍为 `20.5.0-gto.1`。LT 原补丁强制匹配两次旧世界读取，遇到已修复实现会因匹配为零而中止加载。

## 调整

- 旧正式发行包继续应用两处世界读取替换，保留 UI 工厂、持有者和网络协议。
- 新实现只有完整匹配物理客户端判断、正确分支目标及指定 `ClientHolderReader.read` 调用时才保留原字节码。不会因旧调用数为零直接放行未知结构。
- 旧路径先检查匹配数量，再修改指令；不完整实现报错时不会留下部分转换的类。
- 保持当前 GTO、AE2、Core 与 NeoECO 版本约束；没有复制 NeoECO 的 GPL 实现到 LT 源码或正式 JAR。

## 验证

- LT 完整 `check` 与 `jarJar`：1575 项，1570 通过、5 跳过、0 失败。跳过范围沿用既有可选 EAE+ 原件及 ModLauncher 用例要求。
- 远程 UI 定向回归 5/5：旧正式包、新守卫保留、未知空实现、错误分支与不完整旧实现。覆盖实际字节码转换与失败后的类内容不变。
- 单独从上游 `cca7fbb` 读取并编译真实 UI 源码，转换前后字节码完全一致。将其重混淆后放入旧正式 NeoECO 的隔离 QA 副本，验证专服实际类加载；该副本只用于测试，不是官方新版发行包，不对外分发。
- 正式 LT 包在新 UI QA 副本、旧 NeoECO 正式包及未安装 NeoECO 三种组合中执行专服启动探针。运行环境为 GTO `0.6.0-dev-23`、GTOCore `26.10.10`、AE2 `15.2610.31`、Forge `47.4.20`、Java 21。各组需 CPU、STORAGE、PLANNER PASS、`PASS total=0 failed=0 startupFailures=0` 与正常退出；这里的 0 表示不运行额外游戏用例，不计入单元测试数量。

证据位于 `build/neoeco-compat/ui-update/`：`check-build.log`、`actual-upstream-transform.log`、`upstream-ui.log` / `.json`、`old-official.log` / `.json`、`without-neoeco.log` / `.json`。固定测试 LT 副本为 `tested-lt.jar`，SHA256 `66337d211a7fb20e8cbeafc43dad66d3d4e3d75c97e816ff84e67718cb5b99de`；依赖与 QA 副本哈希见同目录 `tested-artifacts.json`。

原始配套制品和历史测试记录保持追溯；本轮构建的 LT 与旧配套 LT 版本号相同，比较制品时须使用 SHA256。Core 未改变。

## 边界

未测试远程 UI 的图形客户端交互、跨端联机或所有 NeoECO 功能。上游发行附件查询曾受 GitHub API 限流，不能将本轮源码重建副本当作已核验的新官方附件。此前完整派发、存档和迁移结果仍对应原报告的制品，本轮不重复计为通过。
