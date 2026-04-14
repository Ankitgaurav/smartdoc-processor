package com.smartdoc.security;

import com.smartdoc.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Tells Spring Security HOW to load a user from our database.
 *
 * Spring Security calls loadUserByUsername() automatically
 * during the authentication process. We just need to fetch
 * the user from DB and wrap it in UserDetailsImpl.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException{
        log.debug("Loading user by username: {}", username);
        return userRepository.findByUsername(username)
                .map(UserDetailsImpl::build)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));
    }


}
