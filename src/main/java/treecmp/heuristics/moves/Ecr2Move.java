package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.metrics.topological.RFMetric;

import java.util.*;

public class Ecr2Move implements TreeMove {
    public final Node top;
    public final Node m1;
    public final Node m2;
    public final Node[] boundarySubtrees;
    public final TopologyTemplate2sECR template;
    public final boolean isOriginalFork;

    private static final RFMetric RF = new RFMetric();
    private List<TopologyTemplate2sECR> resolvedPath = null;

    public Ecr2Move(Node top, Node m1, Node m2, Node[] boundarySubtrees, TopologyTemplate2sECR template) {
        this.top = top;
        this.m1 = m1;
        this.m2 = m2;
        this.boundarySubtrees = boundarySubtrees;
        this.template = template;
        this.isOriginalFork = (m2 != null && top != null && m2.getParent() == top);
    }

    @Override
    public String getDescription() {
        String clusterType = template.isFork ? "Fork" : "Chain";
        return String.format("2-sECR [%s] applying permutation: %s (exact NNI cost: %d)",
                clusterType, Arrays.toString(template.indices), getNniEquivalentCost());
    }

    @Override
    public int getNniEquivalentCost() {
        ensurePathResolved();
        return (resolvedPath != null && !resolvedPath.isEmpty()) ? resolvedPath.size() : calculateFallbackCost(this.template);
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        if (startTree == null || top == null || m1 == null || m2 == null || boundarySubtrees == null || template == null) {
            return Collections.emptyList();
        }

        List<Tree> trajectory = new ArrayList<>();
        ensurePathResolved();

        if (resolvedPath != null && !resolvedPath.isEmpty()) {
            for (TopologyTemplate2sECR stepTemplate : resolvedPath) {
                Tree stepTree = SubtreeEcr2Utils.createEcrTree(
                        startTree, top, m1, m2, boundarySubtrees, stepTemplate, isOriginalFork
                );
                if (stepTree != null) {
                    if (stepTree instanceof SimpleTree) {
                        ((SimpleTree) stepTree).createNodeList();
                    }
                    TreeUtils.computeParentPointers(stepTree.getRoot());
                    trajectory.add(stepTree);
                }
            }
        }

        // Jeśli ścieżka była pusta (np. ruch o koszcie 1 NNI bez kroków pośrednich)
        if (trajectory.isEmpty()) {
            Tree finalTree = SubtreeEcr2Utils.createEcrTree(
                    startTree, top, m1, m2, boundarySubtrees, template, isOriginalFork
            );
            if (finalTree != null) {
                if (finalTree instanceof SimpleTree) {
                    ((SimpleTree) finalTree).createNodeList();
                }
                TreeUtils.computeParentPointers(finalTree.getRoot());
                trajectory.add(finalTree);
            }
        }

        return trajectory;
    }

    private void ensurePathResolved() {
        if (resolvedPath == null) {
            TopologyTemplate2sECR start = getStartTemplate(isOriginalFork);
            resolvedPath = findPathBetweenTemplates(start, this.template);
        }
    }

    /**
     * Wyszukuje najkrótszą ścieżkę 1-NNI w grafie 15 topologii ECR-2.
     */
    private List<TopologyTemplate2sECR> findPathBetweenTemplates(TopologyTemplate2sECR from, TopologyTemplate2sECR to) {
        if (from == null || to == null || isSameTemplate(from, to)) {
            return Collections.emptyList();
        }

        List<TopologyTemplate2sECR> all = SubtreeEcr2Utils.getTemplates();
        TopologyTemplate2sECR startNode = null;
        TopologyTemplate2sECR targetNode = null;

        for (TopologyTemplate2sECR t : all) {
            if (startNode == null && isSameTemplate(t, from)) startNode = t;
            if (targetNode == null && isSameTemplate(t, to)) targetNode = t;
            if (startNode != null && targetNode != null) break;
        }

        if (startNode == null || targetNode == null) {
            return Collections.emptyList();
        }

        Map<TopologyTemplate2sECR, TopologyTemplate2sECR> parentMap = new HashMap<>();
        Queue<TopologyTemplate2sECR> queue = new ArrayDeque<>();

        queue.add(startNode);
        parentMap.put(startNode, null);

        while (!queue.isEmpty()) {
            TopologyTemplate2sECR curr = queue.poll();
            if (isSameTemplate(curr, targetNode)) {
                targetNode = curr;
                break;
            }

            for (TopologyTemplate2sECR cand : all) {
                if (!parentMap.containsKey(cand) && isSingleNni(curr, cand)) {
                    parentMap.put(cand, curr);
                    queue.add(cand);
                }
            }
        }

        if (!parentMap.containsKey(targetNode)) {
            return Collections.emptyList();
        }

        List<TopologyTemplate2sECR> path = new ArrayList<>();
        TopologyTemplate2sECR step = targetNode;
        while (step != null && !isSameTemplate(step, startNode)) {
            path.add(step);
            step = parentMap.get(step);
        }
        Collections.reverse(path);
        return path;
    }

    private TopologyTemplate2sECR getStartTemplate(boolean isOriginalFork) {
        for (TopologyTemplate2sECR t : SubtreeEcr2Utils.getTemplates()) {
            if (t.isFork == isOriginalFork && t.indices[0] == 0 && t.indices[1] == 1 && t.indices[2] == 2 && t.indices[3] == 3) {
                return t;
            }
        }
        return SubtreeEcr2Utils.getTemplates().get(0);
    }

    private static boolean isSameTemplate(TopologyTemplate2sECR a, TopologyTemplate2sECR b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.isFork == b.isFork && Arrays.equals(a.indices, b.indices);
    }

    /**
     * Dwa szablony ECR-2 różnią się o 1-NNI (RF == 2), gdy współdzielą dokładnie 1 split.
     */
    private static boolean isSingleNni(TopologyTemplate2sECR a, TopologyTemplate2sECR b) {
        if (isSameTemplate(a, b)) return false;
        int[] sA = getSplits(a);
        int[] sB = getSplits(b);
        int shared = 0;
        if (sA[0] == sB[0] || sA[0] == sB[1]) shared++;
        if (sA[1] == sB[0] || sA[1] == sB[1]) shared++;
        return shared == 1;
    }

    private static int[] getSplits(TopologyTemplate2sECR t) {
        int split1, split2;
        if (t.isFork) {
            split1 = (1 << t.indices[0]) | (1 << t.indices[1]);
            split2 = (1 << t.indices[2]) | (1 << t.indices[3]);
        } else {
            split1 = (1 << t.indices[0]) | (1 << 4);
            split2 = (1 << t.indices[2]) | (1 << t.indices[3]);
        }
        return new int[]{split1, split2};
    }

    private static int calculateFallbackCost(TopologyTemplate2sECR t) {
        int diff = 0;
        for (int i = 0; i < t.indices.length; i++) {
            if (t.indices[i] != i) diff++;
        }
        return (diff == 0) ? 0 : ((diff <= 2) ? 1 : 2);
    }
}