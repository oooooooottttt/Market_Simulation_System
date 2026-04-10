package com.project___10.demo.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.project___10.demo.dto.KLineDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    // 注入 Redis 和 HTTP 工具
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    //自动读取 application.yml 里绑定的环境变量
    //@Value("${market.api.key}")
    private final String apiKey = "Alphavantage-API-Key";
    // 核心入口：带缓存的高并发数据分发
    public KLineDataDTO fetchKLineData(String symbol) {
        String redisKey = "market:kline:" + symbol;

        //1.缓存拦截，去 Redis 里找
        String cachedData = redisTemplate.opsForValue().get(redisKey);
        if (cachedData != null) {
            log.info("⚡ 命中 Redis 缓存，直接返回！无需消耗 API 额度: {}", symbol);
            return JSON.parseObject(cachedData, KLineDataDTO.class);
        }

        //2. 缓存未命中，调用真实的 Alpha Vantage API
        log.warn("🐢 Redis 无缓存，准备消耗 1 次额度调用 Alpha Vantage: {}", symbol);
        KLineDataDTO dto = callAlphaVantageApi(symbol);

        //3.回写缓存，把拉到的数据存进 Redis，设置 12 小时过期！
        // 因为日K线一天只更新一次，存半天可以最大化节省每天 25 次的免费额度！
        if (dto != null && !dto.getDates().isEmpty()) {
            redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(dto), 12, TimeUnit.HOURS);
            log.info("数据已成功写入 Redis 缓存！");
        }

        return dto;
    }

    @Override
    public KLineDataDTO callAlphaVantageApi(String symbol) {
        // 构建 Alpha Vantage 的合法请求 URL
        String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey;

        try {
            log.info("发送合法请求: {}", url.replace(apiKey, "******")); // 日志里把秘钥打码，安全第一！
            String response = restTemplate.getForObject(url, String.class);
            JSONObject root = JSON.parseObject(response);

            //防熔断机制，Alpha Vantage 额度用光时，不会报 429，而是返回一个 Information 字段
            if (root.containsKey("Information") || root.containsKey("Note")) {
                log.error("Alpha Vantage API 免费额度已耗尽或请求超限！");
                return generateMockData();
            }

            JSONObject timeSeries = root.getJSONObject("Time Series (Daily)");
            if (timeSeries == null) {
                log.error("找不到行情数据，可能是股票代码错误: {}", symbol);
                return generateMockData();
            }

            //Alpha Vantage 返回的是乱序 Map，需要把日期提取出来并按升序排列
            List<String> allDates = new ArrayList<>(timeSeries.keySet());
            Collections.sort(allDates); // 升序排列：老的在前，新的在后

            //只要最近的 100 天数据
            int startIdx = Math.max(0, allDates.size() - 100);
            List<String> dates = new ArrayList<>();
            List<List<BigDecimal>> values = new ArrayList<>();

            for (int i = startIdx; i < allDates.size(); i++) {
                String dateStr = allDates.get(i);
                JSONObject dayData = timeSeries.getJSONObject(dateStr);

                //解析价格并限制2位小数 (Alpha Vantage 的字段名带数字前缀)
                BigDecimal open = dayData.getBigDecimal("1. open").setScale(2, RoundingMode.HALF_UP);
                BigDecimal high = dayData.getBigDecimal("2. high").setScale(2, RoundingMode.HALF_UP);
                BigDecimal low = dayData.getBigDecimal("3. low").setScale(2, RoundingMode.HALF_UP);
                BigDecimal close = dayData.getBigDecimal("4. close").setScale(2, RoundingMode.HALF_UP);

                dates.add(dateStr);
                //ECharts 的格式要求：[开盘, 收盘, 最低, 最高]
                values.add(Arrays.asList(open, close, low, high));
            }

            KLineDataDTO realData = new KLineDataDTO();
            realData.setDates(dates);
            realData.setValues(values);

            log.info("成功解析 {} 条 {} 的真实历史行情！", dates.size(), symbol);
            return realData;

        } catch (Exception e) {
            log.error("API 调用发生异常: {}", e.getMessage());
            return generateMockData(); // 断网兜底
        }
    }

    /**
     * 兜底降级用,但是我感觉这有失真实性，特别是在金融项目中
     */
    @Override
    public KLineDataDTO generateMockData() {
        log.warn("启动备用数据引擎：生成 100 天模拟大盘数据");
        KLineDataDTO dto = new KLineDataDTO();
        List<String> dates = new ArrayList<>();
        List<List<BigDecimal>> values = new ArrayList<>();

        int daysToSimulate = 100;
        LocalDate currentDate = LocalDate.now().minusDays(daysToSimulate + 30);
        BigDecimal lastClose = new BigDecimal("3000.00");
        Random random = new Random();

        int generatedCount = 0;
        while (generatedCount < daysToSimulate) {
            if (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            double gapPercent = (random.nextDouble() - 0.5) * 0.01;
            BigDecimal open = lastClose.multiply(BigDecimal.valueOf(1 + gapPercent)).setScale(2, RoundingMode.HALF_UP);
            double dailyVolPercent = (random.nextDouble() - 0.5) * 0.04;
            BigDecimal close = open.multiply(BigDecimal.valueOf(1 + dailyVolPercent)).setScale(2, RoundingMode.HALF_UP);

            BigDecimal maxOpenClose = open.max(close);
            BigDecimal minOpenClose = open.min(close);
            BigDecimal highShadow = maxOpenClose.multiply(BigDecimal.valueOf(random.nextDouble() * 0.01));
            BigDecimal high = maxOpenClose.add(highShadow).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lowShadow = minOpenClose.multiply(BigDecimal.valueOf(random.nextDouble() * 0.01));
            BigDecimal low = minOpenClose.subtract(lowShadow).setScale(2, RoundingMode.HALF_UP);

            dates.add(currentDate.toString());
            values.add(Arrays.asList(open, close, low, high));

            lastClose = close;
            currentDate = currentDate.plusDays(1);
            generatedCount++;
        }

        dto.setDates(dates);
        dto.setValues(values);
        return dto;
    }
}