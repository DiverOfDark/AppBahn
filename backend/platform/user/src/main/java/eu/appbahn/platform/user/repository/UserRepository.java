package eu.appbahn.platform.user.repository;

import eu.appbahn.platform.user.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByOidcSubjectId(String oidcSubjectId);

    Optional<UserEntity> findByEmail(String email);
}
