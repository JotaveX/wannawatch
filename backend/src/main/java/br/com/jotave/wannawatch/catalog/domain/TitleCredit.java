package br.com.jotave.wannawatch.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "title_credit")
public class TitleCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id")
    private Title title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false)
    private CreditType creditType;

    private String character;

    private String job;

    private String department;

    @Column(name = "credit_order", nullable = false)
    private int creditOrder;

    protected TitleCredit() {}

    public TitleCredit(Title title, Person person, CreditType creditType,
                       String character, String job, String department, int creditOrder) {
        this.title = title;
        this.person = person;
        this.creditType = creditType;
        this.character = character;
        this.job = job;
        this.department = department;
        this.creditOrder = creditOrder;
    }

    public Long getId() { return id; }
    public Title getTitle() { return title; }
    public Person getPerson() { return person; }
    public CreditType getCreditType() { return creditType; }
    public String getCharacter() { return character; }
    public String getJob() { return job; }
    public String getDepartment() { return department; }
    public int getCreditOrder() { return creditOrder; }
}
