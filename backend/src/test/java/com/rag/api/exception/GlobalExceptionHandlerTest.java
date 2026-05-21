package com.rag.api.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.io.IOException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesIllegalArgumentExceptionAsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("参数错误");
        var response = handler.handleBadRequest(ex);
        
        assertEquals(400, response.getStatusCode().value());
        assertEquals("参数错误", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handlesIllegalStateExceptionAsInternalServerError() {
        IllegalStateException ex = new IllegalStateException("状态错误");
        var response = handler.handleIllegalState(ex);
        
        assertEquals(500, response.getStatusCode().value());
        assertEquals("状态错误", response.getBody().message());
    }

    @Test
    void handlesIOExceptionAsInternalServerError() {
        IOException ex = new IOException("读写失败");
        var response = handler.handleIOException(ex);
        
        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().message().contains("文件操作失败"));
    }

    @Test
    void handlesGenericExceptionAsInternalServerError() {
        Exception ex = new RuntimeException("未知错误");
        var response = handler.handleGeneric(ex);
        
        assertEquals(500, response.getStatusCode().value());
        assertEquals("服务器内部错误", response.getBody().message());
    }
}