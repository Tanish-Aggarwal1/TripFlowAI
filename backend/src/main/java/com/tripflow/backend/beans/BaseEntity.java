package com.tripflow.backend.beans;

import java.time.Instant;

import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
    
    /**
     * Identity contract shared by all entities:
     * <ul>
     *   <li>Two instances are equal only when they are the same concrete entity type
     *       (Hibernate proxies unwrapped via {@link Hibernate#getClass}) AND share a
     *       non-null id.</li>
     *   <li>A transient entity (null id) is equal only to itself, so freshly created
     *       instances never collide inside a {@link java.util.Set}.</li>
     *   <li>{@code hashCode} is derived from the entity type, not the id, so it stays
     *       constant when a transient entity is persisted and its id is assigned —
     *       this is what keeps an entity findable in a Set/Map across persist.</li>
     * </ul>
     * Declared {@code final} so subclasses cannot silently break the contract.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        BaseEntity that = (BaseEntity) o;
        return getId() != null && getId().equals(that.getId());
    }
    
    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
    
}
