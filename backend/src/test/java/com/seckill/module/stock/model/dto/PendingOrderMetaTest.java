package com.seckill.module.stock.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PendingOrderMeta} 解析工厂方法单元测试。
 *
 * <p>格式约定 {@code "uid:seckillGoodsId:buyCount"}，分隔符为 {@code :}。</p>
 */
class PendingOrderMetaTest {

    @Test
    @DisplayName("正常解析")
    void parseNormal() {
        var meta = PendingOrderMeta.parse("100:200:3");
        assertEquals(100L, meta.userId());
        assertEquals(200L, meta.seckillGoodsId());
        assertEquals(3, meta.buyCount());
    }

    @Test
    @DisplayName("null → IAE")
    void parseNull() {
        assertThrows(IllegalArgumentException.class, () -> PendingOrderMeta.parse(null));
    }

    @Test
    @DisplayName("空字符串 → IAE")
    void parseBlank() {
        assertThrows(IllegalArgumentException.class, () -> PendingOrderMeta.parse("  "));
    }

    @Test
    @DisplayName("字段数不够 → IAE")
    void parseWrongPartsCount() {
        assertThrows(IllegalArgumentException.class, () -> PendingOrderMeta.parse("100:200"));
        // 多余字段被忽略（向前兼容）
    }

    @Test
    @DisplayName("额外字段被忽略（向前兼容）")
    void parseExtraFieldsIgnored() {
        var meta = PendingOrderMeta.parse("100:200:3:extra:stuff");
        assertEquals(100L, meta.userId());
        assertEquals(200L, meta.seckillGoodsId());
        assertEquals(3, meta.buyCount());
    }

    @Test
    @DisplayName("非数字字段 → IAE")
    void parseNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> PendingOrderMeta.parse("abc:200:3"));
        assertThrows(IllegalArgumentException.class, () -> PendingOrderMeta.parse("100:abc:3"));
        assertThrows(IllegalArgumentException.class, () -> PendingOrderMeta.parse("100:200:abc"));
    }

    @Test
    @DisplayName("负数 buyCount 合法（parse 不校验语义）")
    void parseNegativeBuyCount() {
        var meta = PendingOrderMeta.parse("100:200:-1");
        assertEquals(-1, meta.buyCount());
    }
}
