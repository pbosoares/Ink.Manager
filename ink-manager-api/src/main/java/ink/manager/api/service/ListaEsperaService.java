package ink.manager.api.service;

import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class ListaEsperaService {
    private static final String VERSAO_AVISO_PRIVACIDADE = "2026-10-18-v1";
    public record Pedido(@NotNull UUID chaveIdempotencia,@NotNull @Positive Long servicoId,@Positive Long profissionalId,
            @NotNull OffsetDateTime inicio,@NotNull OffsetDateTime fim,@NotBlank @Size(max=100) String nome,
            @NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,
            @NotBlank @Pattern(regexp="(?=(?:[^0-9]*[0-9]){8,})[0-9+() .-]{8,30}") String telefone,
            @AssertTrue boolean avisoPrivacidadeLido) {}
    public record EntradaCriada(boolean recebida,String mensagem) {}
    public record EntradaDono(Long id,String servico,String profissional,String nome,String email,String telefone,
            OffsetDateTime inicio,OffsetDateTime fim,String estado,Instant criadaEm) {}
    public record OfertaPublica(String servico,String profissional,LocalDate data,LocalTime horario,String fusoHorario,
            java.math.BigDecimal preco,int duracaoMinutos,Instant expiraEm) {}
    public record OfertaEmail(String linkPublico,String servico,String profissional,LocalDate data,LocalTime horario,
            String fusoHorario,java.math.BigDecimal preco,int duracaoMinutos,Instant expiraEm) {}
    public record ReservaAceita(Long reservaId,String servico,String profissional,LocalDate data,LocalTime horario,
            String fusoHorario,java.math.BigDecimal preco,int duracaoMinutos) {}

    private final EstudioRepository estudios; private final ListaEsperaRepository entradas;
    private final OfertaListaEsperaRepository ofertas; private final ServicoRepository servicos;
    private final ProfissionalRepository profissionais; private final MarcacaoRepository marcacoes;
    private final ClienteRepository clientes; private final DisponibilidadeService disponibilidade;
    private final AgendaGuard guard; private final AcessoAtual acesso; private final Clock clock;
    private final AgendamentoEmailService agendamentoEmail;
    private final EmailOutboxService outbox;

    public ListaEsperaService(EstudioRepository estudios,ListaEsperaRepository entradas,OfertaListaEsperaRepository ofertas,
            ServicoRepository servicos,ProfissionalRepository profissionais,MarcacaoRepository marcacoes,
            ClienteRepository clientes,DisponibilidadeService disponibilidade,AgendaGuard guard,AcessoAtual acesso,
            Clock clock,EmailOutboxService outbox,AgendamentoEmailService agendamentoEmail){
        this.estudios=estudios;this.entradas=entradas;this.ofertas=ofertas;this.servicos=servicos;this.profissionais=profissionais;
        this.marcacoes=marcacoes;this.clientes=clientes;this.disponibilidade=disponibilidade;this.guard=guard;
        this.acesso=acesso;this.clock=clock;this.outbox=outbox;this.agendamentoEmail=agendamentoEmail;
    }

    private Estudio porLink(String link){String id=PublicLinkCode.decode(link).orElse(link);return estudios.findByLinkPublicoAndEncerradoEmIsNull(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Link de agendamento nao encontrado."));}
    private ListaEspera entrada(long estudio,long id){return entradas.findByIdAndEstudioId(id,estudio).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Entrada da lista nao encontrada."));}
    private Servico servico(long estudio,long id){return servicos.findByIdAndEstudioId(id,estudio).filter(Servico::isAtivo).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Servico indisponivel."));}

    @Transactional public EntradaCriada inscrever(String link,Pedido pedido){
        Estudio estudio=porLink(link); guard.bloquear(estudio.getId());
        ListaEspera existente=entradas.findByEstudioIdAndChaveIdempotencia(estudio.getId(),pedido.chaveIdempotencia()).orElse(null);
        if(existente!=null){if(!mesmoPedido(existente,pedido))throw new ApiException(HttpStatus.CONFLICT,"Chave de pedido ja utilizada.");return new EntradaCriada(true,"Voce esta na lista de espera.");}
        Instant agora=clock.instant(); ZoneId zona=ZoneId.of(estudio.getFusoHorario());
        Instant inicio=pedido.inicio().toInstant(),fim=pedido.fim().toInstant();
        if(!fim.isAfter(inicio)||inicio.isBefore(agora.plus(Duration.ofMinutes(estudio.getAntecedenciaMinimaMinutos())))
                ||Duration.between(inicio,fim).compareTo(Duration.ofDays(90))>0
                ||inicio.isAfter(agora.plus(Duration.ofDays(365)))) throw new ApiException(HttpStatus.BAD_REQUEST,"Escolha um periodo futuro de ate 90 dias.");
        Servico servico=servico(estudio.getId(),pedido.servicoId());
        Profissional profissional=pedido.profissionalId()==null?null:disponibilidade.profissional(estudio.getId(),pedido.profissionalId());
        ListaEspera e=new ListaEspera();e.setEstudio(estudio);e.setServico(servico);e.setProfissional(profissional);
        e.setInicioDesejado(pedido.inicio());e.setFimDesejado(pedido.fim());e.setNome(pedido.nome().trim());
        e.setEmail(pedido.email().trim().toLowerCase(Locale.ROOT));e.setTelefone(pedido.telefone().trim());
        e.setChaveIdempotencia(pedido.chaveIdempotencia());e.setEstado(EstadoListaEspera.AGUARDANDO);e.setCriadoEm(agora);
        e.setAvisoPrivacidadeVersao(VERSAO_AVISO_PRIVACIDADE);e.setAvisoPrivacidadeLidoEm(agora);
        entradas.saveAndFlush(e);return new EntradaCriada(true,"Voce esta na lista de espera.");
    }

    private boolean mesmoPedido(ListaEspera e,Pedido p){return Objects.equals(e.getServico().getId(),p.servicoId())
        &&Objects.equals(e.getProfissional()==null?null:e.getProfissional().getId(),p.profissionalId())
        &&e.getInicioDesejado().toInstant().equals(p.inicio().toInstant())&&e.getFimDesejado().toInstant().equals(p.fim().toInstant())
        &&e.getNome().equals(p.nome().trim())&&e.getEmail().equals(p.email().trim().toLowerCase(Locale.ROOT))&&e.getTelefone().equals(p.telefone().trim());}

    public List<EntradaDono> listarDoDono(){long tenant=acesso.estudioId();return entradas.findByEstudioIdOrderByCriadoEmAscIdAsc(tenant).stream()
        .map(e->new EntradaDono(e.getId(),e.getServico().getNome(),e.getProfissional()==null?null:e.getProfissional().getNome(),e.getNome(),e.getEmail(),e.getTelefone(),e.getInicioDesejado(),e.getFimDesejado(),e.getEstado().name(),e.getCriadoEm())).toList();}

    @Transactional public void cancelarDoDono(long id){long tenant=acesso.estudioId();guard.bloquear(tenant);ListaEspera e=entrada(tenant,id);
        if(e.getEstado()==EstadoListaEspera.RESERVADA)throw new ApiException(HttpStatus.CONFLICT,"A lista ja gerou uma reserva.");
        if(e.getEstado()!=EstadoListaEspera.CANCELADA&&e.getEstado()!=EstadoListaEspera.EXPIRADA){e.setEstado(EstadoListaEspera.CANCELADA);
            Set<Long> vagas=new HashSet<>();
            ofertas.findByEstudioIdAndEntradaIdOrderByCriadoEmDesc(tenant,id).stream()
                .filter(o->o.getEstado()==EstadoOfertaListaEspera.OFERTADA||o.getEstado()==EstadoOfertaListaEspera.AGUARDANDO_ENVIO)
                .forEach(o->{o.setEstado(EstadoOfertaListaEspera.INVALIDADA);vagas.add(o.getMarcacaoCancelada().getId());
                    outbox.invalidate(tenant,"LISTA_ESPERA_OFERTA",Long.toString(o.getId()));});
            vagas.forEach(vaga->processarCancelamento(tenant,vaga));}
    }

    /** Called by the MarcacaoService cancellation transition, inside its transaction after AgendaGuard.bloquear. */
    @Transactional public void processarCancelamento(long estudioId,long marcacaoId){
        guard.bloquear(estudioId);Marcacao cancelada=marcacoes.findByIdAndEstudioId(marcacaoId,estudioId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Marcacao nao encontrada."));
        if(cancelada.getStatus()!=StatusMarcacao.CANCELADA) return;
        if(cancelada.getServico()==null)return;
        if(ofertas.existsByEstudioIdAndMarcacaoCanceladaIdAndEstado(estudioId,marcacaoId,EstadoOfertaListaEspera.AGUARDANDO_ENVIO)
                ||ofertas.existsByEstudioIdAndMarcacaoCanceladaIdAndEstado(estudioId,marcacaoId,EstadoOfertaListaEspera.OFERTADA))return;
        ZoneId zone=ZoneId.of(cancelada.getEstudio().getFusoHorario());ZonedDateTime slot=cancelada.getData().atTime(cancelada.getHorario()).atZone(zone);OffsetDateTime slotOffset=slot.toOffsetDateTime();
        for(ListaEspera e:entradas.candidatas(estudioId,cancelada.getServico().getId(),cancelada.getProfissional().getId(),slotOffset)){
            if(e.getEstado()!=EstadoListaEspera.AGUARDANDO)continue;
            long dur=e.getServico().getDuracaoMinutos();
            if(!cancelada.getData().atTime(cancelada.getHorario()).plusMinutes(dur).toLocalDate().equals(cancelada.getData()))continue;
            if(!cabeNaJanela(e,cancelada.getData(),cancelada.getHorario(),(int)dur))continue;
            if(e.getProfissional()!=null&&!Objects.equals(e.getProfissional().getId(),cancelada.getProfissional().getId()))continue;
            if(!disponibilidade.consultarEstudio(estudioId,cancelada.getProfissional().getId(),cancelada.getData(),(int)dur,null).horarios().contains(cancelada.getHorario()))continue;
            OfertaListaEspera o=new OfertaListaEspera();o.setEstudio(cancelada.getEstudio());o.setEntrada(e);o.setMarcacaoCancelada(cancelada);
            o.setData(cancelada.getData());o.setHorario(cancelada.getHorario());o.setEstado(EstadoOfertaListaEspera.AGUARDANDO_ENVIO);o.setCriadoEm(clock.instant());o=ofertas.saveAndFlush(o);
            e.setEstado(EstadoListaEspera.OFERTADA);
            String json="{\"ofertaId\":"+o.getId()+",\"listaEsperaId\":"+e.getId()+"}";
            outbox.enqueue(estudioId,e.getEmail(),"LISTA_ESPERA_OFERTA","lista-espera-oferta:"+o.getId(),clock.instant(),Long.toString(o.getId()),json);
            return;
        }
    }

    /** Revalida a vaga a cada tentativa e avanca a fila quando ela deixa de ser utilizavel. */
    @Transactional public Optional<OfertaEmail> prepararEmailOferta(long estudioId,long ofertaId,String tokenRaw,Instant agora){
        guard.bloquear(estudioId);
        OfertaListaEspera o=ofertas.bloquearPorEstudio(estudioId,ofertaId).orElse(null);
        if(o==null||(o.getEstado()!=EstadoOfertaListaEspera.AGUARDANDO_ENVIO&&o.getEstado()!=EstadoOfertaListaEspera.OFERTADA))return Optional.empty();
        ListaEspera e=o.getEntrada();Marcacao cancelada=o.getMarcacaoCancelada();Estudio estudio=o.getEstudio();
        ZoneId zone=ZoneId.of(estudio.getFusoHorario());LocalDateTime inicio=o.getData().atTime(o.getHorario());
        boolean utilizavel=e.getEstado()==EstadoListaEspera.OFERTADA&&cancelada.getStatus()==StatusMarcacao.CANCELADA
            &&e.getServico().isAtivo()&&cancelada.getProfissional().isAtivo()
            &&cabeNaJanela(e,o.getData(),o.getHorario(),e.getServico().getDuracaoMinutos())
            &&!inicio.isBefore(LocalDateTime.ofInstant(agora,zone).plusMinutes(estudio.getAntecedenciaMinimaMinutos()))
            &&disponibilidade.consultarEstudio(estudioId,cancelada.getProfissional().getId(),o.getData(),e.getServico().getDuracaoMinutos(),null).horarios().contains(o.getHorario());
        Instant prazo=inicio.atZone(zone).toInstant().minus(Duration.ofMinutes(estudio.getAntecedenciaMinimaMinutos()));
        Instant maximo=agora.plus(Duration.ofMinutes(30));if(maximo.isBefore(prazo))prazo=maximo;
        if(!utilizavel||!prazo.isAfter(agora)){
            encerrarOferta(o);processarCancelamento(estudioId,cancelada.getId());return Optional.empty();
        }
        o.setTokenHash(sha256(tokenRaw));o.setTokenPrazo(prazo);o.setEstado(EstadoOfertaListaEspera.OFERTADA);
        return Optional.of(new OfertaEmail(estudio.getLinkPublico(),e.getServico().getNome(),cancelada.getProfissional().getNome(),
            o.getData(),o.getHorario(),estudio.getFusoHorario(),e.getServico().getPreco(),e.getServico().getDuracaoMinutos(),prazo));
    }

    /** Promove o proximo candidato apos a expiracao, inclusive quando o link nunca e aberto. */
    @Transactional public int expirarOfertas(Instant agora){
        List<Long> ids=ofertas.encontrarExpiradasEmEstudiosAbertos(EstadoOfertaListaEspera.OFERTADA,agora)
            .stream().map(OfertaListaEspera::getId).toList();
        int expiradas=0;
        for(Long id:ids){
            OfertaListaEspera o=ofertas.findById(id).orElse(null);if(o==null)continue;
            long estudioId=o.getEstudio().getId();guard.bloquear(estudioId);
            o=ofertas.bloquearPorEstudio(estudioId,id).orElse(null);
            if(o==null||o.getEstado()!=EstadoOfertaListaEspera.OFERTADA||o.getTokenPrazo()==null||o.getTokenPrazo().isAfter(agora))continue;
            long vaga=o.getMarcacaoCancelada().getId();encerrarOferta(o);processarCancelamento(estudioId,vaga);expiradas++;
        }
        return expiradas;
    }

    private void encerrarOferta(OfertaListaEspera o){
        o.setEstado(EstadoOfertaListaEspera.EXPIRADA);
        if(o.getEntrada().getEstado()==EstadoListaEspera.OFERTADA)o.getEntrada().setEstado(EstadoListaEspera.EXPIRADA);
        outbox.invalidate(o.getEstudio().getId(),"LISTA_ESPERA_OFERTA",Long.toString(o.getId()));
    }

    @Transactional public void falhaFinalOferta(long estudioId,String referencia){
        final long ofertaId;
        try{ofertaId=Long.parseLong(referencia);}catch(RuntimeException invalid){return;}
        guard.bloquear(estudioId);
        OfertaListaEspera o=ofertas.bloquearPorEstudio(estudioId,ofertaId).orElse(null);
        if(o==null||(o.getEstado()!=EstadoOfertaListaEspera.AGUARDANDO_ENVIO&&o.getEstado()!=EstadoOfertaListaEspera.OFERTADA))return;
        long vaga=o.getMarcacaoCancelada().getId();encerrarOferta(o);processarCancelamento(estudioId,vaga);
    }
    private OfertaListaEspera ofertaPorToken(long tenant,String raw){return ofertas.findByEstudioIdAndTokenHash(tenant,sha256(raw)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Oferta indisponivel."));}
    public OfertaPublica consultarOferta(String link,String raw){Estudio e=porLink(link);OfertaListaEspera o=ofertaPorToken(e.getId(),raw);validarOfertaAberta(o);
        ListaEspera l=o.getEntrada();if(!cabeNaJanela(l,o.getData(),o.getHorario(),l.getServico().getDuracaoMinutos()))throw new ApiException(HttpStatus.GONE,"Oferta indisponivel.");
        return new OfertaPublica(l.getServico().getNome(),o.getMarcacaoCancelada().getProfissional().getNome(),o.getData(),o.getHorario(),e.getFusoHorario(),l.getServico().getPreco(),l.getServico().getDuracaoMinutos(),o.getTokenPrazo());}

    @Transactional public ReservaAceita aceitarOferta(String link,String raw){Estudio e=porLink(link);guard.bloquear(e.getId());OfertaListaEspera o=ofertaPorToken(e.getId(),raw);
        if(o.getEstado()==EstadoOfertaListaEspera.ACEITA&&o.getReserva()!=null)return resposta(o.getReserva(),e);
        validarOfertaAberta(o);ListaEspera l=o.getEntrada();if(l.getEstado()!=EstadoListaEspera.OFERTADA)throw new ApiException(HttpStatus.CONFLICT,"Oferta ja utilizada.");
        Servico s=servico(e.getId(),l.getServico().getId());Profissional p=disponibilidade.profissional(e.getId(),o.getMarcacaoCancelada().getProfissional().getId());
        LocalDateTime start=o.getData().atTime(o.getHorario());ZoneId zone=ZoneId.of(e.getFusoHorario());
        if(!cabeNaJanela(l,o.getData(),o.getHorario(),s.getDuracaoMinutos())
                ||start.isBefore(LocalDateTime.now(clock.withZone(zone)).plusMinutes(e.getAntecedenciaMinimaMinutos()))
                ||!disponibilidade.consultarEstudio(e.getId(),p.getId(),o.getData(),s.getDuracaoMinutos(),null).horarios().contains(o.getHorario()))
            throw new ApiException(HttpStatus.CONFLICT,"A vaga ja nao esta disponivel.");
        Cliente c=new Cliente();c.setEstudio(e);c.setNome(l.getNome());c.setEmail(l.getEmail());c.setTelefone(l.getTelefone());c=clientes.save(c);
        Marcacao m=new Marcacao(c,o.getData(),o.getHorario(),s.getNome(),StatusMarcacao.AGENDADA);m.setEstudio(e);m.setProfissional(p);m.setDuracaoMinutos(s.getDuracaoMinutos());m.definirServico(s);
        Marcacao reserva=marcacoes.saveAndFlush(m);o.setReserva(reserva);o.setEstado(EstadoOfertaListaEspera.ACEITA);l.setEstado(EstadoListaEspera.RESERVADA);outbox.invalidate(e.getId(),"LISTA_ESPERA_OFERTA",Long.toString(o.getId()));
        agendamentoEmail.sincronizar(reserva,null,null);
        return resposta(reserva,e);
    }
    private void validarOfertaAberta(OfertaListaEspera o){if(o.getEstado()!=EstadoOfertaListaEspera.OFERTADA||o.getTokenPrazo()==null||!o.getTokenPrazo().isAfter(clock.instant()))throw new ApiException(HttpStatus.GONE,"Oferta expirada ou encerrada.");}
    private boolean cabeNaJanela(ListaEspera entrada,LocalDate data,LocalTime horario,int duracaoMinutos){
        Instant inicio=data.atTime(horario).atZone(ZoneId.of(entrada.getEstudio().getFusoHorario())).toInstant();
        Instant fim=inicio.plus(Duration.ofMinutes(duracaoMinutos));
        return !inicio.isBefore(entrada.getInicioDesejado().toInstant())&&!fim.isAfter(entrada.getFimDesejado().toInstant());
    }
    private ReservaAceita resposta(Marcacao m,Estudio e){return new ReservaAceita(m.getId(),m.getServicoNome(),m.getProfissional().getNome(),m.getData(),m.getHorario(),e.getFusoHorario(),m.getPrecoPrevisto(),m.getDuracaoMinutos());}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
