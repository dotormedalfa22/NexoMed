package mx.medicalminds.nexomed.seguridad;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Excepciones;

/** Lo que cada usuario hace con su propia cuenta: contraseña y PIN. Siempre pide la contraseña actual. */
@RestController
@RequestMapping("/api/cuenta")
@RequiredArgsConstructor
@Tag(name = "Mi cuenta")
public class CuentaController {

    private final Db db;
    private final PasswordEncoder encoder;
    private final UsuarioActual usuarioActual;
    private final Bitacora bitacora;

    public record CambioContrasena(@NotBlank String actual,
                                   @NotBlank @Pattern(regexp = Reglas.CONTRASENA, message = Reglas.CONTRASENA_MENSAJE) String nueva) {
    }

    public record CambioPin(@NotBlank String contrasena,
                            @NotBlank @Pattern(regexp = Reglas.PIN, message = Reglas.PIN_MENSAJE) String pin) {
    }

    @PutMapping("/contrasena")
    @Transactional
    @Operation(summary = "Cambiar mi contraseña (pide la actual)")
    public void cambiarContrasena(@Valid @RequestBody CambioContrasena c) {
        Sesion s = usuarioActual.obtener();
        exigirContrasena(s, c.actual());
        if (c.actual().equals(c.nueva())) {
            throw new Excepciones.ReglaNegocio("La contraseña nueva debe ser distinta de la actual");
        }
        db.ejecutar("""
                UPDATE usuario SET contrasena_hash = :h, contrasena_actualizada = NOW(), cambiar_contrasena = FALSE
                 WHERE id = :id""", Map.of("h", encoder.encode(c.nueva()), "id", s.id()));
        bitacora.registrar(s, Bitacora.Accion.MODIFICACION, "usuario", s.id(), null, "Cambio de contrasena");
    }

    @PutMapping("/pin")
    @Transactional
    @Operation(summary = "Crear o cambiar mi PIN de acceso (pide la contraseña)")
    public Map<String, Object> cambiarPin(@Valid @RequestBody CambioPin c) {
        Sesion s = usuarioActual.obtener();
        exigirContrasena(s, c.contrasena());
        if (Reglas.pinDebil(c.pin())) {
            throw new Excepciones.ReglaNegocio("El PIN es fácil de adivinar (números iguales o consecutivos). Elija otro.");
        }
        boolean nuevo = db.contar("SELECT COUNT(*) FROM usuario WHERE id = :id AND pin_hash IS NULL", Map.of("id", s.id())) > 0;
        db.ejecutar("UPDATE usuario SET pin_hash = :h, pin_actualizado = NOW() WHERE id = :id",
                Map.of("h", encoder.encode(c.pin()), "id", s.id()));
        bitacora.registrar(s, nuevo ? Bitacora.Accion.CREACION : Bitacora.Accion.MODIFICACION, "usuario", s.id(), null,
                nuevo ? "Alta de PIN" : "Cambio de PIN");
        return Map.of("nuevo", nuevo);
    }

    private void exigirContrasena(Sesion s, String contrasena) {
        String hash = db.valor("SELECT contrasena_hash FROM usuario WHERE id = :id", Map.of("id", s.id()), String.class)
                .orElseThrow();
        if (!encoder.matches(contrasena, hash)) {
            throw new Excepciones.ReglaNegocio("La contraseña actual no es correcta");
        }
    }
}
