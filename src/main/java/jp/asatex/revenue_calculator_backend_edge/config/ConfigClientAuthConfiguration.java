package jp.asatex.revenue_calculator_backend_edge.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
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

    @Value("${spring.cloud.config.uri}")
    private String configServerUri;

    // 供 Spring Cloud Config Client 使用的 RestTemplate 自定义器（经典客户端）
    // Bean 名称需为 configServerRestTemplateCustomizer
    @Bean(name = "configServerRestTemplateCustomizer")
    public RestTemplateCustomizer configServerRestTemplateCustomizer() {
        return (RestTemplate restTemplate) -> restTemplate.getInterceptors().add(new IdTokenInterceptor(configServerUri));
    }

    // 供 Spring Cloud Config Client 使用的 WebClient 自定义器（若客户端走 WebClient）
    // Bean 名称需为 configServerWebClientCustomizer
    @Bean(name = "configServerWebClientCustomizer")
    public WebClientCustomizer configServerWebClientCustomizer() {
        return (WebClient.Builder builder) -> builder.filter((request, next) ->
                Mono.defer(() -> {
                    try {
                        String idToken = fetchIdToken(configServerUri);
                        ClientRequest mutated = ClientRequest.from(request)
                                .headers(h -> h.set("Authorization", "Bearer " + idToken))
                                .build();
                        return next.exchange(mutated);
                    } catch (IOException e) {
                        return Mono.error(e);
                    }
                })
        );
    }

    private static String fetchIdToken(String audience) throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        IdTokenProvider provider = (credentials instanceof IdTokenProvider) ? (IdTokenProvider) credentials : null;
        if (provider == null) {
            throw new IllegalStateException("当前环境的 GoogleCredentials 不支持 IdTokenProvider，请在 Cloud Run/带服务账号的环境运行");
        }
        IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(provider)
                .setTargetAudience(audience)
                .build();
        return tokenCredentials.refreshAccessToken().getTokenValue();
    }

    private static class IdTokenInterceptor implements ClientHttpRequestInterceptor {
        private final String audience;

        private IdTokenInterceptor(String audience) {
            this.audience = audience;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            String idToken = fetchIdToken(audience);
            request.getHeaders().set("Authorization", "Bearer " + idToken);
            return execution.execute(request, body);
        }
    }
}


