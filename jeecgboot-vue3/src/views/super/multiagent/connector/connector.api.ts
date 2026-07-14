import { defHttp } from '/@/utils/http/axios';

const base = '/api/ai/connectors';

export const listConnectors = (params) => defHttp.get({ url: base, params });
export const getConnector = (id: string) => defHttp.get({ url: `${base}/${id}` });
export const createConnector = (data) => defHttp.post({ url: base, data });
export const updateConnector = (id: string, data) => defHttp.put({ url: `${base}/${id}`, data });
export const deleteConnector = (id: string) => defHttp.delete({ url: `${base}/${id}` });
export const enableConnector = (id: string) => defHttp.post({ url: `${base}/${id}/enable` });
export const disableConnector = (id: string) => defHttp.post({ url: `${base}/${id}/disable` });
export const testConnector = (id: string, input: unknown) => defHttp.post({ url: `${base}/${id}/test`, data: { input } });
