package com.project___10.demo.controller;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.TradeOrder;
import com.project___10.demo.service.TradeApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeApplicationService tradeApplicationService;

    @PostMapping("/execute")
    public Result executeTrade(@RequestBody TradeOrder order) {
        if (order.getUserId() == null) {
            return Result.error("User is not logged in");
        }
        if (order.getTicker() == null || order.getTicker().trim().isEmpty()) {
            return Result.error("Ticker is required");
        }
        if (order.getDirection() == null || order.getDirection().trim().isEmpty()) {
            return Result.error("Trade direction is required");
        }
        if (!"BUY".equalsIgnoreCase(order.getDirection()) && !"SELL".equalsIgnoreCase(order.getDirection())) {
            return Result.error("Trade direction must be BUY or SELL");
        }
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            return Result.error("Trade quantity must be greater than 0");
        }
        if (order.getPrice() == null || order.getPrice().signum() <= 0) {
            return Result.error("Price must be greater than 0");
        }

        return tradeApplicationService.executeTrade(order);
    }
}
