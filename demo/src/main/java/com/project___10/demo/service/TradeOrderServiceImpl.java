package com.project___10.demo.service.impl;

import com.project___10.demo.dao.TradeOrderRepository;
import com.project___10.demo.entity.TradeOrder;
import com.project___10.demo.service.TradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeOrderServiceImpl implements TradeOrderService {

    private final TradeOrderRepository tradeOrderRepository;

    @Override
    public List<TradeOrder> getUserOrders(Long userId){
        return tradeOrderRepository.findByUserIdOrderByTradeTimeDesc(userId);
    }

    @Override
    @Transactional
    public TradeOrder saveOrder(TradeOrder order) {

        //1. 安全校验：抛出异常，不返回 Result
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new IllegalArgumentException("订单股数不合法");
        }

        //2. 核心逻辑：后端重新计算总额
        BigDecimal calculatedTotal = order.getPrice()
                .multiply(new BigDecimal(order.getQuantity()))
                .add(order.getFee());
        order.setTotalAmount(calculatedTotal);

        //3. 补全后端信息
        order.setTradeTime(LocalDateTime.now());

        //4. 执行入库：交给 JPA 搬运工
        return tradeOrderRepository.save(order);
    }
}