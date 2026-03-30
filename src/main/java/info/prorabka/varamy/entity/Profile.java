package info.prorabka.varamy.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "level", length = 1)
    private String level;

    @Column(name = "number")
    private Integer number;

    @Column(name = "team", length = 100)
    private String team;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_country_id")
    private Country homeCountry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_region_id")
    private Region homeRegion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_city_id")
    private City homeCity;
}