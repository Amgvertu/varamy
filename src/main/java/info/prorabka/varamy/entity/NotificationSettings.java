package info.prorabka.varamy.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "notify_on_response_to_my_ad", nullable = false)
    private boolean notifyOnResponseToMyAd = true;

    @Column(name = "notify_on_my_response_accepted", nullable = false)
    private boolean notifyOnMyResponseAccepted = true;

    @Column(name = "notify_new_ads_in_city", nullable = false)
    private boolean notifyNewAdsInCity = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_city_id")
    private City notificationCity;  // если null, используется home_city из профиля

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}