package mx.medicalminds.nexomed.catalogos;

import java.time.LocalDate;

/**
 * Datos de un proveedor, tal como vienen en la Constancia de Situación Fiscal (CSF) más el contacto.
 * Es el cuerpo de la API y el renglón de la carga por Excel. {@code regimenFiscal} es la clave del SAT
 * (o su descripción al capturar) y {@code entidad} la clave INEGI (o el nombre del estado).
 */
public record Proveedor(
        String rfc,
        String razonSocial,
        String regimenCapital,
        String nombreComercial,
        String regimenFiscal,
        String actividadEconomica,
        LocalDate fechaInicioOperaciones,
        String estatusPadron,
        String idCif,
        String codigoPostal,
        String tipoVialidad,
        String vialidad,
        String numeroExterior,
        String numeroInterior,
        String colonia,
        String localidad,
        String municipio,
        String entidad,
        String entreCalle,
        String yCalle,
        String contacto,
        String telefono,
        String correo) {
}
