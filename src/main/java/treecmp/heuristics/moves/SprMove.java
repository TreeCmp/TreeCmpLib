package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.topological.RFMetric;

import java.util.*;

public class SprMove implements TreeMove {

    public final Node sourceNode;
    public final Node movingNode;
    public final Node targetNode;

    private static final RFMetric RF = new RFMetric();

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
        if (startTree == null || sourceNode == null || targetNode == null || sourceNode.getParent() == null) {
            return Collections.emptyList();
        }

        Node origParent = sourceNode.getParent();
        List<Node> nodePath = getTargetNodePath(origParent, targetNode);
        if (nodePath.size() < 2) {
            return Collections.emptyList();
        }

        Node sibling = findSibling(sourceNode, origParent);

        List<Tree> trajectory = new ArrayList<>();
        int expectedLeaves = startTree.getExternalNodeCount();
        boolean isUnrooted = startTree.getRoot().getChildCount() >= 3;

        Tree lastTree = startTree;

        for (int i = 1; i < nodePath.size(); i++) {
            Node nextTarget = nodePath.get(i);

            // Wpięcie w rodzeństwo po odcięciu odtwarza pierwotne drzewo
            if (nextTarget == sibling && nextTarget != targetNode) {
                continue;
            }

            Tree intermediate = createIntermediateSprTree(startTree, sourceNode, nextTarget, isUnrooted, expectedLeaves);

            if (intermediate != null) {
                // Weryfikacja różnicy w przestrzeni bezkorzennej eliminuje ukryte no-opy na ścieżce przodków/korzenia
                double diff = computeUnrootedRf(lastTree, intermediate);
                if (diff > 0.0) {
                    trajectory.add(intermediate);
                    lastTree = intermediate;
                }
            }
        }

        // Zabezpieczenie: jeśli żaden krok pośredni nie zmienił topologii, generujemy stan docelowy
        if (trajectory.isEmpty()) {
            Tree finalTree = createIntermediateSprTree(startTree, sourceNode, targetNode, isUnrooted, expectedLeaves);
            if (finalTree != null && computeUnrootedRf(startTree, finalTree) > 0.0) {
                trajectory.add(finalTree);
            }
        }

        return trajectory;
    }

    private double computeUnrootedRf(Tree t1, Tree t2) {
        Tree u1 = TreeCmpUtils.unrootTreeIfNeeded(t1);
        Tree u2 = TreeCmpUtils.unrootTreeIfNeeded(t2);
        return RF.getDistance(u1, u2);
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
            Tree res;
            if (isUnrooted) {
                UTbrUtils uUtils = new UTbrUtils();
                res = uUtils.createUtbrTree(baseTree, prune, prune, target);
            } else {
                TbrUtils tUtils = new TbrUtils();
                res = tUtils.createSprTree(baseTree, prune, target);
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

        if (lcaIdxU == -1) {
            return Collections.emptyList();
        }

        List<Node> fullPath = new ArrayList<>();
        for (int i = 0; i <= lcaIdxU; i++) {
            fullPath.add(pathToRootU.get(i));
        }
        for (int i = lcaIdxV - 1; i >= 0; i--) {
            fullPath.add(pathToRootV.get(i));
        }
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