# NeoECO GTO 兼容记录（2026-10-26）

本页保留 2026-10-26 的历史制品与验证记录。当前 Core gto.6 已增加远程 UI 转换器，LT 的组合兼容修复、最新配套哈希和复测结果见 [2026-10-27 配套验证](GTO_NEOECO_CORE_GTO6_2026-10-27.md)；下文旧哈希与测试数量不代表当前制品。

本次按明确要求接入 [NeoECO `1.20.1-GTO`](https://github.com/Yang120231/NeoECOAEExtension/tree/1.20.1-GTO)，使用提交 `21faad48f110ed682345215f3c3488d348da2b5a` 对应的正式 `20.5.0-gto.1` 发行包。旧版本的 NeoECO 排除要求不适用于这次指定适配；AGENTS.md 没有修改。

## 版本与部署

使用 GTO `0.6.0-dev-23` 官方服务端发行包内的 GTOCore `26.10.10`，配套 AE2-GTO `15.2610.31`、Java 21、Forge `47.4.20`。LT 为 `2.1.2-gto.9`，Core 为 `2.0.3-gto.6`。两端应一起更新配套环境及 TB/LT。旧 GTOCore `26.10.5` 的测试和制品不适用于此组合。

NeoECO 是可选依赖，但装入时必须使用指定的 GTO 发行版。GTOCore、AE2 与 NeoECO 均使用原始发行包，不改第三方 JAR。

| 外部原件 | SHA-256 |
| --- | --- |
| NeoECO 20.5.0-gto.1 | `24f60175359c65d688f24ec65feb2316ced73800486127f77bcec97d392b77b4` |
| GTOCore 26.10.10 | `59a04d1873a57e944dad8edf705762cc3162d591f4d14f3701005071514b93e7` |
| AE2-GTO 15.2610.31 | `f63917bf5a2ca0692c0550ad2d045991f7a493d89021bfb30b51026d4ad31a3d` |

## 实现

- 普通 CPU 保留 GTO 执行器与 NeoECO CPU 包装，LT 超载计划继续使用自身时轮。修正构造注入顺序，避免覆盖或抢先读取尚未初始化的原生 CPU。缓存结构访问器适配 GTO 随机化的剩余产物字段。
- 通过 NeoECO 公开 facade 实现批量准入、任务 UUID 传递、取消与完成通知。精确派发保留 BigInteger 数量，提交前登记输入和待回料，支持同步返回与拒绝退还；不确定接收结果保留资源归属并暂停任务。
- 增加 Forge 无注册表参数的 LT 模糊回料状态保存/加载 API，保持任务 UUID 校验。
- 样板迁移操作 NeoECO 真实物理槽，保留批量编辑通知，检查整个工作站网络的工作线程。两套迁移工具共用同一网络锁；恢复 F4/F6/F9 天枢样板上传分组识别。
- 保留 GTO 原生样板解码，在返回值上过滤仅供 LT 超载供应器使用的样板，不绑定随机类名和局部变量表。
- 正式 NeoECO 在专用服初始化远程 UI 时会加载 ClientLevel 并终止启动。LT 将两个客户端世界读取移入独立客户端辅助类，保留原界面、持有者同步和写入协议。
- 全检复现并修复既有模糊种子借款重复偿还：迟到变体回料抵消借款后同步清除债务，后续产物不再二次偿还，避免过量预留导致循环停滞。新增确定性回归先在旧逻辑失败，再在修复后通过。

没有复制或打包 NeoECO 的 GPL 实现进入 LT。使用其公开 API 与运行时注入；上游许可证和必要署名保留。配套 Core 仅更新依赖元数据与版本断言，没有恢复旧 NeoECO 实现。

## 构建与证据

Core 公共基线为 `7c7a066af9d404010fe0b9472fca687879b36281`。先应用 [gto.5 完整补丁](docs/patches/core-gto.5-from-upstream.patch)，再应用 [gto.6 增量补丁](docs/patches/core-gto.6-from-gto.5.patch)，运行 `check jarJar verifyReleaseJar publishMavenJavaPublicationToMavenRepository`。增量补丁已通过反向应用检查。

LT 使用本地 Core Maven 仓库构建：

```sh
./gradlew -Pthunderbolt_local_repository=/absolute/path/to/core/repo check jarJar
./gradlew -Pthunderbolt_local_repository=/absolute/path/to/core/repo -PgtoQa gtoQaJar gtoQaLtJar
```

本机为离线依赖缓存补充了 Titanium Maven 镜像，未写入正式构建配置。ForgeGradle 证书探测在本机网络失败时使用 JVM 参数 `-Dnet.minecraftforge.gradle.check.certs=false`；这不是游戏启动要求。

- Core 全检：1531 项，1530 通过、1 跳过、0 失败，含 23 项冷启动测试；发行包校验和本地 Maven 发布完成。
- LT 全检：1571 项，1566 通过、5 跳过、0 失败。四项缺少 EAE+ 正式原件的合约用例和一项需 ModLauncher 的旧 CPU 用例跳过；真实 CPU 剩余量在隔离 GTO 游戏测试中另行检查。
- 正式 NeoECO API 的单次精确提交、101 位数量、迁移锁互斥及远程 UI 字节码检查均执行通过，未因缺包跳过。
- 本机原始证据位于 `build/neoeco-compat/`，包括 `core-check-r3.log`、`lt-check-r14.log`、`lt-check-final.log`、`seed-loan-repro.log` 与各轮隔离服务端日志/JSON。日志和游戏存档不加入 Git。LT 修复后的两轮完整检查均为上述零失败结果。

隔离专用服的通过记录（均同时满足 CPU、STORAGE、PLANNER 探针通过）：

| 测试组 | 游戏用例 | 证据（`build/neoeco-compat/`） |
| --- | --- | --- |
| NeoECO 专项 | 2 / 2 | `neoeco-lt-r9.log` / `.json` |
| 实体 CPU、时轮状态、种子循环 | 11 / 11 | `neoeco-regression-base.log` / `.json` |
| 精确订单 | 19 / 19 | `neoeco-regression-exact.log` / `.json` |
| 批量适配器 | 5 / 5 | `neoeco-regression-batch.log` / `.json` |
| 候选索引、零 tick 与延迟回料派发 | 20 / 20 | `neoeco-regression-dispatch.log` / `.json` |

五组共 57 项游戏用例全部通过，0 个失败；基础与精确组使用普通新世界，后两组使用独立平坦新世界。

此外，分别移除和安装 NeoECO，使用正式 LT 主包与独立 QA 探针完成两次新世界启动检查，两次均为 CPU、STORAGE、PLANNER PASS 和 `PASS total=0 failed=0 startupFailures=0`。对应证据为 `production-without-neoeco.log` / `.json` 与 `production-with-neoeco.log` / `.json`。这里的 0 表示只执行启动探针，没有重复计入游戏用例。

NeoECO 专项使用未修改的正式发行包，构造真实 F4 多方块和样板总线，先执行普通合成建立配方验证缓存，再取消预热任务以释放物理工作线程，批量接收后执行工作线程 NBT 往返和取消回收。检查输入等价产物总量、物料模板不被消费、工作线程清空，以及迁移只使用物理槽并正确检测忙碌/空闲。另一项经正式 facade 验证 `2^100 + 37` 份派发的接受与拒绝、同步返回、资源预留和精确结算。

正式配套成品位于 `build/paired-release/`，校验文件为 `SHA256-gto.9.txt`：

| 文件 | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.9.jar` | `c66956a48994d67c1e71636d8258c0d921a517f3f69726a57b7ab6d5ea9074ae` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.6.jar` | `27ccbaa80002945c6e3092b2b8e11074046f903b7436aae115911872d996fbd8` |

已逐字节比较隔离测试包与正式包的全部 2138 个生产类；正式包不含 QA 夹具、测试代理或 NeoECO 实现类。

## 验证边界

专用服使用独立新存档与官方 dev-23 的服务端依赖。测试夹具不打包进正式 JAR，不在正常游戏存档运行。

101 位派发用例使用正式 facade 与受控 provider，验证 LT 的资源预留、同步回料、拒绝退还和数量精度；它不等于真实 FX 虚拟网络的端到端测试。工作线程保存/加载是实际方块实体 NBT 往返，不等于整个服务器重启测试。

本轮尚未验证真实图形客户端、NeoECO 远程界面交互、EMI 操作、跨端联机、真实 FX 虚拟网络和所有工艺配方。旧版客户端通过记录不作为本版证据。官方依赖组合仍会记录可选客户端 Mixin 跳过、LDLib 扫描客户端类型及 LT 配方输入重叠提示；不以日志完全无 ERROR 作为通过标准，也不把未覆盖附属列为兼容通过。
