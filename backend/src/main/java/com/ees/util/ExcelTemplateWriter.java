package com.ees.util;

import com.ees.service.DataBatchService;
import com.ees.service.IndicatorService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Excel 模板与错误明细生成（支持模板下载与错误明细下载）。
 */
public final class ExcelTemplateWriter {

    private ExcelTemplateWriter() {
    }

    /** 指标体系导入模板。 */
    public static byte[] indicatorTemplate() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("指标体系");
            List<String> cols = IndicatorService.TEMPLATE_COLUMNS;
            String[] comments = {
                    "指标编码（唯一，主关联键）", "指标名称", "上级指标编码（顶层留空）", "层级（1 为顶层）",
                    "计量单位", "方向：POSITIVE 正向 / NEGATIVE 负向 / INTERVAL 区间型",
                    "权重（0~1，同层之和建议为 1）", "最小值（标准化下界）", "最大值（标准化上界）",
                    "区间型指标下界", "区间型指标上界", "最优值", "评分规则（JSON 文本）", "排序", "备注"
            };
            CellStyle head = headerStyle(wb);
            Row h = sheet.createRow(0);
            Row c = sheet.createRow(1);
            for (int i = 0; i < cols.size(); i++) {
                h.createCell(i).setCellValue(cols.get(i));
                h.getCell(i).setCellStyle(head);
                c.createCell(i).setCellValue(comments[i]);
                sheet.setColumnWidth(i, i == 1 ? 6000 : 4200);
            }
            // 示例数据（三层结构）
            String[][] demo = {
                    {"A", "综合效能", "", "1", "", "POSITIVE", "", "", "", "", "", "", "", "1", "一级指标示例"},
                    {"A1", "作战能力", "A", "2", "", "POSITIVE", "0.6", "", "", "", "", "", "", "1", "二级指标示例"},
                    {"A2", "保障能力", "A", "2", "", "POSITIVE", "0.4", "", "", "", "", "", "", "2", "二级指标示例"},
                    {"A11", "反应时间", "A1", "3", "秒", "NEGATIVE", "0.5", "0", "60", "", "", "", "", "1", "越小越好"},
                    {"A12", "命中精度", "A1", "3", "%", "POSITIVE", "0.5", "0", "100", "", "", "", "", "2", ""},
                    {"A21", "备件满足率", "A2", "3", "%", "POSITIVE", "1.0", "0", "100", "", "", "", "", "1", ""}
            };
            int r = 2;
            for (String[] row : demo) {
                Row rr = sheet.createRow(r++);
                for (int i = 0; i < row.length; i++) {
                    rr.createCell(i).setCellValue(row[i]);
                }
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("生成指标体系模板失败", e);
        }
    }

    /** 标准试验数据导入模板（附录 A）。 */
    public static byte[] dataTemplate() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("试验数据");
            List<String> cols = DataBatchService.TEMPLATE_COLUMNS;
            String[] comments = {
                    "项目或试验项目编码", "被评估对象或方案编码", "被评估对象名称（可选）",
                    "数据采集或评估时间，如 2026-09-22 10:00:00",
                    "与指标体系版本中的指标编码对应", "原始指标值（数值）",
                    "数据单位，需与指标定义一致", "来源批次或试验批次", "补充说明"
            };
            CellStyle head = headerStyle(wb);
            Row h = sheet.createRow(0);
            Row c = sheet.createRow(1);
            for (int i = 0; i < cols.size(); i++) {
                h.createCell(i).setCellValue(cols.get(i));
                h.getCell(i).setCellStyle(head);
                c.createCell(i).setCellValue(comments[i]);
                sheet.setColumnWidth(i, i == 4 ? 4600 : 4200);
            }
            String[][] demo = {
                    {"PRJ-2026-001", "OBJ-001", "方案一", "2026-09-22 10:00:00", "A11", "18.5", "秒", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-001", "方案一", "2026-09-22 10:00:00", "A12", "92.4", "%", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-001", "方案一", "2026-09-22 10:00:00", "A21", "88.0", "%", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-002", "方案二", "2026-09-22 10:00:00", "A11", "25.0", "秒", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-002", "方案二", "2026-09-22 10:00:00", "A12", "85.0", "%", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-002", "方案二", "2026-09-22 10:00:00", "A21", "94.5", "%", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-003", "方案三", "2026-09-22 10:00:00", "A11", "12.8", "秒", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-003", "方案三", "2026-09-22 10:00:00", "A12", "97.1", "%", "BATCH-01", ""},
                    {"PRJ-2026-001", "OBJ-003", "方案三", "2026-09-22 10:00:00", "A21", "76.3", "%", "BATCH-01", ""}
            };
            int r = 2;
            for (String[] row : demo) {
                Row rr = sheet.createRow(r++);
                for (int i = 0; i < row.length; i++) {
                    rr.createCell(i).setCellValue(row[i]);
                }
            }
            // 字段说明页
            Sheet desc = wb.createSheet("字段说明");
            String[][] fields = {
                    {"字段", "必填", "类型", "说明"},
                    {"project_code", "是", "文本", "项目或试验项目编码"},
                    {"object_code", "是", "文本", "被评估对象或方案编码"},
                    {"object_name", "否", "文本", "被评估对象名称"},
                    {"data_time", "否", "日期时间", "数据采集或评估时间"},
                    {"indicator_code", "是", "文本", "与指标体系版本中的编码对应"},
                    {"indicator_value", "是", "数值", "原始指标值"},
                    {"unit", "否", "文本", "数据单位，需与指标定义一致"},
                    {"source_batch", "否", "文本", "来源批次或试验批次"},
                    {"remark", "否", "文本", "补充说明"}
            };
            CellStyle head2 = headerStyle(wb);
            int dr = 0;
            for (String[] row : fields) {
                Row rr = desc.createRow(dr);
                for (int i = 0; i < row.length; i++) {
                    rr.createCell(i).setCellValue(row[i]);
                    if (dr == 0) {
                        rr.getCell(i).setCellStyle(head2);
                    }
                }
                dr++;
            }
            desc.setColumnWidth(0, 4200);
            desc.setColumnWidth(3, 9000);
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("生成数据模板失败", e);
        }
    }

    /** 错误明细导出。 */
    public static byte[] errorDetail(List<Map<String, String>> rows, String[] headers) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("错误明细");
            CellStyle head = headerStyle(wb);
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
                h.getCell(i).setCellStyle(head);
                sheet.setColumnWidth(i, 4200);
            }
            int r = 1;
            for (Map<String, String> row : rows) {
                Row rr = sheet.createRow(r++);
                for (int i = 0; i < headers.length; i++) {
                    String v = row.get(headers[i]);
                    rr.createCell(i).setCellValue(v == null ? "" : v);
                }
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("生成错误明细失败", e);
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            wb.close();
            return out.toByteArray();
        }
    }
}
