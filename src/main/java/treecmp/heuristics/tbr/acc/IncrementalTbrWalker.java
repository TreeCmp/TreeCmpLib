package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * W pełni przyrostowy Wędrowiec TBR (2D-DFS) dedykowany dla metryk skojarzeniowych (MC, MP).
 * Porusza się po całym otoczeniu TBR wyłącznie za pomocą elementarnych kroków 1-NNI
 * (1-krawędziowych modyfikacji w T1 i T2), co pozwala LapSolverowi na aktualizację
 * macierzy kosztów w O(N^2) zamiast O(N^3).
 */
public class IncrementalTbrWalker {

    private final TbrUtils tbrUtils = new TbrUtils();

    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    /**
     * Interfejs dla metryk skojarzeniowych obsługujących dwuetapową inkrementację 1-NNI:
     * - ruchy celu w drzewie głównym T2 (Target DFS)
     * - ruchy przekorzenienia wewnątrz odciętego fragmentu T1 (Reroot DFS)
     */
    public interface RootedTbrMetric {
        // 1. Faza bisekcji
        void setPrunedState(Node pruneNode, Node wanderingSource);
        void revertPrunedState(Node pruneNode, Node wanderingSource);

        // 2. Faza wpinania w drzewie głównym T2 (1-NNI wzdłuż krawędzi docelowych)
        void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource);
        void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);
        void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);

        // 3. Faza przekorzeniania w poddrzewie T1 (1-NNI wzdłuż krawędzi wewnętrznych T1)
        void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode);
        void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode);

        double getCurrentDistance();
    }

    public void walk(Tree baseTree, RootedTbrMetric metric, TbrVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);
        Node root = baseTree.getRoot();

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            // 1. Wycięcie poddrzewa (Bisection) -> push #1
            metric.setPrunedState(pruneNode, wanderingSource);

            // 2. Punkt startowy w T2 dla target DFS -> push #2
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            // 3. Dwuwymiarowy DFS 1-NNI:
            //    Zewnętrzny DFS nawiguje przekorzenienie w T1,
            //    Wewnętrzny DFS nawiguje punkt wpięcia w T2.
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 4. Przywrócenie pierwotnego stanu drzewa (zdejmuje push #2 oraz push #1)
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    /**
     * Zewnętrzny DFS: wędruje po krawędziach odciętego fragmentu T1.
     * Każdy krok moveRerootDown / moveRerootUp odwraca dokładnie jedną krawędź wewnątrz T1.
     */
    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root, RootedTbrMetric metric, TbrVisitor visitor) {
        // A. Dla bieżącego przekorzenienia w T1 wykonujemy pełny Target DFS w T2
        // (Target w T2 znajduje się w korzeniu root)
        if (isValidMove(pruneNode, currentReroot, root)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, root);
        }

        for (Node child : getPrunedChildren(root, pruneNode)) {
            dfsTarget(root, child, pruneNode, currentReroot, wanderingSource, metric, visitor);
        }

        // B. Schodzimy krokami 1-NNI w głąb poddrzewa T1
        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node nextReroot = currentReroot.getChild(i);

                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                dfsReroot(nextReroot, pruneNode, wanderingSource, root, metric, visitor);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    /**
     * Wewnętrzny DFS: wędruje w dół i w górę gałęzi drzewa docelowego T2.
     * Identyczny z modelem z IncrementalSprWalker.
     */
    private void dfsTarget(Node parentTarget, Node targetNode, Node pruneNode, Node rerootNode, Node wanderingSource, RootedTbrMetric metric, TbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, rerootNode, wanderingSource);

        if (isValidMove(pruneNode, rerootNode, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, rerootNode, targetNode);
        }

        for (Node child : getPrunedChildren(targetNode, pruneNode)) {
            dfsTarget(targetNode, child, pruneNode, rerootNode, wanderingSource, metric, visitor);
        }

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, rerootNode, wanderingSource);
    }

    private boolean isValidMove(Node pruneNode, Node rerootNode, Node targetNode) {
        // Wykluczamy ruch tożsamościowy (brak zmiany topologii)
        if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) return false;
        return tbrUtils.isValidTbrMove(pruneNode, rerootNode, targetNode);
    }

    /**
     * Zwraca listę dzieci z pominięciem węzła, który zniknął w wyniku bisekcji (stary rodzic).
     */
    private List<Node> getPrunedChildren(Node n, Node pruneNode) {
        List<Node> children = new ArrayList<>();
        Node pParent = pruneNode.getParent();

        if (n == pParent) {
            for (int i = 0; i < n.getChildCount(); i++) {
                if (n.getChild(i) != pruneNode) {
                    children.add(n.getChild(i));
                }
            }
            return children;
        }

        for (int i = 0; i < n.getChildCount(); i++) {
            Node c = n.getChild(i);
            if (c == pParent) {
                for (int j = 0; j < pParent.getChildCount(); j++) {
                    if (pParent.getChild(j) != pruneNode) {
                        children.add(pParent.getChild(j));
                    }
                }
            } else {
                children.add(c);
            }
        }
        return children;
    }

    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    private void collectNodes(Node node, List<Node> list) {
        if (node != null) {
            list.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectNodes(node.getChild(i), list);
            }
        }
    }
}