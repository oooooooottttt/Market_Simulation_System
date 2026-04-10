package com.project___10.demo.service;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.Portfolio;
import com.project___10.demo.entity.TradeOrder;
import com.project___10.demo.event.TradeSuccessEvent;

import java.math.BigDecimal;
import java.util.List;

public interface PortfolioService {
    List<Portfolio> getPortfolioById(Long id);

    BigDecimal calculateTotalMarketValue(List<Portfolio> portfolios);

    void addPosition(Long userId, String ticker, Integer quantity, BigDecimal price);

    void reducePosition(Long userId, String ticker, Integer quantity);
}
