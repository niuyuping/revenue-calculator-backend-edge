package jp.asatex.revenue_calculator_backend_edge.bootstrap;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;

/**
 * Provide a custom RestTemplate during Config Data bootstrap so that
 * Spring Cloud Config Client adds a Google ID token (Cloud Run) to requests.
 */
public class CloudRunConfigClientBootstrapRegistryInitializer implements BootstrapRegistryInitializer {

    private static final Logger log = LoggerFactory.getLogger(CloudRunConfigClientBootstrapRegistryInitializer.class);

    @Override
    public void initialize(BootstrapRegistry registry) {
        System.out.println("[Bootstrap] CloudRun RestTemplate initializer invoked");
        registry.register(RestTemplate.class, context -> {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getInterceptors().add(new CloudRunIdTokenInterceptor());
            log.info("CloudRun RestTemplate registered for Config Client");
            System.out.println("[Bootstrap] CloudRun RestTemplate registered for Config Client");
            return restTemplate;
        });
    }

    static class CloudRunIdTokenInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            URI uri = request.getURI();
            String audience = toOrigin(uri);
            try {
                String token = maybeFetchIdToken(audience);
                if (token != null) {
                    request.getHeaders().set("Authorization", "Bearer " + token);
                    System.out.println("[Bootstrap] Authorization header added for uri=" + uri);
                } else {
                    System.out.println("[Bootstrap] Skipping token (no IdTokenProvider), uri=" + uri);
                }
            } catch (Exception e) {
                // 保守处理：令牌获取失败不阻断本地开发
                System.out.println("[Bootstrap] Failed to fetch ID token: " + e);
                log.debug("Failed to fetch ID token", e);
            }
            return execution.execute(request, body);
        }

        private static String toOrigin(URI uri) {
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null) {
                return uri.toString();
            }
            boolean defaultPort = (port == -1) || ("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80);
            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        }

        private static String maybeFetchIdToken(String audience) throws IOException {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            if (!(credentials instanceof IdTokenProvider)) {
                return null;
            }
            IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
                    .setIdTokenProvider((IdTokenProvider) credentials)
                    .setTargetAudience(audience)
                    .build();
            return tokenCredentials.refreshAccessToken().getTokenValue();
        }
    }
}


