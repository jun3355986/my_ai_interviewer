package com.aiinterviewer.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * SSE透传过滤器
 * 确保SSE流式响应正确透传，禁用缓冲
 */
@Slf4j
@Component
public class SSEFilter implements GlobalFilter, Ordered {

    private static final Pattern INTERVIEW_RESUME_PATH = Pattern.compile("^/api/v1/interviews/[^/]+/resume$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 检测是否为SSE请求
        if (isSSERequest(path, request)) {
            log.debug("检测到SSE请求: {}", path);

            ServerHttpResponse response = exchange.getResponse();

            // 设置SSE响应头
            HttpHeaders headers = response.getHeaders();
            // 不强制设置Content-Type，由后端服务决定（避免报错时返回JSON但Header是EventStream）
            // headers.setContentType(MediaType.TEXT_EVENT_STREAM);
            headers.setCacheControl("no-cache");
            headers.set("Connection", "keep-alive");
            headers.set("X-Accel-Buffering", "no"); // 禁用Nginx缓冲
        }

        return chain.filter(exchange);
    }

    /**
     * 判断是否为SSE请求
     */
    private boolean isSSERequest(String path, ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.POST) {
            return false;
        }

        if ("/api/v1/interviews/chat".equals(path) || INTERVIEW_RESUME_PATH.matcher(path).matches()) {
            return true;
        }

        // 根据Accept头判断
        String accept = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Override
    public int getOrder() {
        return -1; // 高优先级，在认证过滤器之后
    }
}
