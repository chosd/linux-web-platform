package com.example.linuxterminal.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GlobalLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 모니터링(Actuator), 정적 리소스, 대시보드 리소스 주기적 요청은 로깅/캐싱 생략
        if (uri.startsWith("/prometheus") || uri.startsWith("/health") || uri.equals("/favicon.ico") || uri.equals("/api/dashboard/resources")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Spring에서 제공하는 Caching Wrapper로 감싸기
        // (이 래퍼들은 내부적으로 byte array에 내용을 저장해둡니다)
        ContentCachingRequestWrapper wrappingRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappingResponse = new ContentCachingResponseWrapper(response);

        // 2. [수정] Trace ID는 직접 생성하지 않고 MDC에서 가져옵니다.
        // (TraceIdFilter가 가장 먼저 실행되어 이미 넣어뒀을 것입니다.)
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            // 혹시라도 필터가 적용 안 됐을 경우를 대비한 방어 로직
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }

        long startTime = System.currentTimeMillis();

        try {
            // 3. 다음 로직 실행
            filterChain.doFilter(wrappingRequest, wrappingResponse);

        } finally {
            // 4. 로그 출력
            long duration = System.currentTimeMillis() - startTime;

            // [수정] Request Body도 JSON일 때만 읽도록 변경 (파일 업로드 시 로그 폭탄 방지)
            String requestBody = "";
            if (isJson(request.getContentType())) {
                requestBody = new String(wrappingRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
            }

            String responseBody = "";
            if (isJson(wrappingResponse.getContentType())) {
                responseBody = new String(wrappingResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            }

            // [로그 출력]
            log.info("{} {} | Status: {} | Time: {}ms",
                    request.getMethod(), request.getRequestURI(),
                    wrappingResponse.getStatus(), duration);

            if (!requestBody.isEmpty()) {
                log.info("Req Body: {}", truncate(requestBody));
            }

            if (!responseBody.isEmpty()) {
                log.info("Res Body: {}", truncate(responseBody));
            }

            // [필수] 응답 본문 복사
            wrappingResponse.copyBodyToResponse();
        }
    }

    private boolean isJson(String contentType) {
        return contentType != null &&
                (contentType.contains("application/json") || contentType.contains("application/xml"));
    }

    private String truncate(String content) {
        if (content.length() > 1000) {
            return content.substring(0, 1000) + "...(truncated)";
        }
        return content;
    }
}