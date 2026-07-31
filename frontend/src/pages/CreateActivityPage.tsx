import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { CreateActivityRequest, GoodsVO } from '../types';
import { listMerchantGoods } from '../api/goods';
import { createActivity } from '../api/activity';
import { useToast } from '../components/Message';
import Button from '../components/Button';
import { Field, Input, Select, Textarea } from '../components/Input';
import { Skeleton } from '../components/Loading';
import { SectionTitle } from '../components/ui';
import { IconAlert, IconArrowLeft, IconPlus, IconTrash } from '../components/icons';
import { isoFromLocalInput, toLocalInputValue } from '../utils/format';

interface SeckillRow {
  goodsId: string | null;
  seckillPrice: string;
  stock: string;
  limitNum: string;
}

interface FormErrors {
  activityName?: string;
  startTime?: string;
  endTime?: string;
}

interface RowError {
  goodsId?: string;
  seckillPrice?: string;
  stock?: string;
  limitNum?: string;
}

const EMPTY_ROW: SeckillRow = { goodsId: null, seckillPrice: '', stock: '', limitNum: '' };

export default function CreateActivityPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [activityName, setActivityName] = useState('');
  const [startTime, setStartTime] = useState(() => toLocalInputValue(new Date(Date.now() + 30 * 60000)));
  const [endTime, setEndTime] = useState(() => toLocalInputValue(new Date(Date.now() + 90 * 60000)));
  const [description, setDescription] = useState('');

  const [errors, setErrors] = useState<FormErrors>({});
  const [rows, setRows] = useState<SeckillRow[]>([{ ...EMPTY_ROW }]);
  const [rowErrors, setRowErrors] = useState<RowError[]>([{}]);

  const [goods, setGoods] = useState<GoodsVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    listMerchantGoods()
      .then((data) => {
        if (alive) setGoods(data);
      })
      .catch((err: unknown) => {
        if (alive) toast.error(err instanceof Error ? err.message : '加载商品失败');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function clearError(key: keyof FormErrors) {
    setErrors((prev) => (prev[key] ? { ...prev, [key]: undefined } : prev));
  }

  function updateRow(idx: number, patch: Partial<SeckillRow>) {
    setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
    setRowErrors((prev) => {
      const cleared = { ...prev[idx] } as RowError;
      (Object.keys(patch) as Array<keyof RowError>).forEach((k) => {
        cleared[k] = undefined;
      });
      const next = [...prev];
      next[idx] = cleared;
      return next;
    });
  }

  function addRow() {
    setRows((prev) => [...prev, { ...EMPTY_ROW }]);
    setRowErrors((prev) => [...prev, {}]);
  }

  function removeRow(idx: number) {
    setRows((prev) => prev.filter((_, i) => i !== idx));
    setRowErrors((prev) => prev.filter((_, i) => i !== idx));
  }

  function validate(): boolean {
    const next: FormErrors = {};
    const name = activityName.trim();
    if (!name) next.activityName = '请输入活动名称';
    else if (name.length > 64) next.activityName = '活动名称不能超过 64 个字符';

    if (!startTime) next.startTime = '请选择开始时间';
    if (!endTime) next.endTime = '请选择结束时间';
    if (startTime && endTime && new Date(endTime).getTime() <= new Date(startTime).getTime()) {
      next.endTime = '结束时间必须晚于开始时间';
    }

    const nextRowErrors: RowError[] = rows.map((row) => {
      const e: RowError = {};
      if (row.goodsId === null) e.goodsId = '请选择商品';
      const sp = Number(row.seckillPrice);
      if (row.seckillPrice === '' || !(sp > 0)) e.seckillPrice = '秒杀价需大于 0';
      const selected = goods.find((g) => g.goodsId === row.goodsId);
      const st = Number(row.stock);
      if (row.stock === '' || !(st > 0)) e.stock = '秒杀库存需大于 0';
      else if (selected && st > selected.stock) e.stock = `不能超过商品库存 ${selected.stock}`;
      const ln = Number(row.limitNum);
      if (row.limitNum === '' || !(ln >= 1)) e.limitNum = '限购数量需大于等于 1';
      return e;
    });

    setErrors(next);
    setRowErrors(nextRowErrors);
    const hasFormErr = Object.keys(next).length > 0;
    const hasRowErr = nextRowErrors.some((e) => Object.keys(e).length > 0);
    return !hasFormErr && !hasRowErr;
  }

  async function handleSubmit() {
    if (!validate()) return;
    setSubmitting(true);
    try {
      const req: CreateActivityRequest = {
        activityName: activityName.trim(),
        startTime: isoFromLocalInput(startTime),
        endTime: isoFromLocalInput(endTime),
        description: description.trim(),
        seckillGoodsList: rows.map((r) => ({
          goodsId: r.goodsId as string,
          seckillPrice: Number(r.seckillPrice),
          stock: Number(r.stock),
          limitNum: Number(r.limitNum),
        })),
      };
      await createActivity(req);
      toast.success('活动已创建为草稿，可在活动广场提交审核');
      navigate('/activity');
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : '创建活动失败');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <Link to="/activity" className="link-row center gap-6 mb-16">
        <IconArrowLeft width={16} height={16} />
        返回活动广场
      </Link>

      <div className="page-head">
        <div>
          <h1 className="title">创建秒杀活动</h1>
          <p className="subtitle">填写信息并绑定秒杀商品，提交后进入待审核</p>
        </div>
      </div>

      <div className="grid grid-2">
        {/* 左：活动信息 */}
        <div className="card card-pad">
          <SectionTitle title="活动信息" desc="设置活动基础信息与时间" />
          <Field label="活动名称" required error={errors.activityName}>
            <Input
              value={activityName}
              invalid={!!errors.activityName}
              maxLength={64}
              placeholder="请输入活动名称"
              onChange={(e) => {
                setActivityName(e.target.value);
                clearError('activityName');
              }}
            />
          </Field>
          <Field label="开始时间" required error={errors.startTime}>
            <Input
              type="datetime-local"
              value={startTime}
              invalid={!!errors.startTime}
              onChange={(e) => {
                setStartTime(e.target.value);
                clearError('startTime');
              }}
            />
          </Field>
          <Field label="结束时间" required error={errors.endTime}>
            <Input
              type="datetime-local"
              value={endTime}
              invalid={!!errors.endTime}
              onChange={(e) => {
                setEndTime(e.target.value);
                clearError('endTime');
              }}
            />
          </Field>
          <Field label="活动描述">
            <Textarea
              value={description}
              placeholder="可选，描述活动规则、亮点等"
              onChange={(e) => setDescription(e.target.value)}
            />
          </Field>
        </div>

        {/* 右：秒杀商品 */}
        <div className="card card-pad">
          <SectionTitle title="秒杀商品" desc="绑定商品并设置秒杀价、库存与限购" />
          {loading ? (
            <div className="col gap-12">
              <Skeleton h={130} />
              <Skeleton h={130} />
            </div>
          ) : (
            <>
              {goods.length === 0 && (
                <div className="alert alert-warning mb-16">
                  <IconAlert className="a-ico" />
                  <span>请先在「商品管理」创建商品</span>
                </div>
              )}
              <div className="col gap-16">
                {rows.map((row, idx) => {
                  const selected = goods.find((g) => g.goodsId === row.goodsId);
                  const re = rowErrors[idx] ?? {};
                  return (
                    <div className="card card-pad" key={idx}>
                      <div className="between mb-16 wrap gap-8">
                        <span className="strong">秒杀商品 {idx + 1}</span>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={rows.length <= 1}
                          onClick={() => removeRow(idx)}
                        >
                          <IconTrash width={14} height={14} />
                          移除
                        </Button>
                      </div>
                      <div className="grid grid-2">
                        <Field label="选择商品" required error={re.goodsId}>
                          <Select
                            value={row.goodsId ?? ''}
                            invalid={!!re.goodsId}
                            onChange={(e) =>
                              updateRow(idx, {
                                goodsId: e.target.value === '' ? null : e.target.value,
                              })
                            }
                          >
                            <option value="" disabled>
                              请选择商品
                            </option>
                            {goods.map((g) => (
                              <option key={g.goodsId} value={g.goodsId}>
                                {g.goodsName}（库存 {g.stock}）
                              </option>
                            ))}
                          </Select>
                        </Field>
                        <Field label="秒杀价" required error={re.seckillPrice}>
                          <Input
                            type="number"
                            min="0"
                            step="0.01"
                            value={row.seckillPrice}
                            invalid={!!re.seckillPrice}
                            placeholder="0.00"
                            onChange={(e) => updateRow(idx, { seckillPrice: e.target.value })}
                          />
                        </Field>
                        <Field
                          label="秒杀库存"
                          required
                          hint={selected ? `不能超过商品库存 ${selected.stock}` : '不能超过商品库存'}
                          error={re.stock}
                        >
                          <Input
                            type="number"
                            min="0"
                            step="1"
                            value={row.stock}
                            invalid={!!re.stock}
                            placeholder="0"
                            onChange={(e) => updateRow(idx, { stock: e.target.value })}
                          />
                        </Field>
                        <Field label="限购数量" required error={re.limitNum}>
                          <Input
                            type="number"
                            min="1"
                            step="1"
                            value={row.limitNum}
                            invalid={!!re.limitNum}
                            placeholder="1"
                            onChange={(e) => updateRow(idx, { limitNum: e.target.value })}
                          />
                        </Field>
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="mt-16">
                <Button variant="outline" size="sm" onClick={addRow}>
                  <IconPlus width={14} height={14} />
                  添加商品
                </Button>
              </div>
            </>
          )}
        </div>
      </div>

      <div className="card card-pad mt-24">
        <div className="between wrap gap-12">
          <p className="muted">提交后活动将以草稿状态保存，可在活动广场提交审核。</p>
          <div className="row gap-12">
            <Button variant="ghost" onClick={() => navigate('/activity')}>
              取消
            </Button>
            <Button variant="primary" size="lg" loading={submitting} onClick={handleSubmit}>
              创建活动（草稿）
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
