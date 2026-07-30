package com.tripflow.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A photo attached to a {@link Stop}. Only the Cloudinary-hosted URL (and,
 * for future delete support, the Cloudinary public id) live here — binaries
 * are never stored in this app; the client uploads directly to Cloudinary
 * using a signed request from {@code CloudinarySigningService}.
 */
@Entity
@Table(name = "stop_photos")
@Getter @Setter @NoArgsConstructor
public class StopPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "cloudinary_public_id", columnDefinition = "TEXT")
    private String cloudinaryPublicId;

    @Column(columnDefinition = "TEXT")
    private String caption;
}