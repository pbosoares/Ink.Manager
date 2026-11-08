'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const owner = () => window.inkApp?.user()?.perfil === 'PROPRIETARIO';
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => Number(value || 0).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
  const fmt = value => Number(value || 0).toLocaleString('pt-BR');
  const state = { report:null, reportKey:'', reportRequest:0, reportLoadingFor:'', config:null, configLoaded:false, configLoading:false };
  const api = (path, options) => window.inkApp.api(path, options);
  const monthNow = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`; };
  function signed(value, suffix='') { if (value == null) return 'Sem base anterior'; const n=Number(value); return `${n>0?'+':''}${fmt(n)}${suffix}`; }
  function renderReport() {
    const panel=$('monthly-report'); if (!panel) return;
    panel.hidden=!owner();
    if (!owner()) return;
    const data=state.report;
    if(state.reportKey!==($('report-month').value||monthNow())){$('report-data').hidden=true;return;}
    if (!data) { $('report-data').hidden=true; return; }
    $('report-data').hidden=false;
    $('report-timezone').textContent=`Fuso do estabelecimento: ${data.fusoHorario}.`;
    const a=data.atual, v=data.variacoes||{};
    const items=[
      ['Agendamentos',a.agendamentos,signed(v.agendamentos)],
      ['Comparecimentos',a.comparecimentos,signed(v.comparecimentos)],
      ['Faltas',a.faltas,signed(v.faltas)],
      ['Cancelamentos',a.cancelamentos,signed(v.cancelamentos)]
    ];
    $('report-kpis').innerHTML=items.map(([title,value,change])=>`<article><span>${title.toUpperCase()}</span><strong>${fmt(value)}</strong><small>Variação: ${esc(change)}</small></article>`).join('');
    const rate=a.taxaComparecimento;
    $('report-rate').textContent=rate==null?'Sem base de comparecimento':`${Number(rate).toLocaleString('pt-BR',{maximumFractionDigits:2})}%`;
    $('report-denominator').textContent=`Base: ${fmt(a.comparecimentos)} comparecimentos + ${fmt(a.faltas)} faltas = ${fmt(a.denominadorComparecimento)}. Cancelamentos e reservas futuras não entram.`;
    $('report-rate-variation').textContent=v.pontosPercentuais==null?'Variação: sem base comparável':`Variação: ${Number(v.pontosPercentuais)>0?'+':''}${Number(v.pontosPercentuais).toLocaleString('pt-BR',{maximumFractionDigits:2})} pontos percentuais`;
    $('report-received').textContent=money(a.receitaRealizada);
    $('report-received-change').textContent=`Variação: ${v.receitaRealizada==null?'sem base anterior':money(v.receitaRealizada)}`;
    $('report-absence-potential').textContent=money(a.valorPotencialFaltas);
    $('report-absence-change').textContent=`Variação: ${v.valorPotencialFaltas==null?'sem base anterior':money(v.valorPotencialFaltas)}`;
    const mode=$('report-range').value, rows=mode==='week'?data.semanas:data.dias;
    const max=Math.max(1,...rows.map(x=>Number(x.agendamentos)||0));
    $('report-chart').innerHTML=rows.map((x,i)=>{const label=mode==='week'?`${x.inicio.slice(8)}–${x.fim.slice(8)}`:x.data.slice(8);const value=Number(x.agendamentos)||0,h=value===0?0:Math.round((value/max)*100);return `<div class="report-bar" title="${esc(label)}: ${fmt(value)} agendamentos"><i style="height:${h}%;opacity:${value===0?0:1}"></i><small>${esc(label)}</small></div>`;}).join('');
    $('report-chart-label').textContent=mode==='week'?'Agendamentos por semana':'Agendamentos por dia';
    $('report-services').innerHTML=data.servicos?.length?`<ol>${data.servicos.map(s=>`<li><span>${esc(s.nome)}</span><b>${fmt(s.agendamentos)}</b><small>${fmt(s.comparecimentos)} comparecimentos · ${fmt(s.faltas)} faltas</small></li>`).join('')}</ol>`:'<p class="muted">Nenhum serviço com reservas neste mês.</p>';
    $('report-comparison-note').textContent=data.comparacaoDisponivel?'Comparação com o mês anterior. Variações de taxa são expressas em pontos percentuais.':'O mês anterior não tem reservas para comparação.';
    renderAbsenceHistory();
  }
  function renderAbsenceHistory(){
    const target=$('absence-history'); if(!target) return;
    const groups=new Map();
    for(const item of window.inkApp?.appointments()||[]){if(item.presenca!=='FALTOU')continue;const key=String(item.cliente?.id??item.cliente?.nome??'cliente');const row=groups.get(key)||{name:item.cliente?.nome||'Cliente',count:0,last:item.data};row.count++;if(item.data>row.last)row.last=item.data;groups.set(key,row);}
    const rows=[...groups.entries()].sort((a,b)=>b[1].count-a[1].count||b[1].last.localeCompare(a[1].last)).slice(0,8);
    target.innerHTML=rows.length?`<ul>${rows.map(([,x])=>`<li><span>${esc(x.name)}</span><small>${x.count} falta(s) · última ${esc(x.last)}</small></li>`).join('')}</ul>`:'<p class="muted">Nenhuma falta registrada no histórico carregado.</p>';
  }
  async function loadReport(){
    if(!owner())return;
    const mes=$('report-month').value||monthNow(), request=++state.reportRequest;state.reportLoadingFor=mes;
    $('report-data').hidden=true;
    $('report-error').textContent='';$('report-loading').hidden=false;$('report-reload').disabled=true;
    try{
      const data=await api(`/relatorios/mensal?mes=${encodeURIComponent(mes)}`);
      if(!data?.atual||!Array.isArray(data.dias)||!Array.isArray(data.semanas)||!Array.isArray(data.servicos))throw new Error('Resposta de relatório incompleta.');
      if(request!==state.reportRequest)return;
      state.report=data;state.reportKey=mes;renderReport();
    }catch(e){if(request===state.reportRequest)$('report-error').textContent=e.message;}
    finally{if(request===state.reportRequest){state.reportLoadingFor='';$('report-loading').hidden=true;$('report-reload').disabled=false;}}
  }
  function ensureReportLoaded(){
    if(!owner())return;
    const mes=$('report-month').value||monthNow();
    if(state.reportKey===mes||state.reportLoadingFor===mes)return;
    return loadReport();
  }
  async function loadConfig(){
    if(!owner()||state.configLoaded||state.configLoading)return;
    state.configLoading=true;
    try{
      const c=await api('/configuracao/agendamento');state.config=c;state.configLoaded=true;
      const f=$('schedule-config-form');f.elements.antecedenciaMinimaMinutos.value=c.antecedenciaMinimaMinutos;f.elements.limiteAgendamentosPorEmail.value=c.limiteAgendamentosPorEmail;$('schedule-timezone').textContent=c.fusoHorario;window.inkApp.render();
    }catch(e){$('schedule-config-message').textContent=e.message;}
    finally{state.configLoading=false;}
  }
  async function saveConfig(event){
    event.preventDefault();const f=event.currentTarget,antecedenciaMinimaMinutos=Number(f.elements.antecedenciaMinimaMinutos.value),limiteAgendamentosPorEmail=Number(f.elements.limiteAgendamentosPorEmail.value);
    if(!Number.isInteger(antecedenciaMinimaMinutos)||antecedenciaMinimaMinutos<0||antecedenciaMinimaMinutos>43200||!Number.isInteger(limiteAgendamentosPorEmail)||limiteAgendamentosPorEmail<1||limiteAgendamentosPorEmail>50){$('schedule-config-message').textContent='Antecedência: 0 a 43.200 minutos. Limite: 1 a 50 reservas.';return;}
    if(!state.config?.fusoHorario){$('schedule-config-message').textContent='Não foi possível carregar as regras atuais. Atualize a página e tente novamente.';return;}
    const button=f.querySelector('button[type=submit]');button.disabled=true;$('schedule-config-message').textContent='Salvando…';
    try{const c=await api('/configuracao/agendamento',{method:'PUT',body:{antecedenciaMinimaMinutos,limiteAgendamentosPorEmail,fusoHorario:state.config?.fusoHorario||$('schedule-timezone').textContent}});state.config=c;$('schedule-timezone').textContent=c.fusoHorario;window.inkApp.render();$('schedule-config-message').textContent='Configurações salvas.';}
    catch(e){$('schedule-config-message').textContent=e.message;}
    finally{button.disabled=false;}
  }
  function appointmentEnded(item){
    const zone=state.config?.fusoHorario||state.report?.fusoHorario||'America/Sao_Paulo';
    const wallNow=new Intl.DateTimeFormat('sv-SE',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date()).replace(' ','T');
    const localStart=`${item.data}T${String(item.horario).slice(0,5)}`;
    const wallAsUtc=Date.parse(`${localStart}:00Z`);
    const wallParts=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).formatToParts(new Date(wallAsUtc));
    const represented=Date.UTC(Number(wallParts.find(x=>x.type==='year').value),Number(wallParts.find(x=>x.type==='month').value)-1,Number(wallParts.find(x=>x.type==='day').value),Number(wallParts.find(x=>x.type==='hour').value),Number(wallParts.find(x=>x.type==='minute').value),Number(wallParts.find(x=>x.type==='second').value));
    const startInstant=wallAsUtc-(represented-wallAsUtc);
    const endParts=new Intl.DateTimeFormat('sv-SE',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(startInstant+(Number(item.duracaoMinutos)||60)*60000)).replace(' ','T');
    return wallNow>=endParts;
  }
  function attendanceAction(item){
    if(!owner()||!state.configLoaded)return '';
    if(item.presenca)return `<small class="attendance-recorded">${item.presenca==='COMPARECEU'?'Compareceu':'Faltou'}</small>`;
    if(item.status==='CANCELADA'||item.recebimentoConfirmado||!appointmentEnded(item))return '';
    if(item.status==='CONCLUIDA')return `<span class="attendance-actions"><button type="button" class="text-button" data-presence="FALTOU" data-appointment-id="${Number(item.id)}">Marcar como faltou</button></span>`;
    return `<span class="attendance-actions"><button type="button" class="text-button" data-presence="COMPARECEU" data-appointment-id="${Number(item.id)}">Compareceu</button><button type="button" class="text-button" data-presence="FALTOU" data-appointment-id="${Number(item.id)}">Faltou</button></span>`;
  }
  async function setPresence(button){
    const id=Number(button.dataset.appointmentId),value=button.dataset.presence;
    if(!Number.isSafeInteger(id)||!['COMPARECEU','FALTOU'].includes(value))return;
    const actions=button.parentElement;actions.querySelectorAll('button').forEach(x=>x.disabled=true);
    try{await api(`/marcacoes/${id}/presenca`,{method:'PUT',body:{presenca:value}});await window.inkApp.reload();if(state.reportKey)await loadReport();}
    catch(e){actions.querySelectorAll('button').forEach(x=>x.disabled=false);window.inkApp.notify(e.message);}
  }
  function render(){if(!owner())return;renderReport();if(window.inkApp.view()==='overview')ensureReportLoaded();if(window.inkApp.view()==='agenda'||window.inkApp.view()==='studio')loadConfig();}
  $('report-reload').addEventListener('click',loadReport);$('report-month').value=monthNow();$('report-range').addEventListener('change',renderReport);
  $('schedule-config-form').addEventListener('submit',saveConfig);
  document.addEventListener('click',event=>{const button=event.target.closest('[data-presence]');if(button)setPresence(button);});
  $('report-month').addEventListener('change',loadReport);
  window.inkAnalytics={render,loadReport,ensureReportLoaded,attendanceAction,close(){state.report=null;state.config=null;state.configLoaded=false;state.configLoading=false;state.reportRequest++;state.reportKey='';state.reportLoadingFor='';},refreshHistory:renderAbsenceHistory};
})()
