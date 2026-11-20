package mx.medicalminds.nexomed.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.config.AppPropiedades;

/**
 * Autenticación con JWT HS256 de vida corta (15 min): si no hay actividad, la sesión muere.
 * API sin estado. Los roles del perfil viajan en el token como autoridades ROLE_&lt;clave&gt;,
 * para usarse con @PreAuthorize("hasRole('USUARIOS_ADMINISTRAR')").
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadConfig {

    private static final String[] PUBLICAS = {
        "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health",
        // Interfaz estática: no contiene datos; todo dato se pide a la API con token.
        "/", "/index.html", "/css/**", "/js/**", "/img/**", "/favicon.ico"
    };

    private static final String CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; frame-ancestors 'self'";

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(PUBLICAS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertidorRoles())))
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                .frameOptions(f -> f.sameOrigin())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    private JwtAuthenticationConverter convertidorRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }

    @Bean
    SecretKey llaveJwt(AppPropiedades propiedades) {
        String secreto = propiedades.seguridad().jwtSecreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Configure JWT_SECRETO con al menos 32 caracteres");
        }
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey llaveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(llaveJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey llaveJwt) {
        return NimbusJwtDecoder.withSecretKey(llaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Solo valida usuario y contraseña; un usuario o perfil inactivo no entra. */
    @Bean
    UserDetailsService detallesUsuario(Db db) {
        return nombre -> db.uno("""
                        SELECT u.nombre_usuario, u.contrasena_hash, u.activo AND p.activo AS habilitado
                          FROM usuario u JOIN perfil p ON p.id = u.perfil_id
                         WHERE u.nombre_usuario = :u""", Map.of("u", nombre))
            .map(u -> User.withUsername((String) u.get("nombre_usuario"))
                .password((String) u.get("contrasena_hash"))
                .authorities("ROLE_USUARIO")
                .disabled(!habilitado(u.get("habilitado")))
                .build())
            .orElseThrow(() -> new UsernameNotFoundException(nombre));
    }

    private static boolean habilitado(Object v) {
        return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0;
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(uds);
        proveedor.setPasswordEncoder(encoder);
        return new ProviderManager(proveedor);
    }
}
