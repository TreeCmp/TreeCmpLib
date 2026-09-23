package treecmp.heuristics.vnd;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.metrics.topological.RFMetric;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleBiFunction;

public class DetailedTrajectoryVndLogger implements VndStepListener {

    public static class LogContext {
        private static final ThreadLocal<String> metric = new ThreadLocal<>();
        private static final ThreadLocal<String> variant = new ThreadLocal<>();
        private static final ThreadLocal<Integer> size = new ThreadLocal<>();
        private static final ThreadLocal<Integer> pairIndex = new ThreadLocal<>();

        public static void set(String m, String v, int s, int p) {
            metric.set(m);
            variant.set(v);
            size.set(s);
            pairIndex.set(p);
        }

        public static void clear() {
            metric.remove();
            variant.remove();
            size.remove();
            pairIndex.remove();
        }

        public static boolean isSet() {
            return metric.get() != null;
        }
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final RFMetric RF = new RFMetric();

    private final String logFile;
    private final ToDoubleBiFunction<Tree, Tree> distanceEvaluator;
    private int stepCounter = 0;
    private Tree lastLoggedTree;

    public DetailedTrajectoryVndLogger(String prefixName, String metricName,
                                       ToDoubleBiFunction<Tree, Tree> distanceEvaluator) {
        String timestamp = LocalDateTime.now().format(DTF);
        String hexUid = String.format("%04X", COUNTER.incrementAndGet() & 0xFFFF);

        int s = (LogContext.isSet() && LogContext.size.get() != null) ? LogContext.size.get() : 10;
        File subDir = new File("logs/" + s);
        if (!subDir.exists()) {
            subDir.mkdirs();
        }

        if (LogContext.isSet()) {
            String m = cleanToken(LogContext.metric.get());
            String v = cleanToken(LogContext.variant.get());
            int p = LogContext.pairIndex.get() != null ? LogContext.pairIndex.get() : 0;
            this.logFile = String.format("logs/%d/proof_%s_%s_N%d_pair%d_%s_%s.txt", s, m, v, s, p, timestamp, hexUid);
        } else {
            this.logFile = String.format("logs/%d/proof_%s_%s_%s_%s.txt", s, cleanToken(prefixName), cleanToken(metricName), timestamp, hexUid);
        }

        this.distanceEvaluator = distanceEvaluator;
    }

    public static String cleanToken(String raw) {
        if (raw == null) return "UNKNOWN";
        return raw.replace("+ Tie", "Tie")
                .replace("+Tie", "Tie")
                .replace("+", "Tie")
                .replace("->", "_")
                .replace(">", "_")
                .replace("<", "_")
                .replaceAll("^[0-9]+\\.\\s*", "")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("(?i)(_Tie)+", "_Tie")
                .replaceAll("^_|_$", "");
    }

    private String toNewickString(Tree tree) {
        if (tree == null || tree.getRoot() == null) return "();";
        return toNewickSubtree(tree.getRoot()) + ";";
    }

    private String toNewickSubtree(Node n) {
        if (n.isLeaf()) {
            return n.getIdentifier() != null ? n.getIdentifier().getName() : "Leaf_" + n.getNumber();
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < n.getChildCount(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toNewickSubtree(n.getChild(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    private int computeUnrootedRf(Tree t1, Tree t2) {
        if (t1 == null || t2 == null) return 0;
        Tree u1 = TreeCmpUtils.unrootTreeIfNeeded(t1);
        Tree u2 = TreeCmpUtils.unrootTreeIfNeeded(t2);
        return (int) Math.round(RF.getDistance(u1, u2) * 2.0);
    }

    @Override
    public void onStart(String testName, Tree startTree, double initialDistance) {
        this.stepCounter = 0;
        this.lastLoggedTree = cloneAndIndex(startTree);

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, false))) {
            pw.println("========================================================");
            pw.println("HEURISTIC TRAJECTORY: " + testName);
            pw.println("========================================================");
            pw.printf(Locale.US, "STEP 0 [START] - Distance: %.4f%n", initialDistance);
            pw.println(toNewickString(this.lastLoggedTree));
            pw.println();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStep(String heuristicName, List<Tree> trajectory, double currentBestValue, Tree targetTree) {
        if (trajectory == null || trajectory.isEmpty()) {
            return;
        }

        List<Tree> flattenedStrictSteps = new ArrayList<>();

        for (Tree candidate : trajectory) {
            if (candidate == null) continue;
            Tree indexed = cloneAndIndex(candidate);

            int diff = computeUnrootedRf(this.lastLoggedTree, indexed);
            if (diff == 0) {
                continue; // Skip no-op
            } else if (diff == 2) {
                flattenedStrictSteps.add(indexed);
                this.lastLoggedTree = indexed;
            } else if (diff >= 4) {
                // Auto-bridge jump: generate intermediate 1-NNI trees (each RF == 2)
                List<Tree> bridge = bridgeGap(this.lastLoggedTree, indexed);
                if (bridge != null && !bridge.isEmpty()) {
                    for (Tree b : bridge) {
                        flattenedStrictSteps.add(b);
                        this.lastLoggedTree = b;
                    }
                } else {
                    flattenedStrictSteps.add(indexed);
                    this.lastLoggedTree = indexed;
                }
            }
        }

        if (flattenedStrictSteps.isEmpty()) {
            return;
        }

        int totalSubsteps = flattenedStrictSteps.size();
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            for (int i = 0; i < totalSubsteps; i++) {
                Tree stepTree = flattenedStrictSteps.get(i);
                stepCounter++;

                String stepLabel = (totalSubsteps == 1)
                        ? heuristicName
                        : heuristicName + " -> NNI_Substep_" + (i + 1);

                double evaluatedDist = (i == totalSubsteps - 1)
                        ? currentBestValue
                        : safeEvaluate(heuristicName, stepTree, targetTree, currentBestValue);

                pw.printf(Locale.US, "STEP %d [%s] - Distance: %.4f%n", stepCounter, stepLabel, evaluatedDist);
                pw.println(toNewickString(stepTree));
                pw.println();
            }
        } catch (Exception ignored) {
        }
    }

    private List<Tree> bridgeGap(Tree start, Tree goal) {
        Queue<List<Tree>> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        String startK = toCanonicalKey(start.getRoot());
        visited.add(startK);

        List<Tree> startPath = new ArrayList<>();
        startPath.add(start);
        queue.add(startPath);

        while (!queue.isEmpty()) {
            List<Tree> path = queue.poll();
            Tree cur = path.get(path.size() - 1);

            if (computeUnrootedRf(cur, goal) == 0) {
                return path.subList(1, path.size());
            }

            if (path.size() > 5) continue;

            for (Tree neighbor : generate1NniNeighbors(cur)) {
                if (computeUnrootedRf(cur, neighbor) != 2) continue;

                String nK = toCanonicalKey(neighbor.getRoot());
                if (visited.add(nK)) {
                    List<Tree> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }
        return null;
    }

    private List<Tree> generate1NniNeighbors(Tree tree) {
        List<Tree> neighbors = new ArrayList<>();
        int leafCount = tree.getExternalNodeCount();

        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node internal = tree.getInternalNode(i);
            if (internal.isRoot()) continue;
            Node parent = internal.getParent();
            if (parent == null) continue;

            for (int c1 = 0; c1 < internal.getChildCount(); c1++) {
                for (int c2 = 0; c2 < parent.getChildCount(); c2++) {
                    if (parent.getChild(c2) == internal) continue;
                    Tree rotated = applyNniSwap(tree, internal, c1, parent, c2);
                    if (rotated != null && rotated.getExternalNodeCount() == leafCount) {
                        neighbors.add(rotated);
                    }
                }
            }
        }

        Node root = tree.getRoot();
        if (root.getChildCount() >= 3) {
            for (int i = 0; i < root.getChildCount(); i++) {
                Node child = root.getChild(i);
                if (child.isLeaf()) continue;

                for (int c = 0; c < child.getChildCount(); c++) {
                    for (int j = 0; j < root.getChildCount(); j++) {
                        if (i == j) continue;
                        Tree rotated = applyRootNniSwap(tree, child, c, root, j);
                        if (rotated != null && rotated.getExternalNodeCount() == leafCount) {
                            neighbors.add(rotated);
                        }
                    }
                }
            }
        }

        return neighbors;
    }

    private static List<Integer> getPathFromRoot(Node node) {
        List<Integer> path = new ArrayList<>();
        Node curr = node;
        while (curr.getParent() != null) {
            Node p = curr.getParent();
            int idx = -1;
            for (int i = 0; i < p.getChildCount(); i++) {
                if (p.getChild(i) == curr) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) break;
            path.add(idx);
            curr = p;
        }
        Collections.reverse(path);
        return path;
    }

    private static Node getNodeByPath(Node root, List<Integer> path) {
        Node curr = root;
        for (int idx : path) {
            if (curr == null || idx < 0 || idx >= curr.getChildCount()) return null;
            curr = curr.getChild(idx);
        }
        return curr;
    }

    private Tree applyNniSwap(Tree baseTree, Node n1, int childIdx1, Node n2, int childIdx2) {
        try {
            List<Integer> path1 = getPathFromRoot(n1);
            List<Integer> path2 = getPathFromRoot(n2);

            Tree copy = new SimpleTree(baseTree);
            if (copy instanceof SimpleTree) {
                ((SimpleTree) copy).createNodeList();
            }
            TreeUtils.computeParentPointers(copy.getRoot());

            Node copyN1 = getNodeByPath(copy.getRoot(), path1);
            Node copyN2 = getNodeByPath(copy.getRoot(), path2);

            if (copyN1 == null || copyN2 == null) return null;
            if (childIdx1 >= copyN1.getChildCount() || childIdx2 >= copyN2.getChildCount()) return null;

            Node child1 = copyN1.getChild(childIdx1);
            Node child2 = copyN2.getChild(childIdx2);

            copyN1.removeChild(childIdx1);
            copyN2.removeChild(childIdx2);

            copyN1.addChild(child2);
            copyN2.addChild(child1);

            child2.setParent(copyN1);
            child1.setParent(copyN2);

            TreeUtils.computeParentPointers(copy.getRoot());
            if (copy instanceof SimpleTree) {
                ((SimpleTree) copy).createNodeList();
            }
            return copy;
        } catch (Exception e) {
            return null;
        }
    }

    private Tree applyRootNniSwap(Tree baseTree, Node child, int grandChildIdx, Node root, int otherChildIdx) {
        try {
            List<Integer> pathChild = getPathFromRoot(child);

            Tree copy = new SimpleTree(baseTree);
            if (copy instanceof SimpleTree) {
                ((SimpleTree) copy).createNodeList();
            }
            TreeUtils.computeParentPointers(copy.getRoot());

            Node copyRoot = copy.getRoot();
            Node copyChild = getNodeByPath(copyRoot, pathChild);

            if (copyChild == null || grandChildIdx >= copyChild.getChildCount() || otherChildIdx >= copyRoot.getChildCount()) {
                return null;
            }

            Node copyOther = copyRoot.getChild(otherChildIdx);
            Node grandChild = copyChild.getChild(grandChildIdx);

            copyChild.removeChild(grandChildIdx);
            copyRoot.removeChild(otherChildIdx);

            copyChild.addChild(copyOther);
            copyRoot.addChild(grandChild);

            copyOther.setParent(copyChild);
            grandChild.setParent(copyRoot);

            TreeUtils.computeParentPointers(copy.getRoot());
            if (copy instanceof SimpleTree) {
                ((SimpleTree) copy).createNodeList();
            }
            return copy;
        } catch (Exception e) {
            return null;
        }
    }

    private String toCanonicalKey(Node node) {
        if (node.isLeaf()) return node.getIdentifier().getName();
        List<String> ch = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) ch.add(toCanonicalKey(node.getChild(i)));
        Collections.sort(ch);
        return "(" + String.join(",", ch) + ")";
    }

    @Override
    public void onFinish(double finalDistance) {
        if (finalDistance > 0.0) {
            try {
                File f = new File(logFile);
                if (f.exists()) {
                    f.delete();
                }
            } catch (Exception ignored) {
            }
            return;
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println("========================================================");
            pw.printf(Locale.US, "[FINAL] Total Steps: %d | Final Distance: %.4f%n", stepCounter, finalDistance);
            pw.println("========================================================");
        } catch (Exception ignored) {
        }
    }

    private Tree cloneAndIndex(Tree t) {
        if (t == null) return null;
        Tree copy = new SimpleTree(t);
        if (copy instanceof SimpleTree) {
            ((SimpleTree) copy).createNodeList();
        }
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    private double safeEvaluate(String heuristicName, Tree stepTree, Tree targetTree, double fallbackValue) {
        try {
            return distanceEvaluator.applyAsDouble(stepTree, targetTree);
        } catch (Exception e) {
            return fallbackValue;
        }
    }

    public int getStepCount() {
        return this.stepCounter;
    }
}