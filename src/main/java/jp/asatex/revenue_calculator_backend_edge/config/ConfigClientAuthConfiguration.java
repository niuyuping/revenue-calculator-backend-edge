package jp.asatex.revenue_calculator_backend_edge.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Configuration
@Profile({"prod", "test"})
public class ConfigClientAuthConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfigClientAuthConfiguration.class);

    @Value("${spring.cloud.config.uri}")
    private String configServerUri;

    // 供 Spring Cloud Config Client 使用的 RestTemplate 自定义器（经典客户端）
    // Bean 名称需为 configServerRestTemplateCustomizer
    @Bean(name = "configServerRestTemplateCustomizer")
    public RestTemplateCustomizer configServerRestTemplateCustomizer() {
        log.info("Registering RestTemplate customizer for Config Server auth, uri={}", configServerUri);
        return (RestTemplate restTemplate) -> restTemplate.getInterceptors().add(new IdTokenInterceptor(configServerUri));
    }

    // 供 Spring Cloud Config Client 使用的 WebClient 自定义器（若客户端走 WebClient）
    // Bean 名称需为 configServerWebClientCustomizer
    @Bean(name = "configServerWebClientCustomizer")
    public WebClientCustomizer configServerWebClientCustomizer() {
        log.info("Registering WebClient customizer for Config Server auth, uri={}", configServerUri);
        return (WebClient.Builder builder) -> builder.filter((request, next) ->
                Mono.defer(() -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("WebClient filter: preparing to add Authorization header, requestUri={}, audience={}", request.url(), configServerUri);
                        }
                        String idToken = fetchIdToken(configServerUri);
                        ClientRequest mutated = ClientRequest.from(request)
                                .headers(h -> h.set("Authorization", "Bearer " + idToken))
                                .build();
                        if (log.isDebugEnabled()) {
                            log.debug("WebClient filter: Authorization header added for requestUri={}", request.url());
                        }
                        return next.exchange(mutated);
                    } catch (IOException e) {
                        log.warn("WebClient filter: failed to fetch ID token for audience={}, error={}", configServerUri, e.toString());
                        return Mono.error(e);
                    }
                })
        );
    }

    private static String fetchIdToken(String audience) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Fetching ID token, audience={}", audience);
        }
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        IdTokenProvider provider = (credentials instanceof IdTokenProvider) ? (IdTokenProvider) credentials : null;
        if (provider == null) {
            throw new IllegalStateException("当前环境的 GoogleCredentials 不支持 IdTokenProvider，请在 Cloud Run/带服务账号的环境运行");
        }
        IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(provider)
                .setTargetAudience(audience)
                .build();
        String token = tokenCredentials.refreshAccessToken().getTokenValue();
        if (log.isDebugEnabled()) {
            // 不打印 token；仅打印长度用于诊断
            log.debug("Fetched ID token successfully (length={})", token != null ? token.length() : -1);
        }
        return token;
    }

    private static class IdTokenInterceptor implements ClientHttpRequestInterceptor {
        private final String audience;

        private IdTokenInterceptor(String audience) {
            this.audience = audience;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            if (log.isDebugEnabled()) {
                log.debug("RestTemplate interceptor: adding Authorization header, requestUri={}, audience={}", request.getURI(), audience);
            }
            String idToken = fetchIdToken(audience);
            request.getHeaders().set("Authorization", "Bearer " + idToken);
            if (log.isDebugEnabled()) {
                log.debug("RestTemplate interceptor: Authorization header added, requestUri={}", request.getURI());
            }
            return execution.execute(request, body);
        }
    }
}


