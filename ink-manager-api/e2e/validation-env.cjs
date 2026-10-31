'use strict';

const { spawnSync } = require('node:child_process');

const LOOPBACK_HOST = '127.0.0.1';
const LOOPBACK_MAIL_FIXTURE_MARKER = 'loopback-smtp-api-preflight-passed';
const WAITLIST_DB_ROLE = 'ink_validation';
const WAITLIST_DB_PASSWORD_SENTINEL = 'e2e-loopback-trust-sentinel';
const VALIDATION_PG_PORTS = new Set(['55446', '55448']);
const SMTP_SENTINELS = Object.freeze({
  MAIL_ENABLED: 'true',
  MAIL_HOST: LOOPBACK_HOST,
  MAIL_PORT: '1025',
  MAIL_USERNAME: 'e2e-mailpit',
  MAIL_PASSWORD: 'mailpit-sink-sentinel-only',
  MAIL_FROM: 'e2e@marcae.example.test',
  MAIL_HEALTH_ENABLED: 'false',
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: 'false',
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: 'false',
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED: 'false'
});

const MAILPIT_PREFLIGHT = String.raw`
'use strict';
const http = require('node:http');
const net = require('node:net');
const cfg = JSON.parse(process.argv[1]);

function probeMailbox() {
  return new Promise((resolve, reject) => {
    const url = new URL('messages?start=0&limit=1', cfg.mailboxApi);
    const req = http.get(url, { timeout: 2500 }, res => {
      if (res.statusCode !== 200) {
        res.resume();
        reject(new Error('mailbox status'));
        return;
      }
      let body = '';
      res.setEncoding('utf8');
      res.on('data', chunk => {
        body += chunk;
        if (body.length > 65536) req.destroy(new Error('mailbox response too large'));
      });
      res.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          if (!Array.isArray(parsed.messages)) throw new Error('not Mailpit API');
          resolve();
        } catch {
          reject(new Error('mailbox response is not Mailpit API'));
        }
      });
    });
    req.on('timeout', () => req.destroy(new Error('mailbox timeout')));
    req.on('error', reject);
  });
}

function probeSmtp() {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: cfg.smtpHost, port: cfg.smtpPort });
    let settled = false;
    const finish = error => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      socket.destroy();
      if (error) reject(error); else resolve();
    };
    const timer = setTimeout(() => finish(new Error('SMTP timeout')), 2500);
    socket.once('error', finish);
    socket.once('data', chunk => {
      const greeting = chunk.toString('utf8').split(/\r?\n/, 1)[0];
      if (!/^220\b/i.test(greeting)) {
        finish(new Error('SMTP service did not return a 220 greeting'));
      } else {
        finish();
      }
    });
  });
}

Promise.all([probeMailbox(), probeSmtp()]).then(
  () => { process.exitCode = 0; },
  () => { process.exitCode = 1; }
);
`;

function isolatedDatabase(value = process.env.DB_URL) {
  const match = String(value || '').match(/^jdbc:(postgresql):\/\/(.*)$/i);
  if (!match) throw new Error('DB_URL must be a PostgreSQL JDBC URL for an isolated local validation database');
  let parsed;
  try { parsed = new URL(`${match[1]}://${match[2]}`); }
  catch { throw new Error('DB_URL must be a valid local PostgreSQL JDBC URL'); }
  const expectedPort = process.env.E2E_VALIDATION_PG_PORT || '55446';
  if (!VALIDATION_PG_PORTS.has(expectedPort)) {
    throw new Error('E2E_VALIDATION_PG_PORT must select a reserved isolated validation port');
  }
  if (parsed.hostname.toLowerCase() !== LOOPBACK_HOST) {
    throw new Error('DB_URL host must be exactly 127.0.0.1 for isolated validation');
  }
  if (parsed.port !== expectedPort) {
    throw new Error('DB_URL port must match E2E_VALIDATION_PG_PORT; the existing cluster on 55439 is reserved');
  }
  if (parsed.username || parsed.password) throw new Error('DB_URL must not embed credentials');
  const name = decodeURIComponent(parsed.pathname.replace(/^\//, ''));
  if (!/^marcae_validation_[a-z0-9_]+$/i.test(name)) {
    throw new Error('DB_URL database name must use the marcae_validation_ prefix');
  }
  return { url: value, name };
}

function database(value = process.env.DB_URL) {
  const isolated = isolatedDatabase(value);
  if (!process.env.DB_USERNAME || !process.env.DB_PASSWORD) {
    throw new Error('DB_USERNAME and DB_PASSWORD must be set for the isolated validation database');
  }
  return { ...isolated, username: process.env.DB_USERNAME, password: process.env.DB_PASSWORD };
}

const REGULAR_BLOCKED_PREFIX = /^(SPRING_|MAIL_|SMTP_|RESEND_|STRIPE_|MP_|MERCADOPAGO_|BILLING_|PAYMENT_)/i;
const JAVA_OVERRIDE_VARIABLES = ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "JAVA_OPTS"];

function regularWebServerEnvironment(baseURL = "http://127.0.0.1:8087") {
  const db = database();
  for (const name of Object.keys(process.env)) {
    if (REGULAR_BLOCKED_PREFIX.test(name)) {
      throw new Error(name + " must be unset before regular Playwright E2E");
    }
  }
  for (const name of JAVA_OVERRIDE_VARIABLES) {
    const inheritedName = Object.keys(process.env).find(key => key.toUpperCase() === name);
    if (inheritedName) {
      throw new Error(inheritedName + " must be unset before regular Playwright E2E");
    }
  }
  const publicUrl = Object.entries(process.env).find(([name]) => name.toUpperCase() === "PUBLIC_URL")?.[1];
  if (publicUrl && publicUrl !== baseURL) {
    throw new Error("PUBLIC_URL must be unset or match the ephemeral loopback app URL");
  }

  // Playwright merges webServer.env with process.env; pass only runtime and app inputs.
  const env = {};
  const pathValue = process.env.Path || process.env.PATH;
  if (pathValue) env.Path = pathValue;
  for (const name of ["SystemRoot", "WINDIR", "TEMP", "TMP", "JAVA_HOME", "USERPROFILE", "LOCALAPPDATA"]) {
    if (process.env[name]) env[name] = process.env[name];
  }
  Object.assign(env, {
    DB_URL: db.url,
    DB_USERNAME: db.username,
    DB_PASSWORD: db.password,
    MAIL_ENABLED: "false",
    MAIL_HOST: "",
    MAIL_PORT: "587",
    MAIL_USERNAME: "",
    MAIL_PASSWORD: "",
    MAIL_FROM: "",
    MAIL_HEALTH_ENABLED: "false",
    BILLING_PROVIDER: "stripe",
    STRIPE_SECRET_KEY: "",
    STRIPE_WEBHOOK_SECRET: "",
    MP_ACCESS_TOKEN: "",
    MP_WEBHOOK_SECRET: "",
    PUBLIC_URL: baseURL,
    JWT_SECRET: "e2e-only-secret-with-more-than-32-bytes"
  });
  return env;
}

function validateInheritedMailEnvironment(baseURL) {
  const expected = new Map(Object.entries(SMTP_SENTINELS).map(([key, value]) => [key, value]));
  const inherited = new Map();
  for (const [rawKey, rawValue] of Object.entries(process.env)) {
    const key = rawKey.toUpperCase();
    if (!/^(MAIL_|SMTP_|SPRING_MAIL_)/.test(key)) continue;
    if (!expected.has(key)) throw new Error(`Unexpected inherited mail/SMTP variable: ${rawKey}`);
    if (key === 'MAIL_ENABLED' && String(rawValue).toLowerCase() === 'false') {
      inherited.set(key, 'false');
      continue;
    }
    const value = key.startsWith('SPRING_MAIL_') ? String(rawValue).toLowerCase() : String(rawValue);
    const wanted = key.startsWith('SPRING_MAIL_') ? expected.get(key).toLowerCase() : expected.get(key);
    if (value !== wanted) throw new Error(`Inherited mail/SMTP variable must be absent or equal its fixed E2E value: ${rawKey}`);
    inherited.set(key, value);
  }

  // A reloaded Playwright worker may inherit these fixed values; enabling mail
  // requires the complete set, and requireLoopbackMail probes both loopback sinks.
  if (inherited.get('MAIL_ENABLED') === 'true') {
    const completeSentinelSet = Array.from(expected).every(([key, expectedValue]) => {
      const actual = inherited.get(key);
      const wanted = key.startsWith('SPRING_MAIL_') ? expectedValue.toLowerCase() : expectedValue;
      return actual !== undefined && actual === wanted;
    });
    if (!completeSentinelSet) throw new Error('MAIL_ENABLED=true requires the complete fixed local SMTP sentinel set');
  }

  for (const name of [
    'SPRING_APPLICATION_JSON', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION',
    'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME', 'SPRING_PROFILES_ACTIVE'
  ]) {
    if (process.env[name]) throw new Error(`${name} must be unset for the isolated waitlist E2E`);
  }
  for (const name of ['SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD']) {
    if (process.env[name]) throw new Error(`${name} must be unset; DB_URL is the only database selector`);
  }
  for (const name of ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']) {
    if (/spring\.mail|mail\.smtp|mail_(enabled|host|port|username|password|from)|smtp_/i.test(process.env[name] || '')) {
      throw new Error(`${name} contains an SMTP override and is not allowed`);
    }
  }
  if (process.env.PUBLIC_URL && process.env.PUBLIC_URL !== baseURL) {
    throw new Error('PUBLIC_URL must be unset or match the ephemeral loopback app URL');
  }
}

function ephemeralEnvironment(db, mailbox, baseURL) {
  const env = { ...process.env };
  for (const key of Object.keys(env)) {
    const upper = key.toUpperCase();
    if (/^(MAIL_|SMTP_|SPRING_MAIL_|STRIPE_|MP_|MERCADOPAGO_|RESEND_)/.test(upper) ||
        /^(SPRING_APPLICATION_JSON|SPRING_CONFIG_LOCATION|SPRING_CONFIG_ADDITIONAL_LOCATION|SPRING_CONFIG_IMPORT|SPRING_CONFIG_NAME|SPRING_PROFILES_ACTIVE|SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS)$/.test(upper)) {
      delete env[key];
    }
  }
  Object.assign(env, {
    DB_URL: db.url,
    DB_USERNAME: db.username,
    DB_PASSWORD: db.password,
    ...SMTP_SENTINELS,
    STRIPE_SECRET_KEY: '',
    STRIPE_WEBHOOK_SECRET: '',
    MP_ACCESS_TOKEN: '',
    MP_WEBHOOK_SECRET: '',
    BILLING_PROVIDER: 'stripe',
    MAIL_HEALTH_ENABLED: 'false',
    PUBLIC_URL: baseURL,
    E2E_MAILBOX_API: mailbox.href,
    JWT_SECRET: 'e2e-only-secret-with-more-than-32-bytes'
  });
  return env;
}

function mailboxEndpoint() {
  const mailboxValue = process.env.E2E_MAILBOX_API || '';
  let mailbox;
  try { mailbox = new URL(mailboxValue); }
  catch { throw new Error('E2E_MAILBOX_API must point to the local Mailpit API'); }
  if (mailbox.protocol !== 'http:' || mailbox.hostname !== LOOPBACK_HOST || mailbox.port !== '8025' ||
      mailbox.username || mailbox.password || mailbox.search || mailbox.hash || mailbox.pathname !== '/api/v1/') {
    throw new Error('E2E_MAILBOX_API must be http://127.0.0.1:8025/api/v1/ with no credentials or extra URL data');
  }
  return mailbox;
}

function preflightLoopbackMail(mailbox, smtp) {
  const probe = spawnSync(process.execPath, ['-e', MAILPIT_PREFLIGHT, JSON.stringify({
    mailboxApi: mailbox.href, smtpHost: smtp.host, smtpPort: smtp.port
  })], {
    encoding: 'utf8', timeout: 6000, windowsHide: true, stdio: 'ignore',
    env: Object.fromEntries(['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP']
      .filter(name => process.env[name]).map(name => [name, process.env[name]]))
  });
  if (probe.error || probe.status !== 0) {
    throw new Error('Local Mailpit SMTP/API preflight failed; no application server was started');
  }
}

function requireLoopbackMail(baseURL) {
  const smtp = { host: LOOPBACK_HOST, port: 1025 };
  const validatedMarker = process.env.E2E_LOCAL_MAIL_FIXTURE === LOOPBACK_MAIL_FIXTURE_MARKER;

  if (validatedMarker) {
    const runnerMailVariables = Object.keys(process.env).filter(name => /^(MAIL_|SMTP_|SPRING_MAIL_)/i.test(name));
    if (runnerMailVariables.length) throw new Error('Validated waitlist runner must not contain MAIL/SMTP variables');
    if (process.env.DB_USERNAME || process.env.DB_PASSWORD) {
      throw new Error('Validated waitlist runner must not retain database credentials');
    }
    if (process.env.PUBLIC_URL && process.env.PUBLIC_URL !== baseURL) {
      throw new Error('PUBLIC_URL must be unset or match the ephemeral loopback app URL');
    }

    // Worker config reload: repeat only non-secret guards. The app environment
    // was built by the initial config load and is not reconstructed here.
    const db = isolatedDatabase();
    const mailbox = mailboxEndpoint();
    preflightLoopbackMail(mailbox, smtp);
    return { db, mailbox, smtp, fixtureValidated: true, serverEnv: undefined };
  }

  validateInheritedMailEnvironment(baseURL);
  const db = database();
  if (db.username !== WAITLIST_DB_ROLE) {
    throw new Error('DB_USERNAME must be the restricted local validation role');
  }
  if (db.password !== WAITLIST_DB_PASSWORD_SENTINEL) {
    throw new Error('DB_PASSWORD must be the fixed local validation sentinel');
  }
  const mailbox = mailboxEndpoint();
  preflightLoopbackMail(mailbox, smtp);
  return {
    db,
    mailbox,
    smtp,
    fixtureValidated: false,
    serverEnv: ephemeralEnvironment(db, mailbox, baseURL)
  };
}

module.exports = {
  database,
  regularWebServerEnvironment,
  requireLoopbackMail,
  LOOPBACK_MAIL_FIXTURE_MARKER
};
