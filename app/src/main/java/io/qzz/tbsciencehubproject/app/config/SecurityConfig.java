package io.qzz.tbsciencehubproject.app.config;

import io.qzz.tbsciencehubproject.user.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration integrating e-signing with existing authentication.
 * Provides methods to retrieve the currently authenticated user for signing operations.
 */
@Configuration
public class SecurityConfig {
    
    /**
     * Configure security filter chain - customize based on your existing auth setup
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Default configuration - customize based on your existing authentication
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/articles/**").authenticated()
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
    
    /**
     * Get the currently authenticated user from Spring Security context.
     * Integrates with existing authentication system - no duplication.
     * 
     * @return User object representing the authenticated user
     * @throws IllegalStateException if no user is authenticated
     */
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        
        Object principal = authentication.getPrincipal();
        
        // If principal is already a User, return it
        if (principal instanceof User) {
            return (User) principal;
        }
        
        // If principal is a String (username), create a simple User wrapper
        if (principal instanceof String) {
            String username = (String) principal;
            return () -> username;
        }
        
        // If principal has a name/username method via reflection
        try {
            java.lang.reflect.Method nameMethod = principal.getClass().getMethod("getName");
            String username = (String) nameMethod.invoke(principal);
            return () -> username;
        } catch (Exception e) {
            // Fallback to toString
            return () -> principal.toString();
        }
    }
    
    /**
     * Check if current user is authenticated
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
    
    /**
     * Get current user ID safely (returns null if not authenticated)
     */
    public static String getCurrentUserIdOrNull() {
        try {
            return getCurrentUser().name();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
