package com.project___10.demo.dto;

import lombok.Data;

@Data
public class UserStatusUpdateRequest {
    private Long adminUserId;
    private Long userId;
    private Integer status;
    private String reason;
}