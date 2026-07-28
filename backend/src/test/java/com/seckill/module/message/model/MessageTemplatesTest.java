package com.seckill.module.message.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTemplatesTest {

    @Test
    void merchantApproved_happyPath() {
        String result = MessageTemplates.merchantApproved(
                "618大促",
                LocalDateTime.of(2026, 7, 28, 20, 0),
                3);

        assertThat(result).isEqualTo("您的秒杀活动「618大促」已通过审核，将于 2026年7月28日 20:00 准时开始。共 3 件商品参与秒杀，祝大卖！");
    }

    @Test
    void merchantApproved_nullName_throws() {
        assertThrows(NullPointerException.class,
                () -> MessageTemplates.merchantApproved(null, LocalDateTime.now(), 1));
    }

    @Test
    void merchantApproved_zeroGoods() {
        String result = MessageTemplates.merchantApproved(
                "测试",
                LocalDateTime.of(2026, 7, 28, 20, 0),
                0);

        assertThat(result).contains("共 0 件商品");
    }

    @Test
    void userAnnouncement_happyPath() {
        String result = MessageTemplates.userAnnouncement(
                "小米旗舰店",
                "618大促",
                LocalDateTime.of(2026, 7, 28, 20, 0),
                "手机、平板、耳机");

        assertThat(result).isEqualTo("📢 小米旗舰店 将于 2026年7月28日 20:00 开启秒杀「618大促」！参与商品：手机、平板、耳机 准时开抢，手慢无！");
    }

    @Test
    void userAnnouncement_nullParams_throws() {
        assertThrows(NullPointerException.class,
                () -> MessageTemplates.userAnnouncement(null, "n", LocalDateTime.now(), "g"));
    }

    @Test
    void merchantSubmittedForReview_happyPath() {
        String result = MessageTemplates.merchantSubmittedForReview("小米旗舰店", "618大促");
        assertThat(result).isEqualTo("商家 小米旗舰店 提交了秒杀活动「618大促」待审核，请及时处理。");
    }

    @Test
    void userBanned_returnsStaticMessage() {
        String result = MessageTemplates.userBanned();
        assertThat(result).contains("封禁");
    }
}
