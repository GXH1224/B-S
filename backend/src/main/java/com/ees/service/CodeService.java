package com.ees.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务编号生成服务。
 *
 * <p>编号格式：{@code PREFIX-yyyyMMdd-####}，例如 {@code TASK-20260922-0001}、
 * {@code SNAP-20260922-0003}、{@code BATCH-20260922-0007}。</p>
 *
 * <p>流水号由数据库当天已有编号的最大值推导，因此<b>服务重启后不会重复</b>；
 * 同时在进程内做序号预占，避免同一事务/并发调用产生相同编号。</p>
 */
@Service
public class CodeService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_SEQ = 9999;

    /** key = PREFIX-yyyyMMdd，value = 已预占的最大序号 */
    private final Map<String, Integer> reserved = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 生成下一个业务编号。
     *
     * @param prefix 编号前缀，如 TASK / SNAP / BATCH / PRJ / DS / RPT
     * @param table  对应表名（内部常量，不接受外部输入）
     * @param column 编号列名（内部常量，不接受外部输入）
     */
    /*
     * 传播级别必须是 REQUIRED，不能是 REQUIRES_NEW。
     *
     * REQUIRES_NEW 会「挂起」外层事务而不是结束它 —— 外层已持有的连接不释放，
     * 同时额外再借一个连接，导致「创建」类接口每次请求占用 2 个数据库连接
     * （ProjectService.save / DataSourceService.save / DataBatchService.importFile /
     *  createBatchFromRows / SnapshotService.createFromBatch / TaskService.create /
     *  TaskService.retry / ReportService.generate 共 8 处调用点全部位于 @Transactional 方法内）。
     *
     * 编号的唯一性并不依赖数据库隔离级别，而是靠下面的进程内 reserved 预占
     * 与 exists() 冲突探测保证，因此复用外层连接是安全的。
     *
     * 注意：不要改成 NOT_SUPPORTED —— 它同样会挂起外层事务，连接照旧翻倍。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(String prefix, String table, String column) {
        String day = LocalDateTime.now().format(DAY);
        String key = prefix + "-" + day;

        Integer cached = reserved.get(key);
        int seq = cached == null ? queryMaxSeq(prefix, day, table, column) : cached;
        seq = seq >= MAX_SEQ ? 1 : seq + 1;
        // 若回绕后与数据库已有编号冲突，则继续向后寻找可用序号
        while (exists(prefix, day, seq, table, column)) {
            seq = seq >= MAX_SEQ ? 1 : seq + 1;
        }
        reserved.put(key, seq);
        return String.format("%s-%s-%04d", prefix, day, seq);
    }

    private int queryMaxSeq(String prefix, String day, String table, String column) {
        try {
            Object max = entityManager
                    .createNativeQuery("SELECT MAX(" + column + ") FROM " + table
                            + " WHERE " + column + " LIKE :like")
                    .setParameter("like", prefix + "-" + day + "-%")
                    .getSingleResult();
            if (max == null) {
                return 0;
            }
            String s = String.valueOf(max);
            int idx = s.lastIndexOf('-');
            if (idx < 0 || idx + 1 >= s.length()) {
                return 0;
            }
            return Integer.parseInt(s.substring(idx + 1));
        } catch (Exception e) {
            // 查询失败时退化为时间片编号，保证仍然唯一
            return LocalDateTime.now().getSecond() * 100 + LocalDateTime.now().getMinute();
        }
    }

    private boolean exists(String prefix, String day, int seq, String table, String column) {
        try {
            Object cnt = entityManager
                    .createNativeQuery("SELECT COUNT(1) FROM " + table
                            + " WHERE " + column + " = :code")
                    .setParameter("code", String.format("%s-%s-%04d", prefix, day, seq))
                    .getSingleResult();
            return ((Number) cnt).longValue() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
