package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.config.RefreshTokenProperties;
import com.tripflow.backend.domain.RefreshToken;
import com.tripflow.backend.exception.InvalidRefreshTokenException;
import com.tripflow.backend.repository.RefreshTokenRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.JwtService;

/**
 * Lifecycle cases for the revocation half of the refresh-token flow: reuse detection with the
 * user-wide blast radius (D-03) and single-device logout (D-04).
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	private static final String RAW_TOKEN = "a-raw-refresh-token-value";
	private static final Long USER_ID = 42L;
	private static final Long TOKEN_ID = 7L;

	@Mock private RefreshTokenRepository refreshTokenRepository;
	@Mock private UserRepository userRepository;
	@Mock private JwtService jwtService;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository, jwtService,
				new RefreshTokenProperties(30, true, "None", "/api/auth"));
	}

	private RefreshToken storedToken() {
		RefreshToken token = new RefreshToken();
		token.setId(TOKEN_ID);
		token.setUserId(USER_ID);
		token.setTokenHash("irrelevant-the-service-hashes-the-input-itself");
		token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
		return token;
	}

	// ---------- D-03: reuse of a redeemed token is a compromise signal ----------

	@Test
	void rotate_replayOfAlreadyUsedToken_revokesEveryTokenForThatUserAndThrows() {
		RefreshToken used = storedToken();
		used.setUsedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));
		when(refreshTokenRepository.markUsed(eq(TOKEN_ID), any(Instant.class))).thenReturn(0);
		when(refreshTokenRepository.revokeAllForUser(eq(USER_ID), any(Instant.class))).thenReturn(3);

		assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, times(1)).revokeAllForUser(eq(USER_ID), any(Instant.class));
		verify(userRepository, times(1)).incrementTokenVersion(USER_ID);
	}

	@Test
	void rotate_replayOfAlreadyUsedToken_doesNotIssueAReplacement() {
		RefreshToken used = storedToken();
		used.setUsedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));
		when(refreshTokenRepository.markUsed(eq(TOKEN_ID), any(Instant.class))).thenReturn(0);
		when(refreshTokenRepository.revokeAllForUser(eq(USER_ID), any(Instant.class))).thenReturn(1);

		assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
		verify(jwtService, never()).generateToken(anyLong(), anyString(), anyInt());
	}

	@Test
	void rotate_losingAConcurrentRedemption_isTreatedAsReuseEvenThoughTheRowLookedUnused() {
		// The row read at the top of rotate() still has usedAt == null — the winning request
		// committed in between. Only the conditional UPDATE's rowcount can see that, which is
		// exactly why the check is not a read of the entity.
		RefreshToken looksUnused = storedToken();
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(looksUnused));
		when(refreshTokenRepository.markUsed(eq(TOKEN_ID), any(Instant.class))).thenReturn(0);
		when(refreshTokenRepository.revokeAllForUser(eq(USER_ID), any(Instant.class))).thenReturn(2);

		assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, times(1)).revokeAllForUser(eq(USER_ID), any(Instant.class));
		verify(jwtService, never()).generateToken(anyLong(), anyString(), anyInt());
	}

	@Test
	void rotate_revokedButNeverUsedToken_throwsWithoutMassRevoke() {
		// A revoked-but-unused token is a logged-out session, not a stolen one. Ordering the
		// revoked/expired checks before the used check is what keeps logout from looking like
		// a compromise signal.
		RefreshToken revoked = storedToken();
		revoked.setRevokedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

		assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(Instant.class));
		verify(userRepository, never()).incrementTokenVersion(anyLong());
	}

	@Test
	void rotate_expiredToken_throwsWithoutMassRevoke() {
		RefreshToken expired = storedToken();
		expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(Instant.class));
	}

	// ---------- D-04: logout ends exactly one session ----------

	@Test
	void revoke_stampsOnlyTheMatchingRowAndNeverMassRevokes() {
		RefreshToken live = storedToken();
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(live));

		refreshTokenService.revoke(RAW_TOKEN);

		ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository, times(1)).save(saved.capture());
		assertThat(saved.getValue()).isSameAs(live);
		assertThat(live.getRevokedAt()).isNotNull();
		verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(Instant.class));
		verify(userRepository, never()).incrementTokenVersion(anyLong());
	}

	@Test
	void revoke_looksUpTheHashNotTheRawToken() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		refreshTokenService.revoke(RAW_TOKEN);

		ArgumentCaptor<String> lookup = ArgumentCaptor.forClass(String.class);
		verify(refreshTokenRepository).findByTokenHash(lookup.capture());
		assertThat(lookup.getValue()).isNotEqualTo(RAW_TOKEN).hasSize(64);
	}

	@Test
	void revoke_unknownToken_isANoOpAndDoesNotThrow() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatCode(() -> refreshTokenService.revoke(RAW_TOKEN)).doesNotThrowAnyException();

		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	@Test
	void revoke_alreadyRevokedOrExpiredToken_isANoOpAndDoesNotThrow() {
		RefreshToken alreadyRevoked = storedToken();
		Instant originalRevokedAt = Instant.now().minus(2, ChronoUnit.HOURS);
		alreadyRevoked.setRevokedAt(originalRevokedAt);
		RefreshToken expired = storedToken();
		expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
		when(refreshTokenRepository.findByTokenHash(anyString()))
				.thenReturn(Optional.of(alreadyRevoked), Optional.of(expired));

		assertThatCode(() -> refreshTokenService.revoke(RAW_TOKEN)).doesNotThrowAnyException();
		assertThatCode(() -> refreshTokenService.revoke(RAW_TOKEN)).doesNotThrowAnyException();

		assertThat(alreadyRevoked.getRevokedAt()).isEqualTo(originalRevokedAt);
		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}
}
