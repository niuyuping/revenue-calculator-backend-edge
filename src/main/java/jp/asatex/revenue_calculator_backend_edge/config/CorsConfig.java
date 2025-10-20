package jp.asatex.revenue_calculator_backend_edge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // 允许的来源 - 使用通配符避免格式错误
        corsConfig.addAllowedOriginPattern("*");
        
        // 允许的 HTTP 方法 - 使用通配符
        corsConfig.addAllowedMethod("*");
        
        // 允许的请求头
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        
        // 是否允许携带凭证
        corsConfig.setAllowCredentials(false);
        
        // 预检请求的缓存时间（秒）
        corsConfig.setMaxAge(3600L);
        
        // 暴露的响应头
        corsConfig.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Methods",
            "Access-Control-Allow-Headers"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return new CorsWebFilter(source);
    }
}
