package com.project___10.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name="biz_trade_order")
public class TradeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String ticker;
    private String direction;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal fee;
    private BigDecimal totalAmount;
    private LocalDateTime tradeTime;
}