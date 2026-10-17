package ink.manager.api.service;
import java.time.LocalTime;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;
import org.springframework.stereotype.Service;
@Service
public class InicializacaoEstudio {
    private final ProfissionalRepository profissionais;
    private final HorarioTrabalhoRepository horarios;
    public InicializacaoEstudio(ProfissionalRepository profissionais,HorarioTrabalhoRepository horarios){this.profissionais=profissionais;this.horarios=horarios;}
    public void criarAgenda(Estudio estudio){
        var profissional=new Profissional(); profissional.setEstudio(estudio); profissional.setNome("Agenda principal"); profissional=profissionais.save(profissional);
        for(int dia=1;dia<=6;dia++) horarios.save(new HorarioTrabalho(profissional,dia,LocalTime.of(9,0),LocalTime.of(18,0)));
    }
}
