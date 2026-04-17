package com.project___10.demo.dao;

import com.project___10.demo.entity.KLineDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KLineDailyRepository extends JpaRepository<KLineDaily, Long> {
    List<KLineDaily> findByTickerOrderByTradeDateAsc(String ticker);

    List<KLineDaily> findTop100ByTickerOrderByTradeDateDesc(String ticker);

    Optional<KLineDaily> findTopByTickerOrderByTradeDateDesc(String ticker);

    boolean existsByTickerAndTradeDate(String ticker, LocalDate tradeDate);
}
