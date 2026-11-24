package br.com.jotave.wannawatch.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private Integer externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "profile_path")
    private String profilePath;

    @Column(name = "known_for_department")
    private String knownForDepartment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Person() {}

    public Person(Integer externalId) {
        this.externalId = externalId;
    }

    public void update(String name, String profilePath, String knownForDepartment) {
        this.name = name;
        this.profilePath = profilePath;
        this.knownForDepartment = knownForDepartment;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Integer getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getProfilePath() { return profilePath; }
    public String getKnownForDepartment() { return knownForDepartment; }
}
