export function connectorResponseMapping(model: Record<string, any>) {
  if (model.resultContractVersion !== 'LEGACY') return null;
  return {
    successPointer: model.successPointer,
    outputPointer: model.outputPointer,
    summaryPointer: model.summaryPointer,
  };
}
