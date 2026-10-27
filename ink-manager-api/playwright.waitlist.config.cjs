'use strict';

const { defineConfig } = require('@playwright/test');
const {
  requireLoopbackMail,
  LOOPBACK_MAIL_FIXTURE_MARKER
} = require('./e2e/validation-env.cjs');

const baseURL = 'http://127.0.0.1:8087';
// Evaluate every guard and probe the known loopback Mailpit before webServer
// can launch the JAR, run Flyway, or start the outbox worker.
const { mailbox, serverEnv } = requireLoopbackMail(baseURL);
// Keep SMTP configuration exclusively in webServer.env. The Playwright
// runner gets only the validated local mailbox URL and a non-secret marker.
for (const key of Object.keys(process.env)) {
  if (/^(MAIL_|SMTP_|SPRING_MAIL_|STRIPE_|MP_|MERCADOPAGO_|RESEND_|BILLING_|PAYMENT_)/i.test(key)) {
    delete process.env[key];
  }
}
delete process.env.DB_USERNAME;
delete process.env.DB_PASSWORD;
process.env.PUBLIC_URL = baseURL;
process.env.E2E_MAILBOX_API = mailbox.href;
process.env.E2E_LOCAL_MAIL_FIXTURE = LOOPBACK_MAIL_FIXTURE_MARKER;

const webServer = {
  command: 'java -jar target/api-0.0.1-SNAPSHOT.jar --server.port=8087 --server.address=127.0.0.1',
  url: `${baseURL}/actuator/health`,
  timeout: 120000,
  reuseExistingServer: false
};
// Playwright worker config reloads validate the marker without rebuilding the
// app environment. Only the initial runner owns webServer.env and starts Java.
if (serverEnv) webServer.env = serverEnv;

module.exports = defineConfig({
  expect: { timeout: 20000 },
  testDir: './e2e',
  testMatch: '**/marcae-waitlist.spec.cjs',
  workers: 1,
  timeout: 150000,
  use: { baseURL, trace: 'off' },
  webServer
});
