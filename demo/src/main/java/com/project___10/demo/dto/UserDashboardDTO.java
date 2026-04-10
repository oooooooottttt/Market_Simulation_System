package com.project___10.demo.dto;

import com.project___10.demo.entity.Portfolio;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class UserDashboardDTO {
    // 1. 装总市值的大数字
    private BigDecimal totalMarketValue;

    // 2. 装用户的持仓列表
    private List<Portfolio> portfolios;


}
