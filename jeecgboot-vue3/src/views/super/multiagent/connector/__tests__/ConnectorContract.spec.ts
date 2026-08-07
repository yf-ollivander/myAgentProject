import { connectorResponseMapping } from '../connector.contract';

describe('Connector result contract', () => {
  const legacy = {
    resultContractVersion: 'LEGACY',
    successPointer: '/success',
    outputPointer: '/output',
    summaryPointer: '/summary',
  };

  it('keeps the three pointers for LEGACY connectors', () => {
    expect(connectorResponseMapping(legacy)).toEqual({
      successPointer: '/success',
      outputPointer: '/output',
      summaryPointer: '/summary',
    });
  });

  it('omits legacy mapping for Result 1.1 connectors', () => {
    expect(connectorResponseMapping({ ...legacy, resultContractVersion: '1.1' })).toBeNull();
  });
});
