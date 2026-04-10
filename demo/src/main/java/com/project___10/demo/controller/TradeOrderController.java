package com.project___10.demo.controller;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.TradeOrder;
import com.project___10.demo.service.TradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradeOrderController {
    private final TradeOrderService tradeOrderService;

    @GetMapping("/user/{userId}")
    public Result<List<TradeOrder>> getUserOrders(@PathVariable("userId") Long userId) {
        //把查出来的List装进 Result 盒子里
        List<TradeOrder> orders = tradeOrderService.getUserOrders(userId);
        return Result.success(orders);
    }

}