import { adminBindingPayload } from '../feishu.binding';

describe('Feishu binding payload', () => {
  it('includes only user-entered binding identity fields', () => {
    const source = {
      senderOpenId: 'ou_sender',
      username: 'alice',
      tenantId: 'other-tenant',
      userId: 'forged-user',
      enabled: false,
      source: 'SELF_SERVICE',
    };

    expect(adminBindingPayload('bot-1', source)).toEqual({
      botId: 'bot-1',
      senderOpenId: 'ou_sender',
      username: 'alice',
    });
  });
});
