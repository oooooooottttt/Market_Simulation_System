package com.project___10.demo.service;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.TradeOrder;

public interface TradeApplicationService {

    Result executeTrade(TradeOrder request);
}
