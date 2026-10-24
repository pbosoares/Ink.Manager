'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

const pidFile = process.env.E2E_DIAGNOSTICS_PIDFILE;
const runId = process.env.E2E_DIAGNOSTICS_RUN_ID;
if (!pidFile || !path.isAbsolute(pidFile) || !/^[A-Za-z0-9_-]+$/.test(runId || '')) {
  process.stderr.write('Diagnostic server PID file is unavailable\n');
  process.exitCode = 2;
} else {
  let child;
  let pidRecorded = false;
  let stopping = false;

  const removeOwnedPidFile = () => {
    if (!pidRecorded || !child?.pid) return;
    try {
      if (fs.readFileSync(pidFile, 'utf8').trim() === String(child.pid)) fs.unlinkSync(pidFile);
    } catch {
      // The run directory can already be gone during teardown.
    }
  };

  const stopChild = signal => {
    stopping = true;
    if (child && child.exitCode === null && child.signalCode === null) {
      try { child.kill(signal); } catch {}
    }
  };

  try {
    fs.mkdirSync(path.dirname(pidFile), { recursive: true });
    child = spawn('java', [
      '-De2e.diagnostics.runId=' + runId,
      '-jar',
      'target/api-0.0.1-SNAPSHOT.jar',
      '--server.port=8087',
      '--server.address=127.0.0.1'
    ], { stdio: 'inherit', windowsHide: true });
    if (!child.pid) throw new Error('child_pid_unavailable');
    fs.writeFileSync(pidFile, String(child.pid) + '\n', { encoding: 'utf8', flag: 'wx' });
    pidRecorded = true;
  } catch {
    if (child && child.exitCode === null && child.signalCode === null) {
      try { child.kill(); } catch {}
    }
    process.stderr.write('Diagnostic application launch failed\n');
    process.exitCode = 1;
  }

  if (child && pidRecorded) {
    process.on('SIGINT', () => stopChild('SIGINT'));
    process.on('SIGTERM', () => stopChild('SIGTERM'));
    process.on('exit', removeOwnedPidFile);
    child.once('error', () => {
      removeOwnedPidFile();
      process.stderr.write('Diagnostic application process failed\n');
      process.exitCode = 1;
    });
    child.once('exit', code => {
      removeOwnedPidFile();
      if (!stopping && code !== 0) process.exitCode = Number.isInteger(code) ? code : 1;
      else process.exitCode = Number.isInteger(code) ? code : 0;
    });
  }
}
