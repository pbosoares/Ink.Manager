const path = require('node:path');
const { defineConfig } = require('@playwright/test');
const { regularWebServerEnvironment } = require('./e2e/validation-env.cjs');
const accessLogDirectory = path.resolve('target', 'diagnostics', 'access');
const diagnosticsHandoffVersion = 'lead057-v1';
const inheritedHandoff = process.env.E2E_DIAGNOSTICS_HANDOFF_VERSION === diagnosticsHandoffVersion;
const diagnosticRunId = inheritedHandoff
  ? process.env.E2E_DIAGNOSTICS_RUN_ID
  : 'lead057-' + process.pid + '-' + Date.now();
if (!/^lead057-\d+-\d+$/.test(diagnosticRunId || '')) {
  throw new Error('Playwright diagnostics run id is unavailable or invalid');
}
const accessLogPrefix = diagnosticRunId;
const expectedPidFile = path.resolve('target', 'diagnostics', 'runs', diagnosticRunId, 'application.pid');
const expectedAccessLog = path.join('target', 'diagnostics', 'access', accessLogPrefix + '.log');
const serverPidFile = inheritedHandoff ? process.env.E2E_DIAGNOSTICS_PIDFILE : expectedPidFile;
const accessLogPath = inheritedHandoff ? process.env.E2E_DIAGNOSTICS_ACCESS_LOG : expectedAccessLog;
if (inheritedHandoff &&
    (!serverPidFile || path.resolve(serverPidFile).toLowerCase() !== expectedPidFile.toLowerCase() ||
     !accessLogPath || path.resolve(accessLogPath).toLowerCase() !== path.resolve(expectedAccessLog).toLowerCase())) {
  throw new Error('Playwright diagnostics environment handoff does not match its run id');
}
const diagnosticsEnvironment = {
  E2E_DIAGNOSTICS_HANDOFF_VERSION: diagnosticsHandoffVersion,
  E2E_DIAGNOSTICS_PIDFILE: serverPidFile,
  E2E_DIAGNOSTICS_RUN_ID: diagnosticRunId,
  E2E_DIAGNOSTICS_ACCESS_LOG: accessLogPath
};
// The test workers inherit these values from the Playwright runner. webServer.env
// separately passes the same run identity to the ephemeral application wrapper.
Object.assign(process.env, diagnosticsEnvironment);
const webServerEnv = {
  ...regularWebServerEnvironment(), // Guard before Playwright can start the application or Flyway.
  // This app process binds to 127.0.0.1 below; diagnostics remain local to this run.
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: 'health,info,metrics',
  MANAGEMENT_ENDPOINT_METRICS_ACCESS: 'read-only',
  SERVER_TOMCAT_MBEANREGISTRY_ENABLED: 'true',
  SERVER_TOMCAT_ACCESSLOG_ENABLED: 'true',
  SERVER_TOMCAT_ACCESSLOG_DIRECTORY: accessLogDirectory,
  SERVER_TOMCAT_ACCESSLOG_PREFIX: accessLogPrefix,
  SERVER_TOMCAT_ACCESSLOG_SUFFIX: '.log',
  SERVER_TOMCAT_ACCESSLOG_PATTERN: '%t %m %U %s %D',
  SERVER_TOMCAT_ACCESSLOG_ROTATE: 'false',
  ...diagnosticsEnvironment
};
module.exports = defineConfig({
  metadata: {
    diagnosticsPidFile: serverPidFile,
    diagnosticsRunId: diagnosticRunId,
    diagnosticsAccessLog: accessLogPath
  },
  expect: { timeout: 20000 },
  testDir: './e2e', testIgnore: '**/marcae-waitlist.spec.cjs', workers: 1, timeout: 90000,
  // Traces can retain Authorization headers. The diagnostic artifacts below
  // contain only status/timing/metrics and do not capture request headers.
  use: { baseURL: 'http://127.0.0.1:8087', trace: 'off' },
  webServer: {
    command: 'node e2e/diagnostic-server.cjs',
    url: 'http://127.0.0.1:8087/actuator/health', timeout: 120000, reuseExistingServer: false,
    env: webServerEnv
  }
});
