<template>
  <div v-if="draft" class="node-panel">
    <a-form layout="vertical" size="small">
      <a-form-item label="节点名称"><a-input v-model:value="draft.name" :disabled="readonly" @change="commit" /></a-form-item>
      <!-- update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】输入映射按节点 ID 引用，直接提供可复制值避免操作者猜测内部标识----------- -->
      <a-form-item label="节点 ID"><a-typography-paragraph copyable><code>{{ draft.id }}</code></a-typography-paragraph></a-form-item>
      <!-- update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】输入映射按节点 ID 引用，直接提供可复制值避免操作者猜测内部标识----------- -->

      <template v-if="draft.type === 'START'">
        <div class="section-title">输入字段</div>
        <div v-for="(field, index) in draft.config.inputSchema" :key="index" class="row row--schema">
          <a-input v-model:value="field.name" placeholder="字段名" :disabled="readonly" @change="commit" />
          <a-select v-model:value="field.type" :options="valueTypeOptions" :disabled="readonly" @change="commit" />
          <a-checkbox v-model:checked="field.required" :disabled="readonly" @change="commit">必填</a-checkbox>
          <a-button danger type="text" :disabled="readonly" @click="remove(draft.config.inputSchema, index)"><Icon icon="ant-design:delete-outlined" /></a-button>
        </div>
        <a-button block :disabled="readonly" @click="addSchema(draft.config.inputSchema, 'name')"><Icon icon="ant-design:plus-outlined" /> 添加字段</a-button>
      </template>

      <template v-else-if="draft.type === 'AGENT'">
        <a-form-item label="stageCode"><a-input v-model:value="draft.config.stageCode" :disabled="readonly" @change="commit" /></a-form-item>
        <a-form-item label="Agent">
          <a-select v-model:value="draft.config.agentId" show-search option-filter-prop="label" :disabled="readonly" @change="commit">
            <a-select-option v-for="agent in agentOptions" :key="agent.id" :value="agent.id" :label="`${agent.name} (${agent.agentCode})`">{{ agent.name }} ({{ agent.agentCode }})</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="错误策略"><a-segmented v-model:value="draft.config.onError" :options="['INHERIT', 'WAIT', 'FAIL']" :disabled="readonly" @change="commit" /></a-form-item>
        <div class="section-title">输入映射</div>
        <div v-for="row in inputRows" :key="row.key" class="row row--mapping">
          <a-input :value="row.key" placeholder="参数名" :disabled="readonly" @change="renameInput(row.key, $event.target.value)" />
          <a-input :value="row.value" placeholder="{{run.input.task}}" :disabled="readonly" @change="setInput(row.key, $event.target.value)" />
          <a-button danger type="text" :disabled="readonly" @click="deleteInput(row.key)"><Icon icon="ant-design:delete-outlined" /></a-button>
        </div>
        <a-button block :disabled="readonly" @click="addInput"><Icon icon="ant-design:plus-outlined" /> 添加映射</a-button>
        <div class="section-title">输出字段</div>
        <div v-for="(field, index) in draft.config.outputSchema" :key="index" class="row row--schema">
          <a-input v-model:value="field.field" placeholder="字段路径" :disabled="readonly" @change="commit" />
          <a-select v-model:value="field.type" :options="valueTypeOptions" :disabled="readonly" @change="commit" />
          <a-checkbox v-model:checked="field.required" :disabled="readonly" @change="commit">必填</a-checkbox>
          <a-button danger type="text" :disabled="readonly" @click="remove(draft.config.outputSchema, index)"><Icon icon="ant-design:delete-outlined" /></a-button>
        </div>
        <a-button block :disabled="readonly" @click="addSchema(draft.config.outputSchema, 'field')"><Icon icon="ant-design:plus-outlined" /> 添加输出</a-button>
        <a-form-item label="可产出类型"><a-select v-model:value="draft.config.artifactOutputs" mode="multiple" :options="artifactTypeOptions" :disabled="readonly" @change="commit" /></a-form-item>
        <ArtifactRows v-model="draft.config.artifactInputs" title="产出物输入" :readonly="readonly" :node-options="ancestorOptions" @change="commit" />
      </template>

      <template v-else-if="draft.type === 'CONDITION'">
        <a-form-item label="来源 Agent"><a-select v-model:value="draft.config.left.nodeId" :options="agentNodeOptions" :disabled="readonly" @change="commit" /></a-form-item>
        <a-form-item label="输出字段"><a-input v-model:value="draft.config.left.field" :disabled="readonly" @change="commit" /></a-form-item>
        <a-form-item label="值类型"><a-select v-model:value="draft.config.left.valueType" :options="valueTypeOptions" :disabled="readonly" @change="syncRightType" /></a-form-item>
        <a-form-item label="操作符"><a-select v-model:value="draft.config.operator" :options="operatorOptions" :disabled="readonly" @change="commit" /></a-form-item>
        <a-form-item v-if="!['EMPTY', 'NOT_EMPTY'].includes(draft.config.operator)" label="比较值"><a-input v-model:value="draft.config.right.value" :disabled="readonly" @change="commit" /></a-form-item>
      </template>

      <template v-else-if="draft.type === 'NOTIFY'">
        <a-form-item label="消息模板"><a-textarea v-model:value="draft.config.messageTemplate" :rows="8" :maxlength="2000" show-count :disabled="readonly" @change="commit" /></a-form-item>
      </template>

      <template v-else-if="draft.type === 'END'">
        <div class="section-title">最终输出</div>
        <div v-for="row in outputRows" :key="row.key" class="row row--mapping">
          <a-input :value="row.key" placeholder="输出名" :disabled="readonly" @change="renameOutput(row.key, $event.target.value)" />
          <a-input :value="row.value" placeholder="{{nodes.agent.output.field}}" :disabled="readonly" @change="setOutput(row.key, $event.target.value)" />
          <a-button danger type="text" :disabled="readonly" @click="deleteOutput(row.key)"><Icon icon="ant-design:delete-outlined" /></a-button>
        </div>
        <a-button block :disabled="readonly" @click="addOutput"><Icon icon="ant-design:plus-outlined" /> 添加输出</a-button>
        <ArtifactRows v-model="draft.config.artifactSelection" title="最终产出物" :readonly="readonly" :node-options="ancestorOptions" @change="commit" />
        <a-form-item label="完成摘要"><a-textarea v-model:value="draft.config.completionSummary" :rows="5" :disabled="readonly" @change="commit" /></a-form-item>
      </template>
    </a-form>
  </div>
  <div v-else class="node-panel__empty">选择节点后编辑属性</div>
</template>

<script lang="ts" setup>
  import { computed, defineComponent, h, ref, watch } from 'vue';
  import Icon from '/@/components/Icon';
  import { clonePipelineJson } from '../pipeline.adapter';
  import { ARTIFACT_TYPES, VALUE_TYPES, type PipelineNode } from '../pipeline.types';

  const props = defineProps<{ node?: PipelineNode; allNodes: PipelineNode[]; agentOptions: any[]; readonly?: boolean }>();
  const emit = defineEmits(['update:node']);
  const draft = ref<any>();
  // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】props 与 ref 中的节点为 Vue Proxy，按 Pipeline JSON 契约复制后再编辑-----------
  watch(() => props.node, (node) => { draft.value = node ? clonePipelineJson(node) : undefined; ensureShape(); }, { immediate: true, deep: true });
  // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】props 与 ref 中的节点为 Vue Proxy，按 Pipeline JSON 契约复制后再编辑-----------

  const valueTypeOptions = VALUE_TYPES.map((value) => ({ label: value, value }));
  const artifactTypeOptions = ARTIFACT_TYPES.map((value) => ({ label: value, value }));
  const operatorOptions = ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'EMPTY', 'NOT_EMPTY'].map((value) => ({ label: value, value }));
  const agentNodeOptions = computed(() => props.allNodes.filter((node) => node.type === 'AGENT').map((node) => ({ label: `${node.name} (${node.id})`, value: node.id })));
  const ancestorOptions = computed(() => agentNodeOptions.value);
  const inputRows = computed(() => Object.entries(draft.value?.config?.input || {}).map(([key, value]) => ({ key, value: String(value) })));
  const outputRows = computed(() => Object.entries(draft.value?.config?.output || {}).map(([key, value]) => ({ key, value: String(value) })));

  function ensureShape() {
    if (!draft.value) return;
    const config = draft.value.config ||= {};
    if (draft.value.type === 'START') config.inputSchema ||= [];
    if (draft.value.type === 'AGENT') {
      config.resultContractVersion = '1.1'; config.input ||= {}; config.outputSchema ||= [];
      config.artifactOutputs ||= []; config.artifactInputs ||= []; config.onError ||= 'INHERIT';
    }
    if (draft.value.type === 'CONDITION') {
      config.left ||= { source: 'NODE_OUTPUT', nodeId: '', field: '', valueType: 'string' };
      config.operator ||= 'EQ'; config.right ||= { valueType: config.left.valueType, value: '' };
    }
    if (draft.value.type === 'NOTIFY') config.messageTemplate ||= '';
    if (draft.value.type === 'END') { config.output ||= {}; config.artifactSelection ||= []; config.completionSummary ||= ''; }
  }
  function commit() { if (!props.readonly) emit('update:node', clonePipelineJson(draft.value)); }
  function addSchema(target, key) { target.push({ [key]: '', type: 'string', required: true }); commit(); }
  function remove(target, index) { target.splice(index, 1); commit(); }
  function addInput() { let key = 'input'; let i = 1; while (key in draft.value.config.input) key = `input${++i}`; draft.value.config.input[key] = ''; commit(); }
  function setInput(key, value) { draft.value.config.input[key] = value; commit(); }
  function renameInput(key, next) { if (!next || next === key) return; draft.value.config.input[next] = draft.value.config.input[key]; delete draft.value.config.input[key]; commit(); }
  function deleteInput(key) { delete draft.value.config.input[key]; commit(); }
  function addOutput() { let key = 'result'; let i = 1; while (key in draft.value.config.output) key = `result${++i}`; draft.value.config.output[key] = ''; commit(); }
  function setOutput(key, value) { draft.value.config.output[key] = value; commit(); }
  function renameOutput(key, next) { if (!next || next === key) return; draft.value.config.output[next] = draft.value.config.output[key]; delete draft.value.config.output[key]; commit(); }
  function deleteOutput(key) { delete draft.value.config.output[key]; commit(); }
  function syncRightType(value) { draft.value.config.right.valueType = value; commit(); }

  const ArtifactRows = defineComponent({
    name: 'ArtifactRows',
    props: { modelValue: { type: Array, default: () => [] }, title: String, readonly: Boolean, nodeOptions: { type: Array, default: () => [] } },
    emits: ['update:modelValue', 'change'],
    setup(rowProps, { emit: rowEmit }) {
      const update = (index, field, value) => { const next: any[] = clonePipelineJson(rowProps.modelValue); next[index][field] = value; rowEmit('update:modelValue', next); rowEmit('change'); };
      const add = () => { rowEmit('update:modelValue', [...rowProps.modelValue, { name: '', sourceNodeId: '', types: [], required: true, selectionMode: 'LATEST' }]); rowEmit('change'); };
      const removeRow = (index) => { const next = [...rowProps.modelValue]; next.splice(index, 1); rowEmit('update:modelValue', next); rowEmit('change'); };
      return () => h('div', { class: 'artifact-block' }, [
        h('div', { class: 'section-title' }, rowProps.title),
        ...rowProps.modelValue.map((row: any, index: number) => h('div', { class: 'artifact-row' }, [
          h('input', { value: row.name, disabled: rowProps.readonly, placeholder: '分组名', onInput: (e: any) => update(index, 'name', e.target.value) }),
          h('select', { value: row.sourceNodeId, disabled: rowProps.readonly, onChange: (e: any) => update(index, 'sourceNodeId', e.target.value) }, [h('option', { value: '' }, '来源节点'), ...(rowProps.nodeOptions as any[]).map((item) => h('option', { value: item.value }, item.label))]),
          h('select', { value: row.types || [], multiple: true, size: 3, disabled: rowProps.readonly, onChange: (e: any) => update(index, 'types', Array.from(e.target.selectedOptions).map((option: any) => option.value)) }, ARTIFACT_TYPES.map((type) => h('option', { value: type }, type))),
          h('select', { value: row.selectionMode, disabled: rowProps.readonly, onChange: (e: any) => update(index, 'selectionMode', e.target.value) }, [h('option', { value: 'LATEST' }, 'LATEST'), h('option', { value: 'ALL' }, 'ALL')]),
          h('select', { value: String(row.required !== false), disabled: rowProps.readonly, onChange: (e: any) => update(index, 'required', e.target.value === 'true') }, [h('option', { value: 'true' }, '必需'), h('option', { value: 'false' }, '可选')]),
          h('button', { type: 'button', disabled: rowProps.readonly, onClick: () => removeRow(index) }, '删除'),
        ])),
        h('button', { class: 'artifact-add', type: 'button', disabled: rowProps.readonly, onClick: add }, '添加产出物引用'),
      ]);
    },
  });
</script>

<style scoped>
  .node-panel { padding: 14px 16px 30px; }
  .node-panel__empty { padding: 36px 18px; color: #8b95a5; text-align: center; }
  .section-title { margin: 14px 0 8px; color: #4b5563; font-weight: 600; }
  .row { display: grid; align-items: center; gap: 6px; margin-bottom: 7px; }
  .row--schema { grid-template-columns: minmax(80px, 1fr) 105px 52px 32px; }
  .row--mapping { grid-template-columns: minmax(70px, .7fr) minmax(120px, 1.3fr) 32px; }
  :deep(.artifact-row) { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 6px; margin-bottom: 9px; padding: 8px; border: 1px solid #e6e9ee; border-radius: 6px; }
  :deep(.artifact-row input), :deep(.artifact-row select) { min-width: 0; height: 30px; border: 1px solid #d9d9d9; border-radius: 4px; padding: 0 6px; background: #fff; }
  :deep(.artifact-row button), :deep(.artifact-add) { height: 30px; border: 1px solid #d9d9d9; border-radius: 4px; background: #fff; cursor: pointer; }
  :deep(.artifact-add) { width: 100%; }
</style>
