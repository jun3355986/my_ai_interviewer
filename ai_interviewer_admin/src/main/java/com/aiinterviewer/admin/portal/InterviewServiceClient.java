package com.aiinterviewer.admin.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 面试微服务的透传客户端。
 *
 * interview 服务自身无鉴权，依赖上游注入的 X-User-Id 并做数据所有权校验；
 * 门户层把管理端绑定的 userId 注入后转发，响应体（统一 Result JSON）原样回传，
 * 非 2xx（如 409 轮次冲突）也保持原始状态码与报文。
 */
@Component
public class InterviewServiceClient {

    public record UpstreamResponse(HttpStatusCode status, String body) {
        public boolean is2xxSuccessful() {
            return status.is2xxSuccessful();
        }
    }

    private final RestClient restClient;
    private final String baseUrl;

    public InterviewServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${interview-service.base-url:http://localhost:9003}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public UpstreamResponse get(String path, Long userId, String username) {
        return restClient.get()
                .uri(baseUrl + path)
                .headers(headers -> applyIdentity(headers, userId, username))
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> new UpstreamResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)));
    }

    public UpstreamResponse post(String path, Long userId, String username, String jsonBody) {
        return restClient.post()
                .uri(baseUrl + path)
                .headers(headers -> applyIdentity(headers, userId, username))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(jsonBody == null ? "{}" : jsonBody)
                .exchange((request, response) -> new UpstreamResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)));
    }

    private void applyIdentity(HttpHeaders headers, Long userId, String username) {
        headers.set("X-User-Id", String.valueOf(userId));
        if (username != null && !username.isBlank()) {
            headers.set("X-User-Name", username);
        }
    }
}
