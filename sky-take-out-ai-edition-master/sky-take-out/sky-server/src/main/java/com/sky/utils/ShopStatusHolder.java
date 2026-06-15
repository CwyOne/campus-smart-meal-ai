package com.sky.utils;

/**
 * 店铺营业状态持有者（临时方案，后续可改为Redis或数据库）
 */
public class ShopStatusHolder {
    private static volatile Integer shopStatus = 1;

    public static Integer getStatus() {
        return shopStatus;
    }

    public static void setStatus(Integer status) {
        shopStatus = status;
    }
}
