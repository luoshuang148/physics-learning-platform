import axios, { AxiosError } from 'axios';
import { getSessionId, resetSessionId } from '@/utils/session';

export const resolveApiBaseUrl = () => {
  // 优先使用环境变量配置
  const configured = import.meta.env.VITE_API_BASE_URL?.trim();
  if (configured) {
    return configured.replace(/\/$/, '');
  }

  // 开发环境（端口5173）：Vite 开发服务器会自动代理 /api 到后端
  // 生产环境（Docker部署）：使用相对路径，请求同域名
  return '/api/v1';
};

const request = axios.create({
  baseURL: resolveApiBaseUrl(),
  timeout: 30000,
});

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('zhixue_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (!config.headers['X-Session-Id']) {
    config.headers['X-Session-Id'] = getSessionId();
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const sid = response.headers['x-session-id'];
    if (sid) {
      sessionStorage.setItem('zhixue_session_id', sid);
    }
    return response;
  },
  (error: AxiosError<any>) => {
    if (error?.response?.data?.code === 4508) {
      resetSessionId();
    }

    if (error.code === 'ERR_NETWORK' && !error.response) {
      const baseURL = typeof error.config?.baseURL === 'string' ? error.config.baseURL : '';
      const hint = baseURL.startsWith('http')
        ? `无法连接后端服务：${baseURL}`
        : '无法连接后端服务，请确认前端开发代理和后端服务已启动';
      return Promise.reject(new Error(hint));
    }

    return Promise.reject(error);
  }
);

export default request;

