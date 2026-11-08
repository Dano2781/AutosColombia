package co.autoscolombia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }


    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername("operario").password(encoder.encode("Acceso123!")).roles("OPERARIO").build(),
            User.withUsername("supervisor").password(encoder.encode("Supervisor123!")).roles("SUPERVISOR").build()
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a
                    .requestMatchers("/login", "/css/**").permitAll()
                    .requestMatchers("/movimientos").hasRole("SUPERVISOR")
                    .requestMatchers("/usuarios/nuevo", "/usuarios/guardar", "/usuarios/*/editar",
                                     "/usuarios/*/estado", "/celdas/nueva", "/celdas/guardar",
                                     "/celdas/*/editar", "/celdas/*/estado").hasRole("SUPERVISOR")
                    .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
