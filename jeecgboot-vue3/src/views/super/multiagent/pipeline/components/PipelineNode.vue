<template>
  <div class="pipeline-node" :class="`pipeline-node--${nodeType.toLowerCase()}`">
    <Icon :icon="icon" :size="18" />
    <span>{{ label }}</span>
  </div>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';
  import Icon from '/@/components/Icon';

  const props = defineProps<{ model?: any; properties?: Record<string, any> }>();
  const nodeType = computed(() => props.model?.properties?.nodeType || props.properties?.nodeType || 'AGENT');
  const label = computed(() => props.model?.properties?.name || props.model?.text?.value || props.properties?.name || nodeType.value);
  const icons = { START: 'ant-design:play-circle-outlined', AGENT: 'ant-design:robot-outlined', CONDITION: 'ant-design:branches-outlined', NOTIFY: 'ant-design:notification-outlined', END: 'ant-design:stop-outlined' };
  const icon = computed(() => icons[nodeType.value] || icons.AGENT);
</script>

<style scoped>
  .pipeline-node { width: 176px; min-height: 54px; display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid #c8d0dc; border-left: 4px solid #64748b; border-radius: 6px; background: #fff; color: #1f2937; box-shadow: 0 2px 7px rgb(15 23 42 / 10%); }
  .pipeline-node--start { border-left-color: #16835b; }
  .pipeline-node--agent { border-left-color: #2563eb; }
  .pipeline-node--condition { border-left-color: #c47700; }
  .pipeline-node--notify { border-left-color: #7c3aed; }
  .pipeline-node--end { border-left-color: #c2413b; }
</style>
