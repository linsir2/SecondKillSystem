package com.seckill.module.goods.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.goods.model.dto.CreateGoodsRequest;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.model.dto.UpdateGoodsRequest;
import com.seckill.module.goods.model.vo.GoodsVO;
import com.seckill.module.goods.service.GoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoodsController")
class GoodsControllerTest {

    @Mock
    private GoodsService goodsService;

    @InjectMocks
    private GoodsController controller;

    private final CurrentUser merchant = new CurrentUser(10L, "商家A", UserRole.merchant);
    private final CurrentUser buyer    = new CurrentUser(20L, "买家A", UserRole.user);

    private final GoodsVO sampleVO = new GoodsVO(
            100L, "测试商品", new BigDecimal("99.99"), 1, 100, LocalDateTime.now());

    @BeforeEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    // ========================================================================
    // POST /api/v1/goods
    // ========================================================================

    @Nested
    @DisplayName("POST /api/v1/goods")
    class CreateGoods {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            var req = new CreateGoodsRequest();
            assertThatThrownBy(() -> controller.createGoods(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(goodsService, never()).createGoods(anyLong(), any());
        }

        @Test
        @DisplayName("非商家 → BusinessException")
        void notMerchant() {
            SecurityContext.set(buyer);
            var req = new CreateGoodsRequest();
            assertThatThrownBy(() -> controller.createGoods(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅商家");
            verify(goodsService, never()).createGoods(anyLong(), any());
        }

        @Test
        @DisplayName("商家创建成功 → 200 + GoodsVO")
        void success() {
            SecurityContext.set(merchant);
            var req = new CreateGoodsRequest();
            req.setGoodsName("新商品");
            req.setPrice(new BigDecimal("49.99"));
            req.setStock(200);

            when(goodsService.createGoods(10L, req)).thenReturn(sampleVO);

            Result<GoodsVO> result = controller.createGoods(req);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo(sampleVO);
            verify(goodsService).createGoods(10L, req);
        }
    }

    // ========================================================================
    // PUT /api/v1/goods/{goodsId}
    // ========================================================================

    @Nested
    @DisplayName("PUT /api/v1/goods/{goodsId}")
    class UpdateGoods {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            var req = new UpdateGoodsRequest();
            assertThatThrownBy(() -> controller.updateGoods(100L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(goodsService, never()).updateGoods(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("非商家 → BusinessException")
        void notMerchant() {
            SecurityContext.set(buyer);
            var req = new UpdateGoodsRequest();
            assertThatThrownBy(() -> controller.updateGoods(100L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅商家");
            verify(goodsService, never()).updateGoods(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("商家更新成功 → 200 + GoodsVO")
        void success() {
            SecurityContext.set(merchant);
            var req = new UpdateGoodsRequest();
            req.setGoodsName("更新名");
            req.setPrice(new BigDecimal("59.99"));
            req.setStock(50);

            when(goodsService.updateGoods(10L, 100L, req)).thenReturn(sampleVO);

            Result<GoodsVO> result = controller.updateGoods(100L, req);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo(sampleVO);
            verify(goodsService).updateGoods(10L, 100L, req);
        }
    }

    // ========================================================================
    // POST /api/v1/goods/{goodsId}/list
    // ========================================================================

    @Nested
    @DisplayName("POST /api/v1/goods/{goodsId}/list")
    class ListGoods {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.listGoods(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(goodsService, never()).listGoods(anyLong(), anyLong());
        }

        @Test
        @DisplayName("非商家 → BusinessException")
        void notMerchant() {
            SecurityContext.set(buyer);
            assertThatThrownBy(() -> controller.listGoods(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅商家");
            verify(goodsService, never()).listGoods(anyLong(), anyLong());
        }

        @Test
        @DisplayName("商家上架成功 → 200 + GoodsVO")
        void success() {
            SecurityContext.set(merchant);
            when(goodsService.listGoods(10L, 100L)).thenReturn(sampleVO);

            Result<GoodsVO> result = controller.listGoods(100L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo(sampleVO);
            verify(goodsService).listGoods(10L, 100L);
        }
    }

    // ========================================================================
    // POST /api/v1/goods/{goodsId}/delist
    // ========================================================================

    @Nested
    @DisplayName("POST /api/v1/goods/{goodsId}/delist")
    class DelistGoods {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.delistGoods(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(goodsService, never()).delistGoods(anyLong(), anyLong());
        }

        @Test
        @DisplayName("非商家 → BusinessException")
        void notMerchant() {
            SecurityContext.set(buyer);
            assertThatThrownBy(() -> controller.delistGoods(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅商家");
            verify(goodsService, never()).delistGoods(anyLong(), anyLong());
        }

        @Test
        @DisplayName("商家下架成功 → 200 + GoodsVO")
        void success() {
            SecurityContext.set(merchant);
            when(goodsService.delistGoods(10L, 100L)).thenReturn(sampleVO);

            Result<GoodsVO> result = controller.delistGoods(100L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo(sampleVO);
            verify(goodsService).delistGoods(10L, 100L);
        }
    }

    // ========================================================================
    // GET /api/v1/goods
    // ========================================================================

    @Nested
    @DisplayName("GET /api/v1/goods")
    class ListMerchantGoods {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.listMerchantGoods())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(goodsService, never()).listMerchantGoods(anyLong());
        }

        @Test
        @DisplayName("非商家 → BusinessException")
        void notMerchant() {
            SecurityContext.set(buyer);
            assertThatThrownBy(() -> controller.listMerchantGoods())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅商家");
            verify(goodsService, never()).listMerchantGoods(anyLong());
        }

        @Test
        @DisplayName("有商品 → 200 + 列表")
        void hasGoods() {
            SecurityContext.set(merchant);
            var list = List.of(sampleVO);
            when(goodsService.listMerchantGoods(10L)).thenReturn(list);

            Result<List<GoodsVO>> result = controller.listMerchantGoods();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0)).isEqualTo(sampleVO);
            verify(goodsService).listMerchantGoods(10L);
        }

        @Test
        @DisplayName("无商品 → 200 + 空列表")
        void emptyGoods() {
            SecurityContext.set(merchant);
            when(goodsService.listMerchantGoods(10L)).thenReturn(List.of());

            Result<List<GoodsVO>> result = controller.listMerchantGoods();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEmpty();
            verify(goodsService).listMerchantGoods(10L);
        }
    }

    // ========================================================================
    // GET /api/v1/goods/{goodsId}
    // ========================================================================

    @Nested
    @DisplayName("GET /api/v1/goods/{goodsId}")
    class GetGoodsInfo {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.getGoodsInfo(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(goodsService, never()).getGoodsInfo(anyLong(), anyLong());
        }

        @Test
        @DisplayName("非商家 → BusinessException")
        void notMerchant() {
            SecurityContext.set(buyer);
            assertThatThrownBy(() -> controller.getGoodsInfo(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅商家");
            verify(goodsService, never()).getGoodsInfo(anyLong(), anyLong());
        }

        @Test
        @DisplayName("商品存在 → 200 + GoodsInfo")
        void found() {
            SecurityContext.set(merchant);
            var info = new GoodsInfo(100L, "测试商品", new BigDecimal("99.99"), 100);
            when(goodsService.getGoodsInfo(100L, 10L)).thenReturn(info);

            Result<GoodsInfo> result = controller.getGoodsInfo(100L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo(info);
            verify(goodsService).getGoodsInfo(100L, 10L);
        }

        @Test
        @DisplayName("商品不存在或不属于该商家 → 200 + data=null")
        void notFound() {
            SecurityContext.set(merchant);
            when(goodsService.getGoodsInfo(100L, 10L)).thenReturn(null);

            Result<GoodsInfo> result = controller.getGoodsInfo(100L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isNull();
            verify(goodsService).getGoodsInfo(100L, 10L);
        }
    }
}
