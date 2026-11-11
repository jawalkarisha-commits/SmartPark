package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_groups", uniqueConstraints = @UniqueConstraint(
    name = "uk_booking_group_reference", columnNames = "group_reference"
))
public class BookingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_reference", nullable = false, length = 36)
    private String groupReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BookingGroup() {
    }

    public BookingGroup(User user) {
        this.user = user;
    }

    @PrePersist
    void initializePersistentFields() {
        if (groupReference == null) groupReference = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getGroupReference() { return groupReference; }
    public User getUser() { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
