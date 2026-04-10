package com.project___10.demo.service;

import com.project___10.demo.dao.PortfolioRepository;
import com.project___10.demo.entity.Portfolio;
import com.project___10.demo.entity.TradeOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private final PortfolioRepository portfoliorepository;

    @Override
    public List<Portfolio> getPortfolioById(Long id) {
        return portfoliorepository.findByUserId(id);
    }

    @Override
    public BigDecimal calculateTotalMarketValue(List<Portfolio> portfolios) {
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        if (portfolios == null || portfolios.isEmpty()) {
            return totalMarketValue;
        }
        for (Portfolio portfolio : portfolios) {
            BigDecimal quantity = new BigDecimal(portfolio.getQuantity());
            // 注意：这里通常应该用“当前市场价”，如果没有，先用成本价代替
            BigDecimal price = portfolio.getAvgCost();
            totalMarketValue = totalMarketValue.add(quantity.multiply(price));
        }
        return totalMarketValue;
    }

    //核心业务：增加持仓（买入时）
    @Override
    @Transactional
    public void addPosition(Long userId, String ticker, Integer quantity, BigDecimal price) {
        Portfolio portfolio = portfoliorepository.findByUserIdAndTicker(userId, ticker);

        if (portfolio == null) {
            //1. 新建持仓
            portfolio = new Portfolio();
            portfolio.setUserId(userId);
            portfolio.setTicker(ticker);
            portfolio.setQuantity(quantity);
            portfolio.setAvgCost(price); // 初始成本就是买入价
        } else {
            //2. 更新持仓（涉及摊薄成本计算）
            //总成本 = (旧数量 * 旧成本) + (新数量 * 新单价)
            BigDecimal oldTotalCost = portfolio.getAvgCost().multiply(new BigDecimal(portfolio.getQuantity()));
            BigDecimal newBatchCost = price.multiply(new BigDecimal(quantity));
            int totalQuantity = portfolio.getQuantity() + quantity;

            //新平均成本 = 总成本 / 总数量
            BigDecimal newAvgCost = oldTotalCost.add(newBatchCost)
                    .divide(new BigDecimal(totalQuantity), 4, RoundingMode.HALF_UP);

            portfolio.setQuantity(totalQuantity);
            portfolio.setAvgCost(newAvgCost);
        }

        portfolio.setUpdatedAt(LocalDateTime.now()); // 加上更新时间
        portfoliorepository.save(portfolio);
    }

    //核心业务：减少持仓（卖出时）
    @Override
    @Transactional
    public void reducePosition(Long userId, String ticker, Integer quantity) {
        Portfolio portfolio = portfoliorepository.findByUserIdAndTicker(userId, ticker);

        // 防超卖校验
        if (portfolio == null || portfolio.getQuantity() < quantity) {
            throw new RuntimeException("持仓不足，无法卖出股票: " + ticker);
        }
        //n+1，防止高频交易

        portfolio.setQuantity(portfolio.getQuantity() - quantity);
        portfolio.setUpdatedAt(LocalDateTime.now());

        if (portfolio.getQuantity() == 0) {
            portfoliorepository.delete(portfolio); // 卖光了就删掉记录
        } else {
            portfoliorepository.save(portfolio);
        }
    }
}