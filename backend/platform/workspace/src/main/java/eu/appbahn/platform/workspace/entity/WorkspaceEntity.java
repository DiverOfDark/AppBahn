package eu.appbahn.platform.workspace.entity;

import eu.appbahn.platform.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workspace")
public class WorkspaceEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 18)
    private String slug;

    @Column(columnDefinition = "jsonb")
    private String quota;

    @Column(name = "runtime_class_name")
    private String runtimeClassName;

    @Column(columnDefinition = "jsonb")
    private String registry;
}
