# 大型过载处理工厂 · 动画与发光层

2026-11-15。整机保持 3×3×3 方块、48×48×48 模型单位。本次加入宽屏动画、独立发光层，并将背面左下角改为线缆接口图案。

## 当前文件

- `large_overload_factory_redesign.bbmodel`：开机工程，贴图全部内嵌，包含 `emissive` 几何分组与同名贴图组。
- `large_overload_factory_redesign_off.bbmodel`：停机工程，暗屏、暗色能源部件，无动画和满亮。
- `previews/animation_emissive_revision.png`：本次常规光照、暗光、背面与停机对照。
- `previews/console_animation.gif`：从 Blockbench 原生贴图动画逐帧截图生成的循环预览。
- `export/assets/ae2lt/`：配套 NeoForge 模型、PNG 与动画元数据。
- `build_redesign.py`：资产生成器。重新生成会覆盖本目录工程与导出文件；继续手工修改后应先另存。

`previews/` 保留最终对照和动画，`checkpoints/` 只保留最终原生导出用于核对。早期草稿与过程截图已移到仓库外归档。

## 宽屏与线缆接口

`console_wide_display` 的尺寸、位置和凹入深度保持不变。34×12 像素屏幕加入滚动波形、状态灯与移动进度亮段：16 帧，每帧 2 tick，1.6 秒循环，不插值。仅屏幕发光图集参与动画，金属外壳与其他光源保持静态。

`console_emissive_on.png` 为 64×1024 竖排贴图，单帧 64×64，配套 `.png.mcmeta` 已导出。停机贴图为静态 64×64。

背面左下角使用新绘制的 12×12 线缆接口：闭合金属接口框、中央插接槽与粉色触点。生成器不再读取 IO 端口的任何贴图。中间和右侧保留过载接口与样板存储的原生像素图案。

## 发光层

晶体、线缆与母线亮纹、线圈、放电触点、磁夹亮条、机壳标志、屏幕内容和背面彩色亮区均已从普通贴图中拆出。静态亮区共用 `emissive_on.png`（128×128）；屏幕单独使用动画贴图。停机工程引用对应的 `_off` 贴图并恢复普通光照。

几何按 `body`、`emissive`、`windows` 分层。外壳与发光贴图使用互补 Alpha：同一位置只有一层拥有可见像素，不通过向外偏移表面制造亮层。晶体和四个放电触点整体沿用原节点，其余含亮区的面共增加 35 个表面载体；没有复制整台机器。

每套工程共 176 个立方体节点、10 个分组、913 个贴图面、13 张内嵌贴图；两种状态合计导出 17 张 PNG 与 1 个动画元数据文件。所有原有 141 个部件的坐标、尺寸和旋转保持一致，根元素 `bottom`、`top`、`inner_base` 保留。

## 保留的结构修订

- 前角立柱完整，前方与左右为三面独立窗。外框与四角齐平，窗井深 4 单位，玻璃厚 2 单位并内收 1 单位。直窗壁复用横梁和立柱，12 个斜向内壁各保留一个可见面。
- 用户删除的三个窗框中央卡块保持删除。切开立柱、三面连窗的方案已撤回。
- 顶部为清晰的闭合盖板和检修区边界，中央条栅内陷 1 单位。两条 3×2 截面的实体线缆位于 3 单位深线槽内，各有两个接头和一个固定卡。
- 后部两组散热片各五片。按要求向左右各加宽 2 单位，片体宽 15、深 9 单位，保留穿过片组的四根导管、上下安装座和中央连接管路。
- 灰白金属与粉色能源保持 AE2LT 材质语言；默认每模型单位 1 像素。图集分辨率不改变实际采样密度。

结构参考 [Mekanism-MoreMachine](https://github.com/lostmyself8/Mekanism-MoreMachine) 的散热与厚窗框组织；本机材质采用 AE2LT 素材和新绘制像素图案。

## 导出与验证

目标为 Minecraft Java 1.21.1 / NeoForge 21.1.220。使用项目已有的 `neoforge:composite`，实体与发光层为 `cutout`，观察窗为 `translucent`。开机发光面使用已核对的 NeoForge `ExtraFaceData`：

```json
"neoforge_data": {
  "block_light": 15,
  "sky_light": 15,
  "ambient_occlusion": false
}
```

发光元素同时设置 `shade: false`；停机模型没有满亮字段。这里实现的是材质在夜间保持亮度，不改变方块的世界光照等级。

游戏接入应使用 `export/` 中的 JSON。Blockbench 的普通 Java 导出不会保留本项目的 NeoForge 复合分层与逐面满亮字段；`checkpoints/native_final_on.json`、`native_final_off.json` 用于核对几何、UV 和贴图引用。

已检查两种状态的实际 Blockbench 预览、16 帧动画及回到首帧、正反面与暗光对照。暗光图通过临时固定发光材质亮度并降低普通材质环境光生成，PNG 的 Alpha 保持不变。Blockbench 自带“发光”模式的 Alpha 语义与 NeoForge 不同，因此该对照是按导出规则模拟，不能作为游戏内验证。

两套工程的 913 个贴图面均通过像素密度核对，内嵌图像逐像素匹配导出 PNG，原生导出与复合模型的几何和 UV 一致。分层后可逐面重建此前全部 754 个表面的 Alpha，没有重叠的实色像素或丢失的遮挡。完整结果见 `delivery_validation.json`、`validation.json`。

正式游戏资源已接入 `src/main/resources/assets/ae2lt/{models,textures}/block/large_overload_factory/`。设计导出仍以中央核心方块为原点；正式模型将纹理引用改为正式路径，并增加 NeoForge 根变换 `translation: [0, 0, 1]`、`origin: corner`，在朝向旋转前向后平移一个方块，对齐控制器。整机经区块模型渲染，成型时隐藏成员单块模型，拆除后恢复。加工时使用开机模型，连续 40 tick 没有操作后停机。

主机方块另有完整的 16×16 `controller_front.png`，配合既有金属侧面、顶面和底面，修复将透明零件图集直接用于整块面导致的纹理缺失；物品模型共用此修正。这张单块贴图不替换确认稿中的整机宽屏或外壳。

游戏接入、AE2 界面和验证记录见仓库 `docs/large-overload-factory-implementation.md`；最终游戏截图位于相邻的 `integration_2026-11-15/`。隔离客户端的临时运行目录已清理，原始记录和 JAR 已归档。
