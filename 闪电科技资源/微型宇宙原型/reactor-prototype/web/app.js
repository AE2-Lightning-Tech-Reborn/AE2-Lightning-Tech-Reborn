// 试玩界面：奇点里的宇宙。只通过规则核的动作（act / step）操作，和脚本、策略走同一条路。
// 渲染是纯函数（状态 → HTML 字符串），便于在 Node 里测试；最底下是很薄的一层 DOM 绑定。
//
// 首轮按“阶段”逐步放出面板：每个阶段只有一个目标，达成后才出现下一块内容。
// 阶段只是界面上的引导，不是游戏规则；脚本和策略不受它限制。

import {
  createRun,
  step,
  act,
  ledger,
  toQ,
  era,
  income,
  maturity,
  grade,
  chargeRate,
  strikeAmount,
  decayTime,
  maxCharge,
  autoAvailable,
  steadyExcited,
  milestone,
  hasLaw,
  collapseNeed,
  devicePrice,
  matureCostMult,
  outputMult,
  fluxCap,
  structQ,
  excitationCap,
  quote,
} from '../src/core.js';
import { RULES, KINDS, lawById } from '../src/params.js';
import { devicePurchase } from '../src/advisor.js';

const KIND_NAME = { cloud: '尘云', nucleus: '晶核', halo: '暗晕' };
const KIND_TEXT = {
  cloud: '不用击发就持续产生势，坍缩后立刻有收入',
  nucleus: '让成熟更便宜',
  halo: '放大势的总产出',
};
function lawText(st, law) {
  if (law.id === 'selfexc') return `结构质量的 ${pct(st.R.selfexcShare, 0)} 可被激发；自动击发不需要电容`;
  if (law.id === 'afterglow') return `过热每散失 1 Q 产生 ${fmt(st.R.afterglowYield)} ×（M* / Q0）势`;
  return law.text;
}
const ERA_NAME = ['原初时代', '凝聚时代'];
const SPEEDS = [1, 5, 20, 100];
const Q0_CHOICES = [1e6, 1e7, 1e8, 1e9];
const ETA_CHOICES = [1, 0.8, 0.5, 0.2];
const FIRST_MATURE = 4;

// ———————————————————————— 格式化 ————————————————————————

export function fmt(n) {
  if (!Number.isFinite(n)) return '∞';
  const a = Math.abs(n);
  if (a >= 1e16) return n.toExponential(2);
  if (a >= 1e12) return `${(n / 1e12).toFixed(2)}万亿`;
  if (a >= 1e8) return `${(n / 1e8).toFixed(2)}亿`;
  if (a >= 1e4) return `${(n / 1e4).toFixed(1)}万`;
  if (a >= 100) return n.toFixed(0);
  return n.toFixed(a >= 10 ? 1 : 2);
}

export function fmtTime(sec) {
  const s = Math.floor(sec);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(r).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function pct(x, digits = 1) {
  return `${(x * 100).toFixed(digits)}%`;
}

// ———————————————————————— 阶段与目标 ————————————————————————

function totalDevices(st) {
  return st.devices[0] + st.devices[1] + st.devices[2];
}

export const STAGES = [
  {
    id: 'ignite',
    title: '点火',
    intro: '奇点里是一团还没有动起来的原初物质。闪电在蓄能，蓄满后击发：被击中的物质进入激发态，激发态的物质会持续产生“势”。激发态会慢慢衰减，所以要不断击发。',
    done: (st) => st.counts.strikes >= 3,
  },
  {
    id: 'devices',
    title: '装置',
    intro: '用势造装置。三种装置各管一件事：击发线圈让每一击激发更多，电容让蓄能更快，约束环让激发态维持更久。三者相乘，决定有多少物质能保持在激发态。',
    done: (st) => totalDevices(st) >= 6,
  },
  {
    id: 'auto',
    title: '自动击发',
    intro: '电容造到 5 个，就能让它蓄满自动击发，不用再手动点。',
    done: (st) => autoAvailable(st),
  },
  {
    id: 'mature',
    title: '成熟',
    intro: '把势投入“成熟”，提高这个宇宙的成熟度。成熟度决定最后的提取率：同样的质量能换多少资源点。每高一级花费翻倍，提取率越来越接近上限，但到不了。成熟度在坍缩时保留。',
    done: (st) => maturity(st) >= 1 - 1e-9,
  },
  {
    id: 'collapse',
    title: '坍缩',
    intro: '累计的势够了，就可以让宇宙坍缩重启：装置、蓄能和势全部清空，一小部分质量散失，换来一条“规律”。规律在这一轮里一直有效，也让你进入凝聚时代。五条里最多选三条，选哪条决定这一轮的打法。',
    done: (st) => st.laws.length >= 1,
  },
  {
    id: 'structure',
    title: '凝聚',
    intro: '凝聚时代可以把弥散质量凝成结构：尘云、晶核、暗晕，各有用处。结构里的质量不能被激发，也不能直接抽取，凝聚和溶解都会散失一点。要不要凝、凝多少，由你决定。',
    done: (st, game) => game.seenStructure,
  },
  {
    id: 'extract',
    title: '抽取',
    intro: '最后是抽取：建抽取口，把弥散质量抽成资源点，按抽取那一刻的提取率记价。抽走的质量不再产生势。可以边抽边继续成熟，后抽的更值钱；也可以分一部分去凝成样本。',
    done: () => false,
  },
];

const STAGE_INDEX = Object.fromEntries(STAGES.map((s, i) => [s.id, i]));

function reached(game, id) {
  return game.showAll || game.stage >= STAGE_INDEX[id];
}

export function advanceStage(game) {
  const st = game.st;
  if (!st) return;
  while (game.stage < STAGES.length - 1 && STAGES[game.stage].done(st, game)) game.stage += 1;
}

export function currentGoal(game) {
  const st = game.st;
  const R = st.R;
  const stage = STAGES[game.stage].id;
  const is = (c) => (cmd) => cmd === c;
  switch (stage) {
    case 'ignite':
      return { text: '击发 3 次', value: st.counts.strikes, target: 3, focus: is('strike') };
    case 'devices':
      return { text: '造 6 个装置（任意种类）', value: totalDevices(st), target: 6, focus: is('device') };
    case 'auto':
      return { text: `把电容造到 ${R.milestones.auto.count} 个`, value: st.devices[0], target: R.milestones.auto.count, focus: (cmd, d) => cmd === 'device' && String(d.kind) === '0' };
    case 'mature':
      return { text: '把成熟度提到 1 级', value: maturity(st), target: 1, focus: is('mature') };
    case 'collapse':
      return { text: '累计够势，坍缩一次，选一条规律', value: Math.min(st.phiEra, collapseNeed(st)), target: collapseNeed(st), big: true, focus: is('collapse') };
    case 'structure':
      return { text: '看看三种结构，决定要不要凝聚', value: 0, target: 1, focus: (cmd) => cmd === 'form' || cmd === 'skipStructure' };
    default:
  }
  // 抽取阶段
  const F = toQ(R, st.free);
  if (st.free === 0n) {
    const left = KINDS.some((k) => st.struct[k] > 0n);
    if (left) return { text: '弥散质量抽完了：把结构溶解掉继续抽，或者直接结算', value: 0, target: 1, focus: (cmd) => cmd === 'dissolve' || cmd === 'settle' };
    return { text: '宇宙已经抽干，结算', value: 1, target: 1, focus: is('settle') };
  }
  if (st.extracting) {
    const q0 = toQ(R, st.Q0);
    return { text: '抽取中。可以继续成熟，后抽的更值钱', value: q0 - F, target: q0, big: true, focus: is('mature') };
  }
  const x = maturity(st);
  if (x < FIRST_MATURE - 1e-9) return { text: `把成熟度提到 ${FIRST_MATURE} 级（提取率约 73%）`, value: x, target: FIRST_MATURE, focus: is('mature') };
  const want = Math.min(F / 120, fluxCap(st));
  if (st.thr < want * 0.97) return { text: '建抽取口（2 分钟抽完）', value: st.thr, target: want, big: true, focus: (cmd, d) => cmd === 'flux' && String(d.seconds) === '120' };
  return { text: '开始抽取', value: 0, target: 1, focus: (cmd, d) => cmd === 'extract' && String(d.on) === '1' };
}

// ———————————————————————— 游戏对象 ————————————————————————

export function newGame() {
  return {
    st: null,
    setup: { Q0: 1e6, eta: 0.8 },
    speed: 1,
    paused: false,
    autoBuy: false,
    sink: null, // 持续投入成熟：{ level }
    stage: 0,
    seenStructure: false,
    showAll: false,
    runs: 0,
    message: '',
    messageOk: true,
  };
}

export function startGame(game) {
  game.st = createRun({ Q0: game.setup.Q0, K: 1 / game.setup.eta });
  game.paused = false;
  game.autoBuy = false;
  game.sink = null;
  game.seenStructure = game.runs > 0;
  game.stage = game.runs > 0 ? STAGES.length - 1 : 0; // 打完过一轮之后不再逐步引导
  say(game, true, '奇点已封闭。投入和利用率已锁定。');
}

function say(game, ok, text) {
  game.message = text;
  game.messageOk = ok;
}

function doAct(game, intent, okText) {
  const res = act(game.st, intent);
  if (res.ok) say(game, true, typeof okText === 'function' ? okText(res) : okText);
  else say(game, false, `没有成功：${res.reason}`);
  return res;
}

function spendable(st) {
  return st.phi * (1 - 1e-9);
}

// 有任意一条规律后，可以让界面代买装置
function autoBuyAllowed(st) {
  return st.laws.length >= 1;
}

function sinkIntent(game) {
  const st = game.st;
  if (!game.sink) return null;
  const need = quote.matureTo(st, game.sink.level);
  if (need <= 1e-6) {
    say(game, true, `成熟度到 ${game.sink.level} 级了，提取率 ${pct(grade(st))}。`);
    game.sink = null;
    return null;
  }
  const spend = Math.min(spendable(st), need);
  return spend > 0 ? { type: 'mature', phi: spend } : null;
}

export function tick(game, n = 1) {
  const st = game.st;
  if (!st || st.settled || game.paused) return;
  for (let i = 0; i < n && !st.settled; i++) {
    const intents = [];
    const s = sinkIntent(game);
    if (s) intents.push(s);
    else if (game.autoBuy && autoBuyAllowed(st)) {
      const g = devicePurchase(st, st.auto.on ? st.auto.level : 1);
      if (g.intent) intents.push(g.intent);
    }
    step(st, intents);
  }
  advanceStage(game);
}

// ———————————————————————— 命令 ————————————————————————

export function dispatch(game, cmd, arg = {}) {
  runCommand(game, cmd, arg);
  advanceStage(game);
}

function runCommand(game, cmd, arg) {
  const st = game.st;
  const R = RULES;
  switch (cmd) {
    case 'setQ0':
      game.setup.Q0 = Number(arg.value);
      return;
    case 'setEta':
      game.setup.eta = Number(arg.value);
      return;
    case 'start':
      startGame(game);
      return;
    case 'restart':
      game.st = null;
      say(game, true, '');
      return;
    case 'speed':
      game.speed = Number(arg.value);
      game.paused = false;
      return;
    case 'pause':
      game.paused = !game.paused;
      return;
    case 'showAll':
      game.showAll = !game.showAll;
      return;
    default:
  }
  if (!st) return;

  switch (cmd) {
    case 'strike':
      doAct(game, { type: 'strike' }, (res) => {
        const hot = res.charge > 1 + 1e-9 ? ` 宇宙过热（${res.charge.toFixed(1)} 格）：势更多，但弥散质量在散失，下一次普通击发才会冷却。` : '';
        return `击发！${fmt(res.excited)} Q 物质进入激发态。${hot}`;
      });
      return;
    case 'device': {
      const kind = Number(arg.kind);
      const d = R.devices[kind];
      doAct(game, { type: 'buyDevice', kind, count: 1 }, () => {
        const n = st.devices[kind];
        const hit = Object.values(R.milestones).find((m) => m.kind === kind && m.count === n);
        return `${d.name} +1。${hit ? `解锁“${hit.name}”：${hit.text}。` : ''}`;
      });
      return;
    }
    case 'auto': {
      const level = Number(arg.level ?? st.auto.level);
      const on = arg.on === undefined ? !st.auto.on : String(arg.on) === '1';
      doAct(game, { type: 'setAuto', on, level }, on ? `自动击发已开启：蓄到 ${level} 格就击发。` : '自动击发已关闭。');
      return;
    }
    case 'autoBuy':
      if (!autoBuyAllowed(st)) return say(game, false, '完成第一次坍缩后才能让它代买装置。');
      game.autoBuy = !game.autoBuy;
      say(game, true, game.autoBuy ? '已开启代买装置：按最划算的顺序买，直到全部弥散质量都能保持激发。' : '已关闭代买装置。');
      return;
    case 'mature': {
      const level = Math.floor(maturity(st) + 1e-9) + 1;
      game.sink = { level };
      say(game, true, `开始向成熟度 ${level} 级持续投入势，够了自动停。想造装置先点“停止投入”。`);
      return;
    }
    case 'stopSink':
      game.sink = null;
      say(game, true, '已停止投入。');
      return;
    case 'collapse': {
      const law = lawById(R, arg.law);
      const res = doAct(game, { type: 'collapse', law: arg.law }, (r) => `宇宙坍缩重启，散失 ${fmt(r.scattered)} Q。取得规律“${law.name}”：${lawText(st, law)}。`);
      if (res.ok) game.sink = null;
      return;
    }
    case 'form': {
      const amount = toQ(R, st.Q0) * Number(arg.frac);
      const res = doAct(game, { type: 'form', kind: arg.kind, amount }, `凝聚了 ${fmt(amount)} Q 的${KIND_NAME[arg.kind]}。`);
      if (res.ok) game.seenStructure = true;
      return;
    }
    case 'skipStructure':
      game.seenStructure = true;
      say(game, true, '先不凝聚。结构面板一直可用。');
      return;
    case 'dissolve': {
      const amount = structQ(st, arg.kind);
      doAct(game, { type: 'dissolve', kind: arg.kind, amount }, `${KIND_NAME[arg.kind]}已全部溶解回弥散质量。`);
      return;
    }
    case 'flux': {
      const F = toQ(R, st.free);
      const target = Math.min(F / Number(arg.seconds), fluxCap(st));
      const missing = target - st.thr;
      if (missing <= target * 1e-6) return say(game, false, '抽取口已经够大了。');
      const amount = Math.min(missing, spendable(st) / R.pt);
      if (amount <= 0) return say(game, false, '势不足。');
      doAct(game, { type: 'buyFlux', amount }, `抽取通量 +${fmt(amount)} Q/秒。`);
      return;
    }
    case 'extract':
      doAct(
        game,
        { type: 'setExtract', on: String(arg.on) === '1', blankShare: st.blankShare },
        String(arg.on) === '1' ? '开始抽取。抽取不可逆，按此刻的提取率记价。' : '停止抽取。',
      );
      return;
    case 'blank':
      doAct(game, { type: 'setExtract', on: st.extracting, blankShare: Number(arg.share) }, `抽取通量的 ${pct(Number(arg.share), 0)} 用来凝成样本胚。`);
      return;
    case 'emergency':
      doAct(game, { type: 'setEmergency', on: !st.emergency }, st.emergency ? '兜底抽取已关闭。' : '兜底抽取已开启：很慢，提取率只有 5%。');
      return;
    case 'settle': {
      const sampleTier = arg.sample === undefined || arg.sample === '' ? null : Number(arg.sample);
      const res = doAct(game, { type: 'settle', sampleTier }, '宇宙终结，已结算。');
      if (res.ok) game.runs += 1;
      return;
    }
    default:
      say(game, false, '未知操作。');
  }
}

// ———————————————————————— 渲染 ————————————————————————

let focusFn = () => false;

function btn(label, cmd, data = {}, opts = {}) {
  const attrs = Object.entries(data)
    .map(([k, v]) => ` data-${k}="${v}"`)
    .join('');
  const cls = ['btn'];
  if (opts.active) cls.push('active');
  if (opts.danger) cls.push('danger');
  if (opts.primary) cls.push('primary');
  if (opts.big) cls.push('big');
  if (opts.cant) cls.push('cant');
  if (focusFn(cmd, data) && !opts.cant) cls.push('hl');
  return `<button class="${cls.join(' ')}" data-cmd="${cmd}"${attrs}>${label}</button>`;
}

function progress(value, target, cls = '') {
  const share = target > 0 ? Math.max(0, Math.min(1, value / target)) : 0;
  return `<div class="progress ${cls}"><span style="width:${(share * 100).toFixed(2)}%"></span></div>`;
}

function badge(game, id) {
  return !game.showAll && STAGES[game.stage].id === id ? '<span class="new">新</span>' : '';
}

function renderSetup(game) {
  const q = Q0_CHOICES.map((v) => btn(`${fmt(v)} Q`, 'setQ0', { value: v }, { active: game.setup.Q0 === v })).join('');
  const e = ETA_CHOICES.map((v) => btn(pct(v, 0), 'setEta', { value: v }, { active: game.setup.eta === v })).join('');
  return `
  <section class="card">
    <h2>创生</h2>
    <p>把一批物质压成奇点，点燃成一个微型宇宙。把它养成熟，再抽干，换成资源点和样本。</p>
    <p>选好投入多少物质和约束场的利用率。封闭之后两者都锁定，不能补料。这个宇宙的总质量只会减少，不会增加。</p>
    <div class="row wrap"><span class="label">投入物质</span>${q}</div>
    <div class="row wrap"><span class="label">利用率</span>${e}<span class="note">利用率越低，凝聚和建抽取口时多散失的质量越多（K = ${(1 / game.setup.eta).toFixed(2)}）</span></div>
    <div class="row">${btn('封闭并点火', 'start', {}, { primary: true })}</div>
    <p class="note">${
      game.runs === 0
        ? '第一轮建议用 100.0万 Q、80%。界面会一步一步放出内容，每一步只有一个目标，整轮十几分钟。'
        : '你已经打完过一轮，这次开局就显示全部面板。宇宙越大，同样的成熟度越难达到。'
    }</p>
  </section>`;
}

function renderGoal(game) {
  const st = game.st;
  const stage = STAGES[game.stage];
  const goal = currentGoal(game);
  const num = goal.big ? `${fmt(goal.value)} / ${fmt(goal.target)}` : `${Math.floor(goal.value * 100) / 100} / ${goal.target}`;
  return `
  <section class="card goal">
    <div class="row wrap"><span class="stagetag">第 ${game.stage + 1} / ${STAGES.length} 步 · ${stage.title}</span><b class="goaltext">目标：${goal.text}</b><span class="note">${num}</span></div>
    ${progress(goal.value, goal.target)}
    <p>${stage.intro}</p>
    <p class="msg ${game.messageOk ? 'ok' : 'bad'}">${game.message || '&nbsp;'}</p>
    <p class="note">${ERA_NAME[era(st)]} · 已运行 ${fmtTime(st.t * st.R.dt)}</p>
  </section>`;
}

// 宇宙：一条表示全部质量去向的长条，加上几个关键读数
function renderUniverse(game) {
  const st = game.st;
  const R = st.R;
  const l = ledger(st);
  const q0 = toQ(R, st.Q0);
  const F = toQ(R, st.free);
  const share = (q) => q / q0;
  const parts = [
    { name: '激发态', cls: 'excited', q: st.excited },
    { name: '弥散（未激发）', cls: 'free', q: Math.max(0, F - st.excited) },
    { name: '尘云', cls: 'cloud', q: structQ(st, 'cloud') },
    { name: '晶核', cls: 'nucleus', q: structQ(st, 'nucleus') },
    { name: '暗晕', cls: 'halo', q: structQ(st, 'halo') },
    { name: '抽取口', cls: 'embedded', q: toQ(R, l.embedded) },
    { name: '已抽取', cls: 'settled', q: toQ(R, l.settled) },
    { name: '样本胚', cls: 'blank', q: toQ(R, l.blank) },
    { name: '散失', cls: 'loss', q: toQ(R, l.loss) },
  ];
  // 激发态的份额往往极小，给它一个看得见的最小宽度
  const bar = parts
    .filter((p) => p.q > 0)
    .map((p) => `<span class="seg ${p.cls}" style="flex:${Math.max(share(p.q), p.cls === 'excited' ? 0.004 : 0).toFixed(6)}" title="${p.name} ${fmt(p.q)} Q"></span>`)
    .join('');
  const legend = parts
    .filter((p) => p.q > 0)
    .map((p) => `<span><i class="seg ${p.cls}"></i>${p.name} ${p.q / q0 < 0.001 ? `${fmt(p.q)} Q` : pct(share(p.q))}</span>`)
    .join('');
  const stats = [
    `<div class="stat"><b>${fmt(st.phi)}</b><span>势（每秒 +${fmt(income(st))}）</span></div>`,
    `<div class="stat"><b>${fmt(st.excited)} Q</b><span>激发态（弥散 ${fmt(st.excitedFree)} Q + 结构 ${fmt(st.excitedStruct)} Q）</span></div>`,
  ];
  if (reached(game, 'mature')) stats.push(`<div class="stat"><b>${maturity(st).toFixed(2)} 级</b><span>成熟度 · 提取率 ${pct(grade(st))}</span></div>`);
  if (reached(game, 'extract')) {
    const booked = Number(quote.bookedPoints(st)) / (R.P * q0);
    stats.push(`<div class="stat"><b>${pct(booked)}</b><span>已入账回收率（上限 ${pct(R.C, 0)}）</span></div>`);
  }
  const laws = st.laws.length > 0 ? `<div class="row wrap"><span class="label">规律</span>${st.laws.map((id) => `<span class="chip own">${lawById(R, id).name} · ${lawText(st, lawById(R, id))}</span>`).join('')}</div>` : '';
  return `
  <section class="card universe ${st.heat > 1 ? 'hot' : ''}">
    <div class="row wrap">${stats.join('')}</div>
    <div class="bar">${bar}</div>
    <div class="legend">${legend}<span class="note">总质量 ${fmt(q0)} Q · 利用率 ${pct(1 / st.K, 0)}</span></div>
    ${laws}
  </section>`;
}

function renderStrike(game) {
  const st = game.st;
  const R = st.R;
  const max = maxCharge(st);
  const ready = st.charge >= R.minStrike;
  const next = strikeAmount(st, Math.max(st.charge, R.minStrike));
  const hot = st.heat > 1 ? `<p class="warn">宇宙过热（${st.heat.toFixed(1)} 格）：普通势产出 ×${(1 + R.overGain * (st.heat - 1)).toFixed(2)}，弥散质量每分钟散失 ${pct(quote.burnRate(st) * 60, 2)}。${hasLaw(st, 'afterglow') ? `余辉按烧失质量另行产势，不再乘过热倍率。` : ''}下一次不超过 1 格的击发会让它冷却。</p>` : '';
  let auto = '';
  if (autoAvailable(st)) {
    const levels = max > 1 ? [1, 2, 3] : [1];
    const picks = levels.map((lv) => btn(`${lv} 格`, 'auto', { on: 1, level: lv }, { active: st.auto.on && st.auto.level === lv })).join('');
    auto = `<div class="row wrap"><span class="label">自动击发</span>${btn(st.auto.on ? '开' : '关', 'auto', {}, { active: st.auto.on })}<span class="note">蓄到</span>${picks}<span class="note">就击发${max > 1 ? '；2 格以上是超限，会让宇宙过热' : ''}</span></div>`;
  } else if (reached(game, 'auto')) {
    auto = `<p class="note">电容造到 ${R.milestones.auto.count} 个后可以自动击发。</p>`;
  }
  return `
  <section class="card strike">
    <h3>击发</h3>
    <div class="row wrap">
      ${btn(`击发（${st.charge.toFixed(2)} 格）`, 'strike', {}, { big: true, primary: ready, cant: !ready })}
      <div class="grow">
        ${progress(st.charge, max, st.charge > 1 ? 'over' : '')}
        <span class="note">蓄能 ${st.charge.toFixed(2)} / ${max} 格 · 每格 ${(1 / chargeRate(st)).toFixed(1)} 秒 · 现在击发会激发 ${fmt(next)} Q · 激发态维持 ${decayTime(st).toFixed(0)} 秒</span>
      </div>
    </div>
    ${hot}${auto}
  </section>`;
}

function renderDevices(game) {
  const st = game.st;
  const R = st.R;
  const phi = spendable(st);
  const now = [`每格 ${(1 / chargeRate(st)).toFixed(1)} 秒`, `每击 ${fmt(strikeAmount(st))} Q`, `维持 ${decayTime(st).toFixed(0)} 秒`];
  const rows = R.devices
    .map((d, j) => {
      const price = devicePrice(st, j, 1);
      const ms = Object.values(R.milestones).find((m) => m.kind === j);
      const got = st.devices[j] >= ms.count;
      return `
      <div class="item">
        <div class="row wrap"><b>${d.name} × ${st.devices[j]}</b><span class="note">${d.text} · 每个 ×${d.mult} · 现在${now[j]}</span></div>
        <div class="row wrap">${btn(`造 1 个 · ${fmt(price)} 势`, 'device', { kind: j }, { cant: price > phi })}
          <span class="${got ? 'chip own' : 'chip'}">${ms.count} 个：${ms.name}${got ? ' ✓' : ''}</span><span class="note">${ms.text}</span></div>
      </div>`;
    })
    .join('');
  const F = toQ(R, st.free);
  const cap = excitationCap(st);
  const steady = steadyExcited(st, st.auto.on ? st.auto.level : 1);
  const autoBuy = autoBuyAllowed(st) ? btn(game.autoBuy ? '代买装置：开' : '代买装置：关', 'autoBuy', {}, { active: game.autoBuy }) : '';
  return `
  <section class="card">
    <h3>装置 ${badge(game, 'devices')}<small>坍缩时清除</small></h3>
    <p class="note">按现在的装置连续击发，激发态大约能稳定在 <b>${fmt(Math.min(steady, cap))} Q</b>，目标可激发质量 ${fmt(cap)} Q（弥散 ${fmt(F)} Q）${steady >= cap ? '，已经覆盖全部可激发质量，再造装置没有用了' : ''}。</p>
    ${rows}
    <div class="row wrap">${autoBuy}</div>
  </section>`;
}

function renderMature(game) {
  const st = game.st;
  const x = maturity(st);
  const next = Math.floor(x + 1e-9) + 1;
  const cost = quote.matureTo(st, next);
  const nextGrade = (st.R.C * next) / (next + st.R.h);
  const action = game.sink
    ? btn(`正在投入 · 还差 ${fmt(quote.matureTo(st, game.sink.level))} 势 · 点这里停止`, 'stopSink', {}, { active: true })
    : btn(`成熟到 ${next} 级 · 需要 ${fmt(cost)} 势`, 'mature');
  const mult = matureCostMult(st);
  return `
  <section class="card">
    <h3>成熟 ${badge(game, 'mature')}<small>坍缩时保留</small></h3>
    <div class="row wrap"><b>${x.toFixed(2)} 级 · 提取率 ${pct(grade(st))}</b>${action}<span class="note">到 ${next} 级时提取率 ${pct(nextGrade)}${mult < 1 ? ` · 当前花费 ×${mult.toFixed(2)}` : ''}</span></div>
    ${progress(x - Math.floor(x + 1e-9), 1)}
  </section>`;
}

function renderCollapse(game) {
  const st = game.st;
  const R = st.R;
  const need = collapseNeed(st);
  const full = st.laws.length >= R.lawSlots;
  const ready = st.phiEra >= need && !full;
  const keep = '装置清空';
  const laws = R.laws
    .map((l) => {
      if (hasLaw(st, l.id)) return `<div class="row wrap"><span class="chip own">已持有</span><b>${l.name}</b><span class="note">${lawText(st, l)}</span></div>`;
      return `<div class="row wrap">${btn(`坍缩，取得“${l.name}”`, 'collapse', { law: l.id }, { danger: true, cant: !ready })}<span class="note">${lawText(st, l)}</span></div>`;
    })
    .join('');
  return `
  <section class="card">
    <h3>坍缩 ${badge(game, 'collapse')}<small>规律 ${st.laws.length} / ${R.lawSlots}</small></h3>
    <p>${full ? '规律已满，不能再坍缩。' : `本时代累计的势 ${fmt(st.phiEra)} / ${fmt(need)}。`} 坍缩时：${keep}，蓄能、激发态和势清空，弥散质量散失 ${pct(R.collapseLoss * (hasLaw(st, 'conserve') ? R.lawConserve : 1), 2)}；成熟度、结构和已抽取的质量保留。</p>
    ${full ? '' : progress(st.phiEra, need)}
    ${laws}
  </section>`;
}

function renderStructures(game) {
  const st = game.st;
  const R = st.R;
  const q0 = toQ(R, st.Q0);
  const effect = {
    cloud: `每秒 +${fmt(R.cloudYield * structQ(st, 'cloud') * outputMult(st))} 势`,
    nucleus: `成熟花费 ÷${(1 + (R.nucleusK * structQ(st, 'nucleus')) / q0).toFixed(2)}`,
    halo: `势的产出 ×${(1 + (R.haloK * structQ(st, 'halo')) / q0).toFixed(2)}`,
  };
  const rows = KINDS.map((k) => {
    const have = structQ(st, k);
    const forms = [0.1, 0.3].map((f) => btn(`凝聚 ${pct(f, 0)}`, 'form', { kind: k, frac: f })).join('');
    return `
    <div class="item">
      <div class="row wrap"><i class="seg ${k} dotbig"></i><b>${KIND_NAME[k]}</b><span class="note">${KIND_TEXT[k]}</span></div>
      <div class="row wrap"><span>${fmt(have)} Q（${pct(have / q0)}）· ${effect[k]}</span>${forms}${have > 0 ? btn('全部溶解', 'dissolve', { kind: k }) : ''}</div>
    </div>`;
  }).join('');
  const skip = !game.seenStructure ? btn('先不凝聚，继续', 'skipStructure') : '';
  return `
  <section class="card">
    <h3>结构 ${badge(game, 'structure')}<small>坍缩时保留</small></h3>
    <p class="note">按总质量的比例凝聚。凝聚时散失 ${pct(quote.formLoss(st), 2)}，溶解时散失 ${pct(quote.dissolveLoss(st), 2)}。结构里的质量不能被激发，抽取前要先溶解。</p>
    ${rows}
    <div class="row wrap">${skip}</div>
  </section>`;
}

function renderExtract(game) {
  const st = game.st;
  const R = st.R;
  const shares = [0, 0.1, 0.2, 0.5].map((s) => btn(pct(s, 0), 'blank', { share: s }, { active: st.blankShare === s })).join('');
  const points = quote.bookedPoints(st);
  const blankQ = toQ(R, st.blank);
  const samples = R.sampleRate.map((rate, i) => (era(st) >= i + 1 ? btn(`结算并带出 ${Math.floor(blankQ / rate)} 份${R.sampleNames[i]}`, 'settle', { sample: i }, { danger: true }) : '')).join('');
  const leftStruct = KINDS.reduce((s, k) => s + structQ(st, k), 0);
  return `
  <section class="card">
    <h3>抽取 ${badge(game, 'extract')}</h3>
    <div class="row wrap"><span class="label">抽取口</span><b>${fmt(st.thr)} / ${fmt(fluxCap(st))} Q/秒</b>
      ${btn('建到 10 分钟抽完', 'flux', { seconds: 600 })}${btn('5 分钟', 'flux', { seconds: 300 })}${btn('2 分钟', 'flux', { seconds: 120 })}
      <span class="note">每 1 Q/秒 ${R.pt} 势，并占用 ${fmt(quote.fluxMatterPerUnit(st))} Q 质量</span></div>
    <div class="row wrap"><span class="label">状态</span><b>${st.extracting ? '抽取中' : '未开始'}</b>
      ${st.extracting ? btn('停止抽取', 'extract', { on: 0 }) : btn('开始抽取', 'extract', { on: 1 }, { primary: true })}
      <span class="label">样本胚比例</span>${shares}</div>
    <hr>
    <p>现在结算可得 <b>${points}</b> 点。结算后宇宙终结：还没抽的 ${fmt(toQ(R, st.free))} Q 弥散质量${leftStruct > 0 ? `和 ${fmt(leftStruct)} Q 结构` : ''}作废。</p>
    <p class="note">样本胚 ${fmt(blankQ)} Q。结算时只能带出一种样本，只能选这个宇宙到过的时代。</p>
    <div class="row wrap">${btn('结算（不带样本）', 'settle', {}, { danger: true })}${samples}</div>
    <div class="row wrap">${btn(st.emergency ? '关闭兜底抽取' : '开启兜底抽取', 'emergency', {}, { active: st.emergency })}
      <span class="note">兜底抽取不用建抽取口，12 小时抽完，提取率只有 5%</span></div>
  </section>`;
}

function renderTools(game) {
  const speeds = SPEEDS.map((v) => btn(`${v}×`, 'speed', { value: v }, { active: !game.paused && game.speed === v })).join('');
  return `
  <section class="card tools">
    <div class="row wrap"><span class="label">试玩工具</span>
      ${btn(game.paused ? '继续' : '暂停', 'pause', {}, { active: game.paused })}${speeds}
      ${btn(game.showAll ? '按步骤显示' : '显示全部面板', 'showAll', {}, { active: game.showAll })}
      <span class="note">快进和“显示全部面板”只是原型的试玩工具，不是游戏规则。</span></div>
  </section>`;
}

function renderReceipt(game) {
  const st = game.st;
  const R = st.R;
  const r = st.receipt;
  const q0 = toQ(R, r.matter.Q0);
  const share = (v) => pct(toQ(R, v) / q0);
  return `
  <section class="card">
    <h2>宇宙终结 · 本轮结算</h2>
    <div class="row wrap">
      <div class="stat"><b>${r.points}</b><span>资源点</span></div>
      <div class="stat"><b>${pct(r.recovery)}</b><span>回收率</span></div>
      <div class="stat"><b>${fmtTime(r.seconds)}</b><span>轮长</span></div>
      <div class="stat"><b>${r.sampleName ? `${r.samples} 份${r.sampleName}` : '无'}</b><span>带出的样本</span></div>
      <div class="stat"><b>${ERA_NAME[r.era]} · ${r.maturity.toFixed(2)} 级</b><span>宇宙等级</span></div>
    </div>
    <p>规律：${r.laws.length > 0 ? r.laws.map((id) => lawById(R, id).name).join('、') : '无'} · 击发 ${r.counts.strikes} 次，其中超限 ${r.counts.overStrikes} 次</p>
    <p>质量去向：已抽取 ${share(r.matter.settled)} · 样本胚 ${share(r.matter.blank)} · 散失 ${share(r.matter.loss)} · 结算时作废 ${share(r.matter.discardedFree + r.matter.discardedStruct + r.matter.discardedEmbedded)}</p>
    <p class="note">规则版本 ${r.rulesVersion}</p>
    <div class="row">${btn('再造一个宇宙', 'restart', {}, { primary: true })}</div>
  </section>`;
}

export function render(game) {
  const head = '<h1>奇点里的宇宙 <small>玩法原型 · 原初与凝聚时代</small></h1>';
  focusFn = () => false;
  if (!game.st) return head + renderSetup(game);
  const st = game.st;
  if (st.settled) return head + renderReceipt(game);

  focusFn = game.showAll ? () => false : currentGoal(game).focus;
  const left = [renderStrike(game)];
  if (reached(game, 'devices')) left.push(renderDevices(game));
  if (reached(game, 'mature')) left.push(renderMature(game));
  const right = [];
  if (reached(game, 'collapse')) right.push(renderCollapse(game));
  if (reached(game, 'structure') && (era(st) >= 1 || game.showAll)) right.push(renderStructures(game));
  if (reached(game, 'extract') && (era(st) >= 1 || game.showAll)) right.push(renderExtract(game));
  const html = `${head}${renderGoal(game)}${renderUniverse(game)}
  <div class="grid${right.length === 0 ? ' single' : ''}">
    <div>${left.join('')}</div>
    ${right.length > 0 ? `<div>${right.join('')}</div>` : ''}
  </div>
  ${renderTools(game)}`;
  focusFn = () => false;
  return html;
}

// ———————————————————————— DOM 绑定 ————————————————————————

export function boot(root, search = '') {
  const game = newGame();
  // 调试用：reactor.html?q=1e6&eta=0.8&all=1&speed=20 直接开一轮
  const params = new URLSearchParams(search);
  if (params.has('q')) {
    game.setup.Q0 = Number(params.get('q'));
    game.setup.eta = Number(params.get('eta') ?? 0.8);
    startGame(game);
    game.showAll = params.get('all') === '1';
    game.speed = Number(params.get('speed') ?? 1);
  }
  const paint = () => {
    root.innerHTML = render(game);
  };
  // 用 pointerdown 而不是 click：界面定时重绘，按下和抬起之间按钮可能已被替换。
  root.addEventListener('pointerdown', (ev) => {
    const el = ev.target.closest('[data-cmd]');
    if (!el) return;
    ev.preventDefault();
    dispatch(game, el.dataset.cmd, el.dataset);
    paint();
  });
  let frame = 0;
  setInterval(() => {
    tick(game, game.speed);
    frame += 1;
    if (frame % 2 === 0 && game.st && !game.st.settled) paint();
  }, RULES.dt * 1000);
  paint();
  return game;
}

if (typeof document !== 'undefined') {
  const root = document.getElementById('app');
  if (root) boot(root, typeof location !== 'undefined' ? location.search : '');
}
