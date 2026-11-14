package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "smartpark_settings")
public class SmartParkSettings {

    @Id
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int id = 1;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "owner_registration_enabled", nullable = false)
    private boolean ownerRegistrationEnabled = true;

    @Column(name = "vehicle_types", nullable = false, length = 500)
    private String vehicleTypes = "Two-wheeler,Four-wheeler,Electric vehicle";

    @Column(name = "cancellation_enabled", nullable = false)
    private boolean cancellationEnabled = true;

    @Column(name = "cancellation_cutoff_minutes", nullable = false)
    private int cancellationCutoffMinutes;

    @Column(name = "refund_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercentage = new BigDecimal("100.00");

    protected SmartParkSettings() {
    }

    public int getId() { return id; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public boolean isOwnerRegistrationEnabled() { return ownerRegistrationEnabled; }
    public void setOwnerRegistrationEnabled(boolean enabled) { this.ownerRegistrationEnabled = enabled; }
    public String getVehicleTypes() { return vehicleTypes; }
    public void setVehicleTypes(String vehicleTypes) { this.vehicleTypes = vehicleTypes; }
    public boolean isCancellationEnabled() { return cancellationEnabled; }
    public void setCancellationEnabled(boolean enabled) { this.cancellationEnabled = enabled; }
    public int getCancellationCutoffMinutes() { return cancellationCutoffMinutes; }
    public void setCancellationCutoffMinutes(int minutes) { this.cancellationCutoffMinutes = minutes; }
    public BigDecimal getRefundPercentage() { return refundPercentage; }
    public void setRefundPercentage(BigDecimal percentage) { this.refundPercentage = percentage; }
}
