import { defHttp } from '/@/utils/http/axios';

const base = '/api/ai/agents';

export const listAgents = (params) => defHttp.get({ url: base, params });
export const getAgent = (id: string) => defHttp.get({ url: `${base}/${id}` });
export const createAgent = (data) => defHttp.post({ url: base, data });
export const updateAgent = (id: string, data) => defHttp.put({ url: `${base}/${id}`, data });
export const deleteAgent = (id: string) => defHttp.delete({ url: `${base}/${id}` });
export const enableAgent = (id: string) => defHttp.post({ url: `${base}/${id}/enable` });
export const disableAgent = (id: string) => defHttp.post({ url: `${base}/${id}/disable` });
export const testAgent = (id: string, input: unknown) => defHttp.post({ url: `${base}/${id}/test`, data: { input } });
