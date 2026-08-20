#!/usr/bin/env node
/**
 * 一条命令跑完全部浏览器端测试。
 *
 * 为什么需要它：这些测试用 Node 内建 node:test + vm，不属于任何 Gradle 测试任务，
 * 以前只在 README 里列了 14 个文件中的 1 个，实际全量跑法散落在 collab-log 里。
 * 重写前端时必须有一条命令能立刻告出「刚才打破了什么」。
 *
 * 三段：语法检查 → node:test 套件 → 两个自研 DOM 脚本。
 * 任一段失败即整体非零退出。
 */
import { readdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const WEB = join(ROOT, 'app/src/main/assets/web');
const SUITE = join(ROOT, 'app/src/test/web');

function run(label, cmd, args) {
  process.stdout.write(`\n── ${label} ──\n`);
  const r = spawnSync(cmd, args, { cwd: ROOT, stdio: 'inherit', shell: false });
  if (r.error) {
    console.error(`${label} 无法启动: ${r.error.message}`);
    return false;
  }
  return r.status === 0;
}

let ok = true;

// 1) 语法检查：任何 JS 资产语法错误应该立刻炸，而不是等浏览器里白屏
for (const f of readdirSync(WEB).filter((n) => n.endsWith('.js')).sort()) {
  ok = run(`syntax ${f}`, process.execPath, ['--check', join(WEB, f)]) && ok;
}

// 2) node:test 套件。显式枚举并排序传参 —— Windows + Node 22 下
//    `node --test <目录>` 会报 MODULE_NOT_FOUND（见 traps-and-fixes.md）
const tests = readdirSync(SUITE).filter((n) => n.endsWith('.test.js')).sort()
  .map((n) => join(SUITE, n));
if (tests.length === 0) {
  console.error('app/src/test/web 下没有找到 *.test.js —— 目录结构变了？');
  ok = false;
} else {
  ok = run(`node:test (${tests.length} files)`, process.execPath, ['--test', ...tests]) && ok;
}

// 3) 两个自研迷你 DOM 脚本
for (const s of ['scripts/test-web-avatar-reflow.js', 'scripts/test-web-login-theme.js']) {
  ok = run(s, process.execPath, [join(ROOT, s)]) && ok;
}

process.stdout.write(ok ? '\n✅ web tests passed\n' : '\n❌ web tests FAILED\n');
process.exit(ok ? 0 : 1);
