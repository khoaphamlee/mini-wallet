package com.khoaphamlee.wallet.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A registered user, mapped to the {@code users} table created by migration V1.
 *
 * <p>Hibernate only validates this mapping against the schema ({@code ddl-auto: validate}); the
 * table itself is owned by Flyway. Column types must therefore match exactly, for example
 * {@code short} for the {@code SMALLINT} column.
 *
 * <p>Lombok generates the getters. There are deliberately no setters: state changes go through
 * methods with a business meaning (such as {@link #register}, and later {@code changePin}). Avoid
 * {@code @Data} on entities: its {@code equals}, {@code hashCode} and {@code toString} misbehave with
 * JPA proxies and would print the password hash.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; application code uses register()
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(name = "pin_failed_attempts", nullable = false)
    private short pinFailedAttempts;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** A new active customer without a PIN. The values must already be normalised and the password hashed. */
    public static User register(String email, String phone, String fullName, String passwordHash) {
        User user = new User();
        user.email = email;
        user.phone = phone;
        user.fullName = fullName;
        user.passwordHash = passwordHash;
        user.role = Role.CUSTOMER;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isPinSet() {
        return pinHash != null;
    }
}
