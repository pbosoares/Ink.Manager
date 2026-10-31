'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFile } = require('node:child_process');

const APP_URL = 'http://127.0.0.1:8087';
const SAMPLE_INTERVAL_MS = 1000;
const VALIDATION_PG_PORTS = new Set(['55446', '55448']);
const METRICS = Object.freeze({
  hikariActive: 'hikaricp.connections.active',
  hikariIdle: 'hikaricp.connections.idle',
  hikariPending: 'hikaricp.connections.pending',
  hikariMax: 'hikaricp.connections.max',
  tomcatBusy: 'tomcat.threads.busy',
  tomcatCurrent: 'tomcat.threads.current',
  tomcatMax: 'tomcat.threads.config.max'
});

const ACTIVITY_SQL = [
  'SELECT pid::text, COALESCE(state, \'\'), COALESCE(wait_event_type, \'\'),',
  'COALESCE(wait_event, \'\'),',
  'COALESCE(floor(EXTRACT(EPOCH FROM (clock_timestamp() - query_start)))::text, \'\'),',
  'COALESCE(floor(EXTRACT(EPOCH FROM (clock_timestamp() - xact_start)))::text, \'\'),',
  'COALESCE(array_to_string(pg_blocking_pids(pid), \',\'), \'\')',
  'FROM pg_stat_activity',
  'WHERE datname = current_database() AND pid <> pg_backend_pid()',
  'ORDER BY pid'
].join(' ');

const PG_STATEMENTS_EXISTS_SQL =
  "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')::text";
const PG_STATEMENTS_BY_QUERY_SQL = [
  'SELECT queryid::text || \'|\' || calls::text || \'|\' ||',
  'ROUND(total_exec_time::numeric, 3)::text || \'|\' ||',
  'ROUND(mean_exec_time::numeric, 3)::text',
  'FROM pg_stat_statements',
  'WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())',
  'ORDER BY queryid'
].join(' ');

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function numberOrNull(value) {
  if (!/^-?\d+(?:\.\d+)?$/.test(String(value || ''))) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function pgTargetFromEnvironment() {
  const match = String(process.env.DB_URL || '').match(/^jdbc:postgresql:\/\/(.+)$/i);
  if (!match) return { ok: false, state: 'jdbc_url_missing' };

  let parsed;
  try { parsed = new URL('postgresql://' + match[1]); }
  catch { return { ok: false, state: 'jdbc_url_invalid' }; }

  const database = decodeURIComponent(parsed.pathname.replace(/^\/+/, ''));
  const expectedPort = process.env.E2E_VALIDATION_PG_PORT || '55446';
  if (!VALIDATION_PG_PORTS.has(expectedPort) || parsed.hostname.toLowerCase() !== '127.0.0.1' || parsed.port !== expectedPort ||
      parsed.username || parsed.password || parsed.searchParams.has('user') ||
      parsed.searchParams.has('password') || !/^marcae_validation_[a-z0-9_]+$/i.test(database)) {
    return { ok: false, state: 'target_rejected' };
  }
  if (!process.env.DB_USERNAME || /[\r\n]/.test(process.env.DB_USERNAME)) {
    return { ok: false, state: 'db_username_missing' };
  }

  return {
    ok: true,
    host: '127.0.0.1',
    port: expectedPort,
    database,
    username: process.env.DB_USERNAME,
    password: String(process.env.DB_PASSWORD || '')
  };
}

function commandEnvironment(pgTarget) {
  const env = {};
  for (const name of ['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP']) {
    if (process.env[name]) env[name] = process.env[name];
  }
  env.PGPASSWORD = pgTarget.password;
  env.PGAPPNAME = 'marcae-e2e-diagnostic-sampler';
  env.PGCONNECT_TIMEOUT = '1';
  env.PGPASSFILE = path.join(process.cwd(), 'target', 'diagnostics', 'unused-pgpass');
  env.PGSERVICEFILE = path.join(process.cwd(), 'target', 'diagnostics', 'unused-pgservice.conf');
  return env;
}

function psqlExecutable() {
  const installed = 'C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe';
  return fs.existsSync(installed) ? installed : 'psql.exe';
}

function jcmdExecutable() {
  const javaHome = process.env.JAVA_HOME;
  if (javaHome) {
    const installed = path.join(javaHome, 'bin', process.platform === 'win32' ? 'jcmd.exe' : 'jcmd');
    if (fs.existsSync(installed)) return installed;
  }
  const temurin25 = 'C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.3.9-hotspot\\bin\\jcmd.exe';
  return fs.existsSync(temurin25) ? temurin25 : 'jcmd.exe';
}

function safeErrorCode(error) {
  if (!error) return null;
  const code = String(error.code || error.name || 'command_failed');
  return /^[A-Za-z0-9_-]+$/.test(code) ? code : 'command_failed';
}

function runCommand(file, args, { timeout = 1500, maxBuffer = 1024 * 1024, env = process.env } = {}) {
  return new Promise(resolve => {
    execFile(file, args, {
      encoding: 'utf8',
      timeout,
      maxBuffer,
      windowsHide: true,
      env
    }, (error, stdout) => {
      resolve({
        ok: !error,
        errorCode: safeErrorCode(error),
        stdout: String(stdout || '')
      });
    });
  });
}

async function queryPostgres(pgTarget, sql) {
  const result = await runCommand(psqlExecutable(), [
    '-X', '-w',
    '-h', pgTarget.host,
    '-p', pgTarget.port,
    '-U', pgTarget.username,
    '-d', pgTarget.database,
    '-v', 'ON_ERROR_STOP=1',
    '-A', '-t', '-F', '|',
    '-c', sql
  ], { timeout: 1400, env: commandEnvironment(pgTarget) });

  if (!result.ok) return { ok: false, state: result.errorCode || 'query_failed' };
  return {
    ok: true,
    rows: result.stdout.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  };
}

async function samplePostgresActivity(pgTarget) {
  const result = await queryPostgres(pgTarget, ACTIVITY_SQL);
  if (!result.ok) return { state: 'unavailable', code: result.state, sessions: [] };

  const sessions = result.rows.map(line => {
    const [pid, state, waitType, waitEvent, queryAge, transactionAge, blockers] = line.split('|');
    return {
      pid: numberOrNull(pid),
      state: state || null,
      waitEventType: waitType || null,
      waitEvent: waitEvent || null,
      queryAgeSeconds: numberOrNull(queryAge),
      transactionAgeSeconds: numberOrNull(transactionAge),
      blockingPids: blockers ? blockers.split(',').map(numberOrNull).filter(Number.isFinite) : []
    };
  });
  return { state: 'sampled', sessions };
}

async function samplePgStatements(pgTarget) {
  const extension = await queryPostgres(pgTarget, PG_STATEMENTS_EXISTS_SQL);
  if (!extension.ok) return { state: 'extension_check_unavailable', code: extension.state };
  if (extension.rows[0] !== 't') return { state: 'not_installed' };

  const stats = await queryPostgres(pgTarget, PG_STATEMENTS_BY_QUERY_SQL);
  if (!stats.ok) return { state: 'stats_unavailable', code: stats.state };
  const byQueryId = [];
  for (const row of stats.rows) {
    const [queryId, calls, totalExecMs, meanExecMs] = row.split('|');
    if (!/^-?\d+$/.test(queryId || '') || !/^\d+$/.test(calls || '')) continue;
    const total = numberOrNull(totalExecMs);
    const mean = numberOrNull(meanExecMs);
    if (total === null || mean === null) continue;
    byQueryId.push({ queryId, calls, totalExecMs: total, meanExecMs: mean });
  }
  return {
    state: 'available',
    byQueryId
  };
}

function pgStatementsDelta(before, after) {
  if (before.state !== 'available' || after.state !== 'available') {
    return {
      state: before.state === after.state ? before.state : 'unavailable',
      beforeState: before.state,
      beforeCode: before.code || null,
      afterState: after.state,
      afterCode: after.code || null
    };
  }
  const beforeById = new Map(before.byQueryId.map(row => [row.queryId, row]));
  const afterById = new Map(after.byQueryId.map(row => [row.queryId, row]));
  const queryIds = [...new Set([...beforeById.keys(), ...afterById.keys()])].sort();
  const deltas = queryIds.map(queryId => {
    const oldValue = beforeById.get(queryId);
    const newValue = afterById.get(queryId);
    const callsBefore = BigInt(oldValue?.calls || '0');
    const callsAfter = BigInt(newValue?.calls || '0');
    const callsDelta = callsAfter - callsBefore;
    const totalBefore = oldValue?.totalExecMs || 0;
    const totalAfter = newValue?.totalExecMs || 0;
    const totalDelta = totalAfter - totalBefore;
    return {
      queryId,
      callsBefore: callsBefore.toString(),
      callsAfter: callsAfter.toString(),
      callsDelta: callsDelta.toString(),
      totalExecMsBefore: totalBefore,
      totalExecMsAfter: totalAfter,
      totalExecMsDelta: Number(totalDelta.toFixed(3)),
      meanExecMsBefore: oldValue?.meanExecMs ?? null,
      meanExecMsAfter: newValue?.meanExecMs ?? null,
      windowMeanExecMs: callsDelta > 0n ? Number((totalDelta / Number(callsDelta)).toFixed(3)) : null
    };
  });
  return {
    state: 'sampled',
    counterResetDetected: deltas.some(row => BigInt(row.callsDelta) < 0n || row.totalExecMsDelta < 0),
    byQueryId: deltas
  };
}

async function sampleMetric(request, headers, metric) {
  try {
    const response = await request.get(APP_URL + '/actuator/metrics/' + encodeURIComponent(metric), {
      headers,
      timeout: 1200
    });
    if (!response.ok()) return { httpStatus: response.status(), value: null };
    const payload = await response.json();
    const measurement = Array.isArray(payload.measurements)
      ? payload.measurements.find(item => item.statistic === 'VALUE') || payload.measurements[0]
      : null;
    const value = measurement ? Number(measurement.value) : NaN;
    return {
      httpStatus: response.status(),
      value: Number.isFinite(value) ? value : null
    };
  } catch {
    return { httpStatus: null, value: null, state: 'request_failed' };
  }
}

async function sampleMetrics(request, headers) {
  const entries = await Promise.all(Object.entries(METRICS).map(async ([key, metric]) => [
    key,
    await sampleMetric(request, headers, metric)
  ]));
  return Object.fromEntries(entries);
}

async function appendJsonLine(filePath, record) {
  try {
    await fs.promises.appendFile(filePath, JSON.stringify(record) + '\n', 'utf8');
    return true;
  } catch {
    return false;
  }
}

const ACCESS_LOG_PATHS = new Set([
  '/auth/login', '/clientes', '/marcacoes', '/profissionais', '/auth/me'
]);

function sanitizedAccessLogPath(pathValue) {
  if (ACCESS_LOG_PATHS.has(pathValue)) return pathValue;
  if (/^\/profissionais\/\d+\/dias-disponiveis$/.test(pathValue)) {
    return '/profissionais/{id}/dias-disponiveis';
  }
  return null;
}

async function accessLogPosition(filePath) {
  if (!filePath) return { state: 'path_missing', size: 0 };
  const root = path.resolve(process.cwd(), 'target', 'diagnostics', 'access') + path.sep;
  const resolved = path.resolve(process.cwd(), filePath);
  if (!resolved.toLowerCase().startsWith(root.toLowerCase())) {
    return { state: 'path_rejected', size: 0 };
  }
  try {
    const stat = await fs.promises.stat(resolved);
    if (!stat.isFile()) return { state: 'not_a_file', size: 0 };
    return { state: 'ready', size: stat.size, resolved };
  } catch (error) {
    return { state: error?.code === 'ENOENT' ? 'not_created' : 'unavailable', size: 0, resolved };
  }
}

async function readSanitizedAccessLogDelta(snapshot) {
  if (snapshot.state !== 'ready') return { state: snapshot.state, entries: [] };
  try {
    const contents = await fs.promises.readFile(snapshot.resolved);
    if (contents.length < snapshot.size) return { state: 'truncated', entries: [] };
    const text = contents.subarray(snapshot.size).toString('utf8');
    const entries = [];
    for (const line of text.split(/\r?\n/)) {
      const match = line.match(/^\[([^\]]+)\]\s+(GET|POST)\s+(\/[A-Za-z0-9/_-]*)\s+(\d{3})\s+(\d+)$/);
      if (!match) continue;
      const safePath = sanitizedAccessLogPath(match[3]);
      if (!safePath) continue;
      entries.push({
        timestamp: match[1],
        method: match[2],
        path: safePath,
        status: Number(match[4]),
        durationMs: Number((Number(match[5]) / 1000).toFixed(3))
      });
    }
    return { state: 'sampled', entries };
  } catch (error) {
    return { state: error?.code === 'ENOENT' ? 'unavailable' : 'read_failed', entries: [] };
  }
}

async function findApplicationPid(pidFile, runId) {
  if (!pidFile || !path.isAbsolute(pidFile)) return { state: 'pidfile_unavailable' };
  if (!/^[A-Za-z0-9_-]+$/.test(runId || '')) return { state: 'run_identity_unavailable' };
  const diagnosticsRoot = path.resolve(process.cwd(), 'target', 'diagnostics', 'runs') + path.sep;
  const normalizedFile = path.resolve(pidFile);
  if (!normalizedFile.toLowerCase().startsWith(diagnosticsRoot.toLowerCase())) {
    return { state: 'pidfile_outside_diagnostics_root' };
  }
  let rawPid;
  try { rawPid = (await fs.promises.readFile(normalizedFile, 'utf8')).trim(); }
  catch { return { state: 'pidfile_unavailable' }; }
  if (!/^\d+$/.test(rawPid)) return { state: 'pidfile_invalid' };
  const pid = Number(rawPid);
  if (!Number.isSafeInteger(pid) || pid < 1) return { state: 'pidfile_invalid' };

  const identity = await runCommand(jcmdExecutable(), [String(pid), 'VM.command_line'], {
    timeout: 3500,
    maxBuffer: 512 * 1024
  });
  if (!identity.ok) return { state: 'pid_identity_unavailable' };
  if (!/api-0\.0\.1-SNAPSHOT\.jar(?:\s|$)/i.test(identity.stdout)) {
    return { state: 'pid_identity_mismatch' };
  }
  const runIdentity = await runCommand(jcmdExecutable(), [String(pid), 'VM.system_properties'], {
    timeout: 3500,
    maxBuffer: 1024 * 1024
  });
  if (!runIdentity.ok) return { state: 'run_identity_unavailable' };
  const expectedRunId = 'e2e.diagnostics.runId=' + runId;
  if (!runIdentity.stdout.split(/\r?\n/).includes(expectedRunId)) {
    return { state: 'run_identity_mismatch' };
  }
  return { state: 'found', pid };
}

async function captureThreadDump(testInfo, offsetMs, pidFile, runId, operation = 'reload') {
  const filePath = testInfo.outputPath('authenticated-' + operation + '-thread-' + offsetMs + 'ms.txt');
  await fs.promises.mkdir(path.dirname(filePath), { recursive: true }).catch(() => {});
  const app = await findApplicationPid(pidFile, runId);
  if (app.state !== 'found') {
    await fs.promises.writeFile(filePath, 'capture_state=' + app.state + '\n', 'utf8').catch(() => {});
    return { offsetMs, state: app.state };
  }

  const result = await runCommand(jcmdExecutable(), [String(app.pid), 'Thread.print'], {
    timeout: 7000,
    maxBuffer: 8 * 1024 * 1024
  });
  const state = result.ok ? 'captured' : 'jcmd_failed';
  const header = [
    'capture_state=' + state,
    'offset_ms=' + offsetMs,
    'captured_at_utc=' + new Date().toISOString(),
    'app_pid=' + app.pid,
    ''
  ].join('\n');
  await fs.promises.writeFile(filePath, header + (result.ok ? sanitizeThreadDump(result.stdout) : 'thread_dump_unavailable\n'), 'utf8').catch(() => {});
  return { offsetMs, state };
}

function sanitizeThreadDump(raw) {
  let threadNumber = 0;
  const safeLines = [];
  for (const line of String(raw).split(/\r?\n/)) {
    if (/^\s*"[^"]*".*$/.test(line)) {
      threadNumber += 1;
      safeLines.push('"thread-' + threadNumber + '"');
      continue;
    }
    if (/^\s*java\.lang\.Thread\.State:/.test(line) ||
        /^\s*at [A-Za-z0-9_.$/<>-]+\([^\r\n]*\)\s*$/.test(line) ||
        /^\s*- (?:waiting|parking|locked|eliminated|waiting to lock|waiting on)\b[^\r\n]*$/.test(line) ||
        /^\s*\.\.\. \d+ more\s*$/.test(line)) {
      safeLines.push(line);
    }
  }
  return safeLines.join('\n') + '\n';
}

async function runAuthenticatedOperationDiagnostics({
  operation, action, browserRequests, page, request, headers, testInfo,
  pidFile, runId, accessLogPath, viewportWidth
}) {
  const pgTarget = pgTargetFromEnvironment();
  if (!pgTarget.ok) {
    throw new Error('Authenticated diagnostics require only an isolated PostgreSQL target on a reserved loopback validation port');
  }

  const outputPath = testInfo.outputPath('authenticated-' + operation + '-diagnostics.jsonl');
  await fs.promises.mkdir(path.dirname(outputPath), { recursive: true });
  const pgStatsBefore = await samplePgStatements(pgTarget);
  const accessLogStart = await accessLogPosition(accessLogPath);
  const startedAt = Date.now();
  let finished = false;
  let sampleCount = 0;
  let outputWritable = true;
  let samplingErrorCode = null;

  const threadTasks = [
    captureThreadDump(testInfo, 0, pidFile, runId, operation),
    delay(5000).then(() => captureThreadDump(testInfo, 5000, pidFile, runId, operation)),
    delay(15000).then(() => captureThreadDump(testInfo, 15000, pidFile, runId, operation))
  ];

  const samplingTask = (async () => {
    let nextSampleAt = startedAt;
    while (!finished) {
      const offsetMs = Date.now() - startedAt;
      const [metrics, postgres] = await Promise.all([
        sampleMetrics(request, headers),
        samplePostgresActivity(pgTarget)
      ]);
      const wrote = await appendJsonLine(outputPath, {
        type: 'sample',
        atUtc: new Date().toISOString(),
        offsetMs,
        viewportWidth,
        metrics,
        postgres
      });
      outputWritable = outputWritable && wrote;
      sampleCount += 1;
      nextSampleAt += SAMPLE_INTERVAL_MS;
      if (finished) break;
      await delay(Math.max(0, nextSampleAt - Date.now()));
    }
  })().catch(() => {
    outputWritable = false;
    samplingErrorCode = 'sampling_failed';
  });

  let outcome = 'completed';
  let operationError;
  try {
    await action();
  } catch (error) {
    outcome = 'failed';
    operationError = error;
  } finally {
    finished = true;
    await samplingTask;
    const finishedAt = Date.now();
    const pgStatsAfter = await samplePgStatements(pgTarget);
    const accessLog = await readSanitizedAccessLogDelta(accessLogStart);
    const threadDumps = await Promise.all(threadTasks);
    const summary = {
      type: 'summary',
      operation,
      outcome,
      ...(operation === 'reload' ? { reloadOutcome: outcome } : {}),
      durationMs: finishedAt - startedAt,
      viewportWidth,
      sampleCount,
      samplingState: samplingErrorCode ? 'unavailable' : 'sampled',
      samplingErrorCode,
      pgStatementsDelta: pgStatementsDelta(pgStatsBefore, pgStatsAfter),
      threadDumps,
      accessLog: accessLogPath ? path.basename(path.resolve(process.cwd(), accessLogPath)) : null,
      accessLogState: accessLog.state,
      accessLogEntries: accessLog.entries,
      ...(typeof browserRequests === 'function' ? { browserRequests: browserRequests() } : {}),
      outputWritable
    };
    await appendJsonLine(outputPath, summary);
    testInfo.annotations.push({
      type: 'authenticated-' + operation + '-diagnostics',
      description: JSON.stringify({ ...summary, outputFile: path.basename(outputPath) })
    });
  }

  if (operationError) throw operationError;
}

async function runAuthenticatedReloadDiagnostics(options) {
  const { page, waitForReady } = options;
  return runAuthenticatedOperationDiagnostics({
    ...options,
    operation: 'reload',
    action: async () => {
      await page.reload();
      await waitForReady();
    }
  });
}

async function runAuthenticatedBootstrapDiagnostics(options) {
  const { performLogin, waitForReady } = options;
  return runAuthenticatedOperationDiagnostics({
    ...options,
    operation: 'bootstrap',
    action: async () => {
      await performLogin();
      await waitForReady();
    }
  });
}

async function runAvailabilityRequestDiagnostics(options) {
  const allowedOperations = new Set(['availability-first', 'availability-repeat']);
  if (!options || !allowedOperations.has(options.operation)) {
    throw new Error('Availability diagnostics operation is not allowed');
  }
  return runAuthenticatedOperationDiagnostics(options);
}

module.exports = {
  runAuthenticatedBootstrapDiagnostics,
  runAuthenticatedReloadDiagnostics,
  runAvailabilityRequestDiagnostics
};
