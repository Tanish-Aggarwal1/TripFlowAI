package com.tripflow.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Bumped to invalidate every access token already issued to this user (M-7): the value is
     * embedded as a JWT claim at issuance and compared against this column on every request in
     * {@code JwtAuthFilter}, so a stale token is rejected without waiting for its own expiry.
     */
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;
}
