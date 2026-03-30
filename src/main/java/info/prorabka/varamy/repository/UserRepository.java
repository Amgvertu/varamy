package info.prorabka.varamy.repository;

import info.prorabka.varamy.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u WHERE " +
            "(:phone IS NULL OR u.phone LIKE %:phone%) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.status = :status)")
    Page<User> findWithFilters(
            @Param("phone") String phone,
            @Param("role") User.UserRole role,
            @Param("status") User.UserStatus status,
            Pageable pageable);
}
