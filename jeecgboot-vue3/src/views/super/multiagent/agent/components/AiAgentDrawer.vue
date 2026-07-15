<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="title" width="min(720px, 100vw)" showFooter destroyOnClose @ok="submit">
    <a-form ref="formRef" :model="model" :rules="rules" layout="vertical">
      <!-- Stack paired fields on narrow screens so operational forms remain readable. -->
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="basic" tab="基本信息">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-item label="Agent 代码" name="agentCode">
                <a-input v-model:value="model.agentCode" :disabled="isUpdate" maxlength="64" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="名称" name="name">
                <a-input v-model:value="model.name" maxlength="100" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="描述" name="description">
            <a-textarea v-model:value="model.description" :rows="4" maxlength="500" show-count />
          </a-form-item>
        </a-tab-pane>
        <a-tab-pane key="prompt" tab="提示词">
          <a-form-item label="系统提示词" name="systemPrompt">
            <a-textarea v-model:value="model.systemPrompt" :rows="16" maxlength="20000" show-count />
          </a-form-item>
        </a-tab-pane>
        <a-tab-pane key="execution" tab="执行配置">
          <a-form-item label="HTTP Connector" name="connectorId">
            <a-select v-model:value="model.connectorId" show-search option-filter-prop="label" :options="connectorOptions" />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-item label="任务超时（秒）" name="timeoutSeconds">
                <a-input-number v-model:value="model.timeoutSeconds" :min="1" :max="300" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="最大重试次数" name="maxRetry">
                <a-input-number v-model:value="model.maxRetry" :min="0" :max="2" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-tab-pane>
        <a-tab-pane key="feishu" tab="飞书绑定">
          <a-form-item label="飞书机器人" name="feishuBotId">
            <a-select v-model:value="model.feishuBotId" allow-clear show-search option-filter-prop="label" :options="botOptions" />
          </a-form-item>
        </a-tab-pane>
      </a-tabs>
    </a-form>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { createAgent, getAgent, updateAgent } from '../agent.api';
  import { listConnectors } from '../../connector/connector.api';
  import { listFeishuBots } from '../../feishu/feishu.api';

  const emit = defineEmits(['register', 'success']);
  const formRef = ref();
  const activeTab = ref('basic');
  const isUpdate = ref(false);
  const currentId = ref('');
  const connectorOptions = ref<any[]>([]);
  const botOptions = ref<any[]>([]);
  const model = reactive<any>({ timeoutSeconds: 300, maxRetry: 0 });
  const rules = {
    agentCode: [{ required: true, message: '请输入 Agent 代码' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '代码必须以字母开头' }],
    name: [{ required: true, message: '请输入名称' }],
    connectorId: [{ required: true, message: '请选择 HTTP Connector' }],
    timeoutSeconds: [{ required: true, type: 'number', min: 1, max: 300 }],
    maxRetry: [{ required: true, type: 'number', min: 0, max: 2 }],
  };

  const title = computed(() => (isUpdate.value ? '编辑 Agent' : '新增 Agent'));

  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    activeTab.value = 'basic';
    formRef.value?.resetFields();
    Object.keys(model).forEach((key) => delete model[key]);
    Object.assign(model, { timeoutSeconds: 300, maxRetry: 0 });
    isUpdate.value = !!data?.id;
    currentId.value = data?.id || '';
    const [connectors, bots] = await Promise.all([
      listConnectors({ enabled: true, pageNo: 1, pageSize: 200 }),
      listFeishuBots({ enabled: true, entryMode: 'DIRECT_AGENT', pageNo: 1, pageSize: 200 }),
    ]);
    connectorOptions.value = (connectors?.records || []).map((item) => ({ label: `${item.name} (${item.connectorCode})`, value: item.id }));
    botOptions.value = (bots?.records || []).map((item) => ({ label: `${item.name} (${item.botKey})`, value: item.id }));
    if (isUpdate.value) Object.assign(model, await getAgent(currentId.value));
    setDrawerProps({ confirmLoading: false });
  });

  async function submit() {
    try {
      await formRef.value.validate();
      setDrawerProps({ confirmLoading: true });
      const payload = { ...model, feishuBotId: model.feishuBotId || null };
      if (isUpdate.value) await updateAgent(currentId.value, payload);
      else await createAgent(payload);
      closeDrawer();
      emit('success');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
