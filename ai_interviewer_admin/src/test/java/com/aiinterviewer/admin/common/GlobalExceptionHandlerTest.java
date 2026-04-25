package com.aiinterviewer.admin.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.exception.GlobalExceptionHandler;
import com.aiinterviewer.admin.common.model.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void mapsAdminBusinessExceptionToResultFailure() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-test-001");

        ResponseEntity<Result<Void>> response = handler.handleAdminBusinessException(
                new AdminBusinessException(4001, "管理员不存在"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(4001);
        assertThat(response.getBody().getMessage()).isEqualTo("管理员不存在");
        assertThat(response.getBody().getTraceId()).isEqualTo("trace-test-001");
        assertThat(response.getBody().getTimestamp()).isPositive();
    }

    @Test
    void mapsAdminBusinessExceptionCode401ToUnauthorized() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Result<Void>> response =
                handler.handleAdminBusinessException(new AdminBusinessException(401, "未登录"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("未登录");
    }

    @Test
    void mapsAdminBusinessExceptionCode404ToNotFound() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Result<Void>> response =
                handler.handleAdminBusinessException(new AdminBusinessException(404, "资源不存在"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("资源不存在");
    }

    @Test
    void mapsAdminBusinessExceptionCode500ToInternalServerError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Result<Void>> response =
                handler.handleAdminBusinessException(new AdminBusinessException(500, "系统异常"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("系统异常");
    }
}
