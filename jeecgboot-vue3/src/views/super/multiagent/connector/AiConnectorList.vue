<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button v-auth="'ai:connector:add'" type="primary" preIcon="ant-design:plus-outlined" @click="openEditor()">新增</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" :dropDownActions="moreActions(record)" />
      </template>
    </BasicTable>
    <AiConnectorDrawer @register="registerDrawer" @success="reload" />
    <a-modal v-model:open="testOpen" title="测试模型 Connector" :confirm-loading="testLoading" @ok="submitTest">
      <a-textarea v-model:value="testInput" :rows="10" spellcheck="false" />
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="multiagent-connector-list">
  import { ref } from 'vue';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import AiConnectorDrawer from './components/AiConnectorDrawer.vue';
  import { deleteConnector, disableConnector, enableConnector, listConnectors, testConnector } from './connector.api';

  const { createMessage } = useMessage();
  const [registerDrawer, { openDrawer }] = useDrawer();
  const testOpen = ref(false);
  const testLoading = ref(false);
  const testInput = ref('{\n  "prompt": "你好"\n}');
  const testRecord = ref<any>();
  // Provider and result mode are the primary operational facts; keep endpoint available for troubleshooting.
  const providerLabels: Record<string, string> = {
    OPENAI_COMPATIBLE: 'OpenAI-compatible', DEEPSEEK: 'DeepSeek', ANTHROPIC: 'Anthropic', GEMINI: 'Gemini', OLLAMA: 'Ollama', CUSTOM: 'Custom',
  };
  const columns = [
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: '代码', dataIndex: 'connectorCode', width: 160 },
    { title: 'Provider', dataIndex: 'providerType', width: 150, customRender: ({ text }) => providerLabels[text] || text || 'Custom' },
    { title: 'Model Name', dataIndex: 'modelName', width: 160, customRender: ({ text }) => text || '-' },
    { title: 'Endpoint', dataIndex: 'baseUrl', width: 280, customRender: ({ record }) => `${record.baseUrl}${record.path}` },
    { title: '鉴权', dataIndex: 'authType', width: 100 },
    { title: 'Result Mode', dataIndex: 'modelResponseMode', width: 120,
      customRender: ({ record }) => record.providerType === 'CUSTOM' || !record.providerType ? record.resultContractVersion : record.modelResponseMode },
    { title: '凭据', dataIndex: 'secretConfigured', width: 90, customRender: ({ text, record }) => record.authType === 'NONE' ? '-' : (text ? '已配置' : '未配置') },
    { title: '状态', dataIndex: 'enabled', width: 90, customRender: ({ text }) => (text ? '启用' : '禁用') },
    { title: '最近测试', dataIndex: 'lastTestStatus', width: 110 },
    { title: '测试时间', dataIndex: 'lastTestTime', width: 170 },
  ];
  const searchFormSchema = [
    { label: '代码', field: 'connectorCode', component: 'Input' },
    { label: '名称', field: 'name', component: 'Input' },
    { label: '状态', field: 'enabled', component: 'Select', componentProps: { options: [{ label: '启用', value: true }, { label: '禁用', value: false }] } },
  ];
  const { tableContext } = useListPage({ tableProps: {
    title: '模型 Connector', api: listConnectors, columns, canResize: false, rowKey: 'id',
    formConfig: { labelWidth: 80, schemas: searchFormSchema, autoSubmitOnEnter: true }, actionColumn: { width: 220 },
  }});
  const [registerTable, { reload }] = tableContext;

  function openEditor(record?: any) { openDrawer(true, { id: record?.id }); }
  function openTest(record: any) { testRecord.value = record; testInput.value = '{\n  "prompt": "你好"\n}'; testOpen.value = true; }
  async function submitTest() {
    try {
      testLoading.value = true;
      const input = testInput.value.trim() ? JSON.parse(testInput.value) : {};
      const result = await testConnector(testRecord.value.id, input);
      result.success ? createMessage.success(result.message) : createMessage.error(result.message);
      testOpen.value = false; reload();
    } catch (error: any) {
      if (error instanceof SyntaxError) createMessage.error('测试输入必须是合法 JSON');
    } finally { testLoading.value = false; }
  }
  async function toggle(record: any) {
    // Surface obvious prerequisites early; backend validation remains authoritative.
    if (!record.enabled && record.authType !== 'NONE' && !record.secretConfigured) {
      createMessage.warning('请先配置 Connector 凭据');
      return;
    }
    record.enabled ? await disableConnector(record.id) : await enableConnector(record.id);
    reload();
  }
  async function remove(record: any) { await deleteConnector(record.id); reload(); }
  function actions(record: any) { return [
    { label: '编辑', icon: 'ant-design:edit-outlined', auth: 'ai:connector:edit', onClick: () => openEditor(record) },
    { label: '测试', icon: 'ant-design:experiment-outlined', auth: 'ai:connector:test', onClick: () => openTest(record) },
  ]; }
  function moreActions(record: any) { return [
    { label: record.enabled ? '禁用' : '启用', icon: record.enabled ? 'ant-design:pause-circle-outlined' : 'ant-design:play-circle-outlined', auth: record.enabled ? 'ai:connector:disable' : 'ai:connector:enable',
      popConfirm: { title: `确认${record.enabled ? '禁用' : '启用'}该 Connector？`, confirm: () => toggle(record) } },
    { label: '删除', icon: 'ant-design:delete-outlined', auth: 'ai:connector:delete', ifShow: !record.enabled,
      popConfirm: { title: '确认删除该 Connector？', confirm: () => remove(record) } },
  ]; }
</script>
