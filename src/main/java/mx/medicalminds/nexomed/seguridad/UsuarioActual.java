package mx.medicalminds.nexomed.seguridad;

import java.util.Map;
import java.util.TreeSet;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Excepciones;

/** Obtiene el usuario autenticado (sujeto del JWT), su perfil, sus roles y la IP de la petición. */
@Component
@RequiredArgsConstructor
public class UsuarioActual {

    private final Db db;

    public Sesion obtener() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new Excepciones.ReglaNegocio("No hay sesión activa");
        }
        return cargar(auth.getName());
    }

    /** Se lee de la base en cada petición: un usuario o perfil desactivado pierde el acceso al momento. */
    public Sesion cargar(String nombreUsuario) {
        Map<String, Object> u = db.uno("""
                SELECT u.id, u.nombre_usuario, u.nombre_completo, p.id AS perfil_id, p.clave AS perfil, p.nombre AS perfil_nombre
                  FROM usuario u JOIN perfil p ON p.id = u.perfil_id
                 WHERE u.nombre_usuario = :u AND u.activo AND p.activo""", Map.of("u", nombreUsuario))
            .orElseThrow(() -> new Excepciones.ReglaNegocio("Usuario inactivo o inexistente"));
        TreeSet<String> roles = new TreeSet<>(db.lista("""
                SELECT r.clave FROM perfil_rol pr JOIN rol r ON r.id = pr.rol_id
                 WHERE pr.perfil_id = :p AND r.activo""", Map.of("p", u.get("perfil_id")))
            .stream().map(f -> (String) f.get("clave")).toList());
        return new Sesion(((Number) u.get("id")).longValue(), (String) u.get("nombre_usuario"),
                (String) u.get("nombre_completo"), (String) u.get("perfil"), (String) u.get("perfil_nombre"), roles);
    }

    public String ip() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        HttpServletRequest request = atributos.getRequest();
        String reenviada = request.getHeader("X-Forwarded-For");
        return reenviada != null ? reenviada.split(",")[0].trim() : request.getRemoteAddr();
    }
}
