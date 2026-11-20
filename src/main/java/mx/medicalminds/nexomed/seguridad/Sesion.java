package mx.medicalminds.nexomed.seguridad;

import java.util.Set;

/** Usuario autenticado: su perfil y los roles (permisos) que el perfil incluye. */
public record Sesion(long id, String usuario, String nombre, String perfil, String perfilNombre, Set<String> roles) {

    public boolean tiene(String rol) {
        return roles.contains(rol);
    }
}
