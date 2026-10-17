const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
  expect: { timeout: 20000 },
  testDir: './e2e', workers: 1, timeout: 90000,
  use: { baseURL: 'http://127.0.0.1:8087', trace: 'retain-on-failure' },
  webServer: {
    command: 'java -jar target/api-0.0.1-SNAPSHOT.jar --server.port=8087 --server.address=127.0.0.1',
    url: 'http://127.0.0.1:8087/actuator/health', timeout: 120000, reuseExistingServer: false,
    env: { JWT_SECRET: 'e2e-only-secret-with-more-than-32-bytes' }
  }
});
