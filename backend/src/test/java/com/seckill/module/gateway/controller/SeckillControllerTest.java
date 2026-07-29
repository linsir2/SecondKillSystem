package com.seckill.module.gateway.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.gateway.model.dto.SeckillRequest;
import com.seckill.module.gateway.model.dto.SeckillResponse;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.service.SeckillStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeckillController")
class SeckillControllerTest {

    @Mock
    private SeckillStockService seckillStockService;

    @InjectMocks
    private SeckillController controller;

    private SeckillRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new SeckillRequest();
        validRequest.setActivityId(100L);
        validRequest.setSeckillGoodsId(200L);
        validRequest.setBuyCount(2);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Nested
    @DisplayName("正常路径")
    class Success {

        @Test
        @DisplayName("扣库存成功 → 返回 orderToken")
        void executeSuccess() {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(seckillStockService.deduct(100L, 200L, 1L, 2))
                    .thenReturn(SeckillDeductResult.success("token-abc", 2));

            Result<SeckillResponse> result = controller.execute(validRequest);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getOrderToken()).isEqualTo("token-abc");
        }

        @Test
        @DisplayName("参数透传：userId 从 SecurityContext 获取")
        void userIdFromContext() {
            SecurityContext.set(new CurrentUser(999L, "用户", UserRole.user));
            when(seckillStockService.deduct(100L, 200L, 999L, 2))
                    .thenReturn(SeckillDeductResult.success("token-xyz", 2));

            Result<SeckillResponse> result = controller.execute(validRequest);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getOrderToken()).isEqualTo("token-xyz");
        }
    }

    @Nested
    @DisplayName("异常路径")
    class Error {

        @Test
        @DisplayName("未登录 → BusinessException")
        void noAuth() {
            assertThatThrownBy(() -> controller.execute(validRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
        }

        @Test
        @DisplayName("售罄 → BusinessException")
        void soldOut() {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(seckillStockService.deduct(anyLong(), anyLong(), anyLong(), anyInt()))
                    .thenReturn(SeckillDeductResult.soldOut());

            assertThatThrownBy(() -> controller.execute(validRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("售罄");
        }

        @Test
        @DisplayName("重复购买 → BusinessException")
        void duplicate() {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(seckillStockService.deduct(anyLong(), anyLong(), anyLong(), anyInt()))
                    .thenReturn(SeckillDeductResult.duplicate());

            assertThatThrownBy(() -> controller.execute(validRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("重复购买");
        }

        @Test
        @DisplayName("超过限购 → BusinessException")
        void overLimit() {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(seckillStockService.deduct(anyLong(), anyLong(), anyLong(), anyInt()))
                    .thenReturn(SeckillDeductResult.overLimit());

            assertThatThrownBy(() -> controller.execute(validRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("限购");
        }

        @Test
        @DisplayName("SeckillStockService 异常 → 传播不拦截")
        void serviceException() {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(seckillStockService.deduct(anyLong(), anyLong(), anyLong(), anyInt()))
                    .thenThrow(new RuntimeException("MQ error"));

            assertThatThrownBy(() -> controller.execute(validRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("MQ error");
        }

        @Test
        @DisplayName("未知结果码 → BusinessException")
        void unknownCode() {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(seckillStockService.deduct(anyLong(), anyLong(), anyLong(), anyInt()))
                    .thenReturn(new SeckillDeductResult(999, null, 0));

            assertThatThrownBy(() -> controller.execute(validRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("系统繁忙");
        }
    }
}
