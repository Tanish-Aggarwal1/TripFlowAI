package com.tripflow.backend.service;

import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.User;
import com.tripflow.backend.dto.ProfileResponse;
import com.tripflow.backend.dto.UpdateInterestsRequest;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SOCIAL-05 (D-07): profile reads/writes are always scoped to the caller's own id, resolved
 * by the controller from {@code UserPrincipal}. No timing-oracle defense is needed here unlike
 * {@link AuthService#login}: the caller is already authenticated as this exact user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return toResponse(loadUser(userId));
    }

    // Replace-wholesale, not a delta merge: the caller always sends the full resulting array
    // (T-06-05-03 keeps username/joinedAt structurally unreachable through this endpoint).
    @Transactional
    public ProfileResponse updateInterests(Long userId, UpdateInterestsRequest request) {
        User user = loadUser(userId);
        user.setInterests(new ArrayList<>(request.interests()));
        log.info("Profile interests updated userId={} count={}", userId, user.getInterests().size());
        return toResponse(user);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.getId(), user.getUsername(), user.getCreatedAt(), user.getInterests());
    }
}
