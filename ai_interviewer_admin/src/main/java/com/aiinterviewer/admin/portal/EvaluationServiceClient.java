package com.aiinterviewer.admin.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 评估微服务客户端 — 门户生成评估报告时代理调用。
 * 评估生成逻辑（聚合评分、分支守卫）在 evaluation 服务内，不做本地复制。
 */
@Component
public class EvaluationServiceClient {

    public record ExchangeResponse(HttpStatusCode status, String body) {
    }

    private final RestClient restClient;
    private final String baseUrl;

    public EvaluationServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${evaluation-service.base-url:http://localhost:9005}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public ExchangeResponse generateReport(String sessionId, Long userId) {
        return restClient.post()
                .uri(baseUrl + "/evaluations/" + sessionId)
                .headers(headers -> headers.set("X-User-Id", String.valueOf(userId)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange((request, response) -> new ExchangeResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)));
    }
}
