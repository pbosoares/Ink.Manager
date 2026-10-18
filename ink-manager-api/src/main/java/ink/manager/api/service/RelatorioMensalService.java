package ink.manager.api.service;

import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.LinhaRelatorioAgendamento;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.EstudioRepository;
import ink.manager.api.repository.MarcacaoRepository;
import ink.manager.api.repository.RecebimentoRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.exception.ApiException;

@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('PROPRIETARIO')")
public class RelatorioMensalService {
    public record Metricas(long agendamentos,long comparecimentos,long faltas,long cancelamentos,
            long denominadorComparecimento,BigDecimal taxaComparecimento,BigDecimal receitaRealizada,
            BigDecimal valorPotencialFaltas){}
    public record Variacoes(Long agendamentos,Long comparecimentos,Long faltas,Long cancelamentos,
            BigDecimal pontosPercentuais,BigDecimal receitaRealizada,BigDecimal valorPotencialFaltas){}
    public record Dia(LocalDate data,long agendamentos,long comparecimentos,long faltas,long cancelamentos){}
    public record Semana(LocalDate inicio,LocalDate fim,long agendamentos,long comparecimentos,long faltas,long cancelamentos){}
    public record Servico(String nome,long agendamentos,long comparecimentos,long faltas){}
    public record Relatorio(YearMonth mes,String fusoHorario,boolean comparacaoDisponivel,Metricas atual,
            Metricas mesAnterior,Variacoes variacoes,List<Dia> dias,List<Semana> semanas,List<Servico> servicos){}
    private record Acumulado(long agendamentos,long comparecimentos,long faltas,long cancelamentos,
            BigDecimal potencial,Map<LocalDate,long[]> dias,Map<String,long[]> servicos){}
    private final MarcacaoRepository marcacoes;
    private final RecebimentoRepository recebimentos;
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    private final Clock clock;
    public RelatorioMensalService(MarcacaoRepository marcacoes,RecebimentoRepository recebimentos,EstudioRepository estudios,AcessoAtual acesso,Clock clock){this.marcacoes=marcacoes;this.recebimentos=recebimentos;this.estudios=estudios;this.acesso=acesso;this.clock=clock;}
    public Relatorio obter(YearMonth mes){
        long estudioId=acesso.estudioId();ZoneId zona=ZoneId.of(estudios.findById(estudioId).orElseThrow().getFusoHorario());
        YearMonth atualMes=YearMonth.now(clock.withZone(zona));if(mes==null)mes=atualMes;
        if(mes.isAfter(atualMes.plusMonths(1))||mes.isBefore(atualMes.minusYears(5)))throw new ApiException(HttpStatus.BAD_REQUEST,"Escolha um mes entre os ultimos cinco anos e o proximo mes.");
        YearMonth anterior=mes.minusMonths(1);
        Acumulado a=acumular(marcacoes.relatorioMensal(estudioId,mes.atDay(1),mes.plusMonths(1).atDay(1)));
        Acumulado p=acumular(marcacoes.relatorioMensal(estudioId,anterior.atDay(1),mes.atDay(1)));
        BigDecimal receita=receita(estudioId,mes,zona),receitaAnt=receita(estudioId,anterior,zona);
        Metricas atual=metricas(a,receita),passado=metricas(p,receitaAnt);
        boolean compara=passado.agendamentos()>0||passado.cancelamentos()>0||receitaAnt.signum()>0;
        BigDecimal pontos=atual.taxaComparecimento()!=null&&passado.taxaComparecimento()!=null?atual.taxaComparecimento().subtract(passado.taxaComparecimento()).setScale(2,RoundingMode.HALF_UP):null;
        Variacoes variacoes=compara?new Variacoes(a.agendamentos-p.agendamentos,a.comparecimentos-p.comparecimentos,a.faltas-p.faltas,a.cancelamentos-p.cancelamentos,pontos,receita.subtract(receitaAnt).setScale(2,RoundingMode.HALF_UP),a.potencial.subtract(p.potencial).setScale(2,RoundingMode.HALF_UP)):new Variacoes(null,null,null,null,null,null,null);
        return new Relatorio(mes,zona.getId(),compara,atual,passado,variacoes,dias(mes,a.dias),semanas(mes,a.dias),ranking(a.servicos));
    }
    private BigDecimal receita(long estudio,YearMonth mes,ZoneId zona){
        Instant inicio=mes.atDay(1).atStartOfDay(zona).toInstant(),fim=mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();
        return recebimentos.valoresNoPeriodo(estudio,inicio,fim).stream().reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
    }
    private Metricas metricas(Acumulado a,BigDecimal receita){long base=a.comparecimentos+a.faltas;
        BigDecimal taxa=base==0?null:BigDecimal.valueOf(a.comparecimentos).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(base),2,RoundingMode.HALF_UP);
        return new Metricas(a.agendamentos,a.comparecimentos,a.faltas,a.cancelamentos,base,taxa,receita,a.potencial.setScale(2,RoundingMode.HALF_UP));}
    private Acumulado acumular(List<LinhaRelatorioAgendamento> linhas){
        long agendamentos=0,comparecimentos=0,faltas=0,cancelamentos=0;BigDecimal potencial=BigDecimal.ZERO;
        Map<LocalDate,long[]> dias=new HashMap<>();Map<String,long[]> servicos=new HashMap<>();
        for(var l:linhas){long[] d=dias.computeIfAbsent(l.data(),k->new long[4]);boolean cancelado=l.status()==StatusMarcacao.CANCELADA;
            if(cancelado){cancelamentos++;d[3]++;continue;}agendamentos++;d[0]++;
            boolean veio="COMPARECEU".equals(l.presenca())||l.status()==StatusMarcacao.CONCLUIDA;
            boolean faltou="FALTOU".equals(l.presenca());
            if(veio){comparecimentos++;d[1]++;}if(faltou){faltas++;d[2]++;if(l.precoPrevisto()!=null)potencial=potencial.add(l.precoPrevisto());}
            if(l.servicoNome()!=null&&!l.servicoNome().isBlank()){long[] s=servicos.computeIfAbsent(l.servicoNome(),k->new long[3]);s[0]++;if(veio)s[1]++;if(faltou)s[2]++;}
        }
        return new Acumulado(agendamentos,comparecimentos,faltas,cancelamentos,potencial,dias,servicos);
    }
    private List<Dia> dias(YearMonth mes,Map<LocalDate,long[]> dados){List<Dia> r=new ArrayList<>();for(LocalDate d=mes.atDay(1);!d.isAfter(mes.atEndOfMonth());d=d.plusDays(1)){long[] v=dados.getOrDefault(d,new long[4]);r.add(new Dia(d,v[0],v[1],v[2],v[3]));}return r;}
    private List<Semana> semanas(YearMonth mes,Map<LocalDate,long[]> dados){List<Semana> r=new ArrayList<>();LocalDate fimMes=mes.atEndOfMonth();for(LocalDate inicio=mes.atDay(1);!inicio.isAfter(fimMes);){LocalDate seg=inicio.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),fim=seg.plusDays(6);if(fim.isAfter(fimMes))fim=fimMes;long a=0,c=0,f=0,x=0;for(LocalDate d=inicio;!d.isAfter(fim);d=d.plusDays(1)){long[] v=dados.getOrDefault(d,new long[4]);a+=v[0];c+=v[1];f+=v[2];x+=v[3];}r.add(new Semana(inicio,fim,a,c,f,x));inicio=fim.plusDays(1);}return r;}
    private List<Servico> ranking(Map<String,long[]> dados){return dados.entrySet().stream().map(e->new Servico(e.getKey(),e.getValue()[0],e.getValue()[1],e.getValue()[2])).sorted(Comparator.comparingLong(Servico::agendamentos).reversed().thenComparing(Servico::nome)).limit(10).toList();}
}
