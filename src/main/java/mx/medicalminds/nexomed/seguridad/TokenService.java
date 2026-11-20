package mx.medicalminds.nexomed.seguridad;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.config.AppPropiedades;

/** Emite JWT de vida corta: si el cliente no renueva por falta de actividad, la sesión se cierra. */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final AppPropiedades propiedades;

    public record Token(String accessToken, long expiraEnSegundos, String perfil, String nombre) {
    }

    public Token emitir(Sesion s) {
        Instant ahora = Instant.now();
        long segundos = propiedades.seguridad().minutosSesion() * 60L;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nexomed")
                .issuedAt(ahora)
                .expiresAt(ahora.plus(segundos, ChronoUnit.SECONDS))
                .subject(s.usuario())
                .claim("perfil", s.perfil())
                .claim("roles", List.copyOf(s.roles()))
                .claim("nombre", s.nombre())
                .build();
        String valor = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(valor, segundos, s.perfil(), s.nombre());
    }
}
