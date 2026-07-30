import { useEffect, useState } from 'react';
import type { GoodsVO } from '../types';
import { createGoods, delistGoods, listGoods, listMerchantGoods, updateGoods } from '../api/goods';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Field, Input } from '../components/Input';
import { Skeleton } from '../components/Loading';
import { EmptyState, Modal } from '../components/ui';
import { IconBox, IconEdit, IconPlus } from '../components/icons';
import { formatDateTime, formatPrice } from '../utils/format';

interface GoodsForm {
  goodsName: string;
  price: string;
  stock: string;
}

interface GoodsFormErrors {
  goodsName?: string;
  price?: string;
  stock?: string;
}

export default function GoodsManagePage() {
  const toast = useToast();

  const [goods, setGoods] = useState<GoodsVO[]>([]);
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<GoodsVO | null>(null);
  const [form, setForm] = useState<GoodsForm>({ goodsName: '', price: '', stock: '' });
  const [formErrors, setFormErrors] = useState<GoodsFormErrors>({});
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function loadGoods() {
    setLoading(true);
    try {
      const data = await listMerchantGoods();
      setGoods(data);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : '加载商品失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadGoods();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function openCreate() {
    setEditing(null);
    setForm({ goodsName: '', price: '', stock: '' });
    setFormErrors({});
    setModalOpen(true);
  }

  function openEdit(g: GoodsVO) {
    setEditing(g);
    setForm({ goodsName: g.goodsName, price: String(g.price), stock: String(g.stock) });
    setFormErrors({});
    setModalOpen(true);
  }

  function setField<K extends keyof GoodsForm>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setFormErrors((prev) => (prev[key] ? { ...prev, [key]: undefined } : prev));
  }

  async function handleSave() {
    const e: GoodsFormErrors = {};
    if (!form.goodsName.trim()) e.goodsName = '请输入商品名称';
    const price = Number(form.price);
    if (form.price === '' || !(price > 0)) e.price = '价格需大于 0';
    const stock = Number(form.stock);
    if (form.stock === '' || !(stock > 0)) e.stock = '库存需大于 0';
    setFormErrors(e);
    if (Object.keys(e).length > 0) return;

    setSaving(true);
    try {
      const name = form.goodsName.trim();
      if (editing) {
        await updateGoods(editing.goodsId, { goodsName: name, price, stock });
        toast.success('商品已更新');
      } else {
        await createGoods({ goodsName: name, price, stock });
        toast.success('商品已创建');
      }
      setModalOpen(false);
      await loadGoods();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(g: GoodsVO) {
    setBusyId(g.goodsId);
    try {
      if (g.status === 1) {
        await delistGoods(g.goodsId);
        toast.success('已下架');
      } else {
        await listGoods(g.goodsId);
        toast.success('已上架');
      }
      await loadGoods();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setBusyId(null);
    }
  }

  const thead = (
    <thead>
      <tr>
        <th>商品</th>
        <th>价格</th>
        <th className="num">库存</th>
        <th>状态</th>
        <th>创建时间</th>
        <th>操作</th>
      </tr>
    </thead>
  );

  const skeletonRows = Array.from({ length: 5 }).map((_, i) => (
    <tr key={i}>
      <td><Skeleton w={140} /></td>
      <td><Skeleton w={70} /></td>
      <td className="num"><Skeleton w={44} /></td>
      <td><Skeleton w={56} h={20} r={999} /></td>
      <td><Skeleton w={130} /></td>
      <td><Skeleton w={120} /></td>
    </tr>
  ));

  return (
    <div>
      <div className="page-head">
        <div>
          <h1 className="title">商品管理</h1>
          <p className="subtitle">管理自有商品库存与上下架</p>
        </div>
        <Button variant="primary" onClick={openCreate}>
          <IconPlus width={16} height={16} />
          新增商品
        </Button>
      </div>

      <div className="card">
        {loading ? (
          <table className="table">
            {thead}
            <tbody>{skeletonRows}</tbody>
          </table>
        ) : goods.length === 0 ? (
          <EmptyState
            icon={<IconBox width={48} height={48} />}
            title="还没有商品"
            desc="创建你的第一件商品，即可参与秒杀活动"
            action={
              <Button variant="primary" onClick={openCreate}>
                <IconPlus width={16} height={16} />
                新增商品
              </Button>
            }
          />
        ) : (
          <table className="table">
            {thead}
            <tbody>
              {goods.map((g) => (
                <tr key={g.goodsId}>
                  <td className="strong">{g.goodsName}</td>
                  <td>
                    <span className="price">{formatPrice(g.price)}</span>
                  </td>
                  <td className="num">{g.stock}</td>
                  <td>
                    {g.status === 1 ? (
                      <span className="badge badge-success">上架</span>
                    ) : (
                      <span className="badge badge-ended">下架</span>
                    )}
                  </td>
                  <td className="nowrap">{formatDateTime(g.createdAt)}</td>
                  <td>
                    <div className="row gap-8 nowrap">
                      <Button variant="ghost" size="sm" onClick={() => openEdit(g)}>
                        <IconEdit width={14} height={14} />
                        编辑
                      </Button>
                      {g.status === 1 ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={busyId === g.goodsId}
                          onClick={() => toggleStatus(g)}
                        >
                          下架
                        </Button>
                      ) : (
                        <Button
                          variant="primary"
                          size="sm"
                          loading={busyId === g.goodsId}
                          onClick={() => toggleStatus(g)}
                        >
                          上架
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Modal
        open={modalOpen}
        title={editing ? '编辑商品' : '新增商品'}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setModalOpen(false)}>
              取消
            </Button>
            <Button variant="primary" loading={saving} onClick={handleSave}>
              保存
            </Button>
          </>
        }
      >
        <Field label="商品名称" required error={formErrors.goodsName}>
          <Input
            value={form.goodsName}
            invalid={!!formErrors.goodsName}
            placeholder="请输入商品名称"
            onChange={(e) => setField('goodsName', e.target.value)}
          />
        </Field>
        <Field label="价格" required error={formErrors.price}>
          <Input
            type="number"
            min="0"
            step="0.01"
            value={form.price}
            invalid={!!formErrors.price}
            placeholder="0.00"
            onChange={(e) => setField('price', e.target.value)}
          />
        </Field>
        <Field label="库存" required error={formErrors.stock}>
          <Input
            type="number"
            min="0"
            step="1"
            value={form.stock}
            invalid={!!formErrors.stock}
            placeholder="0"
            onChange={(e) => setField('stock', e.target.value)}
          />
        </Field>
      </Modal>
    </div>
  );
}
