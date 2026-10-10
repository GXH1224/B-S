package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.domain.SysConfig;
import com.ees.repository.SysConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数服务。参数可在页面维护，算法与数据策略从数据库读取，避免硬编码。
 */
@Service
public class ConfigService {

    private final SysConfigRepository repository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ConfigService(SysConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String get(String key, String def) {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String v = repository.findByConfigKey(key).map(SysConfig::getConfigValue).orElse(def);
        if (v != null) {
            cache.put(key, v);
        }
        return v;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)).trim());
    }

    @Transactional(readOnly = true)
    public List<SysConfig> all() {
        return repository.findAllByOrderByConfigGroupAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> grouped() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (SysConfig c : all()) {
            out.computeIfAbsent(c.getConfigGroup() == null ? "SYSTEM" : c.getConfigGroup(),
                            k -> new LinkedHashMap<>())
                    .put(c.getConfigKey(), buildView(c));
        }
        return out;
    }

    private Map<String, Object> buildView(SysConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("value", c.getConfigValue());
        m.put("description", c.getDescription());
        m.put("editable", c.getEditable());
        return m;
    }

    @Transactional
    public SysConfig update(String key, String value, String operator) {
        SysConfig c = repository.findByConfigKey(key)
                .orElseThrow(() -> BizException.notFound("系统参数不存在：" + key));
        if (Boolean.FALSE.equals(c.getEditable())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该参数为系统保留项，不允许修改：" + key);
        }
        c.setConfigValue(value);
        SysConfig saved = repository.save(c);
        cache.put(key, value);
        return saved;
    }

    @Transactional
    public void updateBatch(Map<String, String> values, String operator) {
        values.forEach((k, v) -> {
            repository.findByConfigKey(k).ifPresent(c -> {
                if (Boolean.TRUE.equals(c.getEditable())) {
                    c.setConfigValue(v);
                    repository.save(c);
                    cache.put(k, v);
                }
            });
        });
    }
}
