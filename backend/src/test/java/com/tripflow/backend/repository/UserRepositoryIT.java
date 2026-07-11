package com.tripflow.backend.repository;

import com.tripflow.backend.beans.User;
import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT {

	@Autowired
	private UserRepository userRepository;

	@Test
	void saveAndFindById() {
		User user = new User();
		user.setUsername("testuser");
		user.setEmail("test@tripflow.com");
		user.setPasswordHash("hashedpassword123");

		User saved = userRepository.save(user);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(userRepository.findById(saved.getId())).isPresent();
	}
}
