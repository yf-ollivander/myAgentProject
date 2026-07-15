<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle><a-button v-auth="'ai:pipeline:add'" type="primary" preIcon="ant-design:plus-outlined" @click="createOpen = true">新增流程</a-button></template>
      <template #action="{ record }"><TableAction :actions="actions(record)" :dropDownActions="moreActions(record)" /></template>
    </BasicTable>
    <a-modal v-model:open="createOpen" title="新增流程" :confirm-loading="creating" @ok="submitCreate">
      <a-form layout="vertical">
        <a-form-item label="流程代码" required><a-input v-model:value="createForm.code" placeholder="software_delivery" /></a-form-item>
        <a-form-item label="流程名称" required><a-input v-model:value="createForm.name" /></a-form-item>
        <a-form-item label="说明"><a-textarea v-model:value="createForm.description" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>
    <VersionDrawer v-model:open="versionOpen" :pipeline-id="versionPipelineId" @open-version="openVersion" />
  </div>
</template>

<script lang="ts" setup name="multiagent-pipeline-list">
  import { reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import VersionDrawer from './components/VersionDrawer.vue';
  import { createPipeline, deletePipeline, disablePipeline, enablePipeline, listPipelines } from './pipeline.api';

  const router = useRouter();
  const { createMessage } = useMessage();
  const createOpen = ref(false);
  const creating = ref(false);
  const createForm = reactive({ code: '', name: '', description: '' });
  const versionOpen = ref(false);
  const versionPipelineId = ref('');

  const columns = [
    { title: '名称', dataIndex: 'name', width: 190 }, { title: '代码', dataIndex: 'pipelineCode', width: 170 },
    { title: '草稿 revision', dataIndex: 'draftRevision', width: 120 }, { title: '最新版本', dataIndex: 'latestVersion', width: 100 },
    { title: '状态', dataIndex: 'enabled', width: 90, customRender: ({ text }) => text ? '启用' : '禁用' },
    { title: '更新时间', dataIndex: 'updateTime', width: 170 },
  ];
  const searchFormSchema = [
    { label: '代码', field: 'code', component: 'Input' }, { label: '名称', field: 'name', component: 'Input' },
    { label: '状态', field: 'enabled', component: 'Select', componentProps: { options: [{ label: '启用', value: true }, { label: '禁用', value: false }] } },
  ];
  const { tableContext } = useListPage({ tableProps: { title: '多 Agent 流程', api: listPipelines, columns, canResize: false, rowKey: 'id', formConfig: { labelWidth: 80, schemas: searchFormSchema, autoSubmitOnEnter: true }, actionColumn: { width: 240 } } });
  const [registerTable, { reload }] = tableContext;

  async function submitCreate() {
    if (!createForm.code || !createForm.name) { createMessage.warning('请填写流程代码和名称'); return; }
    creating.value = true;
    try {
      const result: any = await createPipeline(createForm);
      createOpen.value = false;
      await router.push({ path: '/multi-agent/pipelines/design', query: { id: result.id } });
    } finally { creating.value = false; }
  }
  const edit = (record) => router.push({ path: '/multi-agent/pipelines/design', query: { id: record.id } });
  function showVersions(record) { versionPipelineId.value = record.id; versionOpen.value = true; }
  function openVersion(version) { versionOpen.value = false; router.push({ path: '/multi-agent/pipelines/design', query: { id: versionPipelineId.value, version } }); }
  async function toggle(record) { record.enabled ? await disablePipeline(record.id) : await enablePipeline(record.id); reload(); }
  async function remove(record) { await deletePipeline(record.id); reload(); }
  function actions(record) { return [
    { label: '设计', icon: 'ant-design:edit-outlined', auth: 'ai:pipeline:edit', onClick: () => edit(record) },
    { label: '版本', icon: 'ant-design:history-outlined', auth: 'ai:pipeline:list', ifShow: record.latestVersion > 0, onClick: () => showVersions(record) },
  ]; }
  function moreActions(record) { return [
    { label: record.enabled ? '禁用' : '启用', icon: record.enabled ? 'ant-design:pause-circle-outlined' : 'ant-design:play-circle-outlined', auth: record.enabled ? 'ai:pipeline:disable' : 'ai:pipeline:enable', ifShow: record.latestVersion > 0, popConfirm: { title: `确认${record.enabled ? '禁用' : '启用'}该流程？`, confirm: () => toggle(record) } },
    { label: '删除', icon: 'ant-design:delete-outlined', auth: 'ai:pipeline:delete', ifShow: !record.enabled && record.latestVersion === 0, popConfirm: { title: '确认删除未发布流程？', confirm: () => remove(record) } },
  ]; }
</script>
