import { PipelineDesignerState } from '../pipeline.designer-state';

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
});
