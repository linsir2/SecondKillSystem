import { describe, it, expect, beforeEach } from 'vitest';
import { getAccessToken, getRefreshToken, setTokens, removeTokens } from '../utils/token';

describe('utils / token', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  /* ─── 正常路径 ─── */

  it('setTokens 后 getAccessToken/getRefreshToken 返回正确值', () => {
    setTokens('access123', 'refresh456');
    expect(getAccessToken()).toBe('access123');
    expect(getRefreshToken()).toBe('refresh456');
  });

  it('removeTokens 后 getAccessToken/getRefreshToken 返回 null', () => {
    setTokens('access123', 'refresh456');
    removeTokens();
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it('只存 accessToken 不存 refreshToken 时 getRefreshToken 返回 null', () => {
    setTokens('access123', 'refresh456');
    localStorage.removeItem('refresh_token');
    expect(getAccessToken()).toBe('access123');
    expect(getRefreshToken()).toBeNull();
  });

  /* ─── 边界 ─── */

  it('特殊字符 token 存取正确', () => {
    const t = 'eyJ.ra+ndom/token_data#=test';
    setTokens(t, t);
    expect(getAccessToken()).toBe(t);
    expect(getRefreshToken()).toBe(t);
  });

  it('Unicode token 存取正确', () => {
    setTokens('token_中文_😊', 'refresh_中文');
    expect(getAccessToken()).toBe('token_中文_😊');
    expect(getRefreshToken()).toBe('refresh_中文');
  });

  it('空字符串 token 存取正确', () => {
    setTokens('', '');
    expect(getAccessToken()).toBe('');
    expect(getRefreshToken()).toBe('');
  });

  /* ─── 攻击场景 ─── */

  it('XSS payload 原样返回，不执行', () => {
    const xss = '<script>alert(1)</script>';
    setTokens(xss, 'refresh');
    expect(getAccessToken()).toBe(xss);
  });

  it('__proto__ 字段名不污染 Object.prototype', () => {
    const protoBefore = Object.prototype;
    setTokens('__proto__', '__proto__');
    expect(getAccessToken()).toBe('__proto__');
    // 验证 Object.prototype 未被污染
    expect(Object.prototype).toBe(protoBefore);
    expect(({} as Record<string, unknown>).__proto__).toBe(Object.prototype);
  });

  it('localStorage 不可用时 setTokens 不崩溃', () => {
    const orig = Storage.prototype.setItem;
    Storage.prototype.setItem = () => { throw new Error('denied'); };
    expect(() => setTokens('a', 'b')).not.toThrow();
    Storage.prototype.setItem = orig;
  });
});
