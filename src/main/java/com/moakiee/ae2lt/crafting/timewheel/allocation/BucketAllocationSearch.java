package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;

/** Sparse, resumable long-capacity matching over observed buckets. No row x key matrix and no
 * subset enumeration. BFS augmentations are Edmonds-Karp; one augmentation moves its bottleneck.
 * A pending search is distinct from infeasibility and retains its frontier across CPU ticks. */
final class BucketAllocationSearch {
    record Request(Map<AEKey, Long> options, long copies, long multiplier, boolean perBatch) { }
    private final List<Request> requests;
    private final Map<AEKey, Long> stock;
    private final long quantum;
    private final boolean binary;
    private long trialCopies;
    private long low = 1;
    private long high;
    private boolean first = true;
    private Trial trial;
    private Map<Integer, Map<AEKey, Long>> best;
    private boolean done;
    private long lastWork;

    BucketAllocationSearch(List<Request> requests, Map<AEKey, Long> stock, long quantum,
            long maxCopies, boolean binary) {
        this.requests = List.copyOf(requests);
        this.stock = Map.copyOf(stock);
        this.quantum = quantum;
        this.trialCopies = maxCopies;
        this.high = maxCopies - 1;
        this.binary = binary;
    }

    boolean advance(long workLimit) {
        lastWork = 0;
        while (!done && lastWork < workLimit) {
            if (trial == null) {
                trial = new Trial(requests, stock, quantum, trialCopies);
                lastWork += trial.buildWork;
            }
            if (!trial.advance(Math.max(0, workLimit - lastWork))) {
                lastWork += trial.lastWork;
                return false;
            }
            lastWork += trial.lastWork;
            var result = trial.result();
            if (first) {
                first = false;
                if (result != null || !binary) { best = result; done = true; break; }
            } else if (result != null) {
                best = result;
                low = trialCopies + 1;
            } else high = trialCopies - 1;
            trial = null;
            if (low > high) done = true;
            else trialCopies = low + (high - low) / 2;
        }
        return done;
    }

    long lastWork() { return lastWork; }
    Map<Integer, Map<AEKey, Long>> result() {
        if (!done) throw new IllegalStateException("Pending matching is not an infeasible result");
        return best;
    }

    private static final class Trial {
        private record Assignment(int row, AEKey bucket, Edge edge) { }
        private final List<List<Edge>> graph = new ArrayList<>();
        private final List<Edge> demands = new ArrayList<>();
        private final List<Assignment> assignments = new ArrayList<>();
        private final Map<Integer, Map<AEKey, Long>> allocations = new LinkedHashMap<>();
        private final int[] queue;
        private int head;
        private int tail;
        private final int sink;
        private final Edge[] parent;
        private final int[] seen;
        private int generation;
        private int scanning = -1;
        private int edgeCursor;
        private final long quantum;
        private boolean done;
        private boolean impossible;
        private long buildWork;
        private long lastWork;
        private int pathPhase;
        private int pathNode;
        private long bottleneck;

        Trial(List<Request> requests, Map<AEKey, Long> stock, long quantum, long copies) {
            this.quantum = quantum;
            var free = new LinkedHashMap<>(stock);
            sink = 1 + requests.size() + stock.size();
            parent = new Edge[sink + 1];
            seen = new int[sink + 1];
            queue = new int[sink + 1];
            for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
            buildWork += sink + 1L;
            var bucketNodes = new LinkedHashMap<AEKey, Integer>();
            int next = 1 + requests.size();
            for (var bucket : stock.keySet()) bucketNodes.put(bucket, next++);
            for (int row = 0; row < requests.size(); row++) {
                var request = requests.get(row);
                long units = Math.multiplyExact(request.multiplier,
                        request.perBatch ? Math.min(copies, request.copies) : request.copies);
                if (request.options.size() == 1) {
                    var entry = request.options.entrySet().iterator().next();
                    long amount = Math.multiplyExact(units, entry.getValue());
                    long available = free.get(entry.getKey());
                    if (amount > available) { impossible = done = true; return; }
                    free.put(entry.getKey(), available - amount);
                    allocations.put(row, Map.of(entry.getKey(), amount));
                } else {
                    demands.add(addEdge(0, row + 1, units));
                    for (var bucket : request.options.keySet()) {
                        var edge = addEdge(row + 1, bucketNodes.get(bucket), Long.MAX_VALUE);
                        assignments.add(new Assignment(row, bucket, edge));
                    }
                }
            }
            for (var entry : free.entrySet()) addEdge(bucketNodes.get(entry.getKey()), sink, entry.getValue() / quantum);
            beginBfs();
        }

        private Edge addEdge(int from, int to, long capacity) {
            var forward = new Edge(from, to, capacity);
            var reverse = new Edge(to, from, 0);
            forward.reverse = reverse;
            reverse.reverse = forward;
            graph.get(from).add(forward);
            graph.get(to).add(reverse);
            buildWork += 2;
            return forward;
        }

        private void beginBfs() {
            // Generation stamps avoid clearing every node for each sparse path search.
            if (++generation == 0) { Arrays.fill(seen, 0); generation = 1; }
            head = 0;
            tail = 1;
            queue[0] = 0;
            seen[0] = generation;
            scanning = -1;
            pathPhase = 0;
        }

        boolean advance(long workLimit) {
            lastWork = 0;
            while (!done && lastWork < workLimit) {
                if (seen[sink] == generation) {
                    if (pathPhase == 0) { pathPhase = 1; pathNode = sink; bottleneck = Long.MAX_VALUE; }
                    if (pathNode == 0) {
                        if (pathPhase == 1) { pathPhase = 2; pathNode = sink; }
                        else { beginBfs(); continue; }
                    }
                    var edge = parent[pathNode];
                    if (pathPhase == 1) bottleneck = Math.min(bottleneck, edge.remaining);
                    else {
                        edge.remaining -= bottleneck;
                        edge.reverse.remaining = Math.addExact(edge.reverse.remaining, bottleneck);
                    }
                    pathNode = edge.from;
                    lastWork++;
                    continue;
                }
                if (scanning < 0) {
                    if (head == tail) { done = true; break; }
                    scanning = queue[head++];
                    edgeCursor = 0;
                    lastWork++;
                }
                var edges = graph.get(scanning);
                if (edgeCursor == edges.size()) { scanning = -1; continue; }
                var edge = edges.get(edgeCursor++);
                lastWork++;
                if (edge.remaining > 0 && seen[edge.to] != generation) {
                    parent[edge.to] = edge;
                    seen[edge.to] = generation;
                    queue[tail++] = edge.to;
                }
            }
            return done;
        }

        Map<Integer, Map<AEKey, Long>> result() {
            if (impossible || demands.stream().anyMatch(edge -> edge.remaining > 0)) return null;
            for (var assignment : assignments) {
                long used = assignment.edge.reverse.remaining;
                if (used > 0) allocations.computeIfAbsent(assignment.row, ignored -> new LinkedHashMap<>())
                        .put(assignment.bucket, Math.multiplyExact(used, quantum));
            }
            return allocations;
        }
    }

    private static final class Edge {
        final int from;
        final int to;
        long remaining;
        Edge reverse;
        Edge(int from, int to, long remaining) { this.from = from; this.to = to; this.remaining = remaining; }
    }
}
