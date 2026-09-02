import type { EdgeBranch, NodeType, PipelineEdge, PipelineNode } from './pipeline.types';

export const NODE_TYPE_OPTIONS: Array<{ label: string; value: NodeType }> = [
  { label: '开始节点', value: 'START' },
  { label: 'Agent 节点', value: 'AGENT' },
  { label: '条件节点', value: 'CONDITION' },
  { label: '通知节点', value: 'NOTIFY' },
  { label: '结束节点', value: 'END' },
];

export function defaultNodeConfig(type: NodeType): Record<string, any> {
  if (type === 'START') return { inputSchema: [] };
  if (type === 'AGENT') return { stageCode: '', agentId: '', resultContractVersion: '1.1', input: {}, outputSchema: [], artifactOutputs: [], artifactInputs: [], onError: 'INHERIT' };
  if (type === 'CONDITION') return { left: { source: 'NODE_OUTPUT', nodeId: '', field: '', valueType: 'string' }, operator: 'EQ', right: { valueType: 'string', value: '' } };
  if (type === 'NOTIFY') return { messageTemplate: '' };
  return { output: {}, artifactSelection: [], completionSummary: '' };
}

export function convertPipelineNode(node: PipelineNode, nextType: NodeType): PipelineNode {
  const nextName = NODE_TYPE_OPTIONS.find((option) => option.value === nextType)?.label || nextType;
  return { ...node, type: nextType, name: nextName, config: defaultNodeConfig(nextType) };
}

export function shouldHandleDesignerDelete(event: Pick<KeyboardEvent, 'key' | 'target'>, readonly: boolean, hasSelection: boolean): boolean {
  if (readonly || !hasSelection || !['Delete', 'Backspace'].includes(event.key)) return false;
  const target = event.target as HTMLElement | null;
  const tagName = target?.tagName?.toLowerCase();
  if (['input', 'textarea', 'select'].includes(tagName || '') || target?.isContentEditable) return false;
  return !target?.closest?.('[contenteditable="true"]');
}

export function connectionError(edges: PipelineEdge[], source: string, target: string, branch: EdgeBranch = 'DEFAULT', ignoredEdgeId?: string): string | undefined {
  if (!source || !target) return '请选择起点和终点';
  if (source === target) return '连线的起点和终点不能相同';
  const duplicated = edges.some((edge) => edge.id !== ignoredEdgeId && edge.source === source && edge.target === target && edge.branch === branch);
  return duplicated ? '相同起点、终点和分支的连线已存在' : undefined;
}
