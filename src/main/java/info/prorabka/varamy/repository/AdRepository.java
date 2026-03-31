package info.prorabka.varamy.repository;

import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdRepository extends JpaRepository<Ad, UUID> {

    // ============= ПУБЛИЧНЫЕ МЕТОДЫ (исключают ARCHIVED по умолчанию) =============

    @Query("SELECT DISTINCT a FROM Ad a LEFT JOIN a.levels l WHERE " +
            "a.status != 'ARCHIVED' AND " +
            "(:cityId IS NULL OR a.city.id = :cityId) AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:subType IS NULL OR a.subType = :subType) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:level IS NULL OR l IN :level) AND " +
            "(:authorId IS NULL OR a.author.id = :authorId)")
    Page<Ad> findWithFiltersPublic(
            @Param("cityId") Long cityId,
            @Param("type") Integer type,
            @Param("subType") Integer subType,
            @Param("status") Ad.AdStatus status,
            @Param("level") List<String> level,
            @Param("authorId") UUID authorId,
            Pageable pageable);

    @Query("SELECT DISTINCT a FROM Ad a LEFT JOIN a.levels l WHERE " +
            "a.status = 'ACTIVE' AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:subType IS NULL OR a.subType = :subType) AND " +
            "(:level IS NULL OR l IN :level)")
    Page<Ad> findAllActivePublic(
            @Param("type") Integer type,
            @Param("subType") Integer subType,
            @Param("level") List<String> level,
            Pageable pageable);

    @Query("SELECT DISTINCT a FROM Ad a LEFT JOIN a.levels l WHERE " +
            "a.status IN :statuses AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:subType IS NULL OR a.subType = :subType) AND " +
            "(:level IS NULL OR l IN :level)")
    Page<Ad> findMainPageAdsPublic(
            @Param("statuses") List<Ad.AdStatus> statuses,
            @Param("type") Integer type,
            @Param("subType") Integer subType,
            @Param("level") List<String> level,
            Pageable pageable);

    // ============= АДМИНИСТРАТИВНЫЕ МЕТОДЫ (могут включать ARCHIVED) =============

    @Query("SELECT DISTINCT a FROM Ad a LEFT JOIN a.levels l WHERE " +
            "(:cityId IS NULL OR a.city.id = :cityId) AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:subType IS NULL OR a.subType = :subType) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:level IS NULL OR l IN :level) AND " +
            "(:authorId IS NULL OR a.author.id = :authorId)")
    Page<Ad> findWithFiltersAdmin(
            @Param("cityId") Long cityId,
            @Param("type") Integer type,
            @Param("subType") Integer subType,
            @Param("status") Ad.AdStatus status,
            @Param("level") List<String> level,
            @Param("authorId") UUID authorId,
            Pageable pageable);

    // ============= МЕТОДЫ ДЛЯ АВТОРА (показывают все его объявления) =============

    @Query("SELECT a FROM Ad a WHERE a.author = :author ORDER BY a.createdAt DESC")
    Page<Ad> findByAuthor(@Param("author") User author, Pageable pageable);

    // ============= МЕТОДЫ ДЛЯ МОДЕРАЦИИ =============

    Page<Ad> findByStatus(Ad.AdStatus status, Pageable pageable);

    // ============= МЕТОДЫ ДЛЯ АРХИВАЦИИ И ОЧИСТКИ =============

    @Query("SELECT a FROM Ad a WHERE a.endTime < :endTime AND a.status != :status")
    List<Ad> findByEndTimeBeforeAndStatusNot(
            @Param("endTime") LocalDateTime endTime,
            @Param("status") Ad.AdStatus status);

    @Query("SELECT a FROM Ad a WHERE a.status = :status AND a.createdAt < :date")
    List<Ad> findByStatusAndCreatedAtBefore(
            @Param("status") Ad.AdStatus status,
            @Param("date") LocalDateTime date);

    long countByStatus(Ad.AdStatus status);
}