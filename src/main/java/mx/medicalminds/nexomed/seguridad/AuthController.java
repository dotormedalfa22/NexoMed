package mx.medicalminds.nexomed.seguridad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Db;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Sesión")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final UsuarioActual usuarioActual;
    private final Bitacora bitacora;
    private final Db db;

    public record Credenciales(@NotBlank String usuario, @NotBlank String contrasena) {
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión; devuelve un token Bearer de 15 minutos")
    public TokenService.Token login(@Valid @RequestBody Credenciales c) {
        return authService.iniciarSesion(c.usuario().trim(), c.contrasena());
    }

    @PostMapping("/renovar")
    @Operation(summary = "Renovar el token mientras haya actividad (cierre por inactividad a los 15 min)")
    public TokenService.Token renovar() {
        return tokenService.emitir(usuarioActual.obtener());
    }

    @PostMapping("/salir")
    @Operation(summary = "Registrar el cierre de sesión (manual o por inactividad)")
    public void salir(@RequestBody(required = false) Map<String, String> cuerpo) {
        Sesion s = usuarioActual.obtener();
        bitacora.registrarAcceso(s.usuario(), s.id(), Bitacora.Accion.LOGOUT,
                cuerpo == null ? null : cuerpo.get("motivo"));
    }

    @GetMapping("/yo")
    @Operation(summary = "Usuario en sesión, su perfil y sus roles")
    public Map<String, Object> yo() {
        Sesion s = usuarioActual.obtener();
        List<Map<String, Object>> roles = db.lista("""
                SELECT r.clave, r.nombre, r.modulo FROM rol r
                 WHERE r.clave IN (:roles) ORDER BY r.modulo, r.nombre""",
                Map.of("roles", s.roles().isEmpty() ? List.of("-") : List.copyOf(s.roles())));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", s.id());
        r.put("usuario", s.usuario());
        r.put("nombre", s.nombre());
        r.put("perfil", s.perfil());
        r.put("perfilNombre", s.perfilNombre());
        r.put("roles", roles);
        Map<String, Object> cuenta = db.uno("""
                SELECT correo, pin_hash IS NOT NULL AS tiene_pin, pin_actualizado, contrasena_actualizada,
                       cambiar_contrasena, creado_en FROM usuario WHERE id = :id""", Map.of("id", s.id())).orElseThrow();
        r.put("correo", cuenta.get("correo"));
        r.put("tienePin", verdadero(cuenta.get("tiene_pin")));
        r.put("pinActualizado", cuenta.get("pin_actualizado"));
        r.put("contrasenaActualizada", cuenta.get("contrasena_actualizada"));
        r.put("cambiarContrasena", verdadero(cuenta.get("cambiar_contrasena")));
        r.put("creadoEn", cuenta.get("creado_en"));
        r.put("ultimoAcceso", db.valor("""
                SELECT MAX(fecha) FROM bitacora WHERE usuario_id = :id AND accion = 'LOGIN'
                   AND fecha < (SELECT MAX(fecha) FROM bitacora WHERE usuario_id = :id AND accion = 'LOGIN')""",
                Map.of("id", s.id()), String.class).orElse(null));
        return r;
    }

    private static boolean verdadero(Object v) {
        return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0;
    }
}
