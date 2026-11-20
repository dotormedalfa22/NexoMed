package mx.medicalminds.nexomed.comun;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.seguridad.Sesion;
import mx.medicalminds.nexomed.seguridad.UsuarioActual;
import tools.jackson.databind.ObjectMapper;

/**
 * Bitácora de auditoría: quién, qué, cuándo, desde dónde, con el antes y el después.
 * Los accesos se registran en su propia transacción para que el intento quede aunque falle.
 */
@Service
@RequiredArgsConstructor
public class Bitacora {

    public enum Accion {
        LOGIN, LOGIN_FALLIDO, LOGOUT, CREACION, MODIFICACION, CONSULTA, BAJA
    }

    private final Db db;
    private final UsuarioActual usuarioActual;
    private final ObjectMapper json;

    /** Dentro de la transacción de negocio: si la operación se revierte, el registro también. */
    public void registrar(Sesion s, Accion accion, String tabla, Object registroId, Object antes, Object despues) {
        Map<String, Object> p = new HashMap<>();
        p.put("uid", s.id());
        p.put("usuario", s.usuario());
        p.put("accion", accion.name());
        p.put("tabla", tabla);
        p.put("registro", registroId == null ? null : String.valueOf(registroId));
        p.put("antes", aJson(antes));
        p.put("despues", aJson(despues));
        p.put("ip", usuarioActual.ip());
        db.ejecutar("""
                INSERT INTO bitacora (usuario_id, nombre_usuario, accion, tabla, registro_id, antes, despues, ip)
                VALUES (:uid, :usuario, :accion, :tabla, :registro, :antes, :despues, :ip)""", p);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAcceso(String nombreUsuario, Long usuarioId, Accion accion, String detalle) {
        Map<String, Object> p = new HashMap<>();
        p.put("uid", usuarioId);
        p.put("usuario", nombreUsuario == null ? null
                : nombreUsuario.substring(0, Math.min(50, nombreUsuario.length())));
        p.put("accion", accion.name());
        p.put("detalle", detalle);
        p.put("ip", usuarioActual.ip());
        db.ejecutar("""
                INSERT INTO bitacora (usuario_id, nombre_usuario, accion, tabla, despues, ip)
                VALUES (:uid, :usuario, :accion, 'sesion', :detalle, :ip)""", p);
    }

    private String aJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        String texto = json.writeValueAsString(o);
        return texto.length() > 60000 ? texto.substring(0, 60000) : texto;
    }
}
