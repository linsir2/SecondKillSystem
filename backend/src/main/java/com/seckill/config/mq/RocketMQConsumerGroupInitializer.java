package com.seckill.config.mq;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 应用启动时确保 RocketMQ consumer group 已创建。
 *
 * <p>RocketMQ 5.x 的 {@code autoCreateSubscriptionGroup=true} 有时不生效，
 * 导致 consumer 首次注册被拒后不再重试。本组件在 {@code @PostConstruct} 阶段
 * 用 {@link DefaultMQAdminExt} 主动创建需要的 consumer group，幂等安全。</p>
 *
 * <p>如果启动时 broker 暂不可达，只打 warning 不阻止应用启动——broker 的
 * {@code autoCreateSubscriptionGroup} 兜底。</p>
 */
@Component
public class RocketMQConsumerGroupInitializer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQConsumerGroupInitializer.class);

    /** 临时 group 名称，仅用于 admin 管理操作。不会真的消费消息。 */
    private static final String ADMIN_GROUP = "seckill-admin-helper";

    /** 本应用需要确保存在的 consumer group。 */
    private static final List<String> CONSUMER_GROUPS = List.of(
            "seckill-order-consumer",
            "seckill-order-timeout-consumer"
    );

    private final String namesrvAddr;

    public RocketMQConsumerGroupInitializer(@Value("${rocketmq.name-server:}") String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    @PostConstruct
    public void ensureConsumerGroups() {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            log.warn("RocketMQ nameServer not configured, skipping consumer group init");
            return;
        }

        DefaultMQAdminExt admin = new DefaultMQAdminExt();
        admin.setNamesrvAddr(namesrvAddr);
        admin.setAdminExtGroup(ADMIN_GROUP);
        admin.setInstanceName("admin-" + System.currentTimeMillis());

        try {
            admin.start();
        } catch (Exception e) {
            log.warn("Cannot connect to RocketMQ namesrv [{}], consumer group init deferred " +
                    "(autoCreateSubscriptionGroup will handle if enabled): {}", namesrvAddr, e.getMessage());
            return;
        }

        // ---- 从 namesrv 获取 broker 地址 ----
        String brokerAddr = resolveBrokerAddr(admin);
        if (brokerAddr == null) {
            log.warn("No broker found via namesrv [{}], skipping consumer group init", namesrvAddr);
            admin.shutdown();
            return;
        }

        // ---- 确保每个 consumer group 存在 ----
        for (String group : CONSUMER_GROUPS) {
            ensureGroup(admin, brokerAddr, group);
        }

        admin.shutdown();
    }

    /**
     * 从集群信息中取第一个 broker 地址。
     */
    private String resolveBrokerAddr(DefaultMQAdminExt admin) {
        try {
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            var brokerAddrTable = clusterInfo.getBrokerAddrTable();
            if (brokerAddrTable == null || brokerAddrTable.isEmpty()) {
                return null;
            }
            // 取第一个 broker 的 master 地址（BID=0）
            for (BrokerData data : brokerAddrTable.values()) {
                String addr = data.selectBrokerAddr();
                if (addr != null) return addr;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch broker cluster info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查询 group 是否存在，不存在则创建。
     */
    private void ensureGroup(DefaultMQAdminExt admin, String brokerAddr, String group) {
        try {
            admin.examineSubscriptionGroupConfig(brokerAddr, group);
            log.debug("Consumer group [{}] already exists, skip", group);
        } catch (Exception e) {
            // NOT_FOUND 或任意异常 → 尝试创建
            try {
                SubscriptionGroupConfig config = new SubscriptionGroupConfig();
                config.setGroupName(group);
                admin.createAndUpdateSubscriptionGroupConfig(brokerAddr, config);
                log.info("Consumer group [{}] created successfully", group);
            } catch (Exception ce) {
                log.warn("Failed to create consumer group [{}]: {}", group, ce.getMessage());
            }
        }
    }
}
