package info.prorabka.varamy.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "responses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id", nullable = false)
    private Ad ad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResponseStatus status = ResponseStatus.PENDING;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ResponseStatus {
        PENDING, APPROVED, REJECTED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "response_role")
    private ResponseRole responseRole;

    public enum ResponseRole {
        GOALIE, DEFENDER, FORWARD
    }
}
