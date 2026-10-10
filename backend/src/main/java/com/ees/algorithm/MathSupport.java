package com.ees.algorithm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数值工具：矩阵运算、归一化、等级判定。
 */
public final class MathSupport {

    /** Saaty 随机一致性指标 RI 表（n = 1..15） */
    private static final double[] RI_TABLE = {
            0.00, 0.00, 0.58, 0.90, 1.12, 1.24, 1.32, 1.41, 1.45, 1.49, 1.51, 1.54, 1.56, 1.57, 1.59
    };

    private MathSupport() {
    }

    public static double ri(int n) {
        if (n <= 0) {
            return 0d;
        }
        if (n <= RI_TABLE.length) {
            return RI_TABLE[n - 1];
        }
        // n > 15 时按 1.59 外推（Saaty 表的常见约定）
        return 1.59 + (n - 15) * 0.01;
    }

    public static BigDecimal round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    public static double safe(double v) {
        return (Double.isNaN(v) || Double.isInfinite(v)) ? 0d : v;
    }

    public static double sum(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s;
    }

    public static double[] normalizeToSum(double[] v) {
        double s = sum(v);
        double[] out = new double[v.length];
        if (Math.abs(s) < 1e-15) {
            double each = v.length == 0 ? 0 : 1.0 / v.length;
            for (int i = 0; i < v.length; i++) {
                out[i] = each;
            }
            return out;
        }
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i] / s;
        }
        return out;
    }

    /** 矩阵乘向量。 */
    public static double[] multiply(double[][] a, double[] x) {
        int n = a.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = 0; j < x.length; j++) {
                s += a[i][j] * x[j];
            }
            y[i] = s;
        }
        return y;
    }

    /**
     * 幂法求判断矩阵主特征向量（权重向量）。
     * 相比几何平均法更接近标准 AHP 定义，且数值稳定。
     */
    public static double[] principalEigenVector(double[][] a, int maxIter, double tol) {
        int n = a.length;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = 1.0 / n;
        }
        for (int iter = 0; iter < maxIter; iter++) {
            double[] v = multiply(a, w);
            double[] next = normalizeToSum(v);
            double diff = 0;
            for (int i = 0; i < n; i++) {
                diff += Math.abs(next[i] - w[i]);
            }
            w = next;
            if (diff < tol) {
                break;
            }
        }
        return w;
    }

    /** 几何平均法权重（用于交叉校验）。 */
    public static double[] geometricMeanWeights(double[][] a) {
        int n = a.length;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            double prod = 1;
            for (int j = 0; j < n; j++) {
                prod *= Math.max(a[i][j], 1e-15);
            }
            w[i] = Math.pow(prod, 1.0 / n);
        }
        return normalizeToSum(w);
    }

    /** 最大特征值 λmax = mean( (Aw)_i / w_i )。 */
    public static double lambdaMax(double[][] a, double[] w) {
        int n = a.length;
        double[] aw = multiply(a, w);
        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(w[i]) > 1e-15) {
                sum += aw[i] / w[i];
                cnt++;
            }
        }
        return cnt == 0 ? n : sum / cnt;
    }

    /**
     * 正向 / 负向 / 区间型指标标准化到 [0,1]（对应方案 6.4 实施要求）。
     */
    public static double normalize(double value, String direction,
                                   Double min, Double max,
                                   Double intervalLower, Double intervalUpper) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0d;
        }
        double lo = min == null ? 0d : min;
        double hi = max == null ? 1d : max;
        double span = hi - lo;
        if (Math.abs(span) < 1e-15) {
            return 1d;
        }
        String dir = direction == null ? "POSITIVE" : direction.toUpperCase();
        double r;
        switch (dir) {
            case "NEGATIVE" -> r = (hi - value) / span;
            case "INTERVAL" -> {
                double lower = intervalLower == null ? lo : intervalLower;
                double upper = intervalUpper == null ? hi : intervalUpper;
                if (lower > upper) {
                    double t = lower;
                    lower = upper;
                    upper = t;
                }
                if (value >= lower && value <= upper) {
                    r = 1d;
                } else if (value < lower) {
                    double d = lower - lo;
                    r = Math.abs(d) < 1e-15 ? 0d : 1d - (lower - value) / d;
                } else {
                    double d = hi - upper;
                    r = Math.abs(d) < 1e-15 ? 0d : 1d - (value - upper) / d;
                }
            }
            default -> r = (value - lo) / span;
        }
        return Math.max(0d, Math.min(1d, r));
    }

    /** 按等级阈值（降序）判定等级。 */
    public static String gradeOf(double score, double[] thresholds, String[] names) {
        if (thresholds == null || names == null || thresholds.length == 0) {
            return null;
        }
        for (int i = 0; i < thresholds.length && i < names.length; i++) {
            if (score >= thresholds[i]) {
                return names[i];
            }
        }
        return names[names.length - 1];
    }

    /** 判断矩阵互反性与对角线校验。 */
    public static String validateJudgmentMatrix(double[][] a) {
        if (a == null || a.length == 0) {
            return "判断矩阵为空";
        }
        int n = a.length;
        for (int i = 0; i < n; i++) {
            if (a[i] == null || a[i].length != n) {
                return "判断矩阵必须为方阵，第 " + (i + 1) + " 行维度不一致";
            }
        }
        if (n < 2) {
            return "判断矩阵阶数必须大于 1";
        }
        for (int i = 0; i < n; i++) {
            if (Math.abs(a[i][i] - 1.0) > 1e-6) {
                return "判断矩阵对角线元素必须为 1，第 " + (i + 1) + " 行第 " + (i + 1) + " 列为 " + a[i][i];
            }
            for (int j = 0; j < n; j++) {
                if (a[i][j] <= 0) {
                    return "判断矩阵元素必须为正数，位置 (" + (i + 1) + "," + (j + 1) + ") = " + a[i][j];
                }
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double prod = a[i][j] * a[j][i];
                if (Math.abs(prod - 1.0) > 0.05) {
                    return String.format("判断矩阵互反关系不成立：a(%d,%d)*a(%d,%d)=%.4f，应接近 1",
                            i + 1, j + 1, j + 1, i + 1, prod);
                }
            }
        }
        return null;
    }
}
