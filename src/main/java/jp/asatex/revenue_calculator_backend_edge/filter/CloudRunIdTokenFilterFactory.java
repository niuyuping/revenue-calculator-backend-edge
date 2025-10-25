package jp.asatex.revenue_calculator_backend_edge.filter;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;

@Component
@Profile(value = {"prod", "test"})
public class CloudRunIdTokenFilterFactory extends AbstractGatewayFilterFactory<CloudRunIdTokenFilterFactory.Config> {

    // 缓存基础凭证，避免每次请求重复初始化
    private final GoogleCredentials baseCredentials;

    public CloudRunIdTokenFilterFactory() throws IOException {
        super(Config.class);
        // 在 Cloud Run 中，默认应用凭证会自动使用运行时服务账号
        this.baseCredentials = GoogleCredentials.getApplicationDefault();
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.singletonList("targetUrl");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 目标服务 URL（例如 employee 服务）
            URI requestUri = exchange.getRequest().getURI();

            // Cloud Run audience 需要是完整的 origin（包含 https 与 host，不含 path）
            String audience = (config.getTargetUrl() != null && !config.getTargetUrl().isEmpty())
                    ? config.getTargetUrl()
                    : UriComponentsBuilder.fromUri(requestUri)
                        .scheme("https")
                        .host(requestUri.getHost())
                        .replacePath(null)
                        .build()
                        .toUriString();

            try {
                // 使用 IdTokenCredentials 基于服务账号为目标 audience 生成 ID Token
                IdTokenProvider idTokenProvider = (baseCredentials instanceof IdTokenProvider)
                        ? (IdTokenProvider) baseCredentials
                        : null;
                if (idTokenProvider == null) {
                    return Mono.error(new IllegalStateException("当前环境的 GoogleCredentials 不支持 IdTokenProvider，请在 Cloud Run/带服务账号的环境运行"));
                }

                IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
                        .setIdTokenProvider(idTokenProvider)
                        .setTargetAudience(audience)
                        .build();

                String idToken = tokenCredentials.refreshAccessToken().getTokenValue();

                // 将 ID Token 添加到下游请求头
                ServerHttpRequest requestWithToken = exchange.getRequest().mutate()
                        .header("Authorization", "Bearer " + idToken)
                        .build();

                return chain.filter(exchange.mutate().request(requestWithToken).build());
            } catch (IOException e) {
                return Mono.error(new IllegalStateException("无法为 Cloud Run 目标服务获取 ID Token", e));
            }
        };
    }

    // 用于 DSL 配置的内部类
    public static class Config {
        private String targetUrl;

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }
    }
}


