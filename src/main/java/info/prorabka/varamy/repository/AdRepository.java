package info.prorabka.varamy.repository;

import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.entity.Response;
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

    Page<Ad> findByAuthor(User author, Pageable pageable);

    @Query("SELECT DISTINCT a FROM Ad a LEFT JOIN a.levels l WHERE " +
            "(:cityId IS NULL OR a.city.id = :cityId) AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:subType IS NULL OR a.subType = :subType) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:level IS NULL OR l IN :level) AND " +
            "(:authorId IS NULL OR a.author.id = :authorId)")
    Page<Ad> findWithFilters(
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
    Page<Ad> findAllActive(
            @Param("type") Integer type,
            @Param("subType") Integer subType,
            @Param("level") List<String> level,
            Pageable pageable);

    @Query("SELECT DISTINCT a FROM Ad a LEFT JOIN a.levels l WHERE " +
            "a.status IN :statuses AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:subType IS NULL OR a.subType = :subType) AND " +
            "(:level IS NULL OR l IN :level)")
    Page<Ad> findMainPageAds(@Param("statuses") List<Ad.AdStatus> statuses,
                             @Param("type") Integer type,
                             @Param("subType") Integer subType,
                             @Param("level") List<String> level,
                             Pageable pageable);


    Page<Ad> findByStatus(Ad.AdStatus status, Pageable pageable);

    @Query("SELECT a FROM Ad a WHERE a.endTime < :endTime AND a.status != :status")
    List<Ad> findByEndTimeBeforeAndStatusNot(@Param("endTime") LocalDateTime endTime, @Param("status") Ad.AdStatus status);
}