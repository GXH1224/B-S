package com.ees.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 系统可配置项，前缀 ees.*，与 application.yml 对应。
 */
@ConfigurationProperties(prefix = "ees")
public class EesProperties {

    private Security security = new Security();
    private Storage storage = new Storage();
    private Algorithm algorithm = new Algorithm();
    private DatasourceConnector datasourceConnector = new DatasourceConnector();
    private Report report = new Report();
    private Audit audit = new Audit();

    public static class Security {
        /**
         * JWT 签名密钥。此处<b>刻意不提供任何默认值</b>：内置默认密钥会随源码一起交付，
         * 等同公开密钥，可被用于伪造管理员令牌。
         * 空值由 {@code JwtSecretProvider} 按"环境变量 → 本地密钥文件 → 首次启动自动生成"的顺序解析。
         */
        private String jwtSecret = "";
        private int jwtExpireMinutes = 480;
        private String jwtIssuer = "ees";
        private int maxLoginFail = 5;
        private int lockMinutes = 15;
        private List<String> permitPaths = new ArrayList<>(Arrays.asList(
                "/api/auth/login", "/api/auth/captcha", "/api/health", "/api/health/**",
                "/api/info", "/error"));

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public int getJwtExpireMinutes() {
            return jwtExpireMinutes;
        }

        public void setJwtExpireMinutes(int jwtExpireMinutes) {
            this.jwtExpireMinutes = jwtExpireMinutes;
        }

        public String getJwtIssuer() {
            return jwtIssuer;
        }

        public void setJwtIssuer(String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        public int getMaxLoginFail() {
            return maxLoginFail;
        }

        public void setMaxLoginFail(int maxLoginFail) {
            this.maxLoginFail = maxLoginFail;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }

        public List<String> getPermitPaths() {
            return permitPaths;
        }

        public void setPermitPaths(List<String> permitPaths) {
            this.permitPaths = permitPaths;
        }
    }

    public static class Storage {
        private String root = "./data";
        private String uploadDir = "./data/upload";
        private String snapshotDir = "./data/snapshot";
        private String reportDir = "./data/report";
        private String tempDir = "./data/temp";
        private String allowedExtensions = "xlsx,xls,csv,json,zip,txt";
        private int maxUploadMb = 200;

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public String getSnapshotDir() {
            return snapshotDir;
        }

        public void setSnapshotDir(String snapshotDir) {
            this.snapshotDir = snapshotDir;
        }

        public String getReportDir() {
            return reportDir;
        }

        public void setReportDir(String reportDir) {
            this.reportDir = reportDir;
        }

        public String getTempDir() {
            return tempDir;
        }

        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }

        public String getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(String allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public int getMaxUploadMb() {
            return maxUploadMb;
        }

        public void setMaxUploadMb(int maxUploadMb) {
            this.maxUploadMb = maxUploadMb;
        }

        public List<String> allowedExtensionList() {
            List<String> list = new ArrayList<>();
            for (String s : allowedExtensions.split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim().toLowerCase());
                }
            }
            return list;
        }
    }

    public static class Algorithm {
        /** JAVA_NATIVE 内置实现 / CPP_ENGINE 外部 C++ 算法服务 */
        private String engine = "JAVA_NATIVE";
        private String version = "java-1.0.0";
        private String cppEngineUrl = "http://127.0.0.1:9090";
        private int cppEngineTimeoutSeconds = 600;
        private int maxConcurrentTasks = 4;
        private int taskTimeoutSeconds = 900;
        private double ahpCrThreshold = 0.1;
        private int scoreScale = 4;
        private int displayScale = 2;
        private double floatAbsTolerance = 0.0001;
        private double floatRelTolerance = 0.0001;

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getCppEngineUrl() {
            return cppEngineUrl;
        }

        public void setCppEngineUrl(String cppEngineUrl) {
            this.cppEngineUrl = cppEngineUrl;
        }

        public int getCppEngineTimeoutSeconds() {
            return cppEngineTimeoutSeconds;
        }

        public void setCppEngineTimeoutSeconds(int cppEngineTimeoutSeconds) {
            this.cppEngineTimeoutSeconds = cppEngineTimeoutSeconds;
        }

        public int getMaxConcurrentTasks() {
            return maxConcurrentTasks;
        }

        public void setMaxConcurrentTasks(int maxConcurrentTasks) {
            this.maxConcurrentTasks = maxConcurrentTasks;
        }

        public int getTaskTimeoutSeconds() {
            return taskTimeoutSeconds;
        }

        public void setTaskTimeoutSeconds(int taskTimeoutSeconds) {
            this.taskTimeoutSeconds = taskTimeoutSeconds;
        }

        public double getAhpCrThreshold() {
            return ahpCrThreshold;
        }

        public void setAhpCrThreshold(double ahpCrThreshold) {
            this.ahpCrThreshold = ahpCrThreshold;
        }

        public int getScoreScale() {
            return scoreScale;
        }

        public void setScoreScale(int scoreScale) {
            this.scoreScale = scoreScale;
        }

        public int getDisplayScale() {
            return displayScale;
        }

        public void setDisplayScale(int displayScale) {
            this.displayScale = displayScale;
        }

        public double getFloatAbsTolerance() {
            return floatAbsTolerance;
        }

        public void setFloatAbsTolerance(double floatAbsTolerance) {
            this.floatAbsTolerance = floatAbsTolerance;
        }

        public double getFloatRelTolerance() {
            return floatRelTolerance;
        }

        public void setFloatRelTolerance(double floatRelTolerance) {
            this.floatRelTolerance = floatRelTolerance;
        }
    }

    public static class DatasourceConnector {
        private int connectTimeoutSeconds = 15;
        private int queryTimeoutSeconds = 120;
        private int maxRows = 200000;
        private String forbiddenKeywords = "insert,update,delete,drop,truncate,alter,create,grant,revoke,merge,call,execute";

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getQueryTimeoutSeconds() {
            return queryTimeoutSeconds;
        }

        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
            this.queryTimeoutSeconds = queryTimeoutSeconds;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        public String getForbiddenKeywords() {
            return forbiddenKeywords;
        }

        public void setForbiddenKeywords(String forbiddenKeywords) {
            this.forbiddenKeywords = forbiddenKeywords;
        }

        public List<String> forbiddenKeywordList() {
            List<String> list = new ArrayList<>();
            for (String s : forbiddenKeywords.split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim().toLowerCase());
                }
            }
            return list;
        }
    }

    public static class Report {
        private String company = "效能评估系统";
        private String pdfFontPath = "";
        private String pdfFontName = "STSong-Light";
        private String pdfFontEncoding = "UniGB-UCS2-H";
        private List<String> pdfFontSearchPaths = new ArrayList<>();

        public String getCompany() {
            return company;
        }

        public void setCompany(String company) {
            this.company = company;
        }

        public String getPdfFontPath() {
            return pdfFontPath;
        }

        public void setPdfFontPath(String pdfFontPath) {
            this.pdfFontPath = pdfFontPath;
        }

        public String getPdfFontName() {
            return pdfFontName;
        }

        public void setPdfFontName(String pdfFontName) {
            this.pdfFontName = pdfFontName;
        }

        public String getPdfFontEncoding() {
            return pdfFontEncoding;
        }

        public void setPdfFontEncoding(String pdfFontEncoding) {
            this.pdfFontEncoding = pdfFontEncoding;
        }

        public List<String> getPdfFontSearchPaths() {
            return pdfFontSearchPaths;
        }

        public void setPdfFontSearchPaths(List<String> pdfFontSearchPaths) {
            this.pdfFontSearchPaths = pdfFontSearchPaths;
        }
    }

    public static class Audit {
        private boolean enabled = true;
        private String methods = "POST,PUT,DELETE,PATCH";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMethods() {
            return methods;
        }

        public void setMethods(String methods) {
            this.methods = methods;
        }

        public List<String> methodList() {
            List<String> list = new ArrayList<>();
            for (String s : methods.split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim().toUpperCase());
                }
            }
            return list;
        }
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public DatasourceConnector getDatasourceConnector() {
        return datasourceConnector;
    }

    public void setDatasourceConnector(DatasourceConnector datasourceConnector) {
        this.datasourceConnector = datasourceConnector;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }
}
