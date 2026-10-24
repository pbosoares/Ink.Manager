'use strict';

const { defineConfig } = require('@playwright/test');
const { requireLoopbackMail } = require('./e2e/validation-env.cjs');

const baseURL = 'http://127.0.0.1:8087';
// Evaluate every guard and probe the known loopback Mailpit before webServer
// can launch the JAR, run Flyway, or start the outbox worker.
const { testEnv, serverEnv } = requireLoopbackMail(baseURL);
// The existing spec checks its process environment. These values are fixed
// sentinels in this short-lived Playwright process; external shell values were
// validated and are never copied into the app process.
for (const key of Object.keys(process.env)) {
  if (/^(STRIPE_|MP_|MERCADOPAGO_|RESEND_)/i.test(key)) delete process.env[key];
}
Object.assign(process.env, testEnv);

module.exports = defineConfig({
  expect: { timeout: 20000 },
  testDir: './e2e',
  testMatch: '**/marcae-waitlist.spec.cjs',
  workers: 1,
  timeout: 150000,
  use: { baseURL, trace: 'off' },
  webServer: {
    command: 'java -jar target/api-0.0.1-SNAPSHOT.jar --server.port=8087 --server.address=127.0.0.1',
    url: `${baseURL}/actuator/health`,
    timeout: 120000,
    reuseExistingServer: false,
    env: serverEnv
  }
});
