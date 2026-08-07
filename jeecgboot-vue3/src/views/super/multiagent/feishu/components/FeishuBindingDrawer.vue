<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" title="飞书用户绑定" width="min(860px, 100vw)" destroyOnClose>
    <div class="binding-toolbar">
      <a-button v-auth="'ai:feishu:binding:add'" type="primary" preIcon="ant-design:plus-outlined" @click="adminOpen = true">新增绑定</a-button>
      <a-button v-auth="'ai:feishu:binding:self'" preIcon="ant-design:key-outlined" @click="createToken">本人绑定码</a-button>
    </div>
    <a-table :columns="columns" :data-source="rows" :loading="loading" :pagination="pagination" row-key="id" @change="changePage">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">{{ record.enabled ? '启用' : '禁用' }}</template>
        <template v-else-if="column.key === 'action'">
          <a-button v-if="record.enabled" v-auth="'ai:feishu:binding:disable'" type="link" danger @click="disable(record.id)">禁用</a-button>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="adminOpen" title="新增用户绑定" @ok="createAdmin">
      <a-form :model="admin" layout="vertical">
        <a-form-item label="Sender Open ID" required><a-input v-model:value="admin.senderOpenId" maxlength="128" /></a-form-item>
        <a-form-item label="JEECG 用户名" required><a-input v-model:value="admin.username" maxlength="50" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="tokenOpen" title="本人绑定码" :footer="null" destroyOnClose>
      <a-typography-paragraph copyable><code>{{ token.command }}</code></a-typography-paragraph>
      <a-typography-text type="secondary">有效期至 {{ token.expiresAt }}</a-typography-text>
    </a-modal>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { createFeishuBinding, createSelfBindingToken, disableFeishuBinding, listFeishuBindings } from '../feishu.api';
  import { adminBindingPayload } from '../feishu.binding';

  defineEmits(['register']);
  const botId = ref('');
  const rows = ref<any[]>([]);
  const loading = ref(false);
  const adminOpen = ref(false);
  const tokenOpen = ref(false);
  const admin = reactive({ senderOpenId: '', username: '' });
  const token = reactive({ command: '', expiresAt: '' });
  const pagination = reactive({ current: 1, pageSize: 10, total: 0 });
  const columns = [
    { title: 'Sender Open ID', dataIndex: 'maskedSenderOpenId' },
    { title: 'JEECG 用户', dataIndex: 'username' },
    { title: '来源', dataIndex: 'source', width: 120 },
    { title: '状态', key: 'enabled', width: 90 },
    { title: '操作', key: 'action', width: 90 },
  ];

  const [registerDrawer] = useDrawerInner(async (data) => {
    botId.value = data.botId;
    pagination.current = 1;
    await load();
  });

  async function load() {
    loading.value = true;
    try {
      const result: any = await listFeishuBindings({ botId: botId.value, pageNo: pagination.current, pageSize: pagination.pageSize });
      rows.value = result.records || [];
      pagination.total = result.total || 0;
    } finally { loading.value = false; }
  }
  function changePage(page) { pagination.current = page.current; pagination.pageSize = page.pageSize; load(); }
  async function createAdmin() {
    await createFeishuBinding(adminBindingPayload(botId.value, admin));
    admin.senderOpenId = ''; admin.username = ''; adminOpen.value = false; await load();
  }
  async function disable(id: string) { await disableFeishuBinding(id); await load(); }
  async function createToken() {
    const result: any = await createSelfBindingToken(botId.value);
    token.command = result.command; token.expiresAt = result.expiresAt; tokenOpen.value = true;
  }
</script>

<style scoped>
  .binding-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
