package treecmp.heuristics;

import pal.io.InputSource;
import pal.misc.IdGroup;
import pal.misc.Identifier;
import pal.tree.*;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.BestTreeChooser;
import treecmp.heuristics.spr.TreeValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class TreeNeighborhoodUtils {

    public int num = 0;

    public Tree getTreeFromString(String treeStr) {
        Tree tree = null;
        try (InputSource is = InputSource.openString(treeStr)) {
            tree = new ReadTree(is);
        } catch (TreeParseException | IOException e) {
            e.printStackTrace();
        }
        return tree;
    }

    // ==========================================
    // METODY WSPÓŁDZIELONE DLA TBR i uTBR
    // ==========================================

    public boolean isValidTbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        // Magia upewniająca się, że pokrywamy w 100% otoczenie SPR
        if (pruneNode == rerootNode) {
            return isValidSprMove(pruneNode, targetNode);
        }

        if (targetNode == pruneNode.getParent()) return false;
        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }
        if (targetNode.isRoot() && pruneNode.getParent().isRoot()) return false;
        return true;
    }

    public boolean isValidUTbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        // Magia upewniająca się, że pokrywamy w 100% otoczenie unrooted SPR (wraz z inner moves)
        if (pruneNode == rerootNode) {
            return isValidUsprMove(pruneNode, targetNode);
        }

        if (targetNode.isRoot()) return false;
        if (targetNode == pruneNode.getParent()) return false;
        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }
        return true;
    }

    public Tree createTbrTree(Tree baseTree, Node s, Node r, Node t) {
        Tree resultTree = baseTree.getCopy();

        // Zabezpieczenie przed Index -1: Wymuszamy na PAL przeliczenie kopii
        if (resultTree instanceof pal.tree.SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
        }

        Node source = findNodeEquivalent(resultTree, s);
        Node reroot = findNodeEquivalent(resultTree, r);
        Node target = findNodeEquivalent(resultTree, t);

        if (source == null || reroot == null || target == null) return null;

        Node sourceParent = source.getParent();
        boolean isSourceParentRoot = sourceParent.isRoot();
        Node provisionalRoot = resultTree.getRoot();

        // 1. BEZPIECZNE ODCIĘCIE PODDRZEWA
        if (isSourceParentRoot) {
            Node[] otherChildren = findOtherChildren(source, sourceParent);
            if (otherChildren.length == 1) {
                // Drzewa ukorzenione - korzeń ma tylko 1 pozostałą gałąź, która staje się korzeniem
                provisionalRoot = otherChildren[0];
                provisionalRoot.setParent(null);
            } else if (otherChildren.length == 2) {
                // DRZEWA NIEUKORZENIONE: Magiczne i bezpieczne rozwiązanie!
                // Zostawiamy korzeń, usuwamy z niego tylko odciętą gałąź.
                // Dzięki temu ŻADEN LIŚĆ nie zamieni się w węzeł wewnętrzny i nie zawiesi systemu!
                int sourcePos = findChildPos(source, sourceParent);
                sourceParent.removeChild(sourcePos);
                provisionalRoot = sourceParent;
            }
        } else {
            // Standardowe odcięcie wewnętrzne
            Node otherSourceChild = findOtherChild(source, sourceParent);
            Node sourceParent2 = sourceParent.getParent();
            int sourceParentPos = findChildPos(sourceParent, sourceParent2);

            sourceParent2.setChild(sourceParentPos, otherSourceChild);
            otherSourceChild.setParent(sourceParent2);
        }

        // 2. PRZEKORZENIENIE ODCIĘTEGO PODDRZEWA
        Node newSubtreeRoot = rerootDetachedSubtree(source, reroot);

        // 3. WPIĘCIE PODDRZEWA W NOWE MIEJSCE
        Node newNode = new SimpleNode();
        Node targetParent = target.getParent();

        if (target == provisionalRoot) {
            newNode.setParent(null);
            resultTree.setRoot(newNode);
        } else {
            int targetPos = findChildPos(target, targetParent);
            targetParent.setChild(targetPos, newNode);
            newNode.setParent(targetParent);
            resultTree.setRoot(provisionalRoot);
        }

        newNode.addChild(target); target.setParent(newNode);
        newNode.addChild(newSubtreeRoot); newSubtreeRoot.setParent(newNode);

        // 4. TWARDE PRZEINDEKSOWANIE - chroni przed pętlami w ClusterDist
        if (resultTree instanceof pal.tree.SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
            ((pal.tree.SimpleTree) resultTree).createNodeList();
        }

        return resultTree;
    }

    protected Node rerootDetachedSubtree(Node oldRoot, Node newRootEdgeChild) {
        if (oldRoot == newRootEdgeChild) return oldRoot;

        Node newRoot = new SimpleNode();
        Node curr = newRootEdgeChild.getParent();

        newRoot.addChild(newRootEdgeChild);
        newRootEdgeChild.setParent(newRoot);

        Node childComingFrom = newRootEdgeChild;
        Node parentForCurr = newRoot;

        while (curr != null) {
            Node nextParent = curr.getParent();
            Node sibling = findOtherChild(childComingFrom, curr);

            if (curr == oldRoot) {
                if (sibling != null) {
                    parentForCurr.addChild(sibling);
                    sibling.setParent(parentForCurr);
                }
                break;
            } else {
                parentForCurr.addChild(curr);
                curr.setParent(parentForCurr);

                int childCount = curr.getChildCount();
                for (int k = childCount - 1; k >= 0; k--) {
                    curr.removeChild(k);
                }

                if (sibling != null) {
                    curr.addChild(sibling);
                    sibling.setParent(curr);
                }
            }

            childComingFrom = curr;
            parentForCurr = curr;
            curr = nextParent;
        }
        return newRoot;
    }

    protected Node findNodeEquivalent(Tree newTree, Node oldNode) {
        if (oldNode.isLeaf()) return newTree.getExternalNode(oldNode.getNumber());
        return newTree.getInternalNode(oldNode.getNumber());
    }

    public List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    public void collectNodes(Node node, List<Node> list) {
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), list);
        }
    }

    public List<Node> getSubtreeNodes(Node root) {
        List<Node> list = new ArrayList<>();
        collectNodes(root, list);
        return list;
    }

    // ==========================================
    // STARE METODY DLA SPR I INNE UTILITIES
    // (Zachowane bez zmian, aby stare testy SPR działały idealnie)
    // ==========================================

    public TreeValuePair findBestNeighbour(Tree tree, BestTreeChooser btc, double neighSizeFrac, double inputTreeValue) throws TreeCmpException {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        int neighSize = calcSprNeighbours(tree);
        int estimatedMax = (extNum+intNum)*(extNum+intNum);
        int analyzedTreeNum = 0;
        double frac;

        Node s,t;
        Tree resultTree,  bestTree = null;
        double bestValue = Double.MAX_VALUE;
        double resultValue = Double.MAX_VALUE;

        for (int i=0; i<extNum; i++){
            s = tree.getExternalNode(i);
            for (int j=0; j<extNum; j++){
                t = tree.getExternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    analyzedTreeNum++;
                    resultValue = btc.getValueForTree(resultTree);
                    if (resultValue < bestValue){
                        bestTree = resultTree;
                        bestValue = resultValue;
                    }
                    frac = (double)analyzedTreeNum/(double)estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue){
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                    }
                }
            }
        }

        for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot()) continue;
            for (int j=0; j<extNum; j++){
                t = tree.getExternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    analyzedTreeNum++;
                    resultValue = btc.getValueForTree(resultTree);
                    if (resultValue < bestValue){
                        bestTree = resultTree;
                        bestValue = resultValue;
                    }
                    frac = (double)analyzedTreeNum/(double)estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue){
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                    }
                }
            }
        }

        for (int i=0; i<extNum; i++){
            s = tree.getExternalNode(i);
            for (int j=0; j<intNum; j++){
                t = tree.getInternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    analyzedTreeNum++;
                    resultValue = btc.getValueForTree(resultTree);
                    if (resultValue < bestValue){
                        bestTree = resultTree;
                        bestValue = resultValue;
                    }
                    frac = (double)analyzedTreeNum/(double)estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue){
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                    }
                }
            }
        }

        for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot()) continue;
            for (int j=0; j<intNum; j++){
                t = tree.getInternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    if (resultTree != null){
                        analyzedTreeNum++;
                        resultValue = btc.getValueForTree(resultTree);
                        if (resultValue < bestValue && inputTreeValue > bestValue){
                            bestTree = resultTree;
                            bestValue = resultValue;
                        }
                        frac = (double)analyzedTreeNum/(double)estimatedMax;
                        if (frac > neighSizeFrac){
                            TreeValuePair tvPair = new TreeValuePair();
                            tvPair.setTree(bestTree);
                            tvPair.setValue(bestValue);
                            return tvPair;
                        }
                    }
                }
            }
        }

        TreeValuePair tvPair = new TreeValuePair();
        tvPair.setTree(bestTree);
        tvPair.setValue(bestValue);
        return tvPair;
    }

    public boolean sameParent(Node n1, Node n2){
        boolean n1Root = n1.isRoot();
        boolean n2Root = n2.isRoot();
        if (n1Root && n2Root) return true;
        if (!n1Root && !n2Root){
            Node n1Parent = n1.getParent();
            Node n2Parent = n2.getParent();
            return (n1Parent == n2Parent);
        }
        return false;
    }

    public boolean isChildParent(Node n1, Node n2){
        Node n1Parent = n1.getParent();
        Node n2Parent = n2.getParent();
        return (n2 == n1Parent || n1 == n2Parent);
    }

    public boolean isInnerMove(Node s, Node t){
        Node lca = NodeUtils.getFirstCommonAncestor(s, t);
        return lca == s;
    }

    public boolean isValidSprMove(Node s, Node t) {
        if (sameParent(s, t)) return false;
        if (isChildParent(s, t)) return false;
        if (isInnerMove(s, t)) return false;
        return true;
    }

    public boolean isValidUsprMove(Node s, Node t) {
        if (sameParent(s, t)) return false;
        if (isChildParent(s, t)) return false;
        if (s.isRoot() || t.isRoot()) return false;
        if (distanceEqual3(s, t) && !isSmalestInNNI(s, t)) return false;
        if (distanceEqual2Inner(s, t) && !isSmalestInNNI(s.getParent(), t)) return false;
        if (distanceEqual2Inner(s, t) && !isSmalestInNNI(findOtherChild(s.getParent(), s), t)) return false;
        return true;
    }

    private boolean distanceEqual3(Node s, Node t) {
        Node sParent = s.getParent();
        Node tParent = t.getParent();
        if (sParent.isRoot() || tParent.isRoot()) return false;
        if(sParent != null) {
            for (int i = 0; i < sParent.getChildCount(); i++) {
                if (sParent.getChild(i) == tParent) return true;
            }
        }
        if(tParent != null) {
            for (int i = 0; i < tParent.getChildCount(); i++) {
                if (tParent.getChild(i) == sParent) return true;
            }
        }
        return false;
    }

    private boolean distanceEqual2Inner(Node s, Node t) {
        if (!s.isLeaf()) {
            for (int i = 0; i < s.getChildCount(); i++) {
                Node child = s.getChild(i);
                for (int j = 0; j < child.getChildCount(); j++) {
                    if (child.getChild(j) == t) return true;
                }
            }
        }
        return false;
    }

    private boolean isSmaler(Node s, Node t) {
        if (s == null) return false;
        if (s.isLeaf()) {
            if (t.isLeaf()) return s.getNumber() < t.getNumber();
            else return false;
        } else {
            if (t.isLeaf()) return true;
            else return s.getNumber() < t.getNumber();
        }
    }

    private boolean isSmalestInNNI(Node s, Node t) {
        if(isSmaler(t, s)) return false;
        Node sBrother = findOtherChild(s.getParent(), s);
        if(isSmaler(sBrother, s)) return false;
        Node tBrother = findOtherChild(t.getParent(), t);
        if(isSmaler(tBrother, s)) return false;
        return true;
    }

    public int getNodeDepth(Node node){
        int depth=0;
        if (node.isRoot()) return 0;
        while(!node.isRoot()){
            depth++;
            node=node.getParent();
        }
        return depth;
    }

    public int calcSprNeighbours(Tree baseTree){
        int n= baseTree.getExternalNodeCount();
        int intNum = baseTree.getInternalNodeCount();
        Node node;
        int gammaTemp, gammaSum = 0;
        for (int i = 0; i<intNum; i++){
            node = baseTree.getInternalNode(i);
            if (node.isRoot()) continue;
            gammaTemp = getNodeDepth(node)-1;
            gammaSum += gammaTemp;
        }
        return 2*(n-2)*(2*n - 5) - 2*gammaSum;
    }

    public int calcUsprNeighbours(Tree baseTree){
        int n= baseTree.getExternalNodeCount();
        return  2*(n - 3)*(2*n - 7);
    }

    public Tree createSprTree(Tree baseTree, Node s, Node t){
        Tree resultTree = baseTree.getCopy();
        Node resultRoot = resultTree.getRoot();
        int sourceNum = s.getNumber();
        int targetNum = t.getNumber();

        Node source, target;
        if (s.isLeaf()){
            source = resultTree.getExternalNode(sourceNum);
        }else{
            source = resultTree.getInternalNode(sourceNum);
        }

        if (t.isLeaf()){
            target = resultTree.getExternalNode(targetNum);
        }else{
            target = resultTree.getInternalNode(targetNum);
        }

        Node sourceParent = source.getParent();
        Node targetParent = target.getParent();
        boolean isTargetRoot = target.isRoot();
        boolean isSourceParentRoot = sourceParent.isRoot();

        if (isTargetRoot && isSourceParentRoot) return null;

        Node otherSourceChild = findOtherChild(source,sourceParent);
        Node sourceParent2 = null;
        int sourceParentPos = -1;
        if (!isSourceParentRoot){
            sourceParent2 = sourceParent.getParent();
            sourceParentPos = findChildPos(sourceParent,sourceParent2);
        }

        Node newNode = new SimpleNode();
        if (!isTargetRoot){
            int targetPos = findChildPos(target,targetParent);
            targetParent.setChild(targetPos, newNode);
        }

        if (!isSourceParentRoot){
            sourceParent2.setChild(sourceParentPos, otherSourceChild);
        }
        newNode.addChild(target);
        newNode.addChild(source);

        if (isTargetRoot){
            newNode.setParent(null);
            resultTree.setRoot(newNode);
        } else if (isSourceParentRoot){
            otherSourceChild.setParent(null);
            resultTree.setRoot(otherSourceChild);
        } else{
            resultRoot.setParent(null);
            resultTree.setRoot(resultRoot);
        }
        return resultTree;
    }

    public Tree createUsprTree(Tree baseTree, Node s, Node t){
        Boolean isInnerMove = false;
        if (isInnerMove(s, t)) {
            isInnerMove = true;
            Node tmpS = s;
            s = t;
            t = tmpS;
        }

        Tree resultTree = baseTree.getCopy();
        Node resultRoot = resultTree.getRoot();
        int sourceNum = s.getNumber();
        int targetNum = t.getNumber();

        Node source, target;
        if (s.isLeaf()){
            source = resultTree.getExternalNode(sourceNum);
        }else{
            source = resultTree.getInternalNode(sourceNum);
        }

        if (t.isLeaf()){
            target = resultTree.getExternalNode(targetNum);
        }else{
            target = resultTree.getInternalNode(targetNum);
        }

        Node sourceParent = source.getParent();
        Node targetParent = target.getParent();
        boolean isTargetRoot = target.isRoot();
        boolean isSourceParentRoot = sourceParent.isRoot();

        if (isTargetRoot && isSourceParentRoot) return null;

        Node[] otherSourceChildren = findOtherChildren(source,sourceParent);
        Node sourceParent2 = null;
        int sourceParentPos = -1;
        if (!isSourceParentRoot){
            sourceParent2 = sourceParent.getParent();
            sourceParentPos = findChildPos(sourceParent,sourceParent2);
        }

        Node newNode = new SimpleNode();
        if (!isTargetRoot){
            int targetPos = findChildPos(target,targetParent);
            targetParent.setChild(targetPos, newNode);
        }

        if (!isSourceParentRoot){
            if(isInnerMove) {
                int sourcePos = findChildPos(source, sourceParent);
                sourceParent.removeChild(sourcePos);
            } else {
                for (int i = 0; i < otherSourceChildren.length; i++) {
                    sourceParent2.setChild(sourceParentPos, otherSourceChildren[i]);
                }
            }
        }

        if (isInnerMove) {
            Node child0 =  target.getChild(0);
            Node child1 =  target.getChild(1);
            Node newRoot = null;
            if(child1.isLeaf()) {
                child0.setParent(null);
                child1.setParent(child1);
                child0.addChild(child1);
                newRoot = child0;
            } else {
                child1.setParent(null);
                child0.setParent(child1);
                child1.addChild(child0);
                newRoot = child1;
            }
            Identifier NewRootTidentifier = new Identifier("NewRoot");
            sourceParent.setIdentifier(NewRootTidentifier);
            SimpleTree targetSubtree  = new SimpleTree(newRoot);
            Node newRootInTargetSubtree = TreeUtils.getNodeByName(targetSubtree, NewRootTidentifier.getName());
            targetSubtree.reroot(newRootInTargetSubtree);
            target = targetSubtree.getRoot();
        }

        newNode.addChild(target);
        newNode.addChild(source);

        if (isTargetRoot){
            newNode.setParent(null);
            resultTree.setRoot(newNode);
        } else if (isSourceParentRoot){
            otherSourceChildren[0].setParent(null);
            otherSourceChildren[1].setParent(null);
            if (otherSourceChildren[0].isLeaf()) {
                otherSourceChildren[1].addChild(otherSourceChildren[0]);
                resultTree.setRoot(otherSourceChildren[1]);
            } else {
                otherSourceChildren[0].addChild(otherSourceChildren[1]);
                resultTree.setRoot(otherSourceChildren[0]);
            }
        } else{
            resultRoot.setParent(null);
            resultTree.setRoot(resultRoot);
        }
        return resultTree;
    }

    public int findChildPos(Node child, Node parent){
        int childNum = parent.getChildCount();
        for (int i=0;i<childNum; i++){
            Node ch = parent.getChild(i);
            if (ch == child) return i;
        }
        return -1;
    }

    public Node[] findOtherChildren(Node child1, Node parent){
        int childNum = parent.getChildCount();
        Node[] nodes = new Node[childNum - 1];
        int childInd = 0;
        for (int i=0;i<childNum; i++){
            Node ch = parent.getChild(i);
            if (ch != child1) {
                nodes[childInd] = ch;
                childInd++;
            }
        }
        return nodes;
    }

    public Node findOtherChild(Node child1, Node parent){
        int childNum = parent.getChildCount();
        for (int i=0;i<childNum; i++){
            Node ch = parent.getChild(i);
            if (ch != child1) return ch;
        }
        return null;
    }

    abstract public Tree[] generateNeighbours(Tree tree);
}