<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="title" width="min(720px, 100vw)" showFooter destroyOnClose @ok="submit">
    <a-form ref="formRef" :model="model" :rules="rules" layout="vertical">
      <!-- Stack dense connector fields on narrow screens to keep values inspectable. -->
      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="Connector 代码" name="connectorCode">
            <a-input v-model:value="model.connectorCode" :disabled="isUpdate" maxlength="64" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="名称" name="name">
            <a-input v-model:value="model.name" maxlength="100" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :xs="24" :md="16">
          <a-form-item label="Base URL" name="baseUrl">
            <a-input v-model:value="model.baseUrl" maxlength="500" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="8">
          <a-form-item label="Path" name="path">
            <a-input v-model:value="model.path" maxlength="255" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :xs="24" :md="8">
          <a-form-item label="鉴权类型" name="authType">
            <a-select v-model:value="model.authType" :options="authOptions" />
          </a-form-item>
        </a-col>
        <a-col v-if="model.authType === 'API_KEY'" :xs="24" :md="8">
          <a-form-item label="API Key Header" name="authHeader">
            <a-input v-model:value="model.authHeader" maxlength="100" />
          </a-form-item>
        </a-col>
        <a-col v-if="model.authType !== 'NONE'" :xs="24" :md="model.authType === 'API_KEY' ? 8 : 16">
          <a-form-item label="凭据" name="secret">
            <a-input-password v-model:value="model.secret" maxlength="4000" autocomplete="new-password" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item v-if="isUpdate && secretConfigured && model.authType !== 'NONE'">
        <a-checkbox v-model:checked="model.clearSecret">清除已配置凭据</a-checkbox>
      </a-form-item>
      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="连接超时（秒）" name="connectTimeout">
            <a-input-number v-model:value="model.connectTimeout" :min="1" :max="300" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="读取超时（秒）" name="readTimeout">
            <a-input-number v-model:value="model.readTimeout" :min="1" :max="300" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="非敏感请求头（JSON 对象）" name="requestHeadersText">
        <a-textarea v-model:value="model.requestHeadersText" :rows="5" spellcheck="false" />
      </a-form-item>
      <a-form-item label="结果协议" name="resultContractVersion">
        <a-segmented v-model:value="model.resultContractVersion" block :options="contractOptions" />
      </a-form-item>
      <a-divider v-if="model.resultContractVersion === 'LEGACY'" orientation="left">响应映射</a-divider>
      <a-row v-if="model.resultContractVersion === 'LEGACY'" :gutter="16">
        <a-col :xs="24" :md="8"><a-form-item label="成功字段" name="successPointer"><a-input v-model:value="model.successPointer" /></a-form-item></a-col>
        <a-col :xs="24" :md="8"><a-form-item label="输出字段" name="outputPointer"><a-input v-model:value="model.outputPointer" /></a-form-item></a-col>
        <a-col :xs="24" :md="8"><a-form-item label="摘要字段" name="summaryPointer"><a-input v-model:value="model.summaryPointer" /></a-form-item></a-col>
      </a-row>
    </a-form>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { createConnector, getConnector, updateConnector } from '../connector.api';
  import { connectorResponseMapping } from '../connector.contract';

  const emit = defineEmits(['register', 'success']);
  const { createMessage } = useMessage();
  const formRef = ref();
  const isUpdate = ref(false);
  const currentId = ref('');
  const secretConfigured = ref(false);
  const model = reactive<any>({});
  const authOptions = [
    { label: '无鉴权', value: 'NONE' },
    { label: 'Bearer Token', value: 'BEARER' },
    { label: 'API Key', value: 'API_KEY' },
  ];
  const contractOptions = [
    { label: 'LEGACY', value: 'LEGACY' },
    { label: 'Result 1.1', value: '1.1' },
  ];
  const rules = {
    connectorCode: [{ required: true, message: '请输入 Connector 代码' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '代码必须以字母开头' }],
    name: [{ required: true, message: '请输入名称' }],
    baseUrl: [{ required: true, message: '请输入 Base URL' }],
    path: [{ required: true, message: '请输入 Path' }],
    authType: [{ required: true }],
    connectTimeout: [{ required: true, type: 'number', min: 1, max: 300 }],
    readTimeout: [{ required: true, type: 'number', min: 1, max: 300 }],
    resultContractVersion: [{ required: true }],
    successPointer: [{ required: true }], outputPointer: [{ required: true }], summaryPointer: [{ required: true }],
  };
  const title = computed(() => (isUpdate.value ? '编辑 HTTP Connector' : '新增 HTTP Connector'));

  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    formRef.value?.resetFields();
    Object.keys(model).forEach((key) => delete model[key]);
    Object.assign(model, { authType: 'NONE', authHeader: 'X-API-Key', connectTimeout: 10, readTimeout: 300, resultContractVersion: 'LEGACY',
      requestHeadersText: '{}', successPointer: '/success', outputPointer: '/output', summaryPointer: '/summary', clearSecret: false });
    isUpdate.value = !!data?.id;
    currentId.value = data?.id || '';
    secretConfigured.value = false;
    if (isUpdate.value) {
      const detail: any = await getConnector(currentId.value);
      secretConfigured.value = detail.secretConfigured;
      Object.assign(model, detail, {
        secret: '', clearSecret: false,
        requestHeadersText: JSON.stringify(detail.requestHeaders || {}, null, 2),
        successPointer: detail.responseMapping?.successPointer || '/success',
        outputPointer: detail.responseMapping?.outputPointer || '/output',
        summaryPointer: detail.responseMapping?.summaryPointer || '/summary',
      });
    }
    setDrawerProps({ confirmLoading: false });
  });

  async function submit() {
    try {
      await formRef.value.validate();
      const requestHeaders = JSON.parse(model.requestHeadersText || '{}');
      if (Array.isArray(requestHeaders) || typeof requestHeaders !== 'object') throw new Error();
      setDrawerProps({ confirmLoading: true });
      const payload = {
        connectorCode: model.connectorCode, name: model.name, baseUrl: model.baseUrl, path: model.path,
        authType: model.authType, authHeader: model.authHeader, secret: model.secret || null, clearSecret: !!model.clearSecret,
        connectTimeout: model.connectTimeout, readTimeout: model.readTimeout, requestHeaders,
        resultContractVersion: model.resultContractVersion,
        // Result 1.1 is schema-driven and must never persist legacy JSON pointers.
        responseMapping: connectorResponseMapping(model),
      };
      if (isUpdate.value) await updateConnector(currentId.value, payload);
      else await createConnector(payload);
      closeDrawer();
      emit('success');
    } catch (error: any) {
      if (error instanceof SyntaxError || error?.message === '') createMessage.error('请求头必须是合法 JSON 对象');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
