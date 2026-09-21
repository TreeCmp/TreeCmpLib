package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.metrics.topological.RFMetric;

import java.util.*;

public class Ecr3Move implements TreeMove {
    public final List<Node> cluster;
    public final Node[] boundarySubtrees;
    public final TopologyTemplate3sECR originalSignature;
    public final TopologyTemplate3sECR template;

    private static final RFMetric RF = new RFMetric();
    private List<TopologyTemplate3sECR> resolvedPath = null;

    public Ecr3Move(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR originalSignature, TopologyTemplate3sECR template) {
        this.cluster = cluster;
        this.boundarySubtrees = boundarySubtrees;
        this.originalSignature = originalSignature;
        this.template = template;
    }

    public Ecr3Move(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR template) {
        this(cluster, boundarySubtrees, null, template);
    }

    @Override
    public String getDescription() {
        return String.format("3-sECR: Resolving cluster into new binary topology (exact NNI cost: %d)",
                getNniEquivalentCost());
    }

    @Override
    public int getNniEquivalentCost() {
        ensurePathResolved(null);
        return resolvedPath != null ? Math.max(1, resolvedPath.size() + 1) : 1;
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        if (startTree == null || cluster == null || boundarySubtrees == null || template == null) {
            return Collections.emptyList();
        }

        boolean isUnrooted = startTree.getRoot().getChildCount() >= 3;
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(isUnrooted);
        List<Tree> trajectory = new ArrayList<>();
        Tree lastTree = startTree;

        ensurePathResolved(utils);

        // 1. Aplikujemy kolejne szablony pośrednie leżące na ścieżce 1-NNI
        if (resolvedPath != null) {
            for (TopologyTemplate3sECR stepTemplate : resolvedPath) {
                Tree stepTree = utils.createEcr3Tree(startTree, cluster, boundarySubtrees, stepTemplate);
                if (stepTree != null) {
                    if (stepTree instanceof SimpleTree) {
                        ((SimpleTree) stepTree).createNodeList();
                        TreeUtils.computeParentPointers(stepTree.getRoot());
                    }
                    double diff = RF.getDistance(lastTree, stepTree);
                    if (diff > 0.0) {
                        trajectory.add(stepTree);
                        lastTree = stepTree;
                    }
                }
            }
        }

        // 2. Dodajemy drzewo docelowe
        Tree finalTree = utils.createEcr3Tree(startTree, cluster, boundarySubtrees, template);
        if (finalTree != null) {
            if (finalTree instanceof SimpleTree) {
                ((SimpleTree) finalTree).createNodeList();
                TreeUtils.computeParentPointers(finalTree.getRoot());
            }
            if (RF.getDistance(lastTree, finalTree) > 0.0) {
                trajectory.add(finalTree);
            }
        }

        return trajectory;
    }

    private void ensurePathResolved(SubtreeEcr3Utils utils) {
        if (resolvedPath == null) {
            TopologyTemplate3sECR from = this.originalSignature;
            if (from == null && utils != null && cluster != null && !cluster.isEmpty() && boundarySubtrees != null) {
                try {
                    from = utils.extractSignature(cluster.get(0), cluster, Arrays.asList(boundarySubtrees));
                } catch (Exception ignored) {
                }
            }
            resolvedPath = findPathBetweenTemplates(from, template);
        }
    }

    /**
     * Wyszukuje ścieżkę 1-NNI w grafie 105 drzew binarnych o 5 liściach.
     */
    private List<TopologyTemplate3sECR> findPathBetweenTemplates(TopologyTemplate3sECR from, TopologyTemplate3sECR to) {
        if (to == null) {
            return Collections.emptyList();
        }
        if (from == null) {
            return (to.nniTrajectoryTemplates != null) ? to.nniTrajectoryTemplates : Collections.emptyList();
        }
        if (areIsomorphic(from, to)) {
            return Collections.emptyList();
        }

        List<TopologyTemplate3sECR> all = SubtreeEcr3Utils.getTemplates();
        TopologyTemplate3sECR startNode = null;
        TopologyTemplate3sECR targetNode = null;

        for (TopologyTemplate3sECR t : all) {
            if (startNode == null && areIsomorphic(t, from)) startNode = t;
            if (targetNode == null && areIsomorphic(t, to)) targetNode = t;
            if (startNode != null && targetNode != null) break;
        }

        if (startNode == null || targetNode == null || startNode == targetNode) {
            return (to.nniTrajectoryTemplates != null) ? to.nniTrajectoryTemplates : Collections.emptyList();
        }

        Map<TopologyTemplate3sECR, TopologyTemplate3sECR> parentMap = new IdentityHashMap<>();
        Queue<TopologyTemplate3sECR> queue = new ArrayDeque<>();

        queue.add(startNode);
        parentMap.put(startNode, null);

        while (!queue.isEmpty()) {
            TopologyTemplate3sECR curr = queue.poll();
            if (curr == targetNode) break;

            for (TopologyTemplate3sECR cand : all) {
                if (!parentMap.containsKey(cand) && isExactSingleNniStep(curr, cand)) {
                    parentMap.put(cand, curr);
                    queue.add(cand);
                }
            }
        }

        if (!parentMap.containsKey(targetNode)) {
            return (to.nniTrajectoryTemplates != null) ? to.nniTrajectoryTemplates : Collections.emptyList();
        }

        List<TopologyTemplate3sECR> path = new ArrayList<>();
        TopologyTemplate3sECR step = parentMap.get(targetNode);
        while (step != null && step != startNode) {
            path.add(step);
            step = parentMap.get(step);
        }
        Collections.reverse(path);
        return path;
    }

    public static boolean areIsomorphic(TopologyTemplate3sECR a, TopologyTemplate3sECR b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.leafIndex != -1 || b.leafIndex != -1) {
            return a.leafIndex == b.leafIndex;
        }
        return (areIsomorphic(a.left, b.left) && areIsomorphic(a.right, b.right))
                || (areIsomorphic(a.left, b.right) && areIsomorphic(a.right, b.left));
    }

    private boolean isExactSingleNniStep(TopologyTemplate3sECR a, TopologyTemplate3sECR b) {
        if (areIsomorphic(a, b)) return false;
        int[] clA = getNonTrivialClusters(a);
        int[] clB = getNonTrivialClusters(b);

        int sharedCount = 0;
        for (int maskA : clA) {
            for (int maskB : clB) {
                if (maskA == maskB) {
                    sharedCount++;
                    break;
                }
            }
        }
        return sharedCount == 2;
    }

    private int[] getNonTrivialClusters(TopologyTemplate3sECR template) {
        int[] clusters = new int[3];
        int[] idx = {0};
        collectClusters(template, clusters, idx);
        return clusters;
    }

    private int collectClusters(TopologyTemplate3sECR node, int[] clusters, int[] idx) {
        if (node.leafIndex != -1) {
            return 1 << node.leafIndex;
        }
        int leftMask = collectClusters(node.left, clusters, idx);
        int rightMask = collectClusters(node.right, clusters, idx);
        int currentMask = leftMask | rightMask;

        if (currentMask != 31 && idx[0] < 3) {
            clusters[idx[0]++] = currentMask;
        }
        return currentMask;
    }
}