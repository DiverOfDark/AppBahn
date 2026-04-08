package eu.appbahn.platform.user.repository;

import eu.appbahn.platform.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByOidcSubjectId(String oidcSubjectId);

    Optional<UserEntity> findByEmail(String email);
}
