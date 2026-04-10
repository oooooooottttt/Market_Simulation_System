package com.project___10.demo.service;

import com.project___10.demo.dto.KLineDataDTO;

public interface MarketDataService {
    public KLineDataDTO fetchKLineData(String symbol);
    KLineDataDTO callAlphaVantageApi(String symbol);
    KLineDataDTO generateMockData();
}