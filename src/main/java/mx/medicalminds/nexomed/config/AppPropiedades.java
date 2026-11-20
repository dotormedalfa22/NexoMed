package mx.medicalminds.nexomed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades "app.*" de application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppPropiedades(Seguridad seguridad) {

    public record Seguridad(String jwtSecreto,
                            int minutosSesion,
                            int intentosMaximos,
                            int minutosBloqueo,
                            String usuarioInicial,
                            String usuarioInicialNombre,
                            String usuarioInicialContrasena) {
    }
}
