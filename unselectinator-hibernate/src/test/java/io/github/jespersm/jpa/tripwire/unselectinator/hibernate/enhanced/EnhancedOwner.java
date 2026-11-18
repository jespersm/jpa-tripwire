package io.github.jespersm.jpa.tripwire.unselectinator.hibernate.enhanced;

import jakarta.persistence.*;

@Entity
public class EnhancedOwner {
    @Id
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private EnhancedTarget target;

    public void setId(Long id) { this.id = id; }
    public void setTarget(EnhancedTarget target) { this.target = target; }
    public EnhancedTarget getTarget() { return target; }
}
