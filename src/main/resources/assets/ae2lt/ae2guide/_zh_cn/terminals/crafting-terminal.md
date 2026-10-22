---
navigation:
  title: 天枢合成终端
  icon: ae2lt:tianshu_crafting_terminal
  parent: index.md
  position: 71
item_ids:
  - ae2lt:tianshu_crafting_terminal
  - ae2lt:wireless_tianshu_crafting_terminal
  - ae2lt:overload_alloy_anvil
---

# 天枢合成终端

终端包含合成、锻造、过载合金砧、切石和元件工作台五类工作页，点击工作区旁的标签切换。关闭界面后，工作材料保留在终端中；拆除有线终端时会掉落其中的真实材料。

铁砧沿用原版修复规则、Forge 修复事件和经验消耗。过载合金砧不会因使用或下落而磨损。

元件页可以编辑已插入存储元件的分区、升级卡和模糊匹配模式，通过翻页按钮查看分区槽位。复制模式按钮决定取出元件后是否保留分区模板。

JEI 和 EMI 支持从 ME 网络或玩家背包填入合成、锻造、切石和铁砧材料。填充时按住 Ctrl，可以为缺失材料请求自动合成。

安装 AE2WTLib 后，无线终端还提供装备槽、终端设置、磁铁过滤和垃圾页。离开垃圾页会删除其中的物品。终端合并会保留材料、能量、升级和链接；库存或设置存在冲突时，配方不会执行。
