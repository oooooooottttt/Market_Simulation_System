package com.project___10.demo.dao;

import com.project___10.demo.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    // 按照交易时间倒序查询某个用户的所有流水
    List<TradeOrder> findByUserIdOrderByTradeTimeDesc(Long userId);
}