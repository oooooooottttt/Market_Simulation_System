package com.project___10.demo.controller;

import com.project___10.demo.common.Result;
import com.project___10.demo.dto.UserDashboardDTO;
import com.project___10.demo.entity.Portfolio;
import com.project___10.demo.entity.TradeOrder;
import com.project___10.demo.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;

    @GetMapping("/userdashboards/{userId}")
    public UserDashboardDTO getUserDashboard(@PathVariable Long userId){
        //根据id查出用户的持仓股票列表
        List<Portfolio> portfolios = portfolioService.getPortfolioById(userId);

        //调用编写好的计算总市值的函数
        BigDecimal totalMarketValue = portfolioService.calculateTotalMarketValue(portfolios);

        //将数据一并打包进dto对象然后返回
        UserDashboardDTO userDashboardDto = new UserDashboardDTO();
        userDashboardDto.setTotalMarketValue(totalMarketValue);
        userDashboardDto.setPortfolios(portfolios);
        return userDashboardDto;
    }

}
