package com.ees.service;

import com.ees.common.BizException;
import com.ees.domain.ReportRecord;
import com.ees.repository.ReportRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 报告记录的短事务辅助服务。
 *
 * <p><b>为什么必须单独一个 Bean</b>：Spring 的 {@code @Transactional} 基于代理，
 * 同一个类内部的自调用（self-invocation）不会走代理，事务注解不生效。
 * 因此把「毫秒级的事务操作」拆到本类，由 {@link ReportService} 从外部调用。</p>
 *
 * <p>拆分的目的是把 {@code ReportService.generate()} 里耗时数百毫秒到数秒的
 * 文件渲染（PDF / Word / Excel）移出事务，让数据库连接占用时长从约 3000ms
 * 降到约 20ms，避免「少数人同时导出就打满连接池」。</p>
 */
@Service
public class ReportTxService {

    private final ReportRecordRepository reportRepository;
    private final CodeService codeService;

    public ReportTxService(ReportRecordRepository reportRepository, CodeService codeService) {
        this.reportRepository = reportRepository;
        this.codeService = codeService;
    }

    /** 事务 1：创建导出记录（GENERATING），毫秒级。 */
    @Transactional
    public ReportRecord create(Long taskId, Long resultId, Long projectId,
                               String reportName, String type, String operator) {
        ReportRecord record = new ReportRecord();
        record.setReportCode(codeService.next("RPT", "report_record", "report_code"));
        record.setReportName(reportName);
        record.setTaskId(taskId);
        record.setResultId(resultId);
        record.setProjectId(projectId);
        record.setReportType(type);
        record.setStatus("GENERATING");
        record.setCreatedBy(operator);
        record.setCreatedAt(LocalDateTime.now());
        return reportRepository.save(record);
    }

    /** 事务 2：文件生成成功，置 READY，毫秒级。 */
    @Transactional
    public void markReady(Long id, String fileName, String filePath, long fileSize) {
        reportRepository.findById(id).ifPresent(r -> {
            r.setFileName(fileName);
            r.setFilePath(filePath);
            r.setFileSize(fileSize);
            r.setStatus("READY");
            reportRepository.save(r);
        });
    }

    /**
     * 事务 3：文件生成失败，置 FAILED，毫秒级。
     *
     * <p>独立事务的意义：原实现中 FAILED 状态与抛出的异常处在同一个事务里，
     * 异常触发回滚后 FAILED 状态会一并丢失，前端只能一直看到 GENERATING。</p>
     */
    @Transactional
    public void markFailed(Long id, String errorMsg) {
        reportRepository.findById(id).ifPresent(r -> {
            r.setStatus("FAILED");
            r.setErrorMsg(errorMsg);
            reportRepository.save(r);
        });
    }

    @Transactional(readOnly = true)
    public ReportRecord get(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> BizException.notFound("导出记录不存在"));
    }
}
