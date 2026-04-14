package com.smartdoc.service;

import com.smartdoc.dto.*;
import com.smartdoc.entity.*;
import com.smartdoc.exception.DuplicateResourceException;
import com.smartdoc.repository.UserRepository;
import com.smartdoc.security.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login.
 *
 * register() → validates uniqueness → saves user → returns JWT
 * login()    → delegates to AuthenticationManager → returns JWT
 *
 * Interview point: Why use AuthenticationManager for login
 * instead of checking password manually?
 * AuthenticationManager is Spring Security's central auth entry point.
 * It handles wrong password, disabled account, locked account etc.
 * automatically. Using it means we get all those checks for free.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Check uniqueness before saving
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException(
                    "Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Email '" + request.getEmail() + "' is already registered");
        }

        // Build and save user — password is hashed here
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getUsername());

        // Generate token immediately so user is logged in after registering
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String token = jwtUtils.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtUtils.getExpirationMs())
                .build();
    }

    public AuthResponse login(LoginRequest request) {

        // This line does ALL of: load user, check password, check enabled
        // Throws BadCredentialsException if anything is wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetailsImpl userDetails = (UserDetailsImpl)
                userDetailsService.loadUserByUsername(request.getUsername());

        String token = jwtUtils.generateToken(userDetails);
        log.info("User logged in: {}", request.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(userDetails.getUsername())
                .role(userDetails.getAuthorities()
                        .iterator().next()
                        .getAuthority()
                        .replace("ROLE_", ""))
                .expiresIn(jwtUtils.getExpirationMs())
                .build();
    }
}