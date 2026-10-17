/**
 * 前端账户数据库模块
 * 基于 localStorage 实现，用于在前端本地持久化管理账户信息
 * 不依赖后端，不改动任何后端代码
 *
 * 数据结构与后端 users 表对齐（schema_init.sql）
 */

export type UserType = 'STUDENT' | 'TEACHER';

/** 本地账户记录 */
export interface LocalAccount {
  /** 用户ID（与后端同步） */
  userId: number;
  /** 用户名 */
  username: string;
  /** 用户类型 */
  userType: UserType;
  /** 真实姓名 */
  realName: string;
  /** 班级（学生可选） */
  className: string | null;
  /** 账户是否激活 */
  active: boolean;
  /** 首次注册/登录时间（ISO 字符串） */
  createdAt: string;
  /** 最后登录时间（ISO 字符串） */
  lastLogin: string | null;
  /** JWT token（登录成功后缓存，方便离线展示） */
  cachedToken: string | null;
}

const DB_KEY = 'zhixue_account_db';
const DB_VERSION_KEY = 'zhixue_account_db_version';
const CURRENT_VERSION = '1';

// ─── 内部工具 ──────────────────────────────────────────────────────────────

function readAll(): Record<string, LocalAccount> {
  try {
    const raw = localStorage.getItem(DB_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Record<string, LocalAccount>;
  } catch {
    return {};
  }
}

function writeAll(db: Record<string, LocalAccount>): void {
  localStorage.setItem(DB_KEY, JSON.stringify(db));
  localStorage.setItem(DB_VERSION_KEY, CURRENT_VERSION);
}

// ─── 公开 API ──────────────────────────────────────────────────────────────

/**
 * 新增或覆盖一条账户记录
 * 通常在注册成功后调用
 */
export function upsertAccount(account: LocalAccount): void {
  const db = readAll();
  db[account.username] = { ...account };
  writeAll(db);
}

/**
 * 登录成功后更新最后登录时间和缓存 token
 */
export function updateLoginInfo(
  username: string,
  token: string,
  userId?: number,
  userType?: UserType,
  realName?: string,
  className?: string | null,
): void {
  const db = readAll();
  const now = new Date().toISOString();

  if (db[username]) {
    // 已有记录，仅更新动态字段
    db[username].lastLogin = now;
    db[username].cachedToken = token;
    if (userId !== undefined) db[username].userId = userId;
    if (userType !== undefined) db[username].userType = userType;
    if (realName !== undefined) db[username].realName = realName;
    if (className !== undefined) db[username].className = className;
  } else {
    // 首次登录但未注册（直接通过后端登录），自动创建本地记录
    db[username] = {
      userId: userId ?? 0,
      username,
      userType: userType ?? 'STUDENT',
      realName: realName ?? username,
      className: className ?? null,
      active: true,
      createdAt: now,
      lastLogin: now,
      cachedToken: token,
    };
  }

  writeAll(db);
}

/**
 * 登出时清除缓存 token（保留账户记录）
 */
export function clearTokenByUsername(username: string): void {
  const db = readAll();
  if (db[username]) {
    db[username].cachedToken = null;
    writeAll(db);
  }
}

/**
 * 根据用户名查询单条账户记录
 */
export function findAccount(username: string): LocalAccount | null {
  const db = readAll();
  return db[username] ?? null;
}

/**
 * 获取全部账户记录列表（按最后登录时间倒序）
 */
export function listAccounts(): LocalAccount[] {
  const db = readAll();
  return Object.values(db).sort((a, b) => {
    const ta = a.lastLogin ?? a.createdAt;
    const tb = b.lastLogin ?? b.createdAt;
    return tb.localeCompare(ta);
  });
}

/**
 * 删除单条账户记录
 */
export function deleteAccount(username: string): void {
  const db = readAll();
  delete db[username];
  writeAll(db);
}

/**
 * 清空所有账户记录
 */
export function clearAllAccounts(): void {
  localStorage.removeItem(DB_KEY);
  localStorage.removeItem(DB_VERSION_KEY);
}

/**
 * 获取数据库统计信息
 */
export function getDbStats(): {
  total: number;
  students: number;
  teachers: number;
  version: string;
} {
  const accounts = listAccounts();
  return {
    total: accounts.length,
    students: accounts.filter((a) => a.userType === 'STUDENT').length,
    teachers: accounts.filter((a) => a.userType === 'TEACHER').length,
    version: localStorage.getItem(DB_VERSION_KEY) ?? CURRENT_VERSION,
  };
}

