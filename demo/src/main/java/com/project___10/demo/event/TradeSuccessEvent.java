package com.project___10.demo.event;

import com.project___10.demo.entity.TradeOrder;

// 这是一个纯粹的消息体，用来在不同部门之间传递情报
public class TradeSuccessEvent {
    private TradeOrder order;

    public TradeSuccessEvent(TradeOrder order) {
        this.order = order;
    }

    public TradeOrder getOrder() {
        return order;
    }
}