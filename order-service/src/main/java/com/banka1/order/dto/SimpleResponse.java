package com.banka1.order.dto;

public record SimpleResponse(String status, String message) {
    public static SimpleResponse success(String message) {
        return new SimpleResponse("success", message);
    }
    public static SimpleResponse fail(String message) {
        return new SimpleResponse("fail", message);
    }
}