// 界面逻辑测试：不需要浏览器。用和按钮相同的命令，按引导的顺序手动打完一个教学轮，
// 每一步都渲染一次，确认不抛异常、阶段按预期逐步放出、状态按预期推进。

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { newGame, dispatch, tick, render, currentGoal, STAGES, fmt, fmtTime } from '../web/app.js';
import { toQ, maturity, era, autoAvailable, collapseNeed, assertLedger, steadyExcited } from '../src/core.js';
import { RULES } from '../src/params.js';

const stageId = (game) => STAGES[game.stage].id;

function wait(game, seconds) {
  tick(game, Math.round(seconds / RULES.dt));
  assert.equal(typeof render(game), 'string');
}

// 每秒执行一次 click，直到条件成立
function until(game, cond, click, limitSeconds = 1500) {
  for (let i = 0; i < limitSeconds; i++) {
    if (cond()) return;
    click();
    wait(game, 1);
  }
  assert.fail(`等了 ${limitSeconds} 秒条件仍未成立（阶段 ${stageId(game)}）`);
}

test('格式化', () => {
  assert.equal(fmt(1e6), '100.0万');
  assert.equal(fmt(2.5e8), '2.50亿');
  assert.equal(fmt(42), '42.0');
  assert.equal(fmtTime(75), '01:15');
  assert.equal(fmtTime(3723), '1:02:03');
});

test('创生界面可以选择投入和利用率', () => {
  const game = newGame();
  assert.match(render(game), /创生/);
  dispatch(game, 'setQ0', { value: '10000000' });
  dispatch(game, 'setEta', { value: '0.5' });
  dispatch(game, 'start');
  assert.equal(toQ(RULES, game.st.Q0), 1e7);
  assert.equal(game.st.K, 2);
  assert.match(render(game), /总质量 1000\.0万 Q/);
});

test('开局只有击发和一个目标，其余面板都还没出现', () => {
  const game = newGame();
  dispatch(game, 'start');
  let html = render(game);
  assert.equal(stageId(game), 'ignite');
  assert.match(html, /第 1 \/ 7 步 · 点火/);
  assert.match(html, /目标：击发 3 次/);
  for (const hidden of ['<h3>装置', '<h3>成熟', '<h3>坍缩', '<h3>结构', '<h3>抽取', '自动击发']) {
    assert.doesNotMatch(html, new RegExp(hidden), hidden);
  }
  // 还没蓄能时击发按钮不可用、不高亮；蓄一会儿之后高亮
  assert.doesNotMatch(html, /class="btn[^"]* hl"/);
  wait(game, 2);
  html = render(game);
  assert.match(html, /class="btn primary big hl" data-cmd="strike"/);
});

test('没成功的操作会说明原因', () => {
  const game = newGame();
  dispatch(game, 'start');
  dispatch(game, 'strike');
  assert.equal(game.messageOk, false);
  assert.match(game.message, /没有成功：蓄能不足/);
  dispatch(game, 'device', { kind: '0' });
  assert.match(game.message, /没有成功：势不足/);
  dispatch(game, 'collapse', { law: 'resonate' });
  assert.match(game.message, /累计的势不足/);
  dispatch(game, 'form', { kind: 'halo', frac: '0.1' });
  assert.match(game.message, /凝聚时代/);
  dispatch(game, 'autoBuy');
  assert.match(game.message, /第一次坍缩/);
  dispatch(game, 'settle', { sample: '0' });
  assert.match(game.message, /没有到过这个时代/);
  assert.equal(game.st.settled, false);
});

test('按引导手动打完教学轮：面板逐步出现', () => {
  const game = newGame();
  dispatch(game, 'start');
  const st = game.st;
  const seen = [];
  const note = () => {
    if (seen[seen.length - 1] !== stageId(game)) seen.push(stageId(game));
  };
  // 手动玩家：能击就击
  const strike = () => {
    if (!st.auto.on && st.charge >= 1 - 1e-9) dispatch(game, 'strike');
  };

  // 1 点火
  until(game, () => stageId(game) !== 'ignite', strike);
  note();
  assert.match(game.message, /击发！/);
  assert.ok(st.t * RULES.dt < 20);
  assert.match(render(game), /<h3>装置 <span class="new">新<\/span>/);

  // 2 装置：轮流造三种
  let k = 0;
  until(game, () => stageId(game) !== 'devices', () => {
    strike();
    dispatch(game, 'device', { kind: String([1, 0, 2][k % 3]) });
    if (game.messageOk) k += 1;
  });
  note();

  // 3 自动击发
  until(game, () => stageId(game) !== 'auto', () => {
    strike();
    dispatch(game, 'device', { kind: '0' });
  });
  note();
  assert.equal(autoAvailable(st), true);
  assert.match(game.message, /解锁“自动击发”|电容 \+1/);
  dispatch(game, 'auto', { on: '1', level: '1' });
  assert.equal(st.auto.on, true);
  assert.match(render(game), /<h3>成熟 <span class="new">新<\/span>/);

  // 4 成熟到 1 级：先把装置造到能覆盖全部弥散质量，再投入
  until(game, () => steadyExcited(st) >= toQ(RULES, st.free), () => {
    for (const kind of ['1', '0', '2']) dispatch(game, 'device', { kind });
  });
  assert.match(render(game), /已经覆盖全部可激发质量/);
  dispatch(game, 'mature');
  assert.deepEqual(game.sink, { level: 1 });
  until(game, () => stageId(game) !== 'mature', () => {});
  note();
  assert.equal(game.sink, null);
  assert.match(render(game), /<h3>坍缩 <span class="new">新<\/span>/);
  assert.doesNotMatch(render(game), /<h3>结构/);

  // 5 坍缩：等累计够了，期间继续成熟
  dispatch(game, 'mature');
  until(game, () => st.phiEra >= collapseNeed(st), () => {
    if (!game.sink) dispatch(game, 'mature');
  });
  const x = maturity(st);
  dispatch(game, 'collapse', { law: 'resonate' });
  note();
  assert.equal(game.messageOk, true);
  assert.equal(era(st), 1);
  assert.deepEqual(st.devices, [0, 0, 0]);
  assert.equal(maturity(st), x);
  assert.equal(st.auto.on, false); // 电容没了，自动击发失效
  assert.equal(stageId(game), 'structure');
  assert.match(render(game), /<h3>结构 <span class="new">新<\/span>/);
  assert.doesNotMatch(render(game), /<h3>抽取/);

  // 6 凝聚：凝一成暗晕
  dispatch(game, 'form', { kind: 'halo', frac: '0.1' });
  note();
  assert.ok(st.struct.halo > 0n);
  assert.equal(stageId(game), 'extract');

  // 7 重建（可以代买）→ 成熟到 4 → 抽取口 → 抽取 → 溶解 → 结算
  dispatch(game, 'autoBuy');
  assert.equal(game.autoBuy, true);
  until(game, () => autoAvailable(st), strike);
  dispatch(game, 'auto', { on: '1', level: '1' });
  until(game, () => maturity(st) >= 4 - 1e-9, () => {
    if (!game.sink && steadyExcited(st) >= toQ(RULES, st.free)) dispatch(game, 'mature');
  });
  dispatch(game, 'stopSink');
  assert.match(currentGoal(game).text, /建抽取口/);
  until(game, () => /开始抽取/.test(currentGoal(game).text), () => dispatch(game, 'flux', { seconds: '120' }), 300);
  dispatch(game, 'blank', { share: '0.2' });
  dispatch(game, 'extract', { on: '1' });
  assert.equal(st.extracting, true);
  assert.match(currentGoal(game).text, /抽取中/);
  until(game, () => st.free === 0n, () => {}, 2000);
  assert.match(currentGoal(game).text, /把结构溶解掉/);
  dispatch(game, 'dissolve', { kind: 'halo' });
  assert.equal(st.struct.halo, 0n);
  until(game, () => st.free === 0n, () => {}, 2000);
  assert.match(currentGoal(game).text, /宇宙已经抽干/);
  assertLedger(st);

  dispatch(game, 'settle', { sample: '0' });
  assert.equal(st.settled, true);
  assert.deepEqual(seen, ['devices', 'auto', 'mature', 'collapse', 'structure', 'extract']);
  const r = st.receipt;
  assert.ok(r.samples > 100n);
  assert.ok(r.recovery > 0.45 && r.recovery < 0.65, `回收率 ${r.recovery}`);
  assert.ok(r.seconds > 8 * 60 && r.seconds < 25 * 60, `用时 ${r.seconds} 秒`);
  assert.match(render(game), /宇宙终结/);

  // 打完一轮后，下一轮开局就显示全部面板
  dispatch(game, 'restart');
  assert.match(render(game), /你已经打完过一轮/);
  dispatch(game, 'start');
  assert.equal(stageId(game), 'extract');
  assert.match(render(game), /<h3>坍缩/);
});

test('超限击发会提示过热，界面给出警告', () => {
  const game = newGame();
  dispatch(game, 'start');
  dispatch(game, 'showAll');
  const st = game.st;
  st.devices[1] = RULES.milestones.over.count;
  wait(game, RULES.chargeTime * 3);
  dispatch(game, 'strike');
  assert.match(game.message, /宇宙过热/);
  assert.match(render(game), /class="warn"/);
  assert.match(render(game), /universe hot/);
});

test('可以跳过凝聚', () => {
  const game = newGame();
  dispatch(game, 'start');
  const st = game.st;
  st.counts.strikes = 3;
  st.devices = [5, 1, 0];
  st.matureR = RULES.r0 * 1e6;
  st.phiEra = collapseNeed(st);
  tick(game, 1);
  assert.equal(stageId(game), 'collapse');
  dispatch(game, 'collapse', { law: 'crystal' });
  assert.equal(stageId(game), 'structure');
  dispatch(game, 'skipStructure');
  assert.equal(stageId(game), 'extract');
  assert.match(render(game), /<h3>抽取/);
});

test('显示全部面板、暂停和快进', () => {
  const game = newGame();
  dispatch(game, 'start');
  assert.doesNotMatch(render(game), /<h3>坍缩/);
  dispatch(game, 'showAll');
  const html = render(game);
  for (const shown of ['<h3>装置', '<h3>成熟', '<h3>坍缩', '<h3>结构', '<h3>抽取']) assert.match(html, new RegExp(shown), shown);
  assert.match(html, /结构质量的 50% 可被激发/);
  assert.match(html, /过热每散失 1 Q 产生 5000/);
  assert.equal(STAGES.length, 7);
  const t = game.st.t;
  dispatch(game, 'pause');
  tick(game, 100);
  assert.equal(game.st.t, t);
  dispatch(game, 'speed', { value: '20' });
  tick(game, 100);
  assert.equal(game.st.t, t + 100);
});
