package com.example.linuxterminal.global.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 가장 먼저 실행되어야 함
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // 1. Trace ID 생성 (또는 헤더에서 가져오기)
        String traceId = UUID.randomUUID().toString();

        // 2. MDC에 저장 (로그백 등에서 사용 가능)
        MDC.put(TRACE_ID_KEY, traceId);

        // 3. 응답 헤더에도 넣어줌 (프론트엔드 디버깅용)
        res.setHeader("X-Trace-Id", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 4. 요청 처리 후 MDC 정리 (쓰레드 풀 재사용 시 오염 방지)
            MDC.clear();
        }
    }
}