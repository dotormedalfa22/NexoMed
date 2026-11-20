package mx.medicalminds.nexomed.comun;

import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(Excepciones.NoEncontrado.class)
    public ProblemDetail noEncontrado(Excepciones.NoEncontrado e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(Excepciones.ReglaNegocio.class)
    public ProblemDetail reglaNegocio(Excepciones.ReglaNegocio e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }


    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicado(DuplicateKeyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Ya existe un registro con esa clave");
    }

    /** Los triggers de V1 responden SQLSTATE 45000 con un mensaje "NEXOMED: ...". */
    @ExceptionHandler({DataIntegrityViolationException.class, UncategorizedSQLException.class})
    public ProblemDetail integridad(RuntimeException e) {
        String regla = mensajeTrigger(e);
        if (regla != null) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, regla);
        }
        log.warn("Integridad de datos: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La operación viola la integridad de los datos (registro relacionado inexistente o duplicado)");
    }

    private static String mensajeTrigger(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && "45000".equals(s.getSQLState())) {
                String m = s.getMessage();
                int i = m.indexOf("NEXOMED:");
                return i >= 0 ? m.substring(i + 8).trim() : m;
            }
        }
        return null;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errores = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        pd.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail accesoDenegado(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Su perfil no tiene permiso para esta acción");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e) {
        String regla = mensajeTrigger(e);
        if (regla != null) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, regla);
        }
        log.error("Error no controlado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno. Se registró para su revisión.");
    }
}
