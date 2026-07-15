<template>
  <aside class="palette">
    <div class="palette__title">节点</div>
    <button v-for="item in items" :key="item.type" class="palette__item" type="button" @mousedown="emit('drag', item)">
      <Icon :icon="item.icon" :size="18" />
      <span>{{ item.label }}</span>
    </button>
  </aside>
</template>

<script lang="ts" setup>
  import Icon from '/@/components/Icon';

  const emit = defineEmits(['drag']);
  const items = [
    { type: 'pipeline-start', nodeType: 'START', label: '开始', icon: 'ant-design:play-circle-outlined' },
    { type: 'pipeline-agent', nodeType: 'AGENT', label: 'Agent', icon: 'ant-design:robot-outlined' },
    { type: 'pipeline-condition', nodeType: 'CONDITION', label: '条件', icon: 'ant-design:branches-outlined' },
    { type: 'pipeline-notify', nodeType: 'NOTIFY', label: '通知', icon: 'ant-design:notification-outlined' },
    { type: 'pipeline-end', nodeType: 'END', label: '结束', icon: 'ant-design:stop-outlined' },
  ];
</script>

<style scoped>
  .palette { width: 116px; flex: 0 0 116px; border-right: 1px solid #e5e7eb; background: #f8fafc; padding: 12px 10px; }
  .palette__title { margin: 2px 6px 10px; color: #64748b; font-size: 12px; }
  .palette__item { width: 100%; height: 42px; margin-bottom: 8px; display: flex; align-items: center; gap: 8px; border: 1px solid #d8dee8; border-radius: 6px; background: #fff; color: #263244; cursor: grab; }
  .palette__item:hover { border-color: #4f7fd8; color: #1859b5; }
</style>
