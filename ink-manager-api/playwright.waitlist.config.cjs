'use strict';

const { defineConfig } = require('@playwright/test');
const { requireLoopbackMail } = require('./e2e/validation-env.cjs');

const baseURL = 'http://127.0.0.1:8087';
// This module is evaluated before Playwright starts webServer. Reject unsafe
// database or mail configuration before the JAR can run Flyway or its worker.
const { db, mailbox } = requireLoopbackMail(baseURL);

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
    env: {
      DB_URL: db.url, DB_USERNAME: db.username, DB_PASSWORD: db.password,
      MAIL_ENABLED: 'true', MAIL_HOST: process.env.MAIL_HOST, MAIL_PORT: process.env.MAIL_PORT,
      MAIL_USERNAME: process.env.MAIL_USERNAME, MAIL_PASSWORD: process.env.MAIL_PASSWORD,
      MAIL_FROM: process.env.MAIL_FROM,
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: 'false',
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: 'false',
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED: 'false',
      PUBLIC_URL: baseURL,
      E2E_MAILBOX_API: mailbox.href,
      JWT_SECRET: 'e2e-only-secret-with-more-than-32-bytes'
    }
  }
});
