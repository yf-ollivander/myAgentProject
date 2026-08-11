export const MODEL_PROVIDERS = ['OPENAI_COMPATIBLE', 'DEEPSEEK', 'ANTHROPIC', 'GEMINI', 'OLLAMA'] as const;

export const PROVIDER_PRESETS: Record<string, Record<string, any>> = {
  OPENAI_COMPATIBLE: {
    baseUrl: 'https://api.openai.com', path: '/v1/chat/completions', authType: 'BEARER', authHeader: '', requestHeadersText: '{}',
  },
  DEEPSEEK: {
    baseUrl: 'https://api.deepseek.com', path: '/chat/completions', authType: 'BEARER', authHeader: '', requestHeadersText: '{}',
  },
  ANTHROPIC: {
    baseUrl: 'https://api.anthropic.com', path: '/v1/messages', authType: 'API_KEY', authHeader: 'x-api-key',
    requestHeadersText: '{\n  "anthropic-version": "2023-06-01"\n}',
  },
  GEMINI: {
    baseUrl: 'https://generativelanguage.googleapis.com', path: '/v1beta/models/{model}:generateContent', authType: 'API_KEY',
    authHeader: 'x-goog-api-key', requestHeadersText: '{}',
  },
  OLLAMA: {
    baseUrl: 'http://127.0.0.1:11434', path: '/api/chat', authType: 'NONE', authHeader: '', requestHeadersText: '{}',
  },
  CUSTOM: { baseUrl: '', path: '', authType: 'NONE', authHeader: 'X-API-Key', requestHeadersText: '{}' },
};

export function isModelProvider(providerType?: string) {
  return MODEL_PROVIDERS.includes(providerType as (typeof MODEL_PROVIDERS)[number]);
}

export function connectorResponseMapping(model: Record<string, any>) {
  if (model.providerType !== 'CUSTOM' || model.resultContractVersion !== 'LEGACY') return null;
  return {
    successPointer: model.successPointer,
    outputPointer: model.outputPointer,
    summaryPointer: model.summaryPointer,
  };
}

export function connectorModelOptions(model: Record<string, any>) {
  if (!isModelProvider(model.providerType)) return {};
  return Object.fromEntries(
    ['temperature', 'topP', 'maxTokens']
      .filter((key) => model[key] !== undefined && model[key] !== null && model[key] !== '')
      .map((key) => [key, model[key]])
  );
}

export function switchConnectorProvider(model: Record<string, any>, nextProvider: string) {
  const previous = PROVIDER_PRESETS[model.providerType] || PROVIDER_PRESETS.CUSTOM;
  const next = PROVIDER_PRESETS[nextProvider] || PROVIDER_PRESETS.CUSTOM;
  const preserved = new Set<string>();
  const updates: Record<string, any> = { providerType: nextProvider };
  // Only replace blank or untouched preset values; credentials are intentionally outside this field set.
  ['baseUrl', 'path', 'authType', 'authHeader', 'requestHeadersText'].forEach((field) => {
    const value = model[field];
    if (value === undefined || value === null || value === '' || value === previous[field]) updates[field] = next[field];
    else preserved.add(field);
  });
  if (isModelProvider(nextProvider)) {
    updates.resultContractVersion = '1.1';
    updates.modelResponseMode = isModelProvider(model.providerType) ? model.modelResponseMode || 'TEXT' : 'TEXT';
  } else {
    updates.resultContractVersion = 'LEGACY';
  }
  return { updates, preserved };
}
