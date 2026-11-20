package mx.medicalminds.nexomed.seguridad;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.config.AppPropiedades;

/**
 * Crea el primer usuario (perfil ADMINISTRADOR) cuando la tabla usuario está vacía, con
 * USUARIO_INICIAL y USUARIO_INICIAL_CONTRASENA del .env. Ninguna contraseña queda en el código
 * ni en las migraciones.
 */
@Component
@RequiredArgsConstructor
public class UsuarioInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioInicial.class);
    /** 12+ caracteres con mayúscula, minúscula y número. */
    private static final String CONTRASENA_FUERTE = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{12,}$";

    private final Db db;
    private final PasswordEncoder encoder;
    private final AppPropiedades propiedades;

    @Override
    public void run(ApplicationArguments args) {
        if (db.contar("SELECT COUNT(*) FROM usuario", Map.of()) > 0) {
            return;
        }
        AppPropiedades.Seguridad s = propiedades.seguridad();
        if (s.usuarioInicial() == null || s.usuarioInicial().isBlank()
                || s.usuarioInicialContrasena() == null || s.usuarioInicialContrasena().isBlank()) {
            log.warn("No hay usuarios y faltan USUARIO_INICIAL / USUARIO_INICIAL_CONTRASENA en .env. No se creó el primer usuario.");
            return;
        }
        if (!s.usuarioInicialContrasena().matches(CONTRASENA_FUERTE)) {
            log.warn("La contraseña de USUARIO_INICIAL es de prueba (menos de 12 caracteres con mayúscula, minúscula y número). "
                    + "Cámbiela antes de usar NexoMed en producción.");
        }
        db.ejecutar("""
                INSERT INTO usuario (nombre_usuario, contrasena_hash, nombre_completo, perfil_id)
                SELECT :u, :h, :n, id FROM perfil WHERE clave = 'ADMINISTRADOR'""",
                Map.of("u", s.usuarioInicial().trim(), "h", encoder.encode(s.usuarioInicialContrasena()),
                        "n", s.usuarioInicialNombre()));
        log.info("Usuario inicial '{}' creado con perfil ADMINISTRADOR.", s.usuarioInicial().trim());
    }
}
