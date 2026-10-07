package com.omnichannel.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "shipping_addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String line1;

    private String ward;
    private String district;

    @Column(nullable = false)
    private String city;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected Address() {
    }

    public Address(UUID userId, String receiverName, String phone, String line1,
                   String ward, String district, String city, boolean defaultAddress) {
        this.userId = userId;
        this.receiverName = receiverName;
        this.phone = phone;
        this.line1 = line1;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.defaultAddress = defaultAddress;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getReceiverName() { return receiverName; }
    public String getPhone() { return phone; }
    public String getLine1() { return line1; }
    public String getWard() { return ward; }
    public String getDistrict() { return district; }
    public String getCity() { return city; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
