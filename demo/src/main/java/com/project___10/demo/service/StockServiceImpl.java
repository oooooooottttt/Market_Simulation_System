package com.project___10.demo.service;

import com.project___10.demo.dao.StockRepository;
import com.project___10.demo.entity.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {
    private final StockRepository stockRepository;
    @Override
    public Stock getStockByTicker(String ticker) {
        return stockRepository.findById(ticker).orElseThrow(RuntimeException::new);
    }
}
