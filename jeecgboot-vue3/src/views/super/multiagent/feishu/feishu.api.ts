import { defHttp } from '/@/utils/http/axios';

const base = '/api/ai/feishu-bots';

export const listFeishuBots = (params) => defHttp.get({ url: base, params });
export const getFeishuBot = (id: string) => defHttp.get({ url: `${base}/${id}` });
export const createFeishuBot = (data) => defHttp.post({ url: base, data });
export const updateFeishuBot = (id: string, data) => defHttp.put({ url: `${base}/${id}`, data });
export const deleteFeishuBot = (id: string) => defHttp.delete({ url: `${base}/${id}` });
export const enableFeishuBot = (id: string) => defHttp.post({ url: `${base}/${id}/enable` });
export const disableFeishuBot = (id: string) => defHttp.post({ url: `${base}/${id}/disable` });
export const testFeishuBot = (id: string, testMessage: string) =>
  defHttp.post({ url: `${base}/${id}/test`, data: { testMessage } });
