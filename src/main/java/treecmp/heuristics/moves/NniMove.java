package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NniMove implements TreeMove {

    // Węzeł, który aktualnie "niesiemy" (odcięte poddrzewo)
    public final Node movingSubtree;

    // Węzeł, z którym się zamieniamy miejscami (sąsiad w grafie)[cite: 13]
    public final Node swapPartner;

    public NniMove(Node movingSubtree, Node swapPartner) {
        this.movingSubtree = movingSubtree;
        this.swapPartner = swapPartner;
    }

    @Override
    public String getDescription() {
        return "Swap " + movingSubtree.getIdentifier().getName() +
                " with " + swapPartner.getIdentifier().getName();
    }

    @Override
    public int getNniEquivalentCost() {
        return 1; // 1 NNI to 1 NNI[cite: 13]
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        // Dla pojedynczego ruchu NNI trajektoria to dokładnie 1 krok – drzewo po zamianie
        Tree resultTree = applyMove(startTree);
        if (resultTree == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(resultTree);
    }

    /**
     * Bezpiecznie aplikuje pojedynczą rotację NNI na kopii drzewa startowego.
     */
    private Tree applyMove(Tree startTree) {
        SimpleTree clone = new SimpleTree(startTree);
        clone.createNodeList();
        TreeUtils.computeParentPointers(clone.getRoot());

        Node vChild = findEquivalentNode(startTree.getRoot(), movingSubtree, clone);
        Node vSibling = findEquivalentNode(startTree.getRoot(), swapPartner, clone);

        if (vChild == null || vSibling == null) return null;
        Node pChild = vChild.getParent();
        Node pSibling = vSibling.getParent();
        if (pChild == null || pSibling == null || pChild == pSibling) return null;

        int idxChild = -1, idxSibling = -1;
        for (int i = 0; i < pChild.getChildCount(); i++) {
            if (pChild.getChild(i) == vChild) idxChild = i;
        }
        for (int i = 0; i < pSibling.getChildCount(); i++) {
            if (pSibling.getChild(i) == vSibling) idxSibling = i;
        }

        if (idxChild == -1 || idxSibling == -1) return null;

        // Zamiana poddrzew w strukturze PAL
        pChild.setChild(idxChild, vSibling);
        vSibling.setParent(pChild);
        pSibling.setChild(idxSibling, vChild);
        vChild.setParent(pSibling);

        // Odświeżenie wskaźników i listy węzłów po rotacji
        clone.createNodeList();
        TreeUtils.computeParentPointers(clone.getRoot());

        return clone;
    }

    private Node findEquivalentNode(Node origRoot, Node targetOrig, Tree cloneTree) {
        if (targetOrig == origRoot) return cloneTree.getRoot();
        List<Integer> path = new ArrayList<>();
        Node curr = targetOrig;
        while (curr != origRoot && curr != null) {
            Node p = curr.getParent();
            if (p == null) break;
            for (int i = 0; i < p.getChildCount(); i++) {
                if (p.getChild(i) == curr) {
                    path.add(i);
                    break;
                }
            }
            curr = p;
        }
        Collections.reverse(path);
        Node res = cloneTree.getRoot();
        for (int idx : path) {
            if (idx >= 0 && idx < res.getChildCount()) {
                res = res.getChild(idx);
            } else {
                return null;
            }
        }
        return res;
    }
}