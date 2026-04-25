package com.aiinterviewer.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 日志过滤器
 * 记录请求日志，生成TraceId
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String START_TIME_KEY = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 生成或获取TraceId
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 记录开始时间
        exchange.getAttributes().put(START_TIME_KEY, System.currentTimeMillis());

        // 将TraceId添加到请求头
        String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        log.info("[{}] {} {} from {}",
                finalTraceId,
                request.getMethod(),
                request.getPath().value(),
                request.getRemoteAddress());

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    Long startTime = exchange.getAttribute(START_TIME_KEY);
                    if (startTime != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("[{}] Completed in {}ms with status {}",
                                finalTraceId,
                                duration,
                                exchange.getResponse().getStatusCode());
                    }
                }));
    }

    @Override
    public int getOrder() {
        return -200; // 最高优先级
    }
}
