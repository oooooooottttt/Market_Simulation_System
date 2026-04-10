package com.project___10.demo.controller;

import com.project___10.demo.entity.KLineDaily;
import com.project___10.demo.service.KLineDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/klines")
@RequiredArgsConstructor
public class KLineDailyController {
    private final KLineDailyService kLineDailyService;

    // 访问：http://localhost:8080/api/klines/NVDA
    @GetMapping("/{ticker}")
    public List<KLineDaily> getKLinesByTicker(@PathVariable("ticker") String ticker) {
        return kLineDailyService.getKLinesByTicker(ticker);
    }
}