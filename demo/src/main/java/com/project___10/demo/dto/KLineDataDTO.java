package com.project___10.demo.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class KLineDataDTO {
    // 专门装日期（X轴）
    private List<String> dates;

    // 专门装K线数据（Y轴），格式：[开盘, 收盘, 最低, 最高]
    private List<List<BigDecimal>> values;
}