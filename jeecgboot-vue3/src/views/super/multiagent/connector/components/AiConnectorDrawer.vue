<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="title" width="min(720px, 100vw)" showFooter destroyOnClose @ok="submit">
    <a-form ref="formRef" :model="model" :rules="rules" layout="vertical">
      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="Connector 代码" name="connectorCode"><a-input v-model:value="model.connectorCode" :disabled="isUpdate" maxlength="64" /></a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="名称" name="name"><a-input v-model:value="model.name" maxlength="100" /></a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="Provider" name="providerType">
            <a-select :value="model.providerType" :options="providerOptions" @change="onProviderChange" />
          </a-form-item>
        </a-col>
        <a-col v-if="isModel" :xs="24" :md="12">
          <a-form-item label="Model Name" name="modelName"><a-input v-model:value="model.modelName" maxlength="128" /></a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :xs="24" :md="16">
          <a-form-item label="Base URL" name="baseUrl" :extra="fieldExtra('baseUrl')"><a-input v-model:value="model.baseUrl" maxlength="500" /></a-form-item>
        </a-col>
        <a-col :xs="24" :md="8">
          <a-form-item label="Path" name="path" :extra="fieldExtra('path')"><a-input v-model:value="model.path" maxlength="255" /></a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :xs="24" :md="8">
          <a-form-item label="鉴权类型" name="authType" :extra="fieldExtra('authType')"><a-select v-model:value="model.authType" :options="authOptions" /></a-form-item>
        </a-col>
        <a-col v-if="model.authType === 'API_KEY'" :xs="24" :md="8">
          <a-form-item label="API Key Header" name="authHeader" :extra="fieldExtra('authHeader')"><a-input v-model:value="model.authHeader" maxlength="100" /></a-form-item>
        </a-col>
        <a-col v-if="model.authType !== 'NONE'" :xs="24" :md="model.authType === 'API_KEY' ? 8 : 16">
          <a-form-item label="凭据" name="secret"><a-input-password v-model:value="model.secret" maxlength="4000" autocomplete="new-password" /></a-form-item>
        </a-col>
      </a-row>
      <a-form-item v-if="isUpdate && secretConfigured && model.authType !== 'NONE'">
        <a-checkbox v-model:checked="model.clearSecret">清除已配置凭据</a-checkbox>
      </a-form-item>

      <a-form-item v-if="isModel" label="模型结果模式" name="modelResponseMode">
        <a-segmented v-model:value="model.modelResponseMode" block :options="modelResponseOptions" />
      </a-form-item>
      <template v-else>
        <a-form-item label="Custom 结果协议" name="resultContractVersion">
          <a-segmented v-model:value="model.resultContractVersion" block :options="contractOptions" />
        </a-form-item>
        <a-row v-if="model.resultContractVersion === 'LEGACY'" :gutter="16">
          <a-col :xs="24" :md="8"><a-form-item label="成功字段" name="successPointer"><a-input v-model:value="model.successPointer" /></a-form-item></a-col>
          <a-col :xs="24" :md="8"><a-form-item label="输出字段" name="outputPointer"><a-input v-model:value="model.outputPointer" /></a-form-item></a-col>
          <a-col :xs="24" :md="8"><a-form-item label="摘要字段" name="summaryPointer"><a-input v-model:value="model.summaryPointer" /></a-form-item></a-col>
        </a-row>
      </template>

      <a-collapse v-model:activeKey="advancedKeys" ghost>
        <a-collapse-panel key="advanced" header="高级配置">
          <a-row v-if="isModel" :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-item label="Temperature" name="temperature"><a-input-number v-model:value="model.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" /></a-form-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-item label="Top P" name="topP"><a-input-number v-model:value="model.topP" :min="0" :max="1" :step="0.05" style="width: 100%" /></a-form-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-item label="Max Tokens" name="maxTokens"><a-input-number v-model:value="model.maxTokens" :min="1" :max="65536" style="width: 100%" /></a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="非敏感请求头（JSON 对象）" name="requestHeadersText" :extra="fieldExtra('requestHeadersText')">
            <a-textarea v-model:value="model.requestHeadersText" :rows="5" spellcheck="false" />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item label="连接超时（秒）" name="connectTimeout"><a-input-number v-model:value="model.connectTimeout" :min="1" :max="300" style="width: 100%" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item label="读取超时（秒）" name="readTimeout"><a-input-number v-model:value="model.readTimeout" :min="1" :max="300" style="width: 100%" /></a-form-item></a-col>
          </a-row>
        </a-collapse-panel>
      </a-collapse>
    </a-form>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { createConnector, getConnector, updateConnector } from '../connector.api';
  import { connectorModelOptions, connectorResponseMapping, isModelProvider, switchConnectorProvider } from '../connector.contract';

  const emit = defineEmits(['register', 'success']);
  const { createMessage } = useMessage();
  const formRef = ref();
  const isUpdate = ref(false);
  const currentId = ref('');
  const secretConfigured = ref(false);
  const advancedKeys = ref<string[]>([]);
  const preservedFields = ref(new Set<string>());
  const model = reactive<any>({});
  const providerOptions = [
    { label: 'OpenAI-compatible', value: 'OPENAI_COMPATIBLE' },
    { label: 'DeepSeek', value: 'DEEPSEEK' },
    { label: 'Anthropic', value: 'ANTHROPIC' },
    { label: 'Gemini', value: 'GEMINI' },
    { label: 'Ollama / 本地模型', value: 'OLLAMA' },
    { label: 'Custom', value: 'CUSTOM' },
  ];
  const authOptions = [
    { label: '无鉴权', value: 'NONE' },
    { label: 'Bearer Token', value: 'BEARER' },
    { label: 'API Key', value: 'API_KEY' },
  ];
  const contractOptions = [
    { label: 'LEGACY', value: 'LEGACY' },
    { label: 'Result 1.1', value: '1.1' },
  ];
  const modelResponseOptions = [
    { label: '纯文本', value: 'TEXT' },
    { label: 'Result 1.1', value: 'RESULT_1_1' },
  ];
  const isModel = computed(() => isModelProvider(model.providerType));
  const title = computed(() => (isUpdate.value ? '编辑模型 Connector' : '新增模型 Connector'));
  const rules = computed(() => ({
    connectorCode: [{ required: true, message: '请输入 Connector 代码' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '代码必须以字母开头' }],
    name: [{ required: true, message: '请输入名称' }],
    providerType: [{ required: true }],
    modelName: [{ validator: () => !isModel.value || model.modelName?.trim() ? Promise.resolve() : Promise.reject('请输入模型名称') }],
    baseUrl: [{ required: true, message: '请输入 Base URL' }],
    path: [{ required: true, message: '请输入 Path' }],
    authType: [{ required: true }],
    connectTimeout: [{ required: true, type: 'number', min: 1, max: 300 }],
    readTimeout: [{ required: true, type: 'number', min: 1, max: 300 }],
    resultContractVersion: [{ required: true }],
    modelResponseMode: [{ required: true }],
    successPointer: [{ required: true }], outputPointer: [{ required: true }], summaryPointer: [{ required: true }],
    temperature: [{ type: 'number', min: 0, max: 2 }], topP: [{ type: 'number', min: 0, max: 1 }], maxTokens: [{ type: 'number', min: 1, max: 65536 }],
  }));

  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    formRef.value?.resetFields();
    Object.keys(model).forEach((key) => delete model[key]);
    Object.assign(model, {
      providerType: 'CUSTOM', authType: 'NONE', authHeader: 'X-API-Key', connectTimeout: 10, readTimeout: 300,
      resultContractVersion: 'LEGACY', modelResponseMode: 'TEXT', requestHeadersText: '{}',
      successPointer: '/success', outputPointer: '/output', summaryPointer: '/summary', clearSecret: false,
    });
    advancedKeys.value = [];
    preservedFields.value = new Set();
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
        temperature: detail.modelOptions?.temperature,
        topP: detail.modelOptions?.topP,
        maxTokens: detail.modelOptions?.maxTokens,
      });
      model.providerType = model.providerType || 'CUSTOM';
      model.modelResponseMode = model.modelResponseMode || 'TEXT';
    }
    setDrawerProps({ confirmLoading: false });
  });

  function onProviderChange(nextProvider: string) {
    const result = switchConnectorProvider(model, nextProvider);
    Object.assign(model, result.updates);
    preservedFields.value = result.preserved;
    if (isModelProvider(nextProvider)) model.resultContractVersion = '1.1';
  }

  function fieldExtra(field: string) {
    return preservedFields.value.has(field) ? '已保留自定义值' : undefined;
  }

  async function submit() {
    try {
      await formRef.value.validate();
      const requestHeaders = JSON.parse(model.requestHeadersText || '{}');
      if (Array.isArray(requestHeaders) || typeof requestHeaders !== 'object') throw new Error('headers');
      setDrawerProps({ confirmLoading: true });
      const payload = {
        connectorCode: model.connectorCode, name: model.name, providerType: model.providerType, modelName: isModel.value ? model.modelName : null,
        baseUrl: model.baseUrl, path: model.path, authType: model.authType, authHeader: model.authHeader,
        secret: model.secret || null, clearSecret: !!model.clearSecret, connectTimeout: model.connectTimeout, readTimeout: model.readTimeout,
        requestHeaders, modelOptions: connectorModelOptions(model), modelResponseMode: isModel.value ? model.modelResponseMode : 'TEXT',
        resultContractVersion: isModel.value ? '1.1' : model.resultContractVersion,
        responseMapping: connectorResponseMapping(model),
      };
      if (isUpdate.value) await updateConnector(currentId.value, payload);
      else await createConnector(payload);
      closeDrawer();
      emit('success');
    } catch (error: any) {
      if (error?.errorFields?.some((field: any) => ['temperature', 'topP', 'maxTokens', 'requestHeadersText', 'connectTimeout', 'readTimeout'].includes(field.name?.[0]))) {
        advancedKeys.value = ['advanced'];
      }
      if (error instanceof SyntaxError || error?.message === 'headers') createMessage.error('请求头必须是合法 JSON 对象');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
