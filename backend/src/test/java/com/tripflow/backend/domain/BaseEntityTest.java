package com.tripflow.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.domain.BaseEntity;

class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    static class OtherEntity extends BaseEntity {
    }

    private TestEntity entityWithId(Long id) {
        TestEntity e = new TestEntity();
        e.setId(id);
        return e;
    }

    @Test
    void transientInstancesAreEqualOnlyToThemselves() {
        TestEntity a = new TestEntity();
        TestEntity b = new TestEntity();

        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sameTypeSameIdAreEqualAndShareHashCode() {
        TestEntity a = entityWithId(1L);
        TestEntity b = entityWithId(1L);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void sameTypeDifferentIdAreNotEqual() {
        assertThat(entityWithId(1L)).isNotEqualTo(entityWithId(2L));
    }

    @Test
    void differentTypesWithSameIdAreNotEqual() {
        TestEntity a = entityWithId(1L);
        OtherEntity b = new OtherEntity();
        b.setId(1L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCodeIsStableAcrossIdAssignment() {
        TestEntity e = new TestEntity();
        int before = e.hashCode();
        e.setId(99L);
        int after = e.hashCode();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void setMembershipSurvivesPersist() {
        TestEntity e = new TestEntity();
        Set<BaseEntity> set = new HashSet<>();
        set.add(e);

        e.setId(42L);

        assertThat(set).contains(e);
        assertThat(set).contains(entityWithId(42L));
    }
}