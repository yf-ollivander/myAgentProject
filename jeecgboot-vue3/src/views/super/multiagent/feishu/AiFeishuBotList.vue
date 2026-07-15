<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button v-auth="'ai:feishu:add'" type="primary" preIcon="ant-design:plus-outlined" @click="openEditor()">新增</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" :dropDownActions="moreActions(record)" />
      </template>
    </BasicTable>
    <AiFeishuBotDrawer @register="registerDrawer" @success="reload" />
    <a-modal v-model:open="testOpen" title="测试飞书机器人" :confirm-loading="testLoading" @ok="submitTest">
      <a-textarea v-model:value="testMessage" :rows="4" :maxlength="500" show-count />
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="multiagent-feishu-list">
  import { ref } from 'vue';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import AiFeishuBotDrawer from './components/AiFeishuBotDrawer.vue';
  import { deleteFeishuBot, disableFeishuBot, enableFeishuBot, listFeishuBots, testFeishuBot } from './feishu.api';

  const { createMessage } = useMessage();
  const [registerDrawer, { openDrawer }] = useDrawer();
  const testOpen = ref(false);
  const testLoading = ref(false);
  const testMessage = ref('Multi-Agent 配置测试成功');
  const testRecord = ref<any>();
  const columns = [
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: 'Bot Key', dataIndex: 'botKey', width: 160 },
    { title: 'App ID', dataIndex: 'appId', width: 180 },
    { title: '入口模式', dataIndex: 'entryMode', width: 130, customRender: ({ text }) => entryModeLabels[text] || text },
    { title: '接收方式', dataIndex: 'connectionMode', width: 120, customRender: () => 'SDK 长连接' },
    { title: '连接状态', dataIndex: 'connectionStatus', width: 110, customRender: ({ text }) => connectionStatusLabels[text] || text },
    { title: '事件处理', dataIndex: 'eventHandlingStatus', width: 110, customRender: ({ text }) => text === 'PROCESSING_ENABLED' ? '可处理' : '仅接收' },
    { title: '默认会话', dataIndex: 'defaultChatId', width: 190, customRender: ({ text }) => text || '-' },
    { title: '应用凭据', dataIndex: 'appSecretConfigured', width: 100, customRender: ({ text }) => text ? '已配置' : '未配置' },
    { title: '状态', dataIndex: 'enabled', width: 90, customRender: ({ text }) => (text ? '启用' : '禁用') },
    { title: '最近测试', dataIndex: 'lastTestStatus', width: 110 },
    { title: '测试时间', dataIndex: 'lastTestTime', width: 170 },
  ];
  const connectionStatusLabels = {
    STARTING: '启动中', CONNECTED: '已连接', FAILED: '连接失败',
    DISCONNECTED: '未连接', DISABLED: '未启用',
  };
  const entryModeLabels = { DIRECT_AGENT: '直连 Agent', ORCHESTRATOR: '编排入口' };
  const searchFormSchema = [
    { label: 'Bot Key', field: 'botKey', component: 'Input' },
    { label: '名称', field: 'name', component: 'Input' },
    { label: '入口模式', field: 'entryMode', component: 'Select', componentProps: { options: [
      { label: '直连 Agent', value: 'DIRECT_AGENT' }, { label: '编排入口', value: 'ORCHESTRATOR' },
    ] } },
    { label: '状态', field: 'enabled', component: 'Select', componentProps: { options: [{ label: '启用', value: true }, { label: '禁用', value: false }] } },
  ];
  const { tableContext } = useListPage({ tableProps: {
    title: '飞书机器人', api: listFeishuBots, columns, canResize: false, rowKey: 'id',
    formConfig: { labelWidth: 80, schemas: searchFormSchema, autoSubmitOnEnter: true }, actionColumn: { width: 220 },
  }});
  const [registerTable, { reload }] = tableContext;

  function openEditor(record?: any) { openDrawer(true, { id: record?.id }); }
  function openTest(record: any) {
    if (!record.defaultChatId) {
      createMessage.warning('请先配置默认会话 Chat ID 再发送测试消息');
      return;
    }
    testRecord.value = record;
    testMessage.value = 'Multi-Agent 配置测试成功';
    testOpen.value = true;
  }
  async function submitTest() {
    try {
      testLoading.value = true;
      const result = await testFeishuBot(testRecord.value.id, testMessage.value);
      result.success ? createMessage.success(result.message) : createMessage.error(result.message);
      testOpen.value = false;
      reload();
    } finally {
      testLoading.value = false;
    }
  }
  async function toggle(record: any) {
    // SDK long connections do not use HTTP callback verification or encryption fields.
    if (!record.enabled && !record.appSecretConfigured) {
      createMessage.warning('请先配置 App Secret');
      return;
    }
    record.enabled ? await disableFeishuBot(record.id) : await enableFeishuBot(record.id);
    reload();
  }
  async function remove(record: any) { await deleteFeishuBot(record.id); reload(); }
  function actions(record: any) { return [
    { label: '编辑', icon: 'ant-design:edit-outlined', auth: 'ai:feishu:edit', onClick: () => openEditor(record) },
    { label: '测试', icon: 'ant-design:send-outlined', auth: 'ai:feishu:test', onClick: () => openTest(record) },
  ]; }
  function moreActions(record: any) { return [
    { label: record.enabled ? '禁用' : '启用', icon: record.enabled ? 'ant-design:pause-circle-outlined' : 'ant-design:play-circle-outlined', auth: record.enabled ? 'ai:feishu:disable' : 'ai:feishu:enable',
      popConfirm: { title: `确认${record.enabled ? '禁用' : '启用'}该飞书机器人？`, confirm: () => toggle(record) } },
    { label: '删除', icon: 'ant-design:delete-outlined', auth: 'ai:feishu:delete', ifShow: !record.enabled,
      popConfirm: { title: '确认删除该飞书机器人？', confirm: () => remove(record) } },
  ]; }
</script>
