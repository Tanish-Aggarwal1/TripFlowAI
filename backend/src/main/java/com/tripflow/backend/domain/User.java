package com.tripflow.backend.domain;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * Free-text profile interests (SOCIAL-05, D-07) — the same {@code @JdbcTypeCode}/{@code TEXT[]}
     * pair as {@code Trip.tags}, so D-05's feed ranking can overlap-match the two columns with a
     * single Postgres {@code &&} operator and no join. Initialized here so a freshly-constructed
     * {@code User} never carries null.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
    private List<String> interests = new ArrayList<>();
}
