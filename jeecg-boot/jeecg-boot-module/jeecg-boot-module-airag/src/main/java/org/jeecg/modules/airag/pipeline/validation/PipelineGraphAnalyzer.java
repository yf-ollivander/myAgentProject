package org.jeecg.modules.airag.pipeline.validation;

import org.jeecg.modules.airag.pipeline.contract.PipelineDefinition;
import org.jeecg.modules.airag.pipeline.contract.PipelineEdge;
import org.jeecg.modules.airag.pipeline.contract.PipelineNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PipelineGraphAnalyzer {
    public Analysis analyze(PipelineDefinition definition) {
        Map<String, PipelineNode> nodes = definition.getNodes().stream()
                .filter(Objects::nonNull).filter(node -> node.getId() != null)
                .collect(Collectors.toMap(PipelineNode::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<PipelineEdge>> outgoing = new LinkedHashMap<>();
        Map<String, List<PipelineEdge>> incoming = new LinkedHashMap<>();
        nodes.keySet().forEach(id -> {
            outgoing.put(id, new ArrayList<>());
            incoming.put(id, new ArrayList<>());
        });
        for (PipelineEdge edge : definition.getEdges()) {
            if (edge != null && outgoing.containsKey(edge.getSource()) && incoming.containsKey(edge.getTarget())) {
                outgoing.get(edge.getSource()).add(edge);
                incoming.get(edge.getTarget()).add(edge);
            }
        }
        List<String> topological = topological(nodes.keySet(), outgoing, incoming);
        Set<String> starts = nodes.values().stream()
                .filter(node -> node.getType() == org.jeecg.modules.airag.pipeline.contract.PipelineEnums.NodeType.START)
                .map(PipelineNode::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> reachable = starts.isEmpty() ? Set.of() : walk(starts, outgoing, PipelineEdge::getTarget);
        Set<String> ends = nodes.values().stream()
                .filter(node -> node.getType() == org.jeecg.modules.airag.pipeline.contract.PipelineEnums.NodeType.END)
                .map(PipelineNode::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> canTerminate = ends.isEmpty() ? Set.of() : walk(ends, incoming, PipelineEdge::getSource);
        Map<String, Set<String>> dominators = starts.size() == 1
                ? dominators(nodes.keySet(), incoming, starts.iterator().next()) : Map.of();
        return new Analysis(nodes, outgoing, incoming, topological, reachable, canTerminate, dominators,
                topological.size() == nodes.size());
    }

    private List<String> topological(Set<String> nodeIds, Map<String, List<PipelineEdge>> outgoing,
                                     Map<String, List<PipelineEdge>> incoming) {
        Map<String, Integer> degree = new LinkedHashMap<>();
        nodeIds.forEach(id -> degree.put(id, incoming.get(id).size()));
        PriorityQueue<String> ready = new PriorityQueue<>();
        degree.forEach((id, value) -> { if (value == 0) ready.add(id); });
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.remove();
            order.add(current);
            for (PipelineEdge edge : outgoing.get(current)) {
                int next = degree.compute(edge.getTarget(), (id, value) -> value - 1);
                if (next == 0) ready.add(edge.getTarget());
            }
        }
        return order;
    }

    private Set<String> walk(Set<String> initial, Map<String, List<PipelineEdge>> adjacency,
                             Function<PipelineEdge, String> next) {
        Set<String> visited = new LinkedHashSet<>(initial);
        Deque<String> queue = new ArrayDeque<>(initial);
        while (!queue.isEmpty()) {
            for (PipelineEdge edge : adjacency.getOrDefault(queue.removeFirst(), List.of())) {
                String id = next.apply(edge);
                if (visited.add(id)) queue.addLast(id);
            }
        }
        return visited;
    }

    private Map<String, Set<String>> dominators(Set<String> nodeIds, Map<String, List<PipelineEdge>> incoming,
                                                String start) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String id : nodeIds) {
            result.put(id, id.equals(start) ? new LinkedHashSet<>(Set.of(start)) : new LinkedHashSet<>(nodeIds));
        }
        boolean changed;
        do {
            changed = false;
            for (String id : nodeIds) {
                if (id.equals(start) || incoming.get(id).isEmpty()) continue;
                Iterator<PipelineEdge> predecessors = incoming.get(id).iterator();
                Set<String> next = new LinkedHashSet<>(result.get(predecessors.next().getSource()));
                while (predecessors.hasNext()) next.retainAll(result.get(predecessors.next().getSource()));
                next.add(id);
                if (!next.equals(result.get(id))) {
                    result.put(id, next);
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    public record Analysis(Map<String, PipelineNode> nodes,
                           Map<String, List<PipelineEdge>> outgoing,
                           Map<String, List<PipelineEdge>> incoming,
                           List<String> topologicalOrder,
                           Set<String> reachable,
                           Set<String> canTerminate,
                           Map<String, Set<String>> dominators,
                           boolean dag) {
    }
}
