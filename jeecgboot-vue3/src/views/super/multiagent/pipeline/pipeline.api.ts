import { defHttp } from '/@/utils/http/axios';
import type { PipelineDraft, PipelineVersionView, ValidationResult } from './pipeline.types';

const base = '/api/ai/pipelines';

export class PipelineApiError extends Error {
  constructor(public code: number, message: string, public details: unknown[] = []) {
    super(message);
  }
}

export const listPipelines = (params) => defHttp.get({ url: base, params });
export const createPipeline = (data) => defHttp.post({ url: base, data });
export const getPipeline = (id: string) => defHttp.get({ url: `${base}/${id}` });
export const deletePipeline = (id: string) => defHttp.delete({ url: `${base}/${id}` });
export const getPipelineDraft = (id: string) => defHttp.get<PipelineDraft>({ url: `${base}/${id}/draft` });

export async function savePipelineDraft(id: string, data: unknown): Promise<number> {
  const response: any = await defHttp.put(
    { url: `${base}/${id}/draft`, data },
    { isTransformResponse: false, errorMessageMode: 'none' }
  );
  if (response.code !== 200) throw new PipelineApiError(response.code, response.message, response.result || []);
  return response.result;
}

export const validatePipeline = (id: string, draftRevision: number) =>
  defHttp.post<ValidationResult>({ url: `${base}/${id}/validate`, data: { draftRevision } });
export const publishPipeline = (id: string, data) => defHttp.post({ url: `${base}/${id}/publish`, data });
export const listPipelineVersions = (id: string, params = {}) => defHttp.get({ url: `${base}/${id}/versions`, params });
export const getPipelineVersion = (id: string, version: number) => defHttp.get<PipelineVersionView>({ url: `${base}/${id}/versions/${version}` });
export const enablePipeline = (id: string) => defHttp.post({ url: `${base}/${id}/enable` });
export const disablePipeline = (id: string) => defHttp.post({ url: `${base}/${id}/disable` });
export const listPipelineOptions = (params) => defHttp.get({ url: `${base}/options`, params });
export const listNotificationBotOptions = (params = {}) => defHttp.get({ url: `${base}/notification-bot-options`, params });
