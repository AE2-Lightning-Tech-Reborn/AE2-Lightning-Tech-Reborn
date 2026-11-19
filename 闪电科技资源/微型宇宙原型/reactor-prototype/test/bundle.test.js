// 打包产物的冒烟测试：用一个极简的假 DOM 执行 web/reactor.html 里的脚本，
// 模拟点击和定时器，确认单文件版本能启动、能响应按钮、能推进。
// 这不能代替在真实浏览器里看一眼，只保证脚本本身不报错。

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'web', 'reactor.html'), 'utf8');
const code = html.match(/<script>([\s\S]*)<\/script>/)[1];

function load(search = '') {
  const listeners = {};
  const root = {
    innerHTML: '',
    addEventListener(type, fn) {
      listeners[type] = fn;
    },
  };
  const timers = [];
  const env = {
    document: { getElementById: (id) => (id === 'app' ? root : null) },
    location: { search },
    setInterval: (fn, ms) => {
      timers.push({ fn, ms });
      return timers.length;
    },
  };
  new Function('document', 'location', 'setInterval', code)(env.document, env.location, env.setInterval);
  const click = (dataset) => {
    let prevented = false;
    listeners.pointerdown({
      target: { closest: () => ({ dataset }) },
      preventDefault: () => {
        prevented = true;
      },
    });
    return prevented;
  };
  const advance = (n) => {
    for (let i = 0; i < n; i++) for (const t of timers) t.fn();
  };
  return { root, timers, click, advance };
}

test('单文件版本不残留模块语法', () => {
  assert.equal(/^\s*(import|export)\s/m.test(code), false);
  assert.match(html, /<div id="app"><\/div>/);
});

test('单文件版本能启动并响应按钮', () => {
  const page = load();
  assert.match(page.root.innerHTML, /创生/);
  assert.equal(page.timers.length, 1);
  assert.equal(page.timers[0].ms, 50);

  assert.equal(page.click({ cmd: 'setQ0', value: '1000000' }), true);
  page.click({ cmd: 'start' });
  assert.match(page.root.innerHTML, /目标：击发 3 次/);

  page.click({ cmd: 'strike' });
  assert.match(page.root.innerHTML, /没有成功：蓄能不足/);

  // 快进 5 秒，蓄满一格后击发
  page.click({ cmd: 'speed', value: '20' });
  page.advance(5);
  page.click({ cmd: 'strike' });
  assert.match(page.root.innerHTML, /击发！/);
  assert.match(page.root.innerHTML, /激发态/);

  page.click({ cmd: 'showAll' });
  assert.match(page.root.innerHTML, /<h3>坍缩/);
  page.click({ cmd: 'speed', value: '100' });
  page.advance(80); // 80 个定时器周期 × 100 倍 = 400 秒
  assert.match(page.root.innerHTML, /已运行 06:4\d/);

  page.click({ cmd: 'settle' });
  assert.match(page.root.innerHTML, /宇宙终结/);
  page.click({ cmd: 'restart' });
  assert.match(page.root.innerHTML, /创生/);
});

test('带参数的地址可以直接开一轮', () => {
  const page = load('?q=1e7&eta=0.5&all=1&speed=20');
  assert.match(page.root.innerHTML, /总质量 1000\.0万 Q/);
  assert.match(page.root.innerHTML, /利用率 50%/);
  assert.match(page.root.innerHTML, /<h3>结构/);
  page.advance(40);
  assert.match(page.root.innerHTML, /已运行 00:40/);
});
