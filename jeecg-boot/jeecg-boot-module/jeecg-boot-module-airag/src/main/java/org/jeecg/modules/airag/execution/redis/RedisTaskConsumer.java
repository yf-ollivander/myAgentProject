package org.jeecg.modules.airag.execution.redis;

import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.entity.AiNodeRun;
import org.jeecg.modules.airag.execution.entity.AiRun;
import org.jeecg.modules.airag.execution.mapper.AiNodeRunMapper;
import org.jeecg.modules.airag.execution.mapper.AiRunMapper;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;

@Component
public class RedisTaskConsumer {
    private final StringRedisTemplate redis;private final AiExecutorProperties properties;private final AiNodeRunMapper nodeMapper;private final AiRunMapper runMapper;private final PipelineDefinitionCodec codec;
    private final NodeClaimService claims;private final NodeExecutionService executions;private final ThreadPoolTaskExecutor executor;private final Semaphore permits;private final RedisTaskGroupManager groups;private final ExecutionTenantScope tenantScope;
    private final String consumerName;
    public RedisTaskConsumer(StringRedisTemplate redis,AiExecutorProperties properties,AiNodeRunMapper nodeMapper,AiRunMapper runMapper,
            PipelineDefinitionCodec codec,NodeClaimService claims,NodeExecutionService executions,
            @Qualifier("aiExecutorTaskExecutor")ThreadPoolTaskExecutor executor,RedisTaskGroupManager groups,ExecutionTenantScope tenantScope){this.redis=redis;this.properties=properties;this.nodeMapper=nodeMapper;this.runMapper=runMapper;this.codec=codec;this.claims=claims;this.executions=executions;this.executor=executor;this.groups=groups;this.tenantScope=tenantScope;this.permits=new Semaphore(properties.getMaxPoolSize());this.consumerName=consumerName();}
    @Scheduled(fixedDelay=100)
    public void poll(){if(!properties.isEnabled()||!groups.ensureGroup()||!permits.tryAcquire())return;boolean handedOff=false;try{List<MapRecord<String,Object,Object>> records=redis.opsForStream().read(Consumer.from(properties.getTaskGroup(),consumerName),StreamReadOptions.empty().count(1),StreamOffset.create(properties.getTaskStream(),ReadOffset.from("0")));
            if(records==null||records.isEmpty())records=redis.opsForStream().read(Consumer.from(properties.getTaskGroup(),consumerName),StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),StreamOffset.create(properties.getTaskStream(),ReadOffset.lastConsumed()));
            if(records==null||records.isEmpty())return;MapRecord<String,Object,Object> record=records.get(0);Map<Object,Object> value=record.getValue();String nodeRunId=String.valueOf(value.get("nodeRunId"));AiNodeRun node=nodeMapper.selectById(nodeRunId);
            if(node==null){ack(record.getId());return;}String runId=String.valueOf(value.get("runId"));long version=Long.parseLong(String.valueOf(value.get("dispatchVersion")));String trace=String.valueOf(value.get("traceId"));
            NodeClaimService.ClaimedNode claim=tenantScope.call(node.getTenantId(),()->{
                Date lease=leaseUntil(runId,node);
                return claims.claim(node.getTenantId(),runId,nodeRunId,version,consumerName,trace,lease);
            });
            // ACK follows the committed DB claim; lease recovery owns crashes after this point.
            ack(record.getId());if(claim!=null){executor.execute(()->{try{tenantScope.run(claim.tenantId(),()->executions.execute(claim));}finally{permits.release();}});handedOff=true;}
        }catch(Exception failure){groups.onOperationFailure(failure);}finally{if(!handedOff)permits.release();}}
    private void ack(RecordId id){redis.opsForStream().acknowledge(properties.getTaskStream(),properties.getTaskGroup(),id);}
    private String consumerName(){try{return InetAddress.getLocalHost().getHostName()+"-"+ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+"-"+UUID.randomUUID();}catch(Exception e){return"unknown-"+UUID.randomUUID();}}
    public String recoveryConsumerName(){return consumerName;}
    private Date leaseUntil(String runId,AiNodeRun node){long seconds=60;if("AGENT".equals(node.getNodeType())){AiRun run=runMapper.selectById(runId);if(run!=null){PipelineDefinition definition=codec.readDefinition(run.getDefinitionJson());PipelineNode pipelineNode=definition.getNodes().stream().filter(n->n.getId().equals(node.getNodeId())).findFirst().orElse(null);if(pipelineNode!=null){AgentNodeConfig config=codec.parseNodeConfig(pipelineNode,AgentNodeConfig.class);if(config.getAgentSnapshot()!=null&&config.getAgentSnapshot().getTimeoutSeconds()!=null)seconds=Math.max(60,config.getAgentSnapshot().getTimeoutSeconds()+30L);}}}return new Date(System.currentTimeMillis()+seconds*1000);}
}
