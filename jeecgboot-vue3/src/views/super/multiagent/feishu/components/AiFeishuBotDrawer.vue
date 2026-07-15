<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="title" width="min(680px, 100vw)" showFooter destroyOnClose @ok="submit">
    <a-form ref="formRef" :model="model" :rules="rules" layout="vertical">
      <!-- Stack paired identity fields on narrow screens so long IDs do not collide. -->
      <a-row :gutter="16">
        <a-col :xs="24" :md="12"><a-form-item label="Bot Key" name="botKey"><a-input v-model:value="model.botKey" :disabled="isUpdate" maxlength="64" /></a-form-item></a-col>
        <a-col :xs="24" :md="12"><a-form-item label="名称" name="name"><a-input v-model:value="model.name" maxlength="100" /></a-form-item></a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="入口模式" name="entryMode">
            <a-segmented v-model:value="model.entryMode" block :options="entryModeOptions" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="命令处理" name="commandEnabled">
            <a-switch v-model:checked="model.commandEnabled" checked-children="可处理" un-checked-children="仅接收" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="App ID" name="appId"><a-input v-model:value="model.appId" maxlength="100" /></a-form-item>
      <a-form-item label="App Secret" name="appSecret">
        <a-input-password v-model:value="model.appSecret" autocomplete="new-password" maxlength="4000" />
        <a-checkbox v-if="configured.appSecret" v-model:checked="model.clearAppSecret">清除已配置 App Secret</a-checkbox>
      </a-form-item>
      <a-form-item label="默认会话 Chat ID（可选）" name="defaultChatId" extra="仅用于主动发送测试消息或无来源会话的任务通知。">
        <a-input v-model:value="model.defaultChatId" maxlength="100" />
      </a-form-item>
      <a-form-item v-if="isUpdate" label="长连接状态">
        <a-input :value="model.connectionStatus || 'DISCONNECTED'" readonly />
      </a-form-item>
    </a-form>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { createFeishuBot, getFeishuBot, updateFeishuBot } from '../feishu.api';

  const emit = defineEmits(['register', 'success']);
  const formRef = ref();
  const isUpdate = ref(false);
  const currentId = ref('');
  const model = reactive<any>({});
  const configured = reactive({ appSecret: false });
  const entryModeOptions = [
    { label: '直连 Agent', value: 'DIRECT_AGENT' },
    { label: '编排入口', value: 'ORCHESTRATOR' },
  ];
  const rules = {
    botKey: [{ required: true, message: '请输入 Bot Key' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: 'Bot Key 必须以字母开头' }],
    name: [{ required: true, message: '请输入名称' }],
    appId: [{ required: true, message: '请输入 App ID' }],
    entryMode: [{ required: true, message: '请选择入口模式' }],
  };
  const title = computed(() => (isUpdate.value ? '编辑飞书机器人' : '新增飞书机器人'));

  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    formRef.value?.resetFields();
    Object.keys(model).forEach((key) => delete model[key]);
    Object.assign(model, { entryMode: 'DIRECT_AGENT', commandEnabled: false, clearAppSecret: false });
    Object.assign(configured, { appSecret: false });
    isUpdate.value = !!data?.id;
    currentId.value = data?.id || '';
    if (isUpdate.value) {
      const detail: any = await getFeishuBot(currentId.value);
      Object.assign(configured, { appSecret: detail.appSecretConfigured });
      Object.assign(model, detail, { appSecret: '', clearAppSecret: false });
    }
    setDrawerProps({ confirmLoading: false });
  });

  async function submit() {
    try {
      await formRef.value.validate();
      setDrawerProps({ confirmLoading: true });
      const payload = {
        botKey: model.botKey, name: model.name, appId: model.appId, defaultChatId: model.defaultChatId || null,
        entryMode: model.entryMode, commandEnabled: !!model.commandEnabled,
        // Long connection mode authenticates with the application credential only.
        appSecret: model.appSecret || null, clearAppSecret: !!model.clearAppSecret,
      };
      if (isUpdate.value) await updateFeishuBot(currentId.value, payload);
      else await createFeishuBot(payload);
      closeDrawer(); emit('success');
    } finally { setDrawerProps({ confirmLoading: false }); }
  }
</script>
