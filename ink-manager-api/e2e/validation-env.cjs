'use strict';

const LOOPBACK = new Set(['127.0.0.1', 'localhost', '[::1]', '::1']);

function database(value = process.env.DB_URL) {
  const match = String(value || '').match(/^jdbc:(postgresql):\/\/(.*)$/i);
  if (!match) throw new Error('DB_URL must be a PostgreSQL JDBC URL for an isolated local validation database');
  let parsed;
  try { parsed = new URL(`${match[1]}://${match[2]}`); }
  catch { throw new Error('DB_URL must be a valid local PostgreSQL JDBC URL'); }
  if (!LOOPBACK.has(parsed.hostname.toLowerCase())) throw new Error('DB_URL host must be loopback');
  if (parsed.username || parsed.password) throw new Error('DB_URL must not embed credentials');
  const name = decodeURIComponent(parsed.pathname.replace(/^\//, ''));
  const schema = parsed.searchParams.get('currentSchema') || '';
  if (!/^marcae_validation_[a-z0-9_]+$/i.test(name) && !/^marcae_validation_[a-z0-9_]+$/i.test(schema)) {
    throw new Error('DB_URL database or currentSchema must use the marcae_validation_ prefix');
  }
  if (!process.env.DB_USERNAME || !process.env.DB_PASSWORD) {
    throw new Error('DB_USERNAME and DB_PASSWORD must be set for the isolated validation database');
  }
  return { url: value, username: process.env.DB_USERNAME, password: process.env.DB_PASSWORD };
}

function requireLoopbackMail(baseURL) {
  database();
  const smtpHost = String(process.env.MAIL_HOST || '').toLowerCase();
  const smtpPort = Number(process.env.MAIL_PORT);
  let mailbox;
  try { mailbox = new URL(process.env.E2E_MAILBOX_API || ''); }
  catch { throw new Error('E2E_MAILBOX_API must point to the local fake SMTP mailbox API'); }
  if (String(process.env.MAIL_ENABLED).toLowerCase() !== 'true') throw new Error('MAIL_ENABLED=true is required only for the dedicated waitlist E2E process');
  if (!LOOPBACK.has(smtpHost) || !Number.isInteger(smtpPort) || smtpPort < 1 || smtpPort > 65535) {
    throw new Error('MAIL_HOST and MAIL_PORT must identify a loopback SMTP sink');
  }
  if (!process.env.MAIL_USERNAME || !process.env.MAIL_PASSWORD || !String(process.env.MAIL_FROM || '').toLowerCase().endsWith('.example.test')) {
    throw new Error('Use non-empty fake SMTP credentials and a MAIL_FROM address under example.test');
  }
  if (String(process.env.SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH).toLowerCase() !== 'false' ||
      String(process.env.SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE).toLowerCase() !== 'false' ||
      String(process.env.SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED).toLowerCase() !== 'false') {
    throw new Error('SMTP auth and STARTTLS must be disabled for the local fake sink');
  }
  if (!['http:', 'https:'].includes(mailbox.protocol) || !LOOPBACK.has(mailbox.hostname.toLowerCase())) {
    throw new Error('E2E_MAILBOX_API must use HTTP(S) on loopback');
  }
  if (process.env.PUBLIC_URL !== baseURL) throw new Error('PUBLIC_URL must match the dedicated local Playwright baseURL');
  return { db: database(), mailbox };
}

module.exports = { database, requireLoopbackMail };
