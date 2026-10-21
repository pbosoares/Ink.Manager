const { defineConfig } = require('@playwright/test');
const { database } = require('./e2e/validation-env.cjs');
const db = database(); // Fail before Playwright can start the application or Flyway.
module.exports = defineConfig({
  expect: { timeout: 20000 },
  testDir: './e2e', testIgnore: '**/marcae-waitlist.spec.cjs', workers: 1, timeout: 90000,
  use: { baseURL: 'http://127.0.0.1:8087', trace: 'retain-on-failure' },
  webServer: {
    command: 'java -jar target/api-0.0.1-SNAPSHOT.jar --server.port=8087 --server.address=127.0.0.1',
    url: 'http://127.0.0.1:8087/actuator/health', timeout: 120000, reuseExistingServer: false,
    env: {
      DB_URL: db.url, DB_USERNAME: db.username, DB_PASSWORD: db.password,
      MAIL_ENABLED: 'false', MAIL_HOST: '', MAIL_USERNAME: '', MAIL_PASSWORD: '', MAIL_FROM: '',
      JWT_SECRET: 'e2e-only-secret-with-more-than-32-bytes'
    }
  }
});
