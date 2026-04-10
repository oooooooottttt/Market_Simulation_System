package com.project___10.demo.service;

import com.project___10.demo.entity.KLineDaily;
import java.util.List;

public interface KLineDailyService {
    List<KLineDaily> getKLinesByTicker(String ticker);
}