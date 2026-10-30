const { test } = require('node:test');
const assert = require('node:assert/strict');
const M = require('../dist/js/reactor-model.js');

function run(efficiency = 80, mass = 20000) {
    const s = M.state(); s.draft = { mass, efficiency };
    assert.equal(M.start(s).ok, true); return s;
}
function balanced(s) {
    assert.ok(Math.abs(M.ledger(s).difference) < Math.max(1e-5, s.locked.mass * 1e-9), JSON.stringify(M.ledger(s)));
    for (const k of ['raw', 'feed', 'productMass', 'lostResearch', 'lostUpgrades', 'lostRebuild', 'installed']) assert.ok(s[k] >= 0, k);
}
function research(s, path) {
    s.allocation = 100;
    for (let i = 0; i < 2; i++) { M.buyFacility(s, 'processor'); M.buyFacility(s, 'lab'); }
    const queue = M.TECH.filter(t => t.path === path);
    for (let step = 0; step < 20000 && queue.some(t => !M.owns(s, t.id)); step++) {
        for (const t of queue) M.buyTech(s, t.id);
        M.advance(s, 0.25, false);
    }
    assert.ok(queue.every(t => M.owns(s, t.id)), `failed path ${path}`);
    balanced(s);
}

test('10% is allowed; lower, non-finite and out-of-range inputs never start or silently clamp', () => {
    for (const eta of [10, 10.01, 80, 100]) assert.equal(run(eta).locked.k, 100 / eta);
    for (const eta of [9.999, 0, -1, 100.01, NaN, Infinity, '']) {
        const s = M.state(); s.draft.efficiency = eta;
        const before = structuredClone(s);
        assert.equal(M.start(s).ok, false);
        assert.deepEqual(s, before);
    }
    for (const mass of [99, 0, -1, 1e13, Infinity, NaN]) {
        const s = M.state(); s.draft.mass = mass;
        assert.equal(M.start(s).ok, false);
    }
});

test('outer input is locked across running, paused and inner reset', () => {
    const s = run(); s.draft.mass = 1e9; s.draft.efficiency = 100;
    assert.equal(M.start(s).ok, false);
    s.status = 'paused'; assert.equal(M.start(s).ok, false);
    const locked = structuredClone(s.locked);
    assert.equal(M.resetSubround(s).ok, true);
    assert.deepEqual(s.locked, locked);
    assert.equal(s.raw, 20000 - 180);
    assert.equal(s.status, 'paused'); balanced(s);
});

test('K applies only to upgrade matter, not construction, processing or output', () => {
    const a = run(100), b = run(10);
    M.buyFacility(a, 'processor'); M.buyFacility(b, 'processor');
    a.allocation = b.allocation = 50;
    M.advance(a, 30, false); M.advance(b, 30, false);
    assert.equal(a.productPoints, b.productPoints);
    assert.equal(a.research, b.research);
    assert.equal(a.raw, b.raw);
    assert.equal(M.buyTech(a, 'flow1').ok, true);
    assert.equal(M.buyTech(b, 'flow1').ok, true);
    assert.equal(a.lostUpgrades, 120); assert.equal(b.lostUpgrades, 1200);
    const pa = a.productPoints, pb = b.productPoints;
    M.advance(a, 10, false); M.advance(b, 10, false);
    assert.equal(a.productPoints - pa, b.productPoints - pb);
    balanced(a); balanced(b);
});

test('technology points are cumulative log minus spent; spending does not regenerate them', () => {
    const s = run(); s.allocation = 100; M.advance(s, 30, false);
    const earned = M.techTotal(s);
    assert.equal(M.buyTech(s, 'flow1').ok, true);
    const after = M.techAvailable(s);
    assert.equal(after, earned - 12);
    for (let i = 0; i < 10; i++) assert.equal(M.techAvailable(s), after);
    assert.equal(M.buyTech(s, 'precision1').ok, true, 'no hard exclusion');
    assert.equal(M.techAvailable(s), earned - 24);
    balanced(s);
});

test('inner reset keeps real output, remaining stock and key research, but clears ordinary research and points', () => {
    const s = run(); research(s, 'flow');
    s.allocation = 0; M.advance(s, 30, false);
    const before = { material: M.material(s), product: s.productPoints, productMass: s.productMass, installed: s.installed, lock: structuredClone(s.locked) };
    assert.equal(M.resetSubround(s).ok, true);
    assert.equal(s.keys.flow, true); assert.deepEqual(s.owned, []);
    assert.equal(M.techAvailable(s), 0); assert.equal(s.research, 0);
    assert.equal(s.productPoints, before.product); assert.equal(s.productMass, before.productMass);
    assert.ok(Math.abs(M.material(s) - (before.material - 90)) < 1e-6);
    assert.equal(s.lostRebuild, before.installed); assert.deepEqual(s.locked, before.lock);
    assert.equal(s.installed, 90); balanced(s);
});

test('shallow resets cannot carry or duplicate technology points or give research keys', () => {
    const s = run();
    for (let i = 0; i < 8; i++) {
        s.allocation = 100; M.advance(s, 2, false); M.buyTech(s, 'flow1');
        const points = s.productPoints;
        M.resetSubround(s);
        assert.equal(M.techTotal(s), 0); assert.equal(M.techAvailable(s), 0);
        assert.deepEqual(s.keys, { flow: false, precision: false, cascade: false });
        assert.equal(s.productPoints, points); balanced(s);
    }
});

test('full two-reset route unlocks automation only after outer settlement; outer settlement wipes technology and is idempotent', () => {
    const s = run(); research(s, 'flow'); M.resetSubround(s);
    research(s, 'precision'); M.resetSubround(s); research(s, 'cascade');
    M.selectProcess(s, 'cascade'); s.allocation = 0;
    M.advance(s, 200, false);
    assert.equal(s.fullComplete, true); assert.equal(s.automationUnlocked, false);
    const total = s.productPoints, runId = s.runId;
    assert.equal(M.settle(s).reward, Math.floor(total + 1e-8));
    assert.equal(s.automationUnlocked, true); assert.deepEqual(s.keys, { flow: false, precision: false, cascade: false });
    assert.equal(s.raw, 0); assert.equal(s.feed, 0); assert.equal(s.research, 0);
    const settled = s.totalPoints; assert.equal(M.settle(s).ok, false); assert.equal(s.totalPoints, settled);
    M.start(s); assert.equal(s.runId, runId + 1); assert.equal(s.automationUnlocked, true);
    assert.deepEqual(s.keys, { flow: false, precision: false, cascade: false }); balanced(s);
});

test('pause and depleted material do not create progress; partial settlement remains possible', () => {
    const s = run(80, 100);
    M.advance(s, 100, false); const depleted = structuredClone(s);
    M.advance(s, 100, false);
    assert.equal(s.productPoints, depleted.productPoints); assert.equal(s.research, depleted.research);
    s.status = 'paused'; const paused = structuredClone(s);
    M.advance(s, 100, false); assert.deepEqual(s, paused);
    assert.equal(M.resetSubround(s).ok, false);
    assert.equal(M.settle(s).ok, true);
});

test('save round trip, split-time simulation and maximum input preserve the material ledger', () => {
    const a = run(); a.allocation = 35;
    const b = structuredClone(a);
    M.advance(a, 120, false); for (let i = 0; i < 120; i++) M.advance(b, 1, false);
    assert.ok(Math.abs(a.productPoints - b.productPoints) < 1e-5);
    const c = JSON.parse(JSON.stringify(a)); assert.equal(M.validateSave(c), true);
    M.advance(c, 15, false); M.advance(a, 15, false);
    assert.equal(a.productPoints, c.productPoints); balanced(c);
    const bad = structuredClone(c); bad.raw += 10; assert.equal(M.validateSave(bad), false);
    const big = run(10, 1e12); M.advance(big, 3600, false); balanced(big);
    assert.equal(M.validateSave(big), true);
});

test('incomplete or malformed imported saves are rejected before mounting the interface', () => {
    const valid = run();
    for (const key of ['trace', 'journal', 'autoPlan', 'subround', 'nextTrace', 'fullComplete']) {
        const bad = structuredClone(valid); delete bad[key];
        assert.equal(M.validateSave(bad), false, key);
    }
    for (const key of ['trace', 'journal', 'history']) {
        const bad = structuredClone(valid); bad[key] = [null];
        assert.equal(M.validateSave(bad), false, key);
    }
});

test('unlocked automation completes a three-subround run and stops after one settlement by default', () => {
    const s = run(); s.automationUnlocked = true; s.autoEnabled = true;
    M.advance(s, 3600);
    assert.equal(s.status, 'idle'); assert.equal(s.completedRuns, 1);
    assert.equal(s.history[0].complete, true); assert.equal(s.history[0].subrounds, 3);
    assert.ok(s.history[0].reward > 1000); assert.equal(M.validateSave(s), true);
    M.advance(s, 60); assert.equal(s.completedRuns, 1);
});
