package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.domain.User;
import com.tripflow.backend.dto.ProfileResponse;
import com.tripflow.backend.dto.UpdateInterestsRequest;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

	@Mock private UserRepository userRepository;

	private ProfileService profileService;

	@BeforeEach
	void setUp() {
		profileService = new ProfileService(userRepository);
	}

	private User user(Long id, List<String> interests) {
		User user = new User();
		user.setId(id);
		user.setUsername("user" + id);
		user.setEmail("user" + id + "@example.com");
		user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		user.setInterests(interests);
		return user;
	}

	@Test
	void getProfile_existingUser_mapsToResponse() {
		User user = user(1L, List.of("hiking"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		ProfileResponse response = profileService.getProfile(1L);

		assertThat(response).isEqualTo(new ProfileResponse(1L, "user1", user.getCreatedAt(), List.of("hiking")));
	}

	@Test
	void getProfile_missingUser_throwsNotFound() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.getProfile(999L))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateInterests_replacesWholesale() {
		User user = user(1L, List.of("old-a", "old-b"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		ProfileResponse response = profileService.updateInterests(1L, new UpdateInterestsRequest(List.of("new-a")));

		assertThat(response.interests()).containsExactly("new-a");
		assertThat(user.getInterests()).containsExactly("new-a");
	}

	@Test
	void updateInterests_emptyArray_clearsPreviouslyStoredInterests() {
		User user = user(1L, List.of("hiking", "food"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		ProfileResponse response = profileService.updateInterests(1L, new UpdateInterestsRequest(List.of()));

		assertThat(response.interests()).isEmpty();
		assertThat(user.getInterests()).isEmpty();
	}

	@Test
	void updateInterests_missingUser_throwsNotFound() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.updateInterests(999L, new UpdateInterestsRequest(List.of())))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
