package com.project___10.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name="biz_stock")
public class Stock {
    @Id
    private String ticker;
    private String name;
    private String sector;
    private Integer status;
    private LocalDateTime createdAt;
}