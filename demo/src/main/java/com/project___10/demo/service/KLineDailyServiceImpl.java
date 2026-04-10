package com.project___10.demo.service;

import com.project___10.demo.dao.KLineDailyRepository;
import com.project___10.demo.entity.KLineDaily;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KLineDailyServiceImpl implements KLineDailyService {
    private final KLineDailyRepository kLineDailyRepository;

    @Override
    public List<KLineDaily> getKLinesByTicker(String ticker) {
        return kLineDailyRepository.findByTickerOrderByTradeDateAsc(ticker);
    }
}