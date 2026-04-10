package com.project___10.demo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserVo {
    private Long id;
    private String username;
    private BigDecimal balance;
    private Integer status;
    private String role;
    private LocalDateTime createdAt;
}
