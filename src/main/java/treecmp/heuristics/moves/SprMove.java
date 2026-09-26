package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.RFMetric;

import java.util.*;

public class SprMove implements TreeMove {

    public final Node sourceNode;
    public final Node movingNode;
    public final Node targetNode;

    private static final RFMetric RF_UNROOTED = new RFMetric();
    private static final RFClusterMetric RF_ROOTED = new RFClusterMetric();

    public SprMove(Node sourceNode, Node targetNode) {
        this.sourceNode = sourceNode;
        this.movingNode = sourceNode;
        this.targetNode = targetNode;
    }

    @Override
    public int getNniEquivalentCost() {
        if (sourceNode == null || targetNode == null || sourceNode.getParent() == null) {
            return 1;
        }
        return Math.max(1, calculatePathLength(sourceNode.getParent(), targetNode));
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        if (startTree == null || sourceNode == null || targetNode == null) {
            return Collections.emptyList();
        }

        Node localPrune = findMatchingNode(startTree, sourceNode);
        Node localTarget = findMatchingNode(startTree, targetNode);

        if (localPrune == null || localTarget == null || localPrune.getParent() == null) {
            return Collections.emptyList();
        }

        Node origParent = localPrune.getParent();
        List<Node> nodePath = getTargetNodePath(origParent, localTarget);
        if (nodePath.size() < 2) {
            return Collections.emptyList();
        }

        Node sibling = findSibling(localPrune, origParent);

        List<Tree> rawSteps = new ArrayList<>();
        int expectedLeaves = startTree.getExternalNodeCount();
        boolean isUnrooted = startTree.getRoot().getChildCount() >= 3;

        for (int i = 1; i < nodePath.size(); i++) {
            Node nextTarget = nodePath.get(i);
            if (nextTarget == sibling && nextTarget != localTarget) {
                continue;
            }

            Tree intermediate = createIntermediateSprTree(startTree, localPrune, nextTarget, isUnrooted, expectedLeaves);
            if (intermediate != null) {
                rawSteps.add(intermediate);
            }
        }

        // Zawsze upewniamy się, że docelowe drzewo znajduje się na końcu surowych kroków
        Tree finalTree = createIntermediateSprTree(startTree, localPrune, localTarget, isUnrooted, expectedLeaves);
        if (finalTree != null) {
            rawSteps.add(finalTree);
        }

        if (rawSteps.isEmpty() && finalTree != null) {
            rawSteps.add(finalTree);
        }

        return sanitizeToStrict1NniSequence(startTree, rawSteps, isUnrooted);
    }

    private List<Tree> sanitizeToStrict1NniSequence(Tree startTree, List<Tree> steps, boolean isUnrooted) {
        List<Tree> strictTrajectory = new ArrayList<>();
        Tree last = startTree;

        for (Tree next : steps) {
            int rfUnrooted = getUnrootedRf(last, next);
            if (rfUnrooted == 0) {
                continue;
            }

            int rf = getEffectiveRf(last, next, isUnrooted);
            if (rf == 2) {
                strictTrajectory.add(next);
                last = next;
            } else if (rf >= 4) {
                List<Tree> subPath = bridgeGap(last, next, isUnrooted);
                if (subPath != null && !subPath.isEmpty()) {
                    for (Tree subTree : subPath) {
                        strictTrajectory.add(subTree);
                        last = subTree;
                    }
                }
            }
        }
        return strictTrajectory;
    }

    /**
     * Inteligentne uzupełnianie brakujących kroków 1-NNI za pomocą algorytmu Best-First Search
     * kierowanego minimalizacją dystansu RF do drzewa docelowego.
     */
    public List<Tree> bridgeGap(Tree start, Tree goal, boolean isUnrooted) {
        if (getEffectiveRf(start, goal, isUnrooted) == 0) {
            return Collections.emptyList();
        }

        List<Tree> path = new ArrayList<>();
        Tree cur = start;
        Set<String> visited = new HashSet<>();
        visited.add(toCanonicalKey(cur.getRoot()));

        int maxSteps = 30; // Zabezpieczenie przed nieskończoną pętlą
        while (path.size() < maxSteps) {
            int currentRf = getEffectiveRf(cur, goal, isUnrooted);
            if (currentRf == 0) {
                return path;
            }

            Tree bestNeighbor = null;
            int bestRf = currentRf;
            List<Tree> neighbors = generate1NniNeighbors(cur);

            // 1. Przeszukiwanie zachłanne: znajdź sąsiada ściśle zbliżającego nas do celu
            for (Tree n : neighbors) {
                int rf = getEffectiveRf(n, goal, isUnrooted);
                if (rf < bestRf) {
                    String k = toCanonicalKey(n.getRoot());
                    if (!visited.contains(k)) {
                        bestRf = rf;
                        bestNeighbor = n;
                        if (bestRf == 0) break;
                    }
                }
            }

            // 2. Lookahead o 1 krok, jeśli trafiliśmy na lokalne plateau
            if (bestNeighbor == null) {
                for (Tree n : neighbors) {
                    int rf = getEffectiveRf(n, goal, isUnrooted);
                    if (rf <= currentRf) {
                        String k = toCanonicalKey(n.getRoot());
                        if (!visited.contains(k)) {
                            for (Tree n2 : generate1NniNeighbors(n)) {
                                if (getEffectiveRf(n2, goal, isUnrooted) < currentRf) {
                                    bestNeighbor = n;
                                    break;
                                }
                            }
                            if (bestNeighbor != null) break;
                        }
                    }
                }
            }

            if (bestNeighbor != null) {
                visited.add(toCanonicalKey(bestNeighbor.getRoot()));
                path.add(bestNeighbor);
                cur = bestNeighbor;
            } else {
                break;
            }
        }

        if (getEffectiveRf(cur, goal, isUnrooted) == 0) {
            return path;
        }

        return null;
    }

    private int getUnrootedRf(Tree t1, Tree t2) {
        if (t1 == null || t2 == null) return 0;
        Tree u1 = TreeCmpUtils.unrootTreeIfNeeded(t1);
        Tree u2 = TreeCmpUtils.unrootTreeIfNeeded(t2);
        return (int) Math.round(RF_UNROOTED.getDistance(u1, u2) * 2.0);
    }

    private int getEffectiveRf(Tree t1, Tree t2, boolean isUnrooted) {
        if (t1 == null || t2 == null) return 0;
        int rfU = getUnrootedRf(t1, t2);

        if (!isUnrooted) {
            int rfR = (int) Math.round(RF_ROOTED.getDistance(t1, t2) * 2.0);
            if (rfU == 0 && rfR > 0) return rfR;
        }
        return rfU;
    }

    /**
     * Niezmiennicze topologicznie wyszukiwanie węzła na podstawie zbioru liści (bipartycji).
     */
    private Node findMatchingNode(Tree tree, Node target) {
        if (target == null || tree == null) return null;
        if (target.isLeaf()) {
            return TreeUtils.getNodeByName(tree, target.getIdentifier().getName());
        }

        Set<String> targetLeaves = new HashSet<>();
        collectLeafNames(target, targetLeaves);

        int totalLeaves = tree.getExternalNodeCount();
        Node bestBipartitionMatch = null;

        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node candidate = tree.getInternalNode(i);
            Set<String> candLeaves = new HashSet<>();
            collectLeafNames(candidate, candLeaves);

            if (candLeaves.equals(targetLeaves)) {
                return candidate;
            }

            // Obsługa dopełnienia bipartycji w drzewach nieukorzenionych
            if (candLeaves.size() == totalLeaves - targetLeaves.size()) {
                Set<String> intersection = new HashSet<>(candLeaves);
                intersection.retainAll(targetLeaves);
                if (intersection.isEmpty()) {
                    bestBipartitionMatch = candidate;
                }
            }
        }

        if (bestBipartitionMatch != null) {
            return bestBipartitionMatch;
        }

        int num = target.getNumber();
        if (num >= 0 && num < tree.getInternalNodeCount()) {
            return tree.getInternalNode(num);
        }
        return null;
    }

    private void collectLeafNames(Node node, Set<String> names) {
        if (node.isLeaf()) {
            names.add(node.getIdentifier().getName());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectLeafNames(node.getChild(i), names);
        }
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

    private Node findSibling(Node child, Node parent) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node ch = parent.getChild(i);
            if (ch != child) return ch;
        }
        return null;
    }

    private Tree createIntermediateSprTree(Tree baseTree, Node prune, Node target, boolean isUnrooted, int expectedLeaves) {
        try {
            Tree res = null;
            if (isUnrooted) {
                UsprUtils uspr = new UsprUtils();
                res = uspr.createUsprTree(baseTree, prune, target);
                if (res == null) {
                    UTbrUtils utbr = new UTbrUtils();
                    res = utbr.createUtbrTree(baseTree, prune, prune, target);
                }
            } else {
                SprUtils spr = new SprUtils();
                res = spr.createSprTree(baseTree, prune, target);
                if (res == null) {
                    TbrUtils tbr = new TbrUtils();
                    res = tbr.createSprTree(baseTree, prune, target);
                }
            }

            if (res != null && res.getExternalNodeCount() == expectedLeaves) {
                if (res instanceof SimpleTree) {
                    ((SimpleTree) res).createNodeList();
                }
                TreeUtils.computeParentPointers(res.getRoot());
                return res;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static List<Node> getTargetNodePath(Node u, Node v) {
        List<Node> pathToRootU = new ArrayList<>();
        Set<Node> visitedU = Collections.newSetFromMap(new IdentityHashMap<>());
        Node curr = u;
        while (curr != null) {
            if (!visitedU.add(curr)) {
                throw new IllegalStateException("Wykryto cykl wskaźników 'parent' w strukturze drzewa przy węźle: " + curr);
            }
            pathToRootU.add(curr);
            curr = curr.getParent();
        }

        List<Node> pathToRootV = new ArrayList<>();
        Set<Node> visitedV = Collections.newSetFromMap(new IdentityHashMap<>());
        curr = v;
        while (curr != null) {
            if (!visitedV.add(curr)) {
                throw new IllegalStateException("Wykryto cykl wskaźników 'parent' w strukturze drzewa przy węźle: " + curr);
            }
            pathToRootV.add(curr);
            curr = curr.getParent();
        }

        int idxU = pathToRootU.size() - 1;
        int idxV = pathToRootV.size() - 1;
        int lcaIdxU = -1;
        int lcaIdxV = -1;

        while (idxU >= 0 && idxV >= 0 && pathToRootU.get(idxU) == pathToRootV.get(idxV)) {
            lcaIdxU = idxU;
            lcaIdxV = idxV;
            idxU--;
            idxV--;
        }

        if (lcaIdxU == -1) return Collections.emptyList();

        List<Node> fullPath = new ArrayList<>();
        for (int i = 0; i <= lcaIdxU; i++) fullPath.add(pathToRootU.get(i));
        for (int i = lcaIdxV - 1; i >= 0; i--) fullPath.add(pathToRootV.get(i));
        return fullPath;
    }

    private int calculatePathLength(Node u, Node v) {
        List<Node> path = getTargetNodePath(u, v);
        return Math.max(1, path.size() - 1);
    }

    @Override
    public String getDescription() {
        return "SprMove[source=" + (sourceNode.isLeaf() ? sourceNode.getIdentifier().getName() : "Internal_" + sourceNode.getNumber()) +
                ", target=" + (targetNode.isLeaf() ? targetNode.getIdentifier().getName() : "Internal_" + targetNode.getNumber()) + "]";
    }

    @Override
    public String toString() {
        return getDescription();
    }
}