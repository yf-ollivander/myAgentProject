import {
  connectorModelOptions,
  connectorResponseMapping,
  isModelProvider,
  switchConnectorProvider,
} from '../connector.contract';

describe('connector contract', () => {
  const legacy = {
    providerType: 'CUSTOM', resultContractVersion: 'LEGACY', successPointer: '/success', outputPointer: '/output', summaryPointer: '/summary',
  };

  it('keeps Custom legacy mapping and strips it from Result 1.1 or model Providers', () => {
    expect(connectorResponseMapping(legacy)).toEqual({ successPointer: '/success', outputPointer: '/output', summaryPointer: '/summary' });
    expect(connectorResponseMapping({ ...legacy, resultContractVersion: '1.1' })).toBeNull();
    expect(connectorResponseMapping({ ...legacy, providerType: 'OPENAI_COMPATIBLE' })).toBeNull();
  });

  it('fills new Provider defaults without overwriting custom values or credentials', () => {
    const model = {
      providerType: 'OPENAI_COMPATIBLE', baseUrl: 'https://proxy.example.com', path: '/v1/chat/completions', authType: 'BEARER',
      authHeader: '', requestHeadersText: '{}', secret: 'preserve-me', modelResponseMode: 'TEXT',
    };
    const result = switchConnectorProvider(model, 'ANTHROPIC');
    expect(result.updates.baseUrl).toBeUndefined();
    expect(result.preserved.has('baseUrl')).toBe(true);
    expect(result.updates.path).toBe('/v1/messages');
    expect(result.updates.authType).toBe('API_KEY');
    expect(result.updates.secret).toBeUndefined();
    expect(result.updates.resultContractVersion).toBe('1.1');
  });

  it('submits only known model options', () => {
    expect(isModelProvider('OLLAMA')).toBe(true);
    expect(connectorModelOptions({ providerType: 'GEMINI', temperature: 0.7, topP: null, maxTokens: 4096, unknown: 1 }))
      .toEqual({ temperature: 0.7, maxTokens: 4096 });
    expect(connectorModelOptions({ providerType: 'CUSTOM', temperature: 0.7 })).toEqual({});
  });
});
