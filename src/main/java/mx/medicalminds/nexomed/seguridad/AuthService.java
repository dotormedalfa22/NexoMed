package mx.medicalminds.nexomed.seguridad;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Bitacora.Accion;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.config.AppPropiedades;

/** Inicio de sesión con bloqueo temporal tras varios intentos fallidos. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final Db db;
    private final UsuarioActual usuarioActual;
    private final TokenService tokenService;
    private final Bitacora bitacora;
    private final AppPropiedades propiedades;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenService.Token iniciarSesion(String nombreUsuario, String contrasena) {
        Optional<Map<String, Object>> u = db.uno(
                "SELECT id, intentos_fallidos, bloqueado_hasta FROM usuario WHERE nombre_usuario = :u",
                Map.of("u", nombreUsuario));
        Long id = u.map(f -> ((Number) f.get("id")).longValue()).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();

        if (u.isPresent() && u.get().get("bloqueado_hasta") != null
                && LocalDateTime.parse((String) u.get().get("bloqueado_hasta")).isAfter(ahora)) {
            bitacora.registrarAcceso(nombreUsuario, id, Accion.LOGIN_FALLIDO, "Cuenta bloqueada");
            throw new ResponseStatusException(HttpStatus.LOCKED, "Cuenta bloqueada temporalmente por intentos fallidos");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(nombreUsuario, contrasena));
        } catch (DisabledException e) {
            bitacora.registrarAcceso(nombreUsuario, id, Accion.LOGIN_FALLIDO, "Usuario o perfil inactivo");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Su usuario está desactivado. Avise al administrador.");
        } catch (AuthenticationException e) {
            u.ifPresent(f -> registrarFallo(id, ((Number) f.get("intentos_fallidos")).intValue() + 1, ahora));
            bitacora.registrarAcceso(nombreUsuario, id, Accion.LOGIN_FALLIDO, "Credenciales invalidas");
            // Mismo mensaje exista o no el usuario: no revela qué cuentas existen.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }

        db.ejecutar("""
                UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_acceso = NOW()
                 WHERE id = :id""", Map.of("id", id));
        bitacora.registrarAcceso(nombreUsuario, id, Accion.LOGIN, null);
        return tokenService.emitir(usuarioActual.cargar(nombreUsuario));
    }

    private void registrarFallo(long id, int intentos, LocalDateTime ahora) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        if (intentos >= propiedades.seguridad().intentosMaximos()) {
            p.put("intentos", 0);
            p.put("hasta", ahora.plusMinutes(propiedades.seguridad().minutosBloqueo()));
        } else {
            p.put("intentos", intentos);
            p.put("hasta", null);
        }
        db.ejecutar("UPDATE usuario SET intentos_fallidos = :intentos, bloqueado_hasta = :hasta WHERE id = :id", p);
    }
}
