package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "parking_facilities")
public class ParkingFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_name", nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "operating_start", nullable = false)
    private LocalTime operatingStart;

    @Column(name = "operating_end", nullable = false)
    private LocalTime operatingEnd;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    public ParkingFacility() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
    public LocalTime getOperatingStart() { return operatingStart; }
    public void setOperatingStart(LocalTime operatingStart) { this.operatingStart = operatingStart; }
    public LocalTime getOperatingEnd() { return operatingEnd; }
    public void setOperatingEnd(LocalTime operatingEnd) { this.operatingEnd = operatingEnd; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
}
