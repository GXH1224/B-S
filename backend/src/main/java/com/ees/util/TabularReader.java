package com.ees.util;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import lombok.Getter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表格文件读取（XLSX / XLS / CSV）。对应方案 5.1 文件导入方式。
 */
public final class TabularReader {

    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    private TabularReader() {
    }

    /** 读取结果。 */
    @Getter
    public static class TableData {
        private final List<String> headers = new ArrayList<>();
        private final List<Map<String, String>> rows = new ArrayList<>();
        /** 每行对应的原始行号（1 基，含表头） */
        private final List<Integer> rowNumbers = new ArrayList<>();
        private String sheetName;

        public int size() {
            return rows.size();
        }

        public boolean hasColumn(String name) {
            for (String h : headers) {
                if (h != null && h.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }

        public String get(Map<String, String> row, String... candidates) {
            for (String c : candidates) {
                for (Map.Entry<String, String> e : row.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase(c)) {
                        String v = e.getValue();
                        if (v != null && !v.isBlank()) {
                            return v.trim();
                        }
                    }
                }
            }
            return null;
        }
    }

    public static TableData read(InputStream in, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return readExcel(in);
        }
        if (lower.endsWith(".csv")) {
            return readCsv(in);
        }
        throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                "暂不支持的文件类型：" + fileName + "，请使用 XLSX / XLS / CSV");
    }

    public static TableData readExcel(InputStream in) {
        TableData data = new TableData();
        try (Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                throw new BizException(ErrorCode.FILE_PARSE_FAILED, "Excel 文件不包含任何工作表");
            }
            Sheet sheet = wb.getSheetAt(0);
            data.sheetName = sheet.getSheetName();
            int headerRowIdx = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowIdx);
            if (headerRow == null) {
                throw new BizException(ErrorCode.FILE_PARSE_FAILED, "未找到表头行");
            }
            int lastCol = headerRow.getLastCellNum();
            Set<String> seen = new LinkedHashSet<>();
            for (int c = 0; c < lastCol; c++) {
                String h = cellString(headerRow.getCell(c));
                if (h == null || h.isBlank()) {
                    h = "COL" + (c + 1);
                }
                h = h.trim();
                if (seen.contains(h)) {
                    h = h + "_" + (c + 1);
                }
                seen.add(h);
                data.headers.add(h);
            }
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                boolean empty = true;
                for (int c = 0; c < data.headers.size(); c++) {
                    String v = cellString(row.getCell(c));
                    if (v != null && !v.isBlank()) {
                        empty = false;
                    }
                    map.put(data.headers.get(c), v);
                }
                if (empty) {
                    continue;
                }
                data.rows.add(map);
                data.rowNumbers.add(r + 1);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "Excel 解析失败：" + e.getMessage(), e);
        }
        return data;
    }

    /** 在前 10 行内寻找表头行（含“指标”或列名最多的行）。 */
    private static int findHeaderRow(Sheet sheet) {
        int best = sheet.getFirstRowNum();
        int bestScore = -1;
        int limit = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 9);
        for (int r = sheet.getFirstRowNum(); r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int nonEmpty = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String v = cellString(row.getCell(c));
                if (v != null && !v.isBlank()) {
                    nonEmpty++;
                }
            }
            if (nonEmpty > bestScore) {
                bestScore = nonEmpty;
                best = r;
            }
        }
        return best;
    }

    public static TableData readCsv(InputStream in) {
        TableData data = new TableData();
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "读取 CSV 失败：" + e.getMessage(), e);
        }
        String text = decode(bytes);
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            data.headers.addAll(parser.getHeaderNames());
            for (CSVRecord rec : parser) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String h : data.headers) {
                    map.put(h, rec.isMapped(h) ? rec.get(h) : null);
                }
                data.rows.add(map);
                data.rowNumbers.add((int) rec.getRecordNumber() + 1);
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_PARSE_FAILED, "CSV 解析失败：" + e.getMessage(), e);
        }
        return data;
    }

    /** 自动识别 UTF-8 / GBK 编码，避免中文乱码。 */
    public static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception e) {
            return utf8;
        }
    }

    private static String cellString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getLocalDateTimeCellValue();
            return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        String v = FORMATTER.formatCellValue(cell);
        return v == null ? null : v.trim();
    }

    /** 数值解析，失败返回 null 并输出原因。 */
    public static Double parseNumber(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim().replace(",", "").replace("，", "");
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                if (f.toString().contains("HH")) {
                    return LocalDateTime.parse(t, f);
                }
                return LocalDate.parse(t, f).atStartOfDay();
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        // Excel 序列号日期
        Double num = parseNumber(t);
        if (num != null && num > 20000 && num < 80000) {
            return LocalDate.of(1899, 12, 30).plusDays(num.longValue()).atStartOfDay();
        }
        return null;
    }
}
