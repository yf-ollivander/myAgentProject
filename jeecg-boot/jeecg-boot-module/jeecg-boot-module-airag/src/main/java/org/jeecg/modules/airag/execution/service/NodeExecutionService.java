package org.jeecg.modules.airag.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.gateway.AgentExecutionGateway;
import org.jeecg.modules.airag.execution.gateway.AgentExecutionException;
import org.jeecg.modules.airag.execution.handler.NodeOutcome;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.validation.AgentResultValidator;
import org.springframework.stereotype.Service;
import java.util.*;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

@Service
public class NodeExecutionService {
    private final AiRunMapper runMapper; private final AiNodeRunMapper nodeMapper; private final AiArtifactMapper artifactMapper;
    private final PipelineDefinitionCodec codec; private final PipelineTemplateResolver templates; private final AgentExecutionGateway gateway;
    private final AgentResultValidator resultValidator; private final NodeCompletionService completion; private final ObjectMapper mapper;
    public NodeExecutionService(AiRunMapper runMapper,AiNodeRunMapper nodeMapper,AiArtifactMapper artifactMapper,
            PipelineDefinitionCodec codec,PipelineTemplateResolver templates,AgentExecutionGateway gateway,
            AgentResultValidator resultValidator,NodeCompletionService completion,ObjectMapper mapper){this.runMapper=runMapper;this.nodeMapper=nodeMapper;
        this.artifactMapper=artifactMapper;this.codec=codec;this.templates=templates;this.gateway=gateway;this.resultValidator=resultValidator;this.completion=completion;this.mapper=mapper;}

    public void execute(NodeClaimService.ClaimedNode claim){
        try{
            AiRun run=runMapper.selectById(claim.runId()); AiNodeRun node=nodeMapper.selectById(claim.nodeRunId());
            if(run==null||node==null)return; PipelineDefinition definition=codec.readDefinition(run.getDefinitionJson());
            PipelineNode definitionNode=definition.getNodes().stream().filter(n->n.getId().equals(node.getNodeId())).findFirst().orElseThrow();
            Map<String,JsonNode> outputs=new HashMap<>();Map<String,String> summaries=new HashMap<>();
            nodeMapper.selectList(new QueryWrapper<AiNodeRun>().lambda().eq(AiNodeRun::getRunId,run.getId()).eq(AiNodeRun::getTenantId,run.getTenantId())).forEach(n->{
                if(n.getOutputJson()!=null)outputs.put(n.getNodeId(),read(n.getOutputJson()));if(n.getResultSummary()!=null)summaries.put(n.getNodeId(),n.getResultSummary());});
            JsonNode runInput=read(run.getInputJson()); NodeOutcome outcome=switch(definitionNode.getType()){
                case START -> NodeOutcome.success(runInput,"Input accepted",PipelineEnums.EdgeBranch.DEFAULT.name());
                case AGENT -> executeAgent(run,node,definitionNode,runInput,outputs,summaries,claim.traceId());
                case CONDITION -> executeCondition(definitionNode,runInput,outputs,summaries);
                case NOTIFY -> executeNotify(definitionNode,runInput,outputs,summaries);
                case END -> executeEnd(definitionNode,runInput,outputs,summaries);
            }; completion.complete(claim,outcome);
        }catch(AgentExecutionException e){completion.complete(claim,failure(e.isRetryable(),e.getErrorCode(),e.getMessage()));
        }catch(ExecutionException e){completion.complete(claim,failure(false,e.getErrorCode().name(),e.getMessage()));
        }catch(Exception e){Throwable transport=findTransportFailure(e);if(transport!=null){String code=isTimeout(transport)?"AGENT_TIMEOUT":"AGENT_NETWORK_ERROR";
                completion.complete(claim,failure(true,code,"Agent gateway transport failed"));}
            else completion.complete(claim,failure(false,"EXECUTION_ERROR","Agent execution failed"));}
    }

    private NodeOutcome executeAgent(AiRun run,AiNodeRun node,PipelineNode definitionNode,JsonNode runInput,
            Map<String,JsonNode> outputs,Map<String,String> summaries,String traceId){
        AgentNodeConfig config=codec.parseNodeConfig(definitionNode,AgentNodeConfig.class);
        JsonNode input=config.getInput()==null||config.getInput().isEmpty()?runInput:templates.resolve(codec.valueToTree(config.getInput()),runInput,outputs,summaries);
        List<ArtifactDescriptor> artifactInputs=selectArtifacts(run,config.getArtifactInputs());
        AgentExecutionGateway.AgentExecutionRequest request=new AgentExecutionGateway.AgentExecutionRequest(
                run.getId()+":"+node.getNodeId()+":"+node.getAttemptNo()+":"+node.getResumeGeneration(),run.getId(),run.getTenantId(),node.getId(),
                node.getAttemptNo(),node.getResumeGeneration(),traceId,config.getAgentSnapshot(),input,
                node.getInputJson()==null?null:read(node.getInputJson()),artifactInputs);
        AgentResultContract result=gateway.execute(request);List<String> errors=resultValidator.validate(result);
        if(!errors.isEmpty())return new NodeOutcome(PipelineEnums.AgentResultStatus.FAILED,null,null,List.of(),null,false,
                "AGENT_RESULT_INVALID",String.join("; ",errors),null,null);
        validateDeclaredResult(config,result);
        return new NodeOutcome(result.getStatus(),result.getOutput(),result.getSummary(),result.getArtifacts(),
                result.getStatus()==PipelineEnums.AgentResultStatus.SUCCESS?PipelineEnums.EdgeBranch.DEFAULT.name():null,
                Boolean.TRUE.equals(result.getRetryable()),result.getErrorCode(),result.getErrorMessage(),result.getUserPrompt(),null);
    }

    private NodeOutcome executeCondition(PipelineNode node,JsonNode input,Map<String,JsonNode> outputs,Map<String,String> summaries){
        ConditionNodeConfig config=codec.parseNodeConfig(node,ConditionNodeConfig.class);JsonNode left=value(config.getLeft(),input,outputs);
        JsonNode right=value(config.getRight(),input,outputs);boolean selected=compare(left,right,config.getOperator());
        return NodeOutcome.success(BooleanNode.valueOf(selected),selected?"Condition matched":"Condition did not match",
                selected?PipelineEnums.EdgeBranch.TRUE.name():PipelineEnums.EdgeBranch.FALSE.name());
    }
    private NodeOutcome executeNotify(PipelineNode node,JsonNode input,Map<String,JsonNode> outputs,Map<String,String> summaries){
        NotifyNodeConfig config=codec.parseNodeConfig(node,NotifyNodeConfig.class);String message=templates.resolve(TextNode.valueOf(config.getMessageTemplate()),input,outputs,summaries).asText();
        return new NodeOutcome(PipelineEnums.AgentResultStatus.SUCCESS,NullNode.instance,message,List.of(),PipelineEnums.EdgeBranch.DEFAULT.name(),false,null,null,null,message);
    }
    private NodeOutcome executeEnd(PipelineNode node,JsonNode input,Map<String,JsonNode> outputs,Map<String,String> summaries){
        EndNodeConfig config=codec.parseNodeConfig(node,EndNodeConfig.class);JsonNode output=templates.resolve(codec.valueToTree(config.getOutput()),input,outputs,summaries);
        String summary=templates.resolve(TextNode.valueOf(config.getCompletionSummary()),input,outputs,summaries).asText();
        return NodeOutcome.success(output,summary,null);
    }

    private List<ArtifactDescriptor> selectArtifacts(AiRun run,List<ArtifactInput> inputs){
        List<ArtifactDescriptor> result=new ArrayList<>(); if(inputs==null)return result;
        for(ArtifactInput input:inputs){List<AiArtifact> found=artifactMapper.selectList(new QueryWrapper<AiArtifact>().lambda()
                .eq(AiArtifact::getRunId,run.getId()).eq(AiArtifact::getTenantId,run.getTenantId())
                .in(input.getTypes()!=null&&!input.getTypes().isEmpty(),AiArtifact::getArtifactType,input.getTypes().stream().map(Enum::name).toList())
                .orderByAsc(AiArtifact::getCreateTime).orderByAsc(AiArtifact::getId));
            found=found.stream().filter(a->sourceNodeMatches(a,input.getSourceNodeId(),run.getTenantId())).toList();
            if(Boolean.TRUE.equals(input.getRequired())&&found.isEmpty())throw ExecutionException.of(ExecutionErrorCode.ARTIFACT_INVALID,"Required artifact is missing: "+input.getName());
            if(input.getSelectionMode()==PipelineEnums.SelectionMode.LATEST&&!found.isEmpty())found=List.of(found.get(found.size()-1));
            found.forEach(a->result.add(toDescriptor(a)));}return result;
    }
    private boolean sourceNodeMatches(AiArtifact artifact,String sourceNodeId,String tenantId){AiNodeRun n=nodeMapper.selectOne(new QueryWrapper<AiNodeRun>().lambda().eq(AiNodeRun::getId,artifact.getNodeRunId()).eq(AiNodeRun::getTenantId,tenantId));return n!=null&&sourceNodeId.equals(n.getNodeId());}
    private ArtifactDescriptor toDescriptor(AiArtifact a){ArtifactDescriptor d=new ArtifactDescriptor();d.setType(PipelineEnums.ArtifactType.valueOf(a.getArtifactType()));d.setName(a.getName());d.setUri(a.getUri());
        d.setContent(a.getContentJson()==null?null:read(a.getContentJson()));d.setChecksum(a.getChecksum());d.setVersion(a.getArtifactVersion());d.setMetadata(a.getMetadataJson()==null?Map.of():mapper.convertValue(read(a.getMetadataJson()),new com.fasterxml.jackson.core.type.TypeReference<>(){}));return d;}
    private void validateDeclaredResult(AgentNodeConfig config,AgentResultContract result){if(result.getStatus()!=PipelineEnums.AgentResultStatus.SUCCESS)return;
        JsonNode output=result.getOutput();for(FieldSchema field:config.getOutputSchema()){JsonNode value=output==null?null:output.get(field.effectiveName());if(Boolean.TRUE.equals(field.getRequired())&&value==null)throw ExecutionException.of(ExecutionErrorCode.AGENT_RESULT_INVALID,"Required output is missing: "+field.effectiveName());if(value!=null&&!matchesType(value,field.getType()))throw ExecutionException.of(ExecutionErrorCode.AGENT_RESULT_INVALID,"Output type is invalid: "+field.effectiveName());}
        Set<PipelineEnums.ArtifactType> actual=new HashSet<>();result.getArtifacts().forEach(a->actual.add(a.getType()));
        if(config.getArtifactOutputs()!=null&&!actual.containsAll(config.getArtifactOutputs()))throw ExecutionException.of(ExecutionErrorCode.ARTIFACT_INVALID,"Declared artifacts are missing");}
    private JsonNode value(ConditionNodeConfig.ValueReference ref,JsonNode input,Map<String,JsonNode> outputs){if(ref==null)return NullNode.instance;if(ref.getValue()!=null)return ref.getValue();
        JsonNode root="RUN_INPUT".equals(ref.getSource())?input:outputs.get(ref.getNodeId());return root==null?NullNode.instance:root.path(ref.getField());}
    private boolean compare(JsonNode l,JsonNode r,PipelineEnums.ConditionOperator op){return switch(op){case EMPTY->l==null||l.isNull()||l.asText().isEmpty();case NOT_EMPTY->l!=null&&!l.isNull()&&!l.asText().isEmpty();
        case EQ->Objects.equals(l,r);case NE->!Objects.equals(l,r);case GT->l.asDouble()>r.asDouble();case GE->l.asDouble()>=r.asDouble();case LT->l.asDouble()<r.asDouble();case LE->l.asDouble()<=r.asDouble();};}
    private JsonNode read(String json){try{return mapper.readTree(json);}catch(Exception e){throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"Stored JSON is invalid");}}
    private String abbreviate(String s){if(s==null)return"Execution failed";return s.length()>1000?s.substring(0,1000):s;}
    private NodeOutcome failure(boolean retryable,String code,String message){return new NodeOutcome(PipelineEnums.AgentResultStatus.FAILED,null,null,List.of(),null,retryable,code,abbreviate(message),null,null);}
    private Throwable findTransportFailure(Throwable error){Throwable current=error;while(current!=null){if(current instanceof TimeoutException||current instanceof SocketTimeoutException||current instanceof HttpTimeoutException||current instanceof ConnectException||current instanceof IOException)return current;current=current.getCause();}return null;}
    private boolean isTimeout(Throwable error){return error instanceof TimeoutException||error instanceof SocketTimeoutException||error instanceof HttpTimeoutException;}
    private boolean matchesType(JsonNode value,PipelineEnums.ValueType type){if(type==null)return true;return switch(type){case STRING->value.isTextual();case NUMBER->value.isNumber();case BOOLEAN->value.isBoolean();case OBJECT->value.isObject();case ARRAY->value.isArray();};}
}
