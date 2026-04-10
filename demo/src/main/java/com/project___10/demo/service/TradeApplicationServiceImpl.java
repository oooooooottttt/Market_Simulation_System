package com.project___10.demo.service;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.TradeOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TradeApplicationServiceImpl implements TradeApplicationService {

    private final SysUserService sysUserService;
    private final PortfolioService portfolioService;
    private final TradeOrderService tradeOrderService;

    @Override
    @Transactional(rollbackFor = Exception.class)//金融订单开起事务，保持原子性
    public Result executeTrade(TradeOrder request) {

        //如果没有传手续费，默认给个 0
        if (request.getFee() == null) {
            request.setFee(BigDecimal.ZERO);
        }

        //提前计算总金额：(单价 * 数量) + 手续费
        BigDecimal calculatedTotal = request.getPrice()
                .multiply(new BigDecimal(request.getQuantity()))
                .add(request.getFee());
        request.setTotalAmount(calculatedTotal);

        //核心统筹逻辑：买卖分发
        if ("BUY".equalsIgnoreCase(request.getDirection())) {

            // 买入，扣钱 -> 加仓
            sysUserService.deductBalance(request.getUserId(), request.getTotalAmount());
            portfolioService.addPosition(request.getUserId(), request.getTicker(), request.getQuantity(), request.getPrice());

        } else if ("SELL".equalsIgnoreCase(request.getDirection())) {

            //卖出，减仓 -> 加钱
            portfolioService.reducePosition(request.getUserId(), request.getTicker(), request.getQuantity());
            sysUserService.addBalance(request.getUserId(), request.getTotalAmount());

        } else {
            throw new IllegalArgumentException("未知的交易方向: " + request.getDirection());
        }

        //记录交易流水小票
        TradeOrder savedOrder = tradeOrderService.saveOrder(request);

        //打包返回给前端
        return Result.success(savedOrder);
    }
}