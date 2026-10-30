window.ReactorTemplate = `
<div class="lab-shell">
  <header class="topbar">
    <a class="brand" href="#" @click.prevent="view='production'" aria-label="资源实验室首页">
      <img src="assets/mark.svg" alt="" width="38" height="38">
      <span><strong>资源实验室</strong><small>LIGHTNING TECH · PROTOTYPE 01</small></span>
    </a>
    <nav class="main-nav" aria-label="主要导航">
      <button v-for="tab in tabs" :key="tab.id" :class="{selected:view===tab.id}" @click="view=tab.id"><span>{{tab.icon}}</span>{{tab.name}}</button>
    </nav>
    <div class="top-right"><span class="version">TMT / 0.1.0</span><button class="icon-button" @click="exportSave" title="导出当前存档" aria-label="导出存档">↗</button></div>
  </header>

  <div class="workspace">
    <aside class="sidebar">
      <div class="panel setup-panel">
        <div class="section-top"><span class="eyebrow">01 / 外层输入</span><span class="lock-label">{{active ? '● 已锁定' : '○ 可配置'}}</span></div>
        <h2>准备这次实验</h2><p class="muted small">一次投入，在多个子轮之间分配。</p>
        <label class="input-label" for="mass-input">外部物质量 <span>Q₀</span></label>
        <div class="number-box"><input id="mass-input" aria-label="外部物质量" type="number" min="100" max="1000000000000" step="100" :value="s.draft.mass" :disabled="active" @input="changeMass"><span>单位</span></div>
        <input class="range" aria-label="外部物质量滑块" type="range" min="2" max="12" step="0.01" :value="massRange" :disabled="active" @input="slideMass">
        <div class="range-ends"><span>100</span><span>对数刻度</span><span>1e12</span></div>
        <label class="input-label" for="efficiency-input">升级材料利用率 <span>η</span></label>
        <div class="number-box"><input id="efficiency-input" aria-label="升级材料利用率" type="number" min="10" max="100" step="1" :value="s.draft.efficiency" :disabled="active" @input="changeEfficiency"><span>%</span></div>
        <input class="range" aria-label="升级材料利用率滑块" type="range" min="10" max="100" step="1" :value="s.draft.efficiency" :disabled="active" @input="changeEfficiency">
        <div class="range-ends"><span>10% 最低</span><span>100%</span></div>
        <div class="cost-preview"><span>本次升级物耗</span><strong>×{{n(active ? s.locked.k : kPreview, 2)}}</strong></div>
        <p v-if="!active && inputError" class="input-error" role="alert">{{inputError}}</p>
        <p v-else class="field-help">只作用于科技升级耗材，不影响普通加工和结算。低于 10% 无法启动。</p>
        <div class="presets" v-if="!active"><button @click="preset(5000,80)">小规模</button><button @click="preset(20000,80)">推荐配置</button><button @click="preset(60000,80)">大规模</button></div>
        <button v-if="!active" class="button primary launch" :disabled="!!inputError" @click="launch">启动外层运行 <span>↗</span></button>
        <div v-else class="locked-summary"><span class="dot"></span>本次投入 {{n(s.locked.mass,0)}} · 利用率 {{n(s.locked.efficiency,1)}}%<small>内部重置不会补料，也不会重算利用率。</small></div>
      </div>

      <div class="panel cycle-panel">
        <div class="section-top"><span class="eyebrow">02 / 两层周目</span></div>
        <div class="cycle-heading"><span>外层 {{active ? s.runId : '—'}}</span><b>子轮 {{active ? s.subround : '—'}}</b></div>
        <div class="timeline"><div :class="{done:active}"><i>1</i><span>锁定投入</span></div><div :class="{done:active}"><i>2</i><span>研究 · 生产 · 内部重置</span></div><div :class="{done:s.fullComplete}"><i>3</i><span>最终结算</span></div></div>
        <div class="reset-actions"><button class="button secondary" :disabled="!canReset()" @click="modal='inner'">↻ 内部重置</button><small>保留关键科技、剩余物质和成果</small><button class="button outline" :disabled="!active" @click="modal='outer'">最终结算 <span>{{n(Math.floor(s.productPoints+1e-8),0)}} 点</span></button><small>清空内部成长，领取实际成果</small></div>
      </div>
      <div class="sidebar-bottom"><span class="save-dot"></span>本地自动存档 · 无离线补算<button @click="exportSave">导出</button><label class="text-link" for="import-save">导入</label><input id="import-save" type="file" accept="application/json,.json" class="visually-hidden" @change="importSave"></div>
    </aside>

    <main class="main-content">
      <div class="page-heading"><div><div class="eyebrow">FINITE MATTER / OPEN POSSIBILITIES</div><h1>{{view==='research' ? '让每次突破，留下知识。' : view==='records' ? '比较结果，再开始下一轮。' : view==='guide' ? '一次投入，多次子轮。' : '有限投入，持续探索。'}}</h1></div><span class="status-badge" :class="s.status"><span></span>{{statusName}}</span></div>
      <div class="stats-grid">
        <div class="stat"><span>剩余可用物质</span><strong>{{n(remaining,1)}}</strong><small>基础物质＋活化材料</small></div>
        <div class="stat"><span>可用科技点</span><strong class="blue-text">{{n(availableTP,0)}}<em>TP</em></strong><small>累计 {{totalTP}} · 已花 {{s.techSpent}}</small></div>
        <div class="stat"><span>可结算成果</span><strong class="green-text">{{n(s.productPoints,1)}}</strong><small>尚未计入外部资源点</small></div>
        <div class="stat"><span>本次运行时间</span><strong>{{duration(s.runTime)}}</strong><small>子轮 {{duration(s.subTime)}} · 关键科技 {{keyCount}}/3</small></div>
      </div>
      <div class="hint-banner"><span class="hint-symbol">↳</span><p>{{hint}}</p></div>
      <div class="playback"><div class="playback-left"><button class="button compact secondary" :disabled="!active" @click="togglePause">{{running ? 'Ⅱ 暂停' : '▶ 继续'}}</button><span class="muted small">试玩速度</span><div class="segmented"><button v-for="speedValue in [1,5,20]" :key="speedValue" :class="{on:s.speed===speedValue}" @click="speed(speedValue)">{{speedValue}}×</button></div><button class="text-button" :disabled="!running" @click="fastForward">快进 60 秒 ↗</button></div><span class="playback-note">加速推进相同规则，不增加物质</span></div>

      <template v-if="view==='production'">
        <section class="panel production-panel">
          <div class="panel-heading"><div><span class="eyebrow">03 / 物质流</span><h2>从物质到成果</h2></div><label class="process-selector">生产工艺<select aria-label="生产工艺" :disabled="!active" :value="s.process" @change="changeProcess"><option v-for="p in processDefs" :value="p.id" :disabled="!processUnlocked(p.id)">{{p.name}}{{!processUnlocked(p.id) ? ' · 未掌握' : ''}}</option></select></label></div>
          <div class="material-flow">
            <div class="flow-cell"><div class="flow-icon raw-icon">◌</div><label>基础物质</label><strong>{{n(s.raw,1)}}</strong><small>处理能力 {{n(r.process)}} /秒</small></div>
            <div class="flow-arrow"><span>处理</span><b>⟶</b></div>
            <div class="flow-cell"><div class="flow-icon feed-icon">◇</div><label>活化材料</label><strong>{{n(s.feed,1)}}</strong><small>研究与凝聚共享库存</small></div>
            <div class="flow-arrow"><span>凝聚</span><b>⟶</b></div>
            <div class="flow-cell"><div class="flow-icon product-icon">✧</div><label>结算成果</label><strong class="green-text">{{n(s.productPoints,1)}}</strong><small>当前产额 {{n(r.yield,2)}} 点/材料</small></div>
          </div>
          <div class="allocation-panel"><div class="allocation-heading"><label for="allocation">材料分配</label><span><b class="blue-text">科研 {{n(s.allocation,0)}}%</b><i> / </i><b class="green-text">凝聚 {{n(100-s.allocation,0)}}%</b></span></div><input id="allocation" aria-label="科研分配比例" class="range allocation-range" type="range" min="0" max="100" step="1" :value="s.allocation" :disabled="!active" @input="allocation($event.target.value)"><div class="allocation-options"><button :disabled="!active" @click="allocation(0)">全部凝聚</button><button :disabled="!active" @click="allocation(60)">科研 60%</button><button :disabled="!active" @click="allocation(100)">全部科研</button></div><div class="rate-note">科研需求 {{n(r.research)}} 材料/秒 · 凝聚需求 {{n(r.condense)}} 材料/秒。供给不足时，按需求比例共享材料。</div></div>
          <div class="facilities-grid"><article v-for="f in facilityDefs" :key="f.id" class="facility"><div class="facility-title"><h3>{{f.name}}</h3><span>×{{s.facilities[f.id]}}</span></div><p>{{f.description}}</p><small>{{f.unit}}</small><button class="button buy-button" :disabled="!canBuy(f.id)" @click="buyFacility(f.id)">扩建 +1 <b>{{n(facilityCost(f.id),0)}} 物质</b></button></article></div>
        </section>
        <div class="two-columns">
          <section class="panel chart-panel"><div class="panel-heading"><div><span class="eyebrow">04 / 成果积累</span><h2>这一轮的轨迹</h2></div><span class="muted small">{{n(s.productPoints / Math.max(s.runTime,1),1)}} 点/秒 · 平均</span></div><div class="chart-wrap"><div class="chart-labels"><span>{{n(plot.maxPoints,0)}}</span><span>0</span></div><svg role="img" aria-label="本次运行成果随时间增长的折线图" viewBox="0 0 720 145" preserveAspectRatio="none"><defs><linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#bce5b5" stop-opacity="0.22"/><stop offset="100%" stop-color="#bce5b5" stop-opacity="0.01"/></linearGradient></defs><path d="M12 14H708 M12 72H708 M12 130H708" stroke="#2c3a3d" stroke-dasharray="3 5" fill="none"/><polygon :points="plot.area" fill="url(#chartFill)"/><polyline :points="plot.line" fill="none" stroke="#bce5b5" stroke-width="2.5" vector-effect="non-scaling-stroke"/></svg><div class="chart-axis"><span>0:00</span><span>{{duration(plot.maxTime)}}</span></div></div></section>
          <section class="panel archive-panel"><div class="panel-heading"><div><span class="eyebrow">05 / 关键科技</span><h2>子轮之间的积累</h2></div><button class="text-button" @click="view='research'">研究网络 ↗</button></div><div class="key-list"><div v-for="key in ['flow','precision','cascade']" :class="{acquired:s.keys[key]}"><span>{{s.keys[key] ? '✓' : '○'}}</span><b>{{keyName(key)}}{{key==='cascade' ? '工艺' : '原理'}}</b><small>{{s.keys[key] ? '本次外层内保留' : '尚未掌握'}}</small></div></div><p class="muted small">内部重置保留，外层最终结算清空。</p></section>
        </div>
        <section class="panel automation-panel"><div><span class="eyebrow">06 / 连续运行</span><h2>自动化策略 <span class="tiny-pill">{{s.automationUnlocked ? '已解锁' : '核心通关后开放'}}</span></h2><p class="muted small">掌握级联工艺，生产 1,000 点级联成果并最终结算，即可在后续外层运行使用。</p></div><div v-if="s.automationUnlocked" class="automation-controls"><label class="checkbox"><input type="checkbox" :checked="s.autoEnabled" @change="setAuto">启用自动化</label><select v-model="s.autoPlan" @change="persist" aria-label="自动化策略"><option value="flow">高通量生产</option><option value="precision">精炼生产</option><option value="complete">完整研究 → 级联生产</option></select><label class="checkbox"><input type="checkbox" v-model="s.autoRepeat" @change="persist">结算后自动按本配置补给下一轮</label></div><div v-else class="completion-progress"><div><span>级联成果</span><b>{{n(s.cascadePoints,0)}} / 1,000</b></div><div class="progress-track"><i :style="{width: Math.min(100,s.cascadePoints/10)+'%'}"></i></div></div></section>
      </template>

      <template v-if="view==='research'">
        <section class="panel science-panel"><div><span class="eyebrow">03 / 子轮研究</span><h2>集中投入，跨过突破门槛。</h2><p class="muted">路线不互斥。每条主线总计 80 科技点；浅层辅修也会占用本子轮的点数。</p></div><div class="science-number"><strong>{{availableTP}}<em>TP 可用</em></strong><span>{{n(s.research,0)}} 累计研究量</span></div><div class="science-meter"><div class="progress-track blue"><i :style="{width:scienceProgress+'%'}"></i></div><span>下一科技点还需 {{n(nextResearchNeeded,1)}} 研究量</span><code>TP = ⌊10 × log₂(1 + X / 100)⌋</code></div></section>
        <div class="research-paths"><section v-for="path in ['flow','precision']" :key="path" class="research-path" :class="path"><div class="path-heading"><span class="path-symbol">{{path==='flow' ? '↯' : '✧'}}</span><div><h2>{{path==='flow' ? '高通量路线' : '精炼路线'}}</h2><p>{{path==='flow' ? '更快处理库存，适合快速周转。' : '从同量物质取得更多成果。'}}</p></div><span class="tiny-pill">总计 80 TP</span></div><article v-for="t in pathTech(path)" :key="t.id" class="tech-card" :class="{owned:has(t.id),ready:!techReason(t.id),keytech:!!t.key}"><div class="tech-top"><span class="tech-tier">0{{t.tier}}</span><span v-if="t.key" class="key-tag">跨子轮关键科技</span><span v-if="has(t.id)" class="owned-mark">✓ 已研究</span></div><h3>{{t.name}}</h3><p>{{t.description}}</p><div class="tech-cost"><span><b>{{t.tp}}</b> TP</span><span><b>{{n(actualCost(t),1)}}</b> 物质 <small>含 ×{{n(active?s.locked.k:kPreview,2)}}</small></span></div><button class="button tech-button" :disabled="!!techReason(t.id)" @click="buyTech(t.id)">{{has(t.id) ? '本子轮已完成' : !techReason(t.id) ? '研究 '+t.name+' ↗' : techReason(t.id)}}</button><small v-if="t.key && s.keys[t.key] && !has(t.id)" class="retained-note">原理已保留；再次研究只恢复本子轮普通加成。</small></article></section></div>
        <section class="panel synthesis" :class="{ready:!techReason('cascade'),owned:s.keys.cascade}"><div class="synthesis-symbol">⌘</div><div class="synthesis-copy"><span class="eyebrow">两种原理 · 一条新工艺</span><h2>级联工艺</h2><p>将高通量与精炼研究组合。产额 ×3.60，凝聚吞吐 ×1.25；还可以在本子轮继续研究普通升级。</p><div class="synthesis-keys"><span :class="{acquired:s.keys.flow}">{{s.keys.flow ? '✓' : '○'}} 高通量原理</span><span>＋</span><span :class="{acquired:s.keys.precision}">{{s.keys.precision ? '✓' : '○'}} 精炼原理</span></div></div><div class="synthesis-action"><strong>50 TP <em>＋ {{n(600*(active?s.locked.k:kPreview||1),1)}} 物质</em></strong><button class="button primary" :disabled="!!techReason('cascade')" @click="buyTech('cascade')">{{has('cascade') ? '本子轮已完成' : '研究级联工艺 ↗'}}</button><small>{{s.keys.cascade ? '已掌握，可在运行页选择级联工艺。' : techReason('cascade') || '可以研究'}}</small></div></section>
      </template>

      <template v-if="view==='records'">
        <section class="panel records-panel"><div class="panel-heading"><div><span class="eyebrow">03 / 外层档案</span><h2>已完成 {{s.completedRuns}} 次运行</h2></div><div class="total-points"><span>累计领取</span><strong>{{n(s.totalPoints,0)}} <small>资源点</small></strong></div></div><div v-if="!s.history.length" class="empty-state"><span>≋</span><h3>第一份实验记录，还在等待结算。</h3><p>提前结算也会记录投入、耗时、子轮数和实际成果。</p></div><div v-else class="table-scroll"><table><thead><tr><th>外层</th><th>物质量 / 利用率</th><th>资源点</th><th>耗时</th><th>子轮</th><th>已掌握</th></tr></thead><tbody><tr v-for="record in s.history" :key="record.id"><td>#{{record.id}} <span v-if="record.complete" class="green-text">✓</span></td><td>{{n(record.mass,0)}} / {{record.efficiency}}%</td><td class="green-text">{{n(record.reward,0)}}</td><td>{{duration(record.seconds)}}</td><td>{{record.subrounds}}</td><td>{{record.keys.map(keyName).join(' · ') || '—'}}</td></tr></tbody></table></div></section>
        <section v-if="active" class="panel ledger-panel"><div class="panel-heading"><div><span class="eyebrow">04 / 当前物质账本</span><h2>每份物质都有去向</h2></div><span class="tiny-pill">收支差 {{n(ledger.difference,6)}}</span></div><div class="ledger-grid"><div><span>剩余可用</span><b>{{n(remaining,1)}}</b></div><div><span>已形成成果的物质</span><b>{{n(s.productMass,1)}}</b></div><div><span>研究消耗</span><b>{{n(s.lostResearch,1)}}</b></div><div><span>科技升级消耗</span><b>{{n(s.lostUpgrades,1)}}</b></div><div><span>现有设施占用</span><b>{{n(s.installed,1)}}</b></div><div><span>重置设施损耗</span><b>{{n(s.lostRebuild,1)}}</b></div></div></section>
        <section class="panel benchmark-panel"><div class="panel-heading"><div><span class="eyebrow">05 / 初版数值观察</span><h2>同一模型，六种固定策略</h2></div><div class="report-filters"><select v-model="reportMass" aria-label="模拟物质量"><option :value="5000">5,000 物质</option><option :value="20000">20,000 物质</option><option :value="60000">60,000 物质</option></select><select v-model="reportEfficiency" aria-label="模拟利用率"><option :value="80">80% 利用率</option><option :value="10" :disabled="Number(reportMass)!==20000">10% 利用率</option></select></div></div><p class="muted small">这是预先运行的固定策略模拟，不是你的存档，也不代表最优解。短轮在 300 秒或耗尽时停止，其余最长 3,600 秒；结果包括研究与重建成本。</p><div v-if="reportRows.length" class="table-scroll"><table><thead><tr><th>策略</th><th>成果点数</th><th>耗时</th><th>点数 / 秒</th><th>点数 / 投入</th><th>剩余物质</th></tr></thead><tbody><tr v-for="row in reportRows" :key="row.strategy"><td>{{strategyName(row.strategy)}}</td><td>{{n(row.points,0)}}</td><td>{{duration(row.seconds)}}</td><td>{{n(row.pointsPerSecond,1)}}</td><td>{{n(row.pointsPerInput,2)}}</td><td>{{n(row.remaining,0)}}</td></tr></tbody></table></div><p v-else class="muted">该组合没有预先模拟记录。可以直接在左侧设置并试玩。</p></section>
      </template>

      <template v-if="view==='guide'">
        <section class="panel guide-panel"><span class="eyebrow">03 / 推荐的第一次实验</span><h2>先完成一次突破，再尝试另一条路线。</h2><ol class="guide-steps"><li><b>启动推荐配置</b><p>20,000 物质、80% 利用率。启动会用 90 物质建立三台基础设施。</p></li><li><b>扩建处理与研究单元，集中研究一条路线</b><p>把科研比例提高到 80%–100%，依次购买三项研究。普通升级需要科技点与物质，两条路线均可购买。</p></li><li><b>掌握关键科技后，选择生产或内部重置</b><p>重置清空普通设施和科技点，保留剩余物质、成果和关键科技。下一子轮可以专精另一方向。</p></li><li><b>组合两种原理，研究级联工艺</b><p>通常在新的子轮取得 50 科技点更划算。然后去运行页选择级联工艺，并降低科研比例开始生产。</p></li><li><b>最终结算，留下资源点和操作经验</b><p>生产 1,000 点级联成果后结算，解锁后续自动化。外层结算会清空所有内部科技，不必等到物质耗尽。</p></li></ol></section>
        <div class="two-columns"><section class="panel guide-panel"><h2>科技点为何越来越难取得？</h2><p>累计科技点 = ⌊10 × log₂(1 + 研究量 / 100)⌋。花掉科技点不会重新生成；每材料产生 45 研究量。</p><p>一条主线共需 80 点，平均分配到两条路线会推迟突破。路线没有硬互斥，足够投入或等待仍可能兼修。</p><p>研究量和未花科技点在内部重置时清空。只有实际完成的关键科技保留到外层结束。</p></section><section class="panel guide-panel"><h2>物质与利用率怎么理解？</h2><p>初始物质完整进入预算。利用率只决定升级物耗：100% 为 ×1，80% 为 ×1.25，10% 为 ×10。</p><p>设施建设、一般加工与成果结算不重复乘这个倍率。内部重置不会恢复原始库存，拆除的设施计入损耗。</p><p>外部物质与支撑的实际配方留到 Minecraft 阶段平衡。本版没有跨堆协同和特殊模块效果。</p></section></div>
        <section class="panel guide-panel"><h2>原型与存档</h2><p>本原型由 The Modding Tree 2.7 驱动，使用它的层更新、游戏循环与本地存档。规则模型独立，供后续移植和数值比较。1×、5×、20× 与快进只改变试玩速度，不代表正式 FE 供电曲线。</p><p>每 5 秒自动保存，也可导出 JSON 存档。关闭或隐藏页面不补算离线收益。存档保存在当前浏览器；本地预览与线上地址的存档互不共享，可用导出/导入迁移。</p><div class="license-links"><a href="LICENSE" target="_blank" rel="noopener">TMT · Acamaeda / MIT ↗</a><a href="Prestige-tree-license" target="_blank" rel="noopener">Prestige Tree · Jacorb / MIT ↗</a><a href="THIRD_PARTY_NOTICES.txt" target="_blank" rel="noopener">第三方声明 ↗</a></div><button class="text-button danger" @click="modal='wipe'">清空本原型的全部存档…</button></section>
      </template>

      <section v-if="active && view!=='guide'" class="event-log"><div class="log-title"><span class="eyebrow">实验日志</span><span class="muted small">最近 {{Math.min(5,s.journal.length)}} 项</span></div><div v-for="(event,index) in s.journal.slice(0,5)" :key="index" class="event"><time>{{duration(event.time)}}</time><span class="event-sub">子轮 {{event.subround}}</span><p>{{event.message}}</p></div></section>
      <footer class="main-footer"><span>LIGHTNING TECH / RESOURCE LAB</span><span>有限物质 · 两层重置 · 自然专精</span></footer>
    </main>
  </div>

  <div v-if="toast" class="toast" :class="{bad:toastBad}" role="status">{{toast}}</div>
  <div v-if="modal" class="modal-backdrop" @click.self="modal=''" @keydown.esc="modal=''">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <template v-if="modal==='inner'"><span class="eyebrow">内部子轮重置</span><h2 id="modal-title">带着知识，重新组织生产。</h2><p>清空本子轮的研究量、{{availableTP}} 个未花科技点、普通升级与设施。现有设施的 {{n(s.installed,1)}} 物质计入损耗，新基础设施消耗 90 物质。</p><div class="modal-retain"><b>保留</b><span>剩余物质 {{n(Math.max(0, remaining-90),1)}}（已扣除 90 重建费用）</span><span>已形成成果 {{n(s.productPoints,1)}}</span><span>{{keyCount}} 项关键科技 · 已锁定的利用率</span></div><p class="muted small">没有新关键科技时也可重置，但不会获得固定奖励。</p></template>
      <template v-else-if="modal==='outer'"><span class="eyebrow">外层最终结算</span><h2 id="modal-title">结束这次投入，领取成果。</h2><div class="settlement-number">{{n(Math.floor(s.productPoints),0)}} <span>资源点</span></div><p>本次所有内部科技、设施、剩余 {{n(remaining,1)}} 物质将清空。关键科技不带到下一次外层启动。</p><p v-if="s.fullComplete" class="green-text">已完成核心目标：自动化资格将在结算后保留。</p><p v-else class="muted small">提前结算有效，不要求完整通关。已有自动化资格与外部资源点会保留。</p></template>
      <template v-else-if="modal==='import'"><h2 id="modal-title">导入这份存档？</h2><p>将替换当前原型进度、轮次记录和自动化资格。建议先导出当前存档。</p></template>
      <template v-else><h2 id="modal-title">清空全部原型进度？</h2><p>这会删除当前运行、轮次记录、累计资源点和自动化资格。普通的外层结算不需要执行此操作。</p></template>
      <div class="modal-buttons"><button class="button secondary" @click="modal=''">取消</button><button class="button primary" @click="confirmModal">{{modal==='inner' ? '确认内部重置' : modal==='outer' ? '确认最终结算' : modal==='import' ? '确认导入' : '确认清空'}}</button></div>
    </section>
  </div>
</div>`;
