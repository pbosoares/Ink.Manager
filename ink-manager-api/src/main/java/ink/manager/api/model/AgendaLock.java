package ink.manager.api.model;
import jakarta.persistence.*;
@Entity
@Table(name="agenda_lock")
public class AgendaLock {
 @Id private Integer id;
 protected AgendaLock() {}
}
