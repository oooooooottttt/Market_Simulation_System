package com.project___10.demo.dao;

import com.project___10.demo.entity.KLineDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KLineDailyRepository extends JpaRepository<KLineDaily, Long> {
    // 按日期升序查询某只股票的历史 K 线（画图表通常需要升序）
    List<KLineDaily> findByTickerOrderByTradeDateAsc(String ticker);
}