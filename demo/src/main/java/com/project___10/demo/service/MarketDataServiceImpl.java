package com.project___10.demo.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.project___10.demo.dao.KLineDailyRepository;
import com.project___10.demo.dto.KLineDataDTO;
import com.project___10.demo.entity.KLineDaily;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private static final int CACHE_HOURS = 12;
    private static final int MAX_CHART_POINTS = 100;

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final KLineDailyRepository kLineDailyRepository;

    private final String apiKey = "Alphavantage-API-Key";

    @Override
    public KLineDataDTO fetchKLineData(String symbol) {
        String normalizedSymbol = normalizeTicker(symbol);
        String redisKey = buildRedisKey(normalizedSymbol);

        String cachedJson = redisTemplate.opsForValue().get(redisKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            log.info("Redis cache hit for {}", normalizedSymbol);
            return JSON.parseObject(cachedJson, KLineDataDTO.class);
        }

        KLineDataDTO dbSnapshot = getRecent100KLinesByTicker(normalizedSymbol);
        boolean hasDatabaseData = hasData(dbSnapshot);

        try {
            syncLatestRowsFromApi(normalizedSymbol);
        } catch (IllegalStateException ex) {
            if (!hasDatabaseData) {
                throw ex;
            }
            log.warn("API sync failed for {}, falling back to database snapshot: {}", normalizedSymbol, ex.getMessage());
        }

        KLineDataDTO dbDto = getRecent100KLinesByTicker(normalizedSymbol);
        if (!hasData(dbDto) && hasDatabaseData) {
            dbDto = dbSnapshot;
        }

        if (hasData(dbDto)) {
            writeToRedis(dbDto, redisKey);
            return dbDto;
        }

        throw new IllegalStateException("Market data unavailable for symbol: " + normalizedSymbol);
    }

    private void syncLatestRowsFromApi(String symbol) {
        Optional<KLineDaily> latestDbRecord = kLineDailyRepository.findTopByTickerOrderByTradeDateDesc(symbol);
        LocalDate latestDbDate = latestDbRecord.map(KLineDaily::getTradeDate).orElse(null);

        KLineDataDTO apiDto = callAlphaVantageApi(symbol);
        if (!hasData(apiDto)) {
            return;
        }

        saveNewRowsToDatabase(symbol, apiDto, latestDbDate);
    }

    private KLineDataDTO getRecent100KLinesByTicker(String symbol) {
        List<KLineDaily> kLines = kLineDailyRepository.findTop100ByTickerOrderByTradeDateDesc(symbol);
        Collections.reverse(kLines);
        return buildDtoFromEntities(kLines);
    }

    private KLineDataDTO buildDtoFromEntities(List<KLineDaily> kLines) {
        KLineDataDTO dto = new KLineDataDTO();
        List<String> dates = new ArrayList<>();
        List<List<BigDecimal>> values = new ArrayList<>();

        for (KLineDaily kLine : kLines) {
            dates.add(kLine.getTradeDate().toString());
            values.add(Arrays.asList(
                    kLine.getOpenPrice(),
                    kLine.getClosePrice(),
                    kLine.getLowPrice(),
                    kLine.getHighPrice()
            ));
        }

        dto.setDates(dates);
        dto.setValues(values);
        return dto;
    }

    private void writeToRedis(KLineDataDTO dto, String redisKey) {
        redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(dto), CACHE_HOURS, TimeUnit.HOURS);
        log.info("Redis cache refreshed for key {}", redisKey);
    }

    private void saveNewRowsToDatabase(String symbol, KLineDataDTO dto, LocalDate latestDbDate) {
        List<KLineDaily> list = new ArrayList<>();

        for (int i = 0; i < dto.getDates().size(); i++) {
            LocalDate tradeDate = LocalDate.parse(dto.getDates().get(i));
            if (latestDbDate != null && !tradeDate.isAfter(latestDbDate)) {
                continue;
            }
            if (kLineDailyRepository.existsByTickerAndTradeDate(symbol, tradeDate)) {
                continue;
            }

            List<BigDecimal> item = dto.getValues().get(i);
            KLineDaily entity = new KLineDaily();
            entity.setTicker(symbol);
            entity.setTradeDate(tradeDate);
            entity.setOpenPrice(item.get(0));
            entity.setClosePrice(item.get(1));
            entity.setLowPrice(item.get(2));
            entity.setHighPrice(item.get(3));
            entity.setVolume(0L);
            list.add(entity);
        }

        if (!list.isEmpty()) {
            kLineDailyRepository.saveAll(list);
            log.info("Inserted {} new K-line rows for {}", list.size(), symbol);
        }
    }

    @Override
    public KLineDataDTO callAlphaVantageApi(String symbol) {
        String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey;

        try {
            log.info("Requesting Alpha Vantage for {}", symbol);
            String response = restTemplate.getForObject(url, String.class);
            JSONObject root = JSON.parseObject(response);

            if (root.containsKey("Information") || root.containsKey("Note")) {
                log.error("Alpha Vantage rate limit reached for {}", symbol);
                throw new IllegalStateException("Market data unavailable for symbol: " + symbol);
            }

            JSONObject timeSeries = root.getJSONObject("Time Series (Daily)");
            if (timeSeries == null) {
                log.error("No market data found for {}", symbol);
                throw new IllegalStateException("Market data unavailable for symbol: " + symbol);
            }

            List<String> allDates = new ArrayList<>(timeSeries.keySet());
            Collections.sort(allDates);

            int startIdx = Math.max(0, allDates.size() - MAX_CHART_POINTS);
            List<String> dates = new ArrayList<>();
            List<List<BigDecimal>> values = new ArrayList<>();

            for (int i = startIdx; i < allDates.size(); i++) {
                String dateStr = allDates.get(i);
                JSONObject dayData = timeSeries.getJSONObject(dateStr);

                BigDecimal open = dayData.getBigDecimal("1. open").setScale(2, RoundingMode.HALF_UP);
                BigDecimal high = dayData.getBigDecimal("2. high").setScale(2, RoundingMode.HALF_UP);
                BigDecimal low = dayData.getBigDecimal("3. low").setScale(2, RoundingMode.HALF_UP);
                BigDecimal close = dayData.getBigDecimal("4. close").setScale(2, RoundingMode.HALF_UP);

                dates.add(dateStr);
                values.add(Arrays.asList(open, close, low, high));
            }

            KLineDataDTO realData = new KLineDataDTO();
            realData.setDates(dates);
            realData.setValues(values);
            return realData;
        } catch (Exception e) {
            log.error("Failed to fetch market data for {}: {}", symbol, e.getMessage());
            throw new IllegalStateException("Market data unavailable for symbol: " + symbol);
        }
    }

    private boolean hasData(KLineDataDTO dto) {
        return dto != null
                && dto.getDates() != null
                && !dto.getDates().isEmpty()
                && dto.getValues() != null
                && !dto.getValues().isEmpty();
    }

    private String buildRedisKey(String symbol) {
        return "market:kline:" + symbol;
    }

    private String normalizeTicker(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }
}
