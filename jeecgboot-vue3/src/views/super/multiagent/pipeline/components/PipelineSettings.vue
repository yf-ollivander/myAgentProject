<template>
  <div class="settings">
    <a-form layout="vertical" size="small">
      <a-form-item label="流程代码"><a-input :value="modelValue.code" disabled /></a-form-item>
      <a-form-item label="流程名称"><a-input :value="modelValue.name" :disabled="readonly" @change="set('name', $event.target.value)" /></a-form-item>
      <a-form-item label="触发别名">
        <a-select mode="tags" :value="modelValue.triggerAliases" :disabled="readonly" :max-tag-count="4" @change="set('triggerAliases', $event)" />
      </a-form-item>
      <a-form-item label="ORCHESTRATOR">
        <a-select :value="modelValue.notificationBotId" :disabled="readonly" show-search option-filter-prop="label" @change="set('notificationBotId', $event)">
          <a-select-option v-for="bot in botOptions" :key="bot.id" :value="bot.id" :label="`${bot.name} (${bot.botKey})`">{{ bot.name }} ({{ bot.botKey }})</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="JEECG 默认飞书 Chat"><a-input :value="modelValue.defaultFeishuChatId" :disabled="readonly" @change="set('defaultFeishuChatId', $event.target.value)" /></a-form-item>
      <a-form-item label="技术重试耗尽">
        <a-segmented :value="modelValue.interventionPolicy.onRetriesExhausted" :disabled="readonly" :options="['FAIL', 'WAIT']" @change="setPolicy('onRetriesExhausted', $event)" />
      </a-form-item>
      <a-form-item label="允许介入动作">
        <a-checkbox-group :value="modelValue.interventionPolicy.allowedActions" :disabled="readonly" :options="actionOptions" @change="setPolicy('allowedActions', $event)" />
      </a-form-item>
      <a-form-item label="最终摘要模板"><a-textarea :value="modelValue.finalSummaryTemplate" :disabled="readonly" :rows="4" @change="set('finalSummaryTemplate', $event.target.value)" /></a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
  import type { PipelineMetadata } from '../pipeline.types';
  const props = defineProps<{ modelValue: PipelineMetadata; botOptions: any[]; readonly?: boolean }>();
  const emit = defineEmits(['update:modelValue']);
  const actionOptions = [{ label: '补充输入', value: 'SUPPLY_INPUT' }, { label: '重试', value: 'RETRY' }, { label: '取消', value: 'CANCEL' }];
  const set = (field: string, value: any) => emit('update:modelValue', { ...props.modelValue, [field]: value });
  const setPolicy = (field: string, value: any) => emit('update:modelValue', { ...props.modelValue, interventionPolicy: { ...props.modelValue.interventionPolicy, [field]: value } });
</script>

<style scoped>
  .settings { padding: 14px 16px 24px; }
</style>
