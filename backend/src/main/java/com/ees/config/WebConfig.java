package com.ees.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Web 层配置：显式声明 JSON 使用 UTF-8，避免部分客户端按 ISO-8859-1 解析导致中文乱码。
 *
 * <p>前端静态资源托管不使用自定义 ResourceHandler，而是走 Spring Boot 标准配置
 * <code>spring.web.resources.static-locations</code>（见 application-dev.yml）：
 * 这样首页 <code>/</code> 会由 Spring Boot 的 WelcomePageHandlerMapping 自动映射到
 * index.html，无需手写 view controller（手写容易出现 "/" → "/index.html" → 自身 的无限转发）。
 * 前端使用 hash 路由，因此也不需要 history fallback。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        List<MediaType> jsonTypes = new ArrayList<>();
        jsonTypes.add(new MediaType("application", "json", StandardCharsets.UTF_8));
        jsonTypes.add(new MediaType("application", "*+json", StandardCharsets.UTF_8));

        // 仅调整默认 Jackson 转换器的编码与支持的媒体类型，保留其余默认转换器（Resource、byte[] 等）
        for (HttpMessageConverter<?> c : converters) {
            if (c instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setSupportedMediaTypes(jsonTypes);
                jackson.setDefaultCharset(StandardCharsets.UTF_8);
            }
            if (c instanceof StringHttpMessageConverter stringConverter) {
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }

    @Bean
    public StringHttpMessageConverter utf8StringConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }
}
