package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Klasa reprezentująca pojedynczy ruch TBR (Tree Bisection and Reconnection).
 * Przechowuje pełną informację o strukturze ruchu oraz oblicza równoważny koszt NNI.
 */
public class TbrMove implements TreeMove {

    public final Node movingNode;  // Odcięty korzeń (pruneNode)
    public final Node rerootNode;  // Nowy korzeń po przekorzenieniu odciętego poddrzewa
    public final Node targetNode;  // Docelowe miejsce wpięcia w głównym drzewie

    public TbrMove(Node movingNode, Node rerootNode, Node targetNode) {
        this.movingNode = movingNode;
        this.rerootNode = rerootNode;
        this.targetNode = targetNode;
    }

    @Override
    public String getDescription() {
        return "TbrMove[prune=" + getNodeName(movingNode) +
                ", reroot=" + getNodeName(rerootNode) +
                ", target=" + getNodeName(targetNode) + "]";
    }

    @Override
    public int getNniEquivalentCost() {
        int cost = 0;

        // 1. Koszt przekorzenienia wewnątrz odciętego poddrzewa: dist(movingNode, rerootNode)
        if (movingNode != null && rerootNode != null && movingNode != rerootNode) {
            cost += getPathDistanceInSubtree(movingNode, rerootNode);
        }

        // 2. Koszt przemieszczenia punktu wpięcia w drzewie głównym: dist(movingNode.getParent(), targetNode)
        if (movingNode != null && movingNode.getParent() != null && targetNode != null) {
            Node originalAttachment = movingNode.getParent();
            cost += getTreeDistance(originalAttachment, targetNode, movingNode);
        }

        return Math.max(1, cost);
    }

    private int getPathDistanceInSubtree(Node root, Node target) {
        int dist = 0;
        Node curr = target;
        while (curr != null && curr != root) {
            dist++;
            curr = curr.getParent();
        }
        return dist;
    }

    private int getTreeDistance(Node u, Node v, Node excludeSubtree) {
        if (u == v) return 0;

        int dU = 0;
        Node curr = u;
        while (curr != null) {
            if (curr == excludeSubtree) break;
            dU++;
            curr = curr.getParent();
        }

        int dV = 0;
        curr = v;
        while (curr != null) {
            if (curr == excludeSubtree) break;
            dV++;
            curr = curr.getParent();
        }

        Node pU = u;
        Node pV = v;
        int steps = 0;

        while (dU > dV && pU != null) {
            pU = pU.getParent();
            dU--;
            steps++;
        }
        while (dV > dU && pV != null) {
            pV = pV.getParent();
            dV--;
            steps++;
        }
        while (pU != pV && pU != null && pV != null) {
            pU = pU.getParent();
            pV = pV.getParent();
            steps += 2;
        }

        return steps;
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        if (startTree == null || movingNode == null || targetNode == null) {
            return Collections.emptyList();
        }

        int expectedLeaves = startTree.getExternalNodeCount();
        List<Tree> trajectory = new ArrayList<>();
        Node originalAttachment = movingNode.getParent();

        // 1. Ścieżka przemieszczenia punktu wpięcia w T2 (kolejne elementarne kroki)
        if (originalAttachment != null && originalAttachment != targetNode) {
            List<Node> targetPath = getTargetNodePath(originalAttachment, targetNode);
            for (int i = 1; i < targetPath.size(); i++) {
                Node intermediateTarget = targetPath.get(i);
                // Węzeł root nie stanowi krawędzi regraftu – przejście między gałęziami to pojedynczy obrót NNI
                if (intermediateTarget.isRoot()) {
                    continue;
                }
                if (i == targetPath.size() - 1 && (rerootNode == null || rerootNode == movingNode)) {
                    break;
                }
                Tree stepTree = createIntermediateTree(startTree, movingNode, movingNode, intermediateTarget);
                if (stepTree != null && stepTree.getExternalNodeCount() == expectedLeaves) {
                    trajectory.add(stepTree);
                }
            }
        }

        // 2. Ścieżka przekorzenienia wewnątrz T1 (kolejne obroty NNI w odciętym poddrzewie)
        if (rerootNode != null && rerootNode != movingNode) {
            List<Node> rerootPath = getRerootNodePath(movingNode, rerootNode);
            for (int j = 1; j < rerootPath.size() - 1; j++) {
                Node intermediateReroot = rerootPath.get(j);
                Tree stepTree = createIntermediateTree(startTree, movingNode, intermediateReroot, targetNode);
                if (stepTree != null && stepTree.getExternalNodeCount() == expectedLeaves) {
                    trajectory.add(stepTree);
                }
            }
        }

        // 3. Ostateczne drzewo docelowe
        Tree finalTree = createIntermediateTree(startTree, movingNode, rerootNode, targetNode);
        if (finalTree != null && finalTree.getExternalNodeCount() == expectedLeaves) {
            trajectory.add(finalTree);
        }

        return trajectory;
    }

    private List<Node> getTargetNodePath(Node u, Node v) {
        List<Node> pathToRootU = new ArrayList<>();
        Node curr = u;
        while (curr != null) {
            pathToRootU.add(curr);
            curr = curr.getParent();
        }

        List<Node> pathToRootV = new ArrayList<>();
        curr = v;
        while (curr != null) {
            pathToRootV.add(curr);
            curr = curr.getParent();
        }

        int idxU = pathToRootU.size() - 1;
        int idxV = pathToRootV.size() - 1;
        while (idxU >= 0 && idxV >= 0 && pathToRootU.get(idxU) == pathToRootV.get(idxV)) {
            idxU--;
            idxV--;
        }

        List<Node> fullPath = new ArrayList<>();
        for (int i = 0; i <= idxU + 1; i++) {
            fullPath.add(pathToRootU.get(i));
        }
        for (int i = idxV; i >= 0; i--) {
            fullPath.add(pathToRootV.get(i));
        }
        return fullPath;
    }

    private List<Node> getRerootNodePath(Node root, Node target) {
        List<Node> path = new ArrayList<>();
        Node curr = target;
        while (curr != null && curr != root) {
            path.add(curr);
            curr = curr.getParent();
        }
        path.add(root);
        Collections.reverse(path);
        return path;
    }

    private Tree createIntermediateTree(Tree baseTree, Node prune, Node reroot, Node target) {
        if (baseTree == null || prune == null || target == null) {
            return null;
        }
        int expectedLeaves = baseTree.getExternalNodeCount();
        boolean isUnrooted = baseTree.getRoot().getChildCount() >= 3;
        try {
            Tree res;
            if (isUnrooted) {
                UTbrUtils uUtils = new UTbrUtils();
                res = uUtils.createUtbrTree(baseTree, prune, reroot, target);
            } else {
                TbrUtils tUtils = new TbrUtils();
                if (prune == reroot) {
                    res = tUtils.createSprTree(baseTree, prune, target);
                } else {
                    res = tUtils.createTbrTree(baseTree, prune, reroot, target);
                }
                if (res instanceof SimpleTree) {
                    TreeUtils.computeParentPointers(res.getRoot());
                    ((SimpleTree) res).createNodeList();
                }
            }
            if (res != null && res.getExternalNodeCount() == expectedLeaves) {
                return res;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return getDescription();
    }

    private String getNodeName(Node n) {
        if (n == null) return "null";
        return n.isLeaf() ? n.getIdentifier().getName() : "Internal_" + n.getNumber();
    }
}