package com.project___10.demo.controller;


import com.project___10.demo.entity.Stock;
import com.project___10.demo.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor // Lombok 会自动为所有带 final 的字段生成构造函数！
public class StockController {
    private final StockService stockService;

    @GetMapping("/stocks/{ticker}")
    public Stock getUserById(@PathVariable("ticker") String ticker) {
        return stockService.getStockByTicker(ticker);
    }
}
