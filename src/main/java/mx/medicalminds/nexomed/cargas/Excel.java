package mx.medicalminds.nexomed.cargas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import mx.medicalminds.nexomed.comun.Excepciones;
import mx.medicalminds.nexomed.comun.Parecidos;

/**
 * Lectura y plantillas de Excel para las cargas (modelo de las cargas de SIALSS): primera hoja, renglón 1 con
 * los títulos (se buscan por nombre, sin importar el orden ni los acentos), renglones vacíos fuera, máximo
 * 5,000 renglones con datos.
 */
public final class Excel {

    public static final int MAX_RENGLONES = 5000;

    private Excel() {
    }

    /** Columna de una carga. {@code lista}: fórmula de la lista desplegable (o null). */
    public record Columna(String titulo, boolean obligatoria, int ancho, String ejemplo, String ayuda,
                          boolean fecha, String lista) {
        public Columna(String titulo, boolean obligatoria, int ancho, String ejemplo, String ayuda) {
            this(titulo, obligatoria, ancho, ejemplo, ayuda, false, null);
        }
    }

    /** Renglón leído: número de renglón de Excel y un valor por columna (texto, LocalDate o null). */
    public record Fila(int renglon, List<Object> valores) {
        public String texto(int i) {
            Object v = valores.get(i);
            return v == null ? null : v instanceof LocalDate d ? d.format(DMA) : v.toString();
        }
    }

    public static final DateTimeFormatter DMA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static List<Fila> leer(MultipartFile archivo, List<Columna> columnas) {
        String nombre = archivo == null ? "" : archivo.getOriginalFilename();
        if (archivo == null || archivo.isEmpty()) {
            throw new Excepciones.ReglaNegocio("El archivo " + nombre + " está vacío (0 bytes). No se cargó nada.");
        }
        try (InputStream in = archivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                throw new Excepciones.ReglaNegocio("El archivo no tiene hojas. No se cargó nada.");
            }
            Sheet hoja = wb.getSheetAt(0);
            DataFormatter formato = new DataFormatter(java.util.Locale.forLanguageTag("es-MX"));
            FormulaEvaluator evaluador = wb.getCreationHelper().createFormulaEvaluator();

            Row cabeza = hoja.getRow(0);
            Map<String, Integer> posiciones = new HashMap<>();
            if (cabeza != null) {
                for (Cell c : cabeza) {
                    posiciones.putIfAbsent(Parecidos.normalizar(formato.formatCellValue(c)), c.getColumnIndex());
                }
            }
            List<Integer> indices = new ArrayList<>();
            List<String> faltan = new ArrayList<>();
            for (Columna col : columnas) {
                Integer i = posiciones.get(Parecidos.normalizar(col.titulo()));
                if (i == null && col.obligatoria()) {
                    faltan.add(col.titulo());
                }
                indices.add(i);
            }
            if (!faltan.isEmpty()) {
                throw new Excepciones.ReglaNegocio("Al renglón de títulos le faltan las columnas: " + String.join(", ", faltan)
                        + ". Descargue la plantilla y úsela tal cual. No se cargó nada.");
            }

            List<Fila> filas = new ArrayList<>();
            for (int r = 1; r <= hoja.getLastRowNum(); r++) {
                Row fila = hoja.getRow(r);
                if (fila == null) {
                    continue;
                }
                List<Object> valores = new ArrayList<>();
                boolean vacia = true;
                for (Integer i : indices) {
                    Object v = i == null ? null : valor(fila.getCell(i), formato, evaluador);
                    valores.add(v);
                    vacia &= v == null;
                }
                if (vacia) {
                    continue;
                }
                if (filas.size() == MAX_RENGLONES) {
                    throw new Excepciones.ReglaNegocio("El archivo pasa de " + MAX_RENGLONES
                            + " renglones con datos. Divídalo en varios archivos. No se cargó nada.");
                }
                filas.add(new Fila(r + 1, valores));
            }
            return filas;
        } catch (Excepciones.ReglaNegocio e) {
            throw e;
        } catch (Exception e) {
            throw new Excepciones.ReglaNegocio("No se pudo leer " + nombre
                    + ": debe ser un archivo de Excel (.xlsx o .xls) sin contraseña. No se cargó nada.");
        }
    }

    private static Object valor(Cell c, DataFormatter formato, FormulaEvaluator evaluador) {
        if (c == null) {
            return null;
        }
        CellType tipo = c.getCellType() == CellType.FORMULA ? evaluador.evaluateFormulaCell(c) : c.getCellType();
        if (tipo == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        String t = formato.formatCellValue(c, evaluador).replace('\u00A0', ' ').trim();
        return t.isEmpty() ? null : t;
    }

    private static final String[] MESES = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO",
            "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};

    /**
     * Fecha de Excel, número de serie, dd/mm/aaaa, dd-mm-aaaa, aaaa-mm-dd o como la escribe la CSF
     * ("26 DE FEBRERO DE 2024"). Lanza IllegalArgumentException si no se entiende.
     */
    public static LocalDate fecha(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        String t = v.toString().trim().toUpperCase(java.util.Locale.ROOT);
        try {
            if (t.matches("\\d{5}")) {
                return DateUtil.getLocalDateTime(Double.parseDouble(t)).toLocalDate();
            }
            if (t.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")) {
                String[] p = t.split("[/-]");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(t);
            }
            var m = java.util.regex.Pattern.compile("^(\\d{1,2}) DE ([A-Z]+) DE (\\d{4})$").matcher(t);
            if (m.matches()) {
                for (int i = 0; i < 12; i++) {
                    if (MESES[i].equals(m.group(2))) {
                        return LocalDate.of(Integer.parseInt(m.group(3)), i + 1, Integer.parseInt(m.group(1)));
                    }
                }
            }
        } catch (java.time.DateTimeException | NumberFormatException e) {
            // cae al error de abajo
        }
        throw new IllegalArgumentException(v + " no es una fecha válida (use dd/mm/aaaa)");
    }

    /**
     * Plantilla: hoja de captura (títulos, un renglón de ejemplo, columnas en formato texto, listas desplegables),
     * hoja Catalogos y hoja Instrucciones.
     *
     * @param catalogos títulos de columna de la hoja Catalogos y sus valores
     */
    public static byte[] plantilla(String nombreHoja, List<Columna> columnas, Map<String, List<String>> catalogos,
                                   List<String> instrucciones) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFColor marca = new XSSFColor(new byte[] {0x0b, 0x38, 0x54});
            XSSFColor acento = new XSSFColor(new byte[] {0x19, (byte) 0xa9, (byte) 0x97});
            Font blanca = wb.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle titulo = wb.createCellStyle();
            titulo.setFont(blanca);
            titulo.setFillForegroundColor(marca);
            titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titulo.setWrapText(true);
            XSSFCellStyle tituloObligatorio = wb.createCellStyle();
            tituloObligatorio.cloneStyleFrom(titulo);
            tituloObligatorio.setFillForegroundColor(acento);
            short formatoTexto = wb.createDataFormat().getFormat("@");
            CellStyle texto = wb.createCellStyle();
            texto.setDataFormat(formatoTexto);
            CellStyle fecha = wb.createCellStyle();
            fecha.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
            Font gris = wb.createFont();
            gris.setItalic(true);
            gris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            CellStyle ejemplo = wb.createCellStyle();
            ejemplo.setDataFormat(formatoTexto);
            ejemplo.setFont(gris);

            Sheet hoja = wb.createSheet(nombreHoja);
            Row cabeza = hoja.createRow(0);
            cabeza.setHeightInPoints(32);
            Row muestra = hoja.createRow(1);
            DataValidationHelper ayudaValidacion = hoja.getDataValidationHelper();
            for (int i = 0; i < columnas.size(); i++) {
                Columna col = columnas.get(i);
                Cell c = cabeza.createCell(i);
                c.setCellValue(col.titulo() + (col.obligatoria() ? " *" : ""));
                c.setCellStyle(col.obligatoria() ? tituloObligatorio : titulo);
                hoja.setColumnWidth(i, Math.min(255, col.ancho()) * 256);
                hoja.setDefaultColumnStyle(i, col.fecha() ? fecha : texto);
                Cell e = muestra.createCell(i);
                e.setCellValue(col.ejemplo() == null ? "" : col.ejemplo());
                e.setCellStyle(ejemplo);
                if (col.lista() != null) {
                    DataValidation v = ayudaValidacion.createValidation(
                            ayudaValidacion.createFormulaListConstraint(col.lista()),
                            new CellRangeAddressList(1, MAX_RENGLONES + 1, i, i));
                    v.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                    v.setShowErrorBox(true);
                    v.createErrorBox("Valor fuera del catálogo", "Elija un valor de la lista (hoja Catalogos).");
                    hoja.addValidationData(v);
                }
            }
            hoja.createFreezePane(0, 1);

            Sheet cat = wb.createSheet("Catalogos");
            int col = 0;
            for (var entrada : catalogos.entrySet()) {
                Row r0 = cat.getRow(0) == null ? cat.createRow(0) : cat.getRow(0);
                Cell c = r0.createCell(col);
                c.setCellValue(entrada.getKey());
                c.setCellStyle(titulo);
                List<String> valores = entrada.getValue();
                for (int i = 0; i < valores.size(); i++) {
                    Row r = cat.getRow(i + 1) == null ? cat.createRow(i + 1) : cat.getRow(i + 1);
                    r.createCell(col).setCellValue(valores.get(i));
                }
                cat.setColumnWidth(col, 70 * 256);
                col += 2;
            }

            Sheet ins = wb.createSheet("Instrucciones");
            ins.setColumnWidth(0, 120 * 256);
            List<String> lineas = new ArrayList<>(instrucciones);
            lineas.add("El renglón 2 de la plantilla es un ejemplo y no se carga; bórrelo o escriba encima.");
            lineas.add("Máximo " + MAX_RENGLONES + " renglones por archivo. Las columnas en verde son obligatorias (*).");
            for (int i = 0; i < lineas.size(); i++) {
                ins.createRow(i).createCell(0).setCellValue(lineas.get(i));
            }

            wb.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }
}
