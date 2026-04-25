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
}
