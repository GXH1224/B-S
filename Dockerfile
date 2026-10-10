# ============================================================================
#  效能评估系统 - 后端镜像
#
#  说明：本项目的前端页面已经打包进 evaluation-backend.jar 的 static 目录，
#        因此一个容器就能同时提供「页面 + 接口」，不需要额外的 Nginx 容器。
#
#  构建前提：backend/target/evaluation-backend.jar 必须已经存在
#            （由 mvn clean package 产出，交付包里已附带）
# ============================================================================
FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="EES Backend" \
      org.opencontainers.image.description="效能评估系统后端（含内嵌前端页面）" \
      org.opencontainers.image.version="1.0.0"

# 时区与编码
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

WORKDIR /app

# 只拷贝 jar，不拷贝源码
COPY backend/target/evaluation-backend.jar /app/app.jar

# 运行期数据目录：上传文件 / 快照 / 报告 / 日志 / JWT 签名密钥
# 该目录会通过 docker-compose 挂载为持久卷，容器重建不丢数据
RUN mkdir -p /app/data/upload /app/data/snapshot /app/data/report /app/data/temp /app/data/logs

EXPOSE 8080

# prod 档：连接池 50、日志级别 WARN、关闭 swagger
ENTRYPOINT ["java", \
  "-Dfile.encoding=UTF-8", \
  "-Duser.timezone=Asia/Shanghai", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar", \
  "--spring.profiles.active=prod"]
