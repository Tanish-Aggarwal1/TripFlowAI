package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.beans.User;
import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT {

	@Autowired
	private UserRepository userRepository;

	private User newUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashedpassword123");
        return user;
    }

    @Test
    void saveAndFindById() {
        User saved = userRepository.save(newUser("testuser", "test@tripflow.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(userRepository.findById(saved.getId())).isPresent();
    }

    // ---------- findByEmail ----------

    @Test
    void findByEmail_existingUser_returnsUser() {
        userRepository.save(newUser("tanish", "tanish@tripflow.com"));

        assertThat(userRepository.findByEmail("tanish@tripflow.com"))
                .isPresent()
                .get()
                .extracting(User::getUsername)
                .isEqualTo("tanish");
    }

    @Test
    void findByEmail_missingUser_returnsEmpty() {
        assertThat(userRepository.findByEmail("ghost@tripflow.com")).isEmpty();
    }

    @Test
    void findByEmail_isCaseSensitive() {
        // Documents current behavior: Postgres text equality is case-sensitive by
        // default, so this differs from RegisterRequest's @Email validation which
        // doesn't normalize case either. If login-by-email is ever meant to be
        // case-insensitive, this test should be the one that's updated.
        userRepository.save(newUser("neel", "neel@tripflow.com"));

        assertThat(userRepository.findByEmail("Neel@TripFlow.com")).isEmpty();
    }

    // ---------- findByUsername ----------

    @Test
    void findByUsername_existingUser_returnsUser() {
        userRepository.save(newUser("pratham", "pratham@tripflow.com"));

        assertThat(userRepository.findByUsername("pratham"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("pratham@tripflow.com");
    }

    @Test
    void findByUsername_missingUser_returnsEmpty() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    // ---------- existsByEmail ----------

    @Test
    void existsByEmail_userPresent_returnsTrue() {
        userRepository.save(newUser("joann", "joann@tripflow.com"));

        assertThat(userRepository.existsByEmail("joann@tripflow.com")).isTrue();
    }

    @Test
    void existsByEmail_userAbsent_returnsFalse() {
        assertThat(userRepository.existsByEmail("nobody@tripflow.com")).isFalse();
    }

    // ---------- existsByUsername ----------

    @Test
    void existsByUsername_userPresent_returnsTrue() {
        userRepository.save(newUser("joann", "joann@tripflow.com"));

        assertThat(userRepository.existsByUsername("joann")).isTrue();
    }

    @Test
    void existsByUsername_userAbsent_returnsFalse() {
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }

    // ---------- DB-level uniqueness ----------
    // AuthService.register() checks existsByEmail/existsByUsername first, but that
    // check-then-act is racy under concurrent requests. These pin the DB-level
    // @Column(unique = true) constraints on User as the real safety net — the same
    // pattern used for Place.externalPlaceId dedup (REF T20).

    @Test
    void save_duplicateEmail_violatesUniqueConstraint() {
        userRepository.saveAndFlush(newUser("first", "dup@tripflow.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("second", "dup@tripflow.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_duplicateUsername_violatesUniqueConstraint() {
        userRepository.saveAndFlush(newUser("dupuser", "first@tripflow.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dupuser", "second@tripflow.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
