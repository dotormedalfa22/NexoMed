package mx.medicalminds.nexomed.seguridad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Excepciones;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios")
@PreAuthorize("hasRole('USUARIOS_ADMINISTRAR')")
public class UsuarioController {

    private final Db db;
    private final PasswordEncoder encoder;
    private final UsuarioActual usuarioActual;
    private final Bitacora bitacora;

    public record NuevoUsuario(
            @NotBlank @Pattern(regexp = "^[a-z0-9._-]{3,50}$", message = "de 3 a 50 minúsculas, números, punto o guion") String usuario,
            @NotBlank @Size(max = 150) String nombreCompleto,
            @Email @Size(max = 150) String correo,
            @NotNull Long perfilId,
            @NotBlank @Pattern(regexp = Reglas.CONTRASENA, message = Reglas.CONTRASENA_MENSAJE) String contrasena) {
    }

    @GetMapping("/perfiles")
    @Operation(summary = "Perfiles activos para asignar a un usuario")
    public List<Map<String, Object>> perfiles() {
        return db.lista("SELECT id, clave, nombre, descripcion FROM perfil WHERE activo ORDER BY nombre");
    }

    @GetMapping
    @Operation(summary = "Usuarios registrados")
    public List<Map<String, Object>> listar() {
        return db.lista("""
                SELECT u.id, u.nombre_usuario, u.nombre_completo, u.correo, p.nombre AS perfil, u.activo,
                       u.cambiar_contrasena, u.ultimo_acceso, u.creado_en
                  FROM usuario u JOIN perfil p ON p.id = u.perfil_id ORDER BY u.creado_en DESC""");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Alta de usuario; al entrar por primera vez deberá cambiar la contraseña inicial")
    public Map<String, Object> crear(@Valid @RequestBody NuevoUsuario n) {
        if (db.contar("SELECT COUNT(*) FROM perfil WHERE id = :id AND activo", Map.of("id", n.perfilId())) == 0) {
            throw new Excepciones.ReglaNegocio("El perfil elegido no existe o está desactivado");
        }
        if (db.contar("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = :u", Map.of("u", n.usuario())) > 0) {
            throw new Excepciones.ReglaNegocio("Ya existe el usuario " + n.usuario());
        }
        Map<String, Object> p = new HashMap<>();
        p.put("u", n.usuario());
        p.put("hash", encoder.encode(n.contrasena()));
        p.put("nombre", n.nombreCompleto().trim());
        p.put("correo", n.correo() == null || n.correo().isBlank() ? null : n.correo().trim());
        p.put("perfil", n.perfilId());
        long id = db.insertar("""
                INSERT INTO usuario (nombre_usuario, contrasena_hash, nombre_completo, correo, perfil_id, cambiar_contrasena)
                VALUES (:u, :hash, :nombre, :correo, :perfil, TRUE)""", p);
        bitacora.registrar(usuarioActual.obtener(), Bitacora.Accion.CREACION, "usuario", id, null,
                Map.of("usuario", n.usuario(), "perfilId", n.perfilId()));
        return Map.of("id", id, "usuario", n.usuario());
    }
}
