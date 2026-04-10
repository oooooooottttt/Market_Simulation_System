package com.project___10.demo.controller;

import com.project___10.demo.dto.KLineDataDTO;
import com.project___10.demo.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market")
@CrossOrigin
@RequiredArgsConstructor // 注入神器
public class MarketDataController {

    private final MarketDataService marketDataService; // 把 Service 请过来

    @GetMapping("/kline/{symbol}")
    public KLineDataDTO getKLineData(@PathVariable String symbol) {
        return marketDataService.fetchKLineData(symbol);
    }
}