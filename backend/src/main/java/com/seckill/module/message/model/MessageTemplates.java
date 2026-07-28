package com.seckill.module.message.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 通知模板工具类。
 *
 * <p>所有方法返回原始内容，不转义——转义统一在 {@link com.seckill.module.message.service.UserMessageService#sendMessage} 层处理。</p>
 */
public final class MessageTemplates {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    private MessageTemplates() {}

    /**
     * 审核通过 → 商家通知。
     *
     * @param activityName 活动名（非 null）
     * @param startTime    开始时间（非 null）
     * @param goodsCount   秒杀商品数
     */
    public static String merchantApproved(String activityName, LocalDateTime startTime, int goodsCount) {
        Objects.requireNonNull(activityName, "activityName must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        return "您的秒杀活动「" + activityName + "」已通过审核，将于 "
                + startTime.format(FMT) + " 准时开始。共 "
                + goodsCount + " 件商品参与秒杀，祝大卖！";
    }

    /**
     * 审核通过 → 用户广播（v2）。
     *
     * @param merchantName 商家名（非 null）
     * @param activityName 活动名（非 null）
     * @param startTime    开始时间（非 null）
     * @param goodsNames   商品名列表（逗号分隔，非 null）
     */
    public static String userAnnouncement(String merchantName, String activityName,
                                          LocalDateTime startTime, String goodsNames) {
        Objects.requireNonNull(merchantName, "merchantName must not be null");
        Objects.requireNonNull(activityName, "activityName must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(goodsNames, "goodsNames must not be null");
        return "📢 " + merchantName + " 将于 " + startTime.format(FMT)
                + " 开启秒杀「" + activityName + "」！参与商品："
                + goodsNames + " 准时开抢，手慢无！";
    }

    /**
     * 提交审核 → 管理员通知。
     *
     * @param merchantName 商家名（非 null）
     * @param activityName 活动名（非 null）
     */
    public static String merchantSubmittedForReview(String merchantName, String activityName) {
        Objects.requireNonNull(merchantName, "merchantName must not be null");
        Objects.requireNonNull(activityName, "activityName must not be null");
        return "商家 " + merchantName + " 提交了秒杀活动「" + activityName + "」待审核，请及时处理。";
    }

    /**
     * 封禁通知。
     */
    public static String userBanned() {
        return "您的账号因违规操作已被封禁，封禁期间无法参与秒杀活动。如有疑问请联系管理员。";
    }
}
