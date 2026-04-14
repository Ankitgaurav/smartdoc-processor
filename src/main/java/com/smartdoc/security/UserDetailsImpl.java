package com.smartdoc.security;


import com.smartdoc.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter between our User entity and Spring Security.
 *
 * Why this exists:
 * Spring Security doesn't know about our User entity.
 * It only understands UserDetails interface.
 * This class bridges the two — wraps our User and
 * implements UserDetails so Spring Security can use it.
 *
 * Interview point: Why not implement UserDetails directly on User entity?
 * Because it would couple your domain model to a framework.
 * If you ever switch security frameworks, your User entity breaks.
 * Separation of concerns — keep domain clean.
 */
@Getter
public class UserDetailsImpl implements UserDetails {
    private final long id;
    private final String username;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    private UserDetailsImpl(User user){
        this.id =user.getId();
        this.username=user.getUsername();
        this.email=user.getEmail();
        this.password=user.getPassword();
        this.enabled= user.isEnabled();

        // "ROLE_" prefix is required by Spring Security
        // Role.USER  → "ROLE_USER"
        // Role.ADMIN → "ROLE_ADMIN"

        this.authorities = List.of(new SimpleGrantedAuthority("ROLE" + user.getRole().name()));
    }
    // Factory method — cleaner than calling constructor directly
    public static UserDetailsImpl build(User user) {
        return new UserDetailsImpl(user);
    }
    // These 3 methods control account locking/expiry features
    // We're keeping them simple — always return true
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

}
