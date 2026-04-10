package com.project___10.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user_status_log")
public class UserStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long adminUserId;
    private Long targetUserId;
    private Integer operationType;
    private Integer targetStatus;
    private String reason;
    private LocalDateTime createdAt;
}