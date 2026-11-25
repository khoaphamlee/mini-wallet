package com.khoaphamlee.wallet.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Sends 401 and 403 errors raised by Spring Security through {@code GlobalExceptionHandler}.
 *
 * <p>Security checks run in filters, before any controller, so {@code @RestControllerAdvice} never
 * sees these exceptions on its own. Handing them to Spring MVC's exception resolver gives them the
 * same ProblemDetail format, {@code code} and {@code traceId} as every other error.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /** No token, or a malformed, expired or forged one: 401. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
        response.setHeader("WWW-Authenticate", "Bearer");
        resolver.resolveException(request, response, null, ex);
    }

    /** Valid token, but the role is not allowed here: 403. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
        resolver.resolveException(request, response, null, ex);
    }
}
