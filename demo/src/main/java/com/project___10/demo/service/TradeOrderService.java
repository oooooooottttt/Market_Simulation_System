package com.project___10.demo.service;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.TradeOrder;

import java.math.BigDecimal;
import java.util.List;

public interface TradeOrderService {
    List<TradeOrder> getUserOrders(Long userId);

    TradeOrder saveOrder(TradeOrder order);

}