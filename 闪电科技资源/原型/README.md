# 闪电科技 · 资源实验室

基于 The Modding Tree 2.7 的可玩网页原型，验证有限物质预算、对数科技点、自然专精和两层重置。首版规则与数值均为试验参数，不代表 Minecraft 内的配方、FE 曲线或经济平衡。

[在线试玩（当前为所有者私人访问）](https://lightning-resources-lab-20261030.deborah-fuentes60.chatgpt.site)

源码快照对应 Sites 发布提交 `a0ac64d49c571800aeaba1753260ce2a1d197b55`，部署已成功。访问需使用获准的 ChatGPT 账号；不要求读者拥有该账号，本地运行不依赖 Sites。

## 本地运行

在本目录执行 `python3 -m http.server 18763 --directory dist`，打开 `http://127.0.0.1:18763/`。静态文件和 Vue 均已包含，无需安装前端依赖。直接打开 HTML 文件不支持读取预生成的比较数据，建议通过 HTTP 访问。

- 入口：`dist/index.html`
- 独立规则模型：`dist/js/reactor-model.js`
- TMT 层与循环接入：`dist/js/layers.js`
- 中文界面：`dist/js/lab-ui.js`、`lab-template.js`、`dist/css/lab.css`
- 参数与固定策略记录：`BALANCE.md`、`balance-results.json`
- 检查：`node --test tests/model.test.cjs`
- 重新模拟：`node scripts/balance.cjs`，然后 `cp balance-results.json dist/balance-results.json`

## 推荐试玩

使用默认 20,000 物质与 80% 利用率。先扩建处理、研究单元，集中完成一条路线的 3 个研究，再内部重置完成另一条路线，最后在新子轮研究级联工艺。去运行页选择级联工艺、把材料分配改成凝聚；生产 1,000 点级联成果并最终结算后，解锁后续自动化。可使用 5×、20× 或快进 60 秒缩短试玩。

外部物质量范围 100–1e12，滑块采用对数刻度；利用率范围 10%–100%，低于 10% 无法启动。启动后锁定输入，暂停和内部重置也不能修改或补料。利用率只影响科技升级的物质费用：K = 100 / 利用率百分数。

存档仅保存在浏览器本地，每 5 秒自动保存；也支持导出/导入。隐藏、关闭页面不补算离线收益。预览与发布地址使用独立存档，可导出迁移。原型重复开轮会模拟新的外部供给，尚未实现 Minecraft 外部库存扣除。

## 验证范围

11 项模型检查通过：利用率边界、输入锁定、K 作用范围、科技点记账、浅重置、两层继承与单次结算、暂停与耗尽、序列化与最大输入收支、坏存档拒绝及自动化完整运行。

浏览器通过：9% 被禁用、10% 启动、暂停刷新恢复、三子轮取得两种原理与级联工艺、生产达到核心目标。完整数值比较采用固定策略，没有搜索最优解；尤其小预算不能机械执行多子轮模板。没有 Minecraft 实机/供电/机群/实物兑换测试。

## 上游

TMT 来源：https://github.com/Acamaeda/The-Modding-Tree

固定提交：`4d8a86cfb3c59ef3ef4c222f21ef4fbee980c621`。保留 TMT 的游戏循环、层系统、Decimal 和存档逻辑，替换示例内容与 UI。已保留 MIT 许可、The Prestige Tree 署名及 Vue / break_eternity 声明，详见 `dist/THIRD_PARTY_NOTICES.txt`。
