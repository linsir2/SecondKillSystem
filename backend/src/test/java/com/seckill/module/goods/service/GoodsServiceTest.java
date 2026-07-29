package com.seckill.module.goods.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.goods.mapper.GoodsMapper;
import com.seckill.module.goods.model.dto.CreateGoodsRequest;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.model.dto.UpdateGoodsRequest;
import com.seckill.module.goods.model.entity.Goods;
import com.seckill.module.goods.model.vo.GoodsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsServiceTest {

    @Mock
    private GoodsMapper goodsMapper;

    @InjectMocks
    private GoodsServiceImpl goodsService;

    @Captor
    private ArgumentCaptor<Goods> goodsCaptor;

    // ========================================================================
    // helpers
    // ========================================================================

    private Goods createEntity(Long id, Long merchantId, String name,
                               BigDecimal price, int status, int stock) {
        Goods g = new Goods();
        g.setGoodsId(id);
        g.setGoodsName(name);
        g.setMerchantId(merchantId);
        g.setPrice(price);
        g.setStatus(status);
        g.setStock(stock);
        g.setCreatedAt(LocalDateTime.of(2026, 7, 27, 10, 0));
        return g;
    }

    private void assertInfoMatch(GoodsInfo info, Long id, String name,
                                 BigDecimal price, int stock) {
        assertThat(info.getGoodsId()).isEqualTo(id);
        assertThat(info.getGoodsName()).isEqualTo(name);
        assertThat(info.getPrice()).isEqualTo(price);
        assertThat(info.getStock()).isEqualTo(stock);
    }

    // ========================================================================
    // getGoodsInfo — OHS 单查
    // ========================================================================

    @Nested
    class GetGoodsInfo {

        @Test
        void happyPath() {
            Goods goods = createEntity(1L, 10L, "商品A", new BigDecimal("99.99"), 1, 100);
            when(goodsMapper.selectOne(any())).thenReturn(goods);

            GoodsInfo info = goodsService.getGoodsInfo(1L, 10L);

            assertInfoMatch(info, 1L, "商品A", new BigDecimal("99.99"), 100);
        }

        @Test
        void nullGoodsId_throws() {
            assertThatThrownBy(() -> goodsService.getGoodsInfo(null, 10L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullMerchantId_throws() {
            assertThatThrownBy(() -> goodsService.getGoodsInfo(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notFound_returnsNull() {
            when(goodsMapper.selectOne(any())).thenReturn(null);

            GoodsInfo info = goodsService.getGoodsInfo(999L, 10L);

            assertThat(info).isNull();
        }

        @Test
        void wrongMerchant_returnsNull() {
            // 另一个商家的商品——query wrapper 的 eq(merchantId) 会过滤掉
            when(goodsMapper.selectOne(any())).thenReturn(null);

            GoodsInfo info = goodsService.getGoodsInfo(1L, 20L);

            assertThat(info).isNull();
        }

        @Test
        void nullPrice_defensive() {
            Goods g = createEntity(1L, 10L, "商品", null, 1, 100);
            when(goodsMapper.selectOne(any())).thenReturn(g);

            GoodsInfo info = goodsService.getGoodsInfo(1L, 10L);

            assertThat(info.getPrice()).isNull();
        }
    }

    // ========================================================================
    // getGoodsInfoList — OHS 批量（保序 + merchant 过滤）
    // ========================================================================

    @Nested
    class GetGoodsInfoList {

        @Test
        void happyPath() {
            when(goodsMapper.selectList(any())).thenReturn(List.of(
                    createEntity(1L, 10L, "商品A", BigDecimal.TEN, 1, 100),
                    createEntity(2L, 10L, "商品B", BigDecimal.ONE, 1, 50)));

            List<GoodsInfo> list = goodsService.getGoodsInfoList(List.of(1L, 2L), 10L);

            assertThat(list).hasSize(2);
            assertInfoMatch(list.get(0), 1L, "商品A", BigDecimal.TEN, 100);
            assertInfoMatch(list.get(1), 2L, "商品B", BigDecimal.ONE, 50);
        }

        @Test
        void nullGoodsIds_throws() {
            assertThatThrownBy(() -> goodsService.getGoodsInfoList(null, 10L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullMerchantId_throws() {
            assertThatThrownBy(() -> goodsService.getGoodsInfoList(List.of(1L), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void emptyList_returnsEmpty() {
            List<GoodsInfo> list = goodsService.getGoodsInfoList(emptyList(), 10L);

            assertThat(list).isEmpty();
        }

        @Test
        void orderPreservation_whenMapperReturnsDifferentOrder() {
            // selectList 用 IN(...)，不保证返回顺序
            when(goodsMapper.selectList(any())).thenReturn(List.of(
                    createEntity(2L, 10L, "商品B", BigDecimal.ONE, 1, 50),
                    createEntity(1L, 10L, "商品A", BigDecimal.TEN, 1, 100)));

            List<GoodsInfo> list = goodsService.getGoodsInfoList(List.of(1L, 2L), 10L);

            assertThat(list.get(0).getGoodsId()).isEqualTo(1L); // 保持输入序
            assertThat(list.get(1).getGoodsId()).isEqualTo(2L);
        }

        @Test
        void partialExists_nullAtMissing() {
            when(goodsMapper.selectList(any())).thenReturn(List.of(
                    createEntity(1L, 10L, "商品A", BigDecimal.TEN, 1, 100)));

            List<GoodsInfo> list = goodsService.getGoodsInfoList(List.of(1L, 999L), 10L);

            assertThat(list).hasSize(2);
            assertInfoMatch(list.get(0), 1L, "商品A", BigDecimal.TEN, 100);
            assertThat(list.get(1)).isNull();
        }

        @Test
        void allNotFound_returnsAllNull() {
            when(goodsMapper.selectList(any())).thenReturn(emptyList());

            List<GoodsInfo> list = goodsService.getGoodsInfoList(List.of(999L, 888L), 10L);

            assertThat(list).hasSize(2);
            assertThat(list.get(0)).isNull();
            assertThat(list.get(1)).isNull();
        }
    }

    // ========================================================================
    // createGoods — 字段校验
    // ========================================================================

    @Nested
    class CreateGoods_FieldValidation {

        @Test
        void nullName_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req(null, BigDecimal.TEN, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankName_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("", BigDecimal.TEN, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nameTooLong_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("A".repeat(256), BigDecimal.TEN, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullPrice_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("商品", null, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void zeroPrice_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("商品", BigDecimal.ZERO, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativePrice_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("商品", new BigDecimal("-0.01"), 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullStock_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("商品", BigDecimal.TEN, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativeStock_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(1L, req("商品", BigDecimal.TEN, -1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullMerchantId_throws() {
            assertThatThrownBy(() -> goodsService.createGoods(null, req("商品", BigDecimal.TEN, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private CreateGoodsRequest req(String name, BigDecimal price, Integer stock) {
            var r = new CreateGoodsRequest();
            r.setGoodsName(name);
            r.setPrice(price);
            r.setStock(stock);
            return r;
        }
    }

    // ========================================================================
    // createGoods — 写入与返回
    // ========================================================================

    @Nested
    class CreateGoods_Write {

        @Test
        void happyPath_returnsGoodsVO() {
            var request = new CreateGoodsRequest();
            request.setGoodsName("新商品");
            request.setPrice(new BigDecimal("19.99"));
            request.setStock(200);

            when(goodsMapper.insert(any(Goods.class))).thenReturn(1);

            GoodsVO vo = goodsService.createGoods(1L, request);

            // 返回的 VO 字段正确
            assertThat(vo.getGoodsName()).isEqualTo("新商品");
            assertThat(vo.getPrice()).isEqualTo(new BigDecimal("19.99"));
            assertThat(vo.getStock()).isEqualTo(200);

            // 写入的 entity 有正确的默认值
            verify(goodsMapper).insert((Goods) goodsCaptor.capture());
            Goods entity = goodsCaptor.getValue();
            assertThat(entity.getGoodsName()).isEqualTo("新商品");
            assertThat(entity.getMerchantId()).isEqualTo(1L);
            assertThat(entity.getPrice()).isEqualTo(new BigDecimal("19.99"));
            assertThat(entity.getStock()).isEqualTo(200);
            assertThat(entity.getStatus()).isEqualTo(1);  // 默认上架
            assertThat(entity.getCreatedAt()).isNotNull(); // 设置时间
        }

        @Test
        void zeroStock_allowed() {
            var request = new CreateGoodsRequest();
            request.setGoodsName("零库存商品");
            request.setPrice(BigDecimal.TEN);
            request.setStock(0);
            when(goodsMapper.insert(any(Goods.class))).thenReturn(1);

            GoodsVO vo = goodsService.createGoods(1L, request);

            assertThat(vo.getStock()).isEqualTo(0);
        }
    }

    // ========================================================================
    // updateGoods
    // ========================================================================

    @Nested
    class UpdateGoods {

        private UpdateGoodsRequest validReq(String name) {
            var r = new UpdateGoodsRequest();
            r.setGoodsName(name);
            r.setPrice(new BigDecimal("29.99"));
            r.setStock(150);
            return r;
        }

        @Test
        void happyPath_updatesFields() {
            Goods existing = createEntity(10L, 1L, "旧名", BigDecimal.TEN, 1, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);
            when(goodsMapper.updateById(any(Goods.class))).thenReturn(1);

            GoodsVO vo = goodsService.updateGoods(1L, 10L, validReq("新名"));

            verify(goodsMapper).updateById((Goods) goodsCaptor.capture());
            Goods updated = goodsCaptor.getValue();
            assertThat(updated.getGoodsName()).isEqualTo("新名");
            assertThat(updated.getPrice()).isEqualTo(new BigDecimal("29.99"));
            assertThat(updated.getStock()).isEqualTo(150);
            assertThat(updated.getStatus()).isEqualTo(1); // 不受 updateGoods 影响
            assertThat(vo.getGoodsName()).isEqualTo("新名");
        }

        @Test
        void notFound_throws() {
            when(goodsMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> goodsService.updateGoods(1L, 999L, validReq("新名")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("商品不存在");
        }

        @Test
        void notOwner_throws() {
            Goods existing = createEntity(10L, 2L, "商品", BigDecimal.TEN, 1, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);

            assertThatThrownBy(() -> goodsService.updateGoods(1L, 10L, validReq("新名")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权操作该商品");
        }

        @Test
        void nullGoodsId_throws() {
            assertThatThrownBy(() -> goodsService.updateGoods(1L, null, validReq("新名")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullMerchantId_throws() {
            assertThatThrownBy(() -> goodsService.updateGoods(null, 10L, validReq("新名")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nameTooLong_throws() {
            assertThatThrownBy(() -> goodsService.updateGoods(1L, 10L, validReq("A".repeat(256))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fieldValidation_reusesRules() {
            var r = new UpdateGoodsRequest();
            assertThatThrownBy(() -> goodsService.updateGoods(1L, 10L, r))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========================================================================
    // listGoods — 上架
    // ========================================================================

    @Nested
    class ListGoods {

        @Test
        void happyPath_setsStatus1() {
            Goods existing = createEntity(10L, 1L, "商品", BigDecimal.TEN, 0, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);
            when(goodsMapper.updateById(any(Goods.class))).thenReturn(1);

            GoodsVO vo = goodsService.listGoods(1L, 10L);

            verify(goodsMapper).updateById((Goods) goodsCaptor.capture());
            assertThat(goodsCaptor.getValue().getStatus()).isEqualTo(1);
            assertThat(vo.getStatus()).isEqualTo(1);
        }

        @Test
        void alreadyListed_idempotent() {
            Goods existing = createEntity(10L, 1L, "商品", BigDecimal.TEN, 1, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);

            GoodsVO vo = goodsService.listGoods(1L, 10L);

            // 不调 updateById
            verify(goodsMapper, never()).updateById(any(Goods.class));
            assertThat(vo.getStatus()).isEqualTo(1);
        }

        @Test
        void notFound_throws() {
            when(goodsMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> goodsService.listGoods(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("商品不存在");
        }

        @Test
        void notOwner_throws() {
            Goods existing = createEntity(10L, 2L, "商品", BigDecimal.TEN, 0, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);

            assertThatThrownBy(() -> goodsService.listGoods(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权操作该商品");
        }
    }

    // ========================================================================
    // delistGoods — 下架
    // ========================================================================

    @Nested
    class DelistGoods {

        @Test
        void happyPath_setsStatus0() {
            Goods existing = createEntity(10L, 1L, "商品", BigDecimal.TEN, 1, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);
            when(goodsMapper.updateById(any(Goods.class))).thenReturn(1);

            GoodsVO vo = goodsService.delistGoods(1L, 10L);

            verify(goodsMapper).updateById((Goods) goodsCaptor.capture());
            assertThat(goodsCaptor.getValue().getStatus()).isEqualTo(0);
            assertThat(vo.getStatus()).isEqualTo(0);
        }

        @Test
        void alreadyDelisted_idempotent() {
            Goods existing = createEntity(10L, 1L, "商品", BigDecimal.TEN, 0, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);

            GoodsVO vo = goodsService.delistGoods(1L, 10L);

            verify(goodsMapper, never()).updateById(any(Goods.class));
            assertThat(vo.getStatus()).isEqualTo(0);
        }

        @Test
        void notFound_throws() {
            when(goodsMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> goodsService.delistGoods(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("商品不存在");
        }

        @Test
        void notOwner_throws() {
            Goods existing = createEntity(10L, 2L, "商品", BigDecimal.TEN, 1, 100);
            when(goodsMapper.selectById(10L)).thenReturn(existing);

            assertThatThrownBy(() -> goodsService.delistGoods(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权操作该商品");
        }
    }

    // ========================================================================
    // deductStock — 审核预占库存
    // ========================================================================

    @Nested
    @DisplayName("deductStock")
    class DeductStock {

        private final Long goodsId = 100L;
        private final Long merchantId = 1L;
        private final int delta = 10;

        private Goods goodsWithStock(int stock) {
            return createEntity(goodsId, merchantId, "测试商品", BigDecimal.TEN, 1, stock);
        }

        @Test
        @DisplayName("快乐路径 → selectById + deductStock 传递正确参数")
        void happyPath() {
            when(goodsMapper.selectById(goodsId)).thenReturn(goodsWithStock(100));
            when(goodsMapper.deductStock(goodsId, delta)).thenReturn(1);

            goodsService.deductStock(goodsId, merchantId, delta);

            verify(goodsMapper).selectById(goodsId);
            verify(goodsMapper).deductStock(goodsId, delta);
        }

        @Test
        @DisplayName("goodsId=null → IAE")
        void nullGoodsId() {
            assertThatThrownBy(() -> goodsService.deductStock(null, merchantId, delta))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(goodsMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("merchantId=null → IAE")
        void nullMerchantId() {
            assertThatThrownBy(() -> goodsService.deductStock(goodsId, null, delta))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delta=0 → IAE")
        void deltaZero() {
            assertThatThrownBy(() -> goodsService.deductStock(goodsId, merchantId, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delta 负数 → IAE")
        void deltaNegative() {
            assertThatThrownBy(() -> goodsService.deductStock(goodsId, merchantId, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("商品不存在 → BusinessException")
        void goodsNotFound() {
            when(goodsMapper.selectById(goodsId)).thenReturn(null);

            assertThatThrownBy(() -> goodsService.deductStock(goodsId, merchantId, delta))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
            verify(goodsMapper).selectById(goodsId);
            verify(goodsMapper, never()).deductStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("不属于该商家 → BusinessException")
        void wrongMerchant() {
            when(goodsMapper.selectById(goodsId)).thenReturn(goodsWithStock(100));

            assertThatThrownBy(() -> goodsService.deductStock(goodsId, 2L, delta))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
        }

        @Test
        @DisplayName("库存不足 → BusinessException")
        void stockInsufficient() {
            when(goodsMapper.selectById(goodsId)).thenReturn(goodsWithStock(5));

            assertThatThrownBy(() -> goodsService.deductStock(goodsId, merchantId, delta))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
            verify(goodsMapper).selectById(goodsId);
            verify(goodsMapper, never()).deductStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("原子扣减受影响行数为0 → BusinessException")
        void deductLockConflict() {
            when(goodsMapper.selectById(goodsId)).thenReturn(goodsWithStock(100));
            when(goodsMapper.deductStock(goodsId, delta)).thenReturn(0);

            assertThatThrownBy(() -> goodsService.deductStock(goodsId, merchantId, delta))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("扣减失败");
            verify(goodsMapper).deductStock(goodsId, delta);
        }
    }

    // ========================================================================
    // listMerchantGoods — 商家商品列表
    // ========================================================================

    @Nested
    class ListMerchantGoods {

        @Test
        void returnsAllGoods_includingDelisted() {
            when(goodsMapper.selectList(any())).thenReturn(List.of(
                    createEntity(3L, 1L, "商品C", BigDecimal.ONE, 1, 30),
                    createEntity(2L, 1L, "商品B", BigDecimal.TEN, 0, 20),  // 已下架
                    createEntity(1L, 1L, "商品A", BigDecimal.ONE, 1, 10)));

            List<GoodsVO> list = goodsService.listMerchantGoods(1L);

            assertThat(list).hasSize(3);
            // 倒序——最新在前
            assertThat(list.get(0).getGoodsId()).isEqualTo(3L);
            assertThat(list.get(1).getGoodsId()).isEqualTo(2L);
            assertThat(list.get(2).getGoodsId()).isEqualTo(1L);
            // 包含已下架
            assertThat(list.get(1).getStatus()).isEqualTo(0);
        }

        @Test
        void emptyList_whenNoGoods() {
            when(goodsMapper.selectList(any())).thenReturn(emptyList());

            List<GoodsVO> list = goodsService.listMerchantGoods(1L);

            assertThat(list).isEmpty();
        }

        @Test
        void doesNotIncludeOtherMerchantsGoods() {
            // selectList 已用 eq(merchantId) 过滤，模拟结果只包含该商家商品
            when(goodsMapper.selectList(any())).thenReturn(List.of(
                    createEntity(1L, 1L, "本店商品", BigDecimal.TEN, 1, 100)));

            List<GoodsVO> list = goodsService.listMerchantGoods(1L);

            assertThat(list).allMatch(vo -> vo.getGoodsName().contains("本店"));
        }
    }

    // ========================================================================
    // restoreStock — 活动结束后回补库存
    // ========================================================================

    @Nested
    @DisplayName("restoreStock")
    class RestoreStock {

        private final Long goodsId = 300L;

        @Test
        @DisplayName("G1 delta=5 → addStock 调用, affected=1 无异常")
        void happyPath() {
            when(goodsMapper.addStock(goodsId, 5)).thenReturn(1);

            goodsService.restoreStock(goodsId, 5);

            verify(goodsMapper).addStock(goodsId, 5);
        }

        @Test
        @DisplayName("G2 goodsId=null → IAE")
        void nullGoodsId() {
            assertThatThrownBy(() -> goodsService.restoreStock(null, 5))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(goodsMapper, never()).addStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("G3 delta=-1 → IAE")
        void negativeDelta() {
            assertThatThrownBy(() -> goodsService.restoreStock(goodsId, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(goodsMapper, never()).addStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("G4 delta=0 → 直接 return, 不调 mapper")
        void zeroDelta() {
            goodsService.restoreStock(goodsId, 0);

            verify(goodsMapper, never()).addStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("G5 mapper 返回 0（商品不存在）→ BusinessException")
        void goodsNotFound() {
            when(goodsMapper.addStock(goodsId, 5)).thenReturn(0);

            assertThatThrownBy(() -> goodsService.restoreStock(goodsId, 5))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("回补失败");
            verify(goodsMapper).addStock(goodsId, 5);
        }
    }
}
