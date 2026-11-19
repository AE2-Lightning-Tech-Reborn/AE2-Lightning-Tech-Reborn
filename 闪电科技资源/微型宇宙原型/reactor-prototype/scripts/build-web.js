// 把规则核和界面打成一个可以直接双击打开的 HTML 文件（不需要本地服务器）。
//   node scripts/build-web.js   →   web/reactor.html

import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (p) => readFileSync(join(root, p), 'utf8');

function stripModuleSyntax(code) {
  return code
    .replace(/^import\s[\s\S]*?from\s+['"][^'"]+['"];?[ \t]*\r?\n/gm, '')
    .replace(/^export\s+\{[^}]*\};?[ \t]*\r?\n/gm, '')
    .replace(/^export\s+(?=(const|let|function|class|async)\b)/gm, '');
}

const script = ['src/params.js', 'src/core.js', 'src/advisor.js', 'web/app.js'].map((p) => `// —— ${p} ——\n${stripModuleSyntax(read(p))}`).join('\n');
if (/^\s*(import|export)\s/m.test(script)) throw new Error('打包后仍残留 import/export');

const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>资源反应堆 · 玩法原型</title>
<style>
${read('web/style.css')}
</style>
</head>
<body>
<div id="app"></div>
<script>
(function () {
'use strict';
${script}
})();
</script>
</body>
</html>
`;

writeFileSync(join(root, 'web/reactor.html'), html, 'utf8');
console.log(`已生成 web/reactor.html（${(html.length / 1024).toFixed(1)} KB）`);
