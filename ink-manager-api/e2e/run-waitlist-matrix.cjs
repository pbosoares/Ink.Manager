'use strict';

const { spawnSync } = require('node:child_process');
const path = require('node:path');

const project = path.resolve(__dirname, '..');
const playwright = path.join(project, 'node_modules', 'playwright', 'cli.js');

// Each viewport gets a fresh app process. This preserves the real in-memory
// public rate limit while preventing one synthetic viewport from consuming the
// next viewport's allowance during the same E2E run.
for (const viewport of ['1366px', '390px']) {
  process.stdout.write(`\n=== Lista/oferta ${viewport}; app isolado ===\n`);
  const result = spawnSync(process.execPath, [
    playwright,
    'test',
    '--config=playwright.waitlist.config.cjs',
    `--grep=${viewport}`
  ], {
    cwd: project,
    env: process.env,
    stdio: 'inherit',
    windowsHide: true
  });

  if (result.error) {
    process.stderr.write(`Falha ao iniciar Playwright: ${result.error.message}\n`);
    process.exitCode = 1;
    break;
  }
  if (result.status !== 0) {
    process.exitCode = result.status || 1;
    break;
  }
}
