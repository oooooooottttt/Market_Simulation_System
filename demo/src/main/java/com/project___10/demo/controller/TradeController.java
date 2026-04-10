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
        if (order.getUserId() == null) return Result.error("用户未登录！");
        if (order.getQuantity() == null || order.getQuantity() <= 0) return Result.error("交易数量必须大于 0！");

        return tradeApplicationService.executeTrade(order);
    }
}
