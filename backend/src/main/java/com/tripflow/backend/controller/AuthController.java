package com.tripflow.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.AuthResponse;
import com.tripflow.backend.dto.LoginRequest;
import com.tripflow.backend.dto.RegisterRequest;

import jakarta.validation.Valid;

/**
 * STUB — Story 2 scaffolding only.
 * Fake tokens so Neel (Story 3) can build the login/register flow against
 * a stable contract. Pratham: replace internals with real validation
 * against User entity + real JWT issuance. Don't rename fields without
 * flagging Neel — frontend will bind directly to this shape.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // TODO(Pratham): validate credentials, issue real signed JWT
        return ResponseEntity.ok(new AuthResponse(
                "stub-token-" + request.email(), "Bearer", 1L, request.email()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // TODO(Pratham): persist User, hash password, issue real signed JWT
        return ResponseEntity.ok(new AuthResponse(
                "stub-token-" + request.email(), "Bearer", 1L, request.username()
        ));
    }
}
