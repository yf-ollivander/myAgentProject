export interface AdminBindingForm {
  senderOpenId: string;
  username: string;
}

export function adminBindingPayload(botId: string, form: AdminBindingForm) {
  // Whitelist fields so tenant/user authority cannot be injected from UI state.
  return {
    botId,
    senderOpenId: form.senderOpenId,
    username: form.username,
  };
}
