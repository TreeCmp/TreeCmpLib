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

public class TbrMove implements TreeMove {

    public final Node movingNode;
    public final Node sourceNode;
    public final Node rerootNode;
    public final Node targetNode;

    private static final RFMetric RF = new RFMetric();

    public TbrMove(Node movingNode, Node rerootNode, Node targetNode) {
        this.movingNode = movingNode;
        this.sourceNode = movingNode;
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
        if (movingNode != null && rerootNode != null && movingNode != rerootNode) {
            cost += getPathDistanceInSubtree(movingNode, rerootNode);
        }
        if (movingNode != null && movingNode.getParent() != null && targetNode != null) {
            cost += getTreeDistance(movingNode.getParent(), targetNode);
        }
        return Math.max(1, cost);
    }

    private int getPathDistanceInSubtree(Node root, Node target) {
        int dist = 0;
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Node curr = target;
        while (curr != null && curr != root) {
            if (!visited.add(curr)) throw new IllegalStateException("Cykl parent wewnątrz poddrzewa: " + curr);
            dist++;
            curr = curr.getParent();
        }
        return dist;
    }

    private int getTreeDistance(Node u, Node v) {
        if (u == v) return 0;
        List<Node> path = getTargetNodePath(u, v);
        return Math.max(1, path.size() - 1);
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        if (startTree == null || movingNode == null || targetNode == null) {
            return Collections.emptyList();
        }

        List<Tree> fullTrajectory = new ArrayList<>();
        int expectedLeaves = startTree.getExternalNodeCount();
        boolean isUnrooted = startTree.getRoot().getChildCount() >= 3;

        Node origParent = movingNode.getParent();
        Node sibling = findSibling(movingNode, origParent);
        Node baseAttachment = (sibling != null) ? sibling : origParent;

        Node currentEffectiveReroot = movingNode;
        Tree lastTree = startTree;

        // FAZA 1: Przekorzenienie poddrzewa przy zachowaniu pozycji wpięcia baseAttachment
        if (rerootNode != null && movingNode != rerootNode && baseAttachment != null) {
            List<Node> rerootPath = getRerootNodePath(movingNode, rerootNode);
            for (int i = 1; i < rerootPath.size(); i++) {
                currentEffectiveReroot = rerootPath.get(i);
                Tree intermediate = createIntermediateTree(startTree, movingNode, currentEffectiveReroot, baseAttachment, isUnrooted, expectedLeaves);
                if (intermediate != null) {
                    double diff = computeRf(lastTree, intermediate);
                    if (diff > 0.0) {
                        fullTrajectory.add(intermediate);
                        lastTree = intermediate;
                    }
                }
            }
        }

        // FAZA 2: Przesuwanie punktu wpięcia wzdłuż ścieżki do targetNode
        if (origParent != null) {
            List<Node> targetPath = getTargetNodePath(origParent, targetNode);
            for (int i = 1; i < targetPath.size(); i++) {
                Node nextTarget = targetPath.get(i);

                if (nextTarget == baseAttachment && nextTarget != targetNode) {
                    continue;
                }

                Tree intermediate = createIntermediateTree(startTree, movingNode, currentEffectiveReroot, nextTarget, isUnrooted, expectedLeaves);
                if (intermediate != null) {
                    double diff = computeRf(lastTree, intermediate);
                    if (diff > 0.0) {
                        fullTrajectory.add(intermediate);
                        lastTree = intermediate;
                    }
                }
            }
        }

        return fullTrajectory;
    }

    private double computeRf(Tree t1, Tree t2) {
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

    private Tree createIntermediateTree(Tree baseTree, Node prune, Node reroot, Node target, boolean isUnrooted, int expectedLeaves) {
        try {
            Tree res;
            if (isUnrooted) {
                UTbrUtils uUtils = new UTbrUtils();
                res = uUtils.createUtbrTree(baseTree, prune, reroot, target);
            } else {
                TbrUtils tUtils = new TbrUtils();
                res = tUtils.createTbrTree(baseTree, prune, reroot, target);
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
            if (!visitedU.add(curr)) throw new IllegalStateException("Cykl parent: " + curr);
            pathToRootU.add(curr);
            curr = curr.getParent();
        }

        List<Node> pathToRootV = new ArrayList<>();
        Set<Node> visitedV = Collections.newSetFromMap(new IdentityHashMap<>());
        curr = v;
        while (curr != null) {
            if (!visitedV.add(curr)) throw new IllegalStateException("Cykl parent: " + curr);
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

    private List<Node> getRerootNodePath(Node root, Node target) {
        List<Node> path = new ArrayList<>();
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Node curr = target;
        while (curr != null && curr != root) {
            if (!visited.add(curr)) throw new IllegalStateException("Cykl reroot: " + curr);
            path.add(curr);
            curr = curr.getParent();
        }
        if (curr == root) {
            path.add(root);
            Collections.reverse(path);
            return path;
        }
        return Collections.emptyList();
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