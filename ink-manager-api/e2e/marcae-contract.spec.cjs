const { test, expect } = require('@playwright/test');
const { randomUUID } = require('node:crypto');

// Run only against the disposable database supplied to Playwright's real API.
test('catalogo publico, reserva idempotente, conflito e snapshot na agenda', async ({ request }) => {
  const email = `marcae-${randomUUID()}@example.com`;
  const password = 'SenhaTeste123';
  const signup = await request.post('/auth/estudios', { data: {
    nome: 'Responsavel E2E', nomeEstudio: 'Negocio Marcae E2E', email, senha: password
  }});
  expect(signup.status()).toBe(201);
  const login = await request.post('/auth/login', { data: { email, senha: password } });
  expect(login.ok()).toBeTruthy();
  const { token } = await login.json();
  const headers = { Authorization: `Bearer ${token}` };
  const created = await request.post('/servicos', { headers, data: {
    nome: 'Servico E2E', preco: 85.50, duracaoMinutos: 30, ativo: true
  }});
  expect(created.status()).toBe(201);
  const service = await created.json();
  const linkResponse = await request.get('/servicos/link', { headers });
  expect(linkResponse.ok()).toBeTruthy();
  const { caminho } = await linkResponse.json();
  const link = new URL(caminho, 'http://localhost').searchParams.get('local');
  expect(link).toBeTruthy();
  const publicPath = `/publico/${encodeURIComponent(link)}`;
  const catalogResponse = await request.get(publicPath);
  expect(catalogResponse.ok()).toBeTruthy();
  expect(catalogResponse.headers()['cache-control']).toContain('no-store');
  const catalog = await catalogResponse.json();
  expect(catalog.nome).toBe('Negocio Marcae E2E');
  expect(catalog.servicos).toEqual(expect.arrayContaining([
    expect.objectContaining({ id: service.id, nome: 'Servico E2E', preco: 85.50, duracaoMinutos: 30 })
  ]));
  expect(Object.keys(catalog).sort()).toEqual(['antecedenciaMinimaMinutos','fusoHorario','hoje','nome','profissionais','servicos','versaoAvisoPrivacidade'].sort());
  expect(catalog.antecedenciaMinimaMinutos).toEqual(expect.any(Number));
  expect(catalog.versaoAvisoPrivacidade).toEqual(expect.any(String));
  expect(catalog.profissionais.length).toBeGreaterThan(0);
  const professional = catalog.profissionais[0];
  expect(Object.keys(professional).sort()).toEqual(['id','nome']);
  let selected;
  for (let day = 1; day <= 7; day++) {
    const date = new Date(`${catalog.hoje}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate() + day);
    const data = date.toISOString().slice(0, 10);
    const slots = await request.get(`${publicPath}/horarios`, { params: {
      servicoId: service.id, profissionalId: professional.id, data
    }});
    expect(slots.ok()).toBeTruthy();
    const availability = await slots.json();
    if (availability.horarios.length) { selected = { data, horario: availability.horarios[0] }; break; }
  }
  expect(selected, 'Expediente inicial deve oferecer horario nos proximos sete dias').toBeTruthy();
  const payload = { chave: randomUUID(), servicoId: service.id, profissionalId: professional.id,
    ...selected, nome: 'Cliente publico E2E', telefone: '21988887777', email: 'cliente-publico@example.com', avisoPrivacidadeLido: true };
  const reserve = await request.post(`${publicPath}/reservas`, { data: payload });
  expect(reserve.status()).toBe(201);
  const confirmation = await reserve.json();
  expect(confirmation).toMatchObject({ servico: 'Servico E2E', preco: 85.50, data: selected.data });
  expect(confirmation).not.toHaveProperty('telefone');
  const repeat = await request.post(`${publicPath}/reservas`, { data: payload });
  expect(repeat.status()).toBe(201);
  expect(await repeat.json()).toEqual(confirmation);
  const conflict = await request.post(`${publicPath}/reservas`, { data: { ...payload, chave: randomUUID() } });
  expect(conflict.status()).toBe(409);
  const changedKey = await request.post(`${publicPath}/reservas`, { data: { ...payload, nome: 'Pedido diferente' } });
  expect(changedKey.status()).toBe(409);
  const agenda = await request.get('/marcacoes', { headers });
  expect(agenda.ok()).toBeTruthy();
  const entries = await agenda.json();
  expect(entries).toHaveLength(1);
  expect(entries[0]).toMatchObject({ servicoNome: 'Servico E2E', precoPrevisto: 85.50, duracaoMinutos: 30 });
  const futureReceipt = await request.post(`/recebimentos/marcacoes/${entries[0].id}`, { headers, data: { valor: 85.50 } });
  expect(futureReceipt.status()).toBe(409);
  const summary = await request.get('/recebimentos', { headers });
  expect(summary.ok()).toBeTruthy();
  expect(await summary.json()).toMatchObject({ moeda: 'BRL', totalSemana: 0, totalMes: 0, recebimentos: [] });
});
