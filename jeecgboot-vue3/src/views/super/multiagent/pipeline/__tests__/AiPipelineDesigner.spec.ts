import { PipelineDesignerState } from '../pipeline.designer-state';
import { connectionError, convertPipelineNode, defaultNodeConfig, shouldHandleDesignerDelete } from '../pipeline.node-actions';

describe('AiPipelineDesigner state', () => {
  it('marks changes dirty and clears them only after save', () => {
    const state = new PipelineDesignerState();
    state.changed();
    expect(state.dirty).toBe(true);
    state.saved(3);
    expect(state.dirty).toBe(false);
    expect(state.draftRevision).toBe(3);
  });

  it('locates validation issues by node or edge', () => {
    const state = new PipelineDesignerState();
    expect(state.locate({ scope: 'NODE', nodeId: 'agent', field: 'agentId', code: 'REQUIRED', message: 'required' })).toEqual({ kind: 'node', id: 'agent' });
    expect(state.locate({ scope: 'EDGE', edgeId: 'e1', field: 'branch', code: 'REQUIRED', message: 'required' })).toEqual({ kind: 'edge', id: 'e1' });
  });

  it('enforces published-version read only behavior in code', () => {
    const state = new PipelineDesignerState(true);
    state.changed();
    expect(state.dirty).toBe(false);
    expect(() => state.assertEditable()).toThrow('read-only');
  });

  it('resets type-specific configuration when a node is converted', () => {
    const converted = convertPipelineNode({ id: 'agent_1', type: 'AGENT', name: '撰稿', config: { agentId: 'agent-id', input: { prompt: 'old' } } }, 'CONDITION');

    expect(converted).toEqual({ id: 'agent_1', type: 'CONDITION', name: '条件节点', config: defaultNodeConfig('CONDITION') });
    expect(converted.config).not.toHaveProperty('agentId');
  });

  it('handles Delete and Backspace only outside editable controls', () => {
    expect(shouldHandleDesignerDelete({ key: 'Delete', target: null }, false, true)).toBe(true);
    expect(shouldHandleDesignerDelete({ key: 'Backspace', target: { tagName: 'INPUT' } as any }, false, true)).toBe(false);
    expect(shouldHandleDesignerDelete({ key: 'Delete', target: { tagName: 'DIV', closest: () => ({}) } as any }, false, true)).toBe(false);
    expect(shouldHandleDesignerDelete({ key: 'Delete', target: null }, true, true)).toBe(false);
    expect(shouldHandleDesignerDelete({ key: 'Delete', target: null }, false, false)).toBe(false);
  });

  it('rejects self loops and exact duplicate connections', () => {
    const edges = [{ id: 'e1', source: 'start', target: 'agent', branch: 'DEFAULT' as const }];

    expect(connectionError(edges, 'agent', 'agent')).toBe('连线的起点和终点不能相同');
    expect(connectionError(edges, 'start', 'agent')).toBe('相同起点、终点和分支的连线已存在');
    expect(connectionError(edges, 'start', 'agent', 'TRUE')).toBeUndefined();
    expect(connectionError(edges, 'start', 'agent', 'DEFAULT', 'e1')).toBeUndefined();
  });
});
