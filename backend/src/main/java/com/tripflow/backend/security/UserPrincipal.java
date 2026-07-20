package com.tripflow.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Typed authentication principal, replacing the raw Long principal + AuthUtils
 * string-parsing seam. Set by JwtAuthFilter, resolved in controllers via
 * @AuthenticationPrincipal UserPrincipal.
 *
 * No roles/authorities exist in the app yet — getAuthorities() returns an
 * empty list. Revisit when SCRUM-71 (public/community endpoints) needs them.
 */
public record UserPrincipal(Long userId, String email) implements UserDetails {

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}