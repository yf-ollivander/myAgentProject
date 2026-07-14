<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button v-auth="'ai:agent:add'" type="primary" preIcon="ant-design:plus-outlined" @click="openEditor()">新增</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" :dropDownActions="moreActions(record)" />
      </template>
    </BasicTable>
    <AiAgentDrawer @register="registerDrawer" @success="reload" />
    <a-modal v-model:open="testOpen" title="测试 Agent" :confirm-loading="testLoading" @ok="submitTest">
      <a-textarea v-model:value="testInput" :rows="10" spellcheck="false" />
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="multiagent-agent-list">
  import { ref } from 'vue';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import AiAgentDrawer from './components/AiAgentDrawer.vue';
  import { deleteAgent, disableAgent, enableAgent, listAgents, testAgent } from './agent.api';

  const { createMessage } = useMessage();
  const [registerDrawer, { openDrawer }] = useDrawer();
  const testOpen = ref(false);
  const testLoading = ref(false);
  const testInput = ref('{\n  "task": "health check"\n}');
  const testRecord = ref<any>();

  const columns = [
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: '代码', dataIndex: 'agentCode', width: 160 },
    { title: 'Connector', dataIndex: 'connectorName', width: 180 },
    { title: '飞书机器人', dataIndex: 'feishuBotName', width: 160, customRender: ({ text }) => text || '-' },
    { title: '超时', dataIndex: 'timeoutSeconds', width: 90, customRender: ({ text }) => `${text}s` },
    { title: '重试', dataIndex: 'maxRetry', width: 70 },
    { title: '状态', dataIndex: 'enabled', width: 90, customRender: ({ text }) => (text ? '启用' : '禁用') },
    { title: '最近测试', dataIndex: 'lastTestStatus', width: 110 },
    { title: '测试时间', dataIndex: 'lastTestTime', width: 170 },
  ];
  const searchFormSchema = [
    { label: '代码', field: 'agentCode', component: 'Input' },
    { label: '名称', field: 'name', component: 'Input' },
    { label: '状态', field: 'enabled', component: 'Select', componentProps: { options: [{ label: '启用', value: true }, { label: '禁用', value: false }] } },
  ];
  const { tableContext } = useListPage({
    tableProps: {
      title: 'Agent 管理', api: listAgents, columns, canResize: false, rowKey: 'id',
      formConfig: { labelWidth: 80, schemas: searchFormSchema, autoSubmitOnEnter: true },
      actionColumn: { width: 220 },
    },
  });
  const [registerTable, { reload }] = tableContext;

  function openEditor(record?: any) {
    openDrawer(true, { id: record?.id });
  }

  function openTest(record: any) {
    testRecord.value = record;
    testInput.value = '{\n  "task": "health check"\n}';
    testOpen.value = true;
  }

  async function submitTest() {
    try {
      testLoading.value = true;
      const input = testInput.value.trim() ? JSON.parse(testInput.value) : {};
      const result = await testAgent(testRecord.value.id, input);
      result.success ? createMessage.success(result.message) : createMessage.error(result.message);
      testOpen.value = false;
      reload();
    } catch (error: any) {
      if (error instanceof SyntaxError) createMessage.error('测试输入必须是合法 JSON');
    } finally {
      testLoading.value = false;
    }
  }

  async function toggle(record: any) {
    // Surface obvious prerequisites early; backend validation remains authoritative.
    if (!record.enabled && (!record.connectorName || (record.feishuBotId && !record.feishuBotName))) {
      createMessage.warning('请先补全并启用关联配置');
      return;
    }
    if (record.enabled) await disableAgent(record.id);
    else await enableAgent(record.id);
    reload();
  }

  async function remove(record: any) {
    await deleteAgent(record.id);
    reload();
  }

  function actions(record: any) {
    return [
      { label: '编辑', icon: 'ant-design:edit-outlined', auth: 'ai:agent:edit', onClick: () => openEditor(record) },
      { label: '测试', icon: 'ant-design:experiment-outlined', auth: 'ai:agent:test', onClick: () => openTest(record) },
    ];
  }

  function moreActions(record: any) {
    return [
      { label: record.enabled ? '禁用' : '启用', icon: record.enabled ? 'ant-design:pause-circle-outlined' : 'ant-design:play-circle-outlined', auth: record.enabled ? 'ai:agent:disable' : 'ai:agent:enable',
        popConfirm: { title: `确认${record.enabled ? '禁用' : '启用'}该 Agent？`, confirm: () => toggle(record) } },
      { label: '删除', icon: 'ant-design:delete-outlined', auth: 'ai:agent:delete', ifShow: !record.enabled,
        popConfirm: { title: '确认删除该 Agent？', confirm: () => remove(record) } },
    ];
  }
</script>
