package eu.appbahn.platform.user.entity;

import eu.appbahn.platform.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(name = "oidc_subject_id", unique = true)
    private String oidcSubjectId;

    @Column(nullable = false)
    private String email;
}
