package dev.network.member.profile;

import jakarta.persistence.*;

@Entity
@Table(name = "experiences")
public class Experience {
    @Id
    public String id;

    @Column(nullable = false)
    public String memberId;

    @Column(nullable = false, length = 100)
    public String company;

    @Column(nullable = false, length = 100)
    public String title;

    @Column(nullable = false, length = 7)
    public String startMonth;

    @Column(length = 7)
    public String endMonth;

    public int position;

    protected Experience() {
    }
}
