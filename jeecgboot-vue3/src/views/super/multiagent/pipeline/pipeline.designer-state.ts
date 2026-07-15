import type { ValidationIssue } from './pipeline.types';

export class PipelineDesignerState {
  draftRevision = 0;
  dirty = false;
  readonly = false;
  selectedNodeId?: string;
  selectedEdgeId?: string;
  issues: ValidationIssue[] = [];

  constructor(readonlyMode = false) {
    this.readonly = readonlyMode;
  }

  changed() {
    if (!this.readonly) this.dirty = true;
  }

  saved(nextRevision: number) {
    this.draftRevision = nextRevision;
    this.dirty = false;
  }

  setIssues(issues: ValidationIssue[]) {
    this.issues = [...issues];
  }

  locate(issue: ValidationIssue) {
    this.selectedNodeId = issue.nodeId;
    this.selectedEdgeId = issue.edgeId;
    return issue.nodeId ? { kind: 'node' as const, id: issue.nodeId } : issue.edgeId ? { kind: 'edge' as const, id: issue.edgeId } : undefined;
  }

  assertEditable() {
    if (this.readonly) throw new Error('Published pipeline versions are read-only');
  }
}
