package io.github.jespersm.jpa.tripwire.unselectinator.hibernate.enhanced;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EnhancedTarget {
    @Id
    private Long id;
    private String label;

    public void setId(Long id) { this.id = id; }
    public void setLabel(String label) { this.label = label; }
    public String getLabel() { return label; }
}
