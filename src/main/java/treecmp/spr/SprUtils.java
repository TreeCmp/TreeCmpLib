/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.spr;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import pal.io.InputSource;
import pal.misc.IdGroup;
import pal.misc.Identifier;
import pal.tree.*;
import treecmp.common.ClusterDist;
import treecmp.common.TreeCmpException;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.RFMetric;

/**
 *
 * @author Damian
 */
public class SprUtils {

public static int num = 0;

    public static Tree[] generateRSprNeighbours(Tree tree){

        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int neighSize = calcSprNeighbours(tree);
        Set<TreeHolder> sprTreeSet = new HashSet<TreeHolder>((4*neighSize)/3);
        // System.out.println("Neigh siez="+neighSize);
        Node s,t;
        Tree resultTree;
        //leaf to leaf
        for (int i=0; i<extNum; i++){
            s = tree.getExternalNode(i);
            for (int j=0; j<extNum; j++){
                t = tree.getExternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    sprTreeSet.add(new TreeRootedHolder(resultTree,idGroup));
                    // System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                }
            }
        }
        //non-leaf and non-root to leaf
        for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot())
                continue;
            for (int j=0; j<extNum; j++){
                t = tree.getExternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    sprTreeSet.add(new TreeRootedHolder(resultTree,idGroup));
                    //System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                }
            }
        }
        //leaf - non-leaf
        for (int i=0; i<extNum; i++){
            s = tree.getExternalNode(i);
            for (int j=0; j<intNum; j++){
                t = tree.getInternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    sprTreeSet.add(new TreeRootedHolder(resultTree,idGroup));
                    //System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                }
            }
        }

        //non-leaf, non-root to non-leaf

        for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot())
                continue;
            for (int j=0; j<intNum; j++){
                t = tree.getInternalNode(j);
                if (isValidSprMove(s,t)){
                    resultTree = createSprTree(tree,s,t);
                    if (resultTree != null){
                        sprTreeSet.add(new TreeRootedHolder(resultTree,idGroup));
                        // System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                    }
                }
            }
        }

        int n = sprTreeSet.size();
        Tree [] sprTreeArray = new Tree[n];
        int i=0;
        for (TreeHolder th: sprTreeSet ){
            sprTreeArray[i] = th.tree;
            i++;
        }
        return sprTreeArray;
    }

    public static Tree getTreeFromString(String treeStr) {
        Tree tree = null;
        InputSource is = InputSource.openString(treeStr);
        try {
            tree = new ReadTree(is);
            is.close();
        } catch (TreeParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tree;
    }

    public static Tree[] generateUSprNeighbours(Tree tree) throws TreeCmpException {

        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int neighSize = calcUsprNeighbours(tree);
        Set<TreeUnootedHolder> usprTreeSet = new HashSet<TreeUnootedHolder>((4*neighSize)/3);
        // System.out.println("Neigh siez="+neighSize);
        Node s,t;
        Tree resultTree;
        //leaf to leaf
        for (int i=0; i<extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j=0; j<extNum; j++){
                t = tree.getExternalNode(j);
                if (isValidUsprMove(s,t)) {
                    resultTree = createUsprTree(tree,s,t);
                    usprTreeSet.add(new TreeUnootedHolder(resultTree,idGroup));
                    // System.out.println("neigbours/neighsize = "+usprTreeSet.size() +"/" +neighSize);
                }
            }
        }
        //non-leaf and non-root to leaf
        for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot())
                continue;
            for (int j=0; j<extNum; j++){
                t = tree.getExternalNode(j);
                if (isValidUsprMove(s,t)) {
                    resultTree = createUsprTree(tree,s,t);
                    try {
                        usprTreeSet.add(new TreeUnootedHolder(resultTree, idGroup));
                    }
                    catch (Exception e) {
                        int lalal = 9;
                    }
                    // System.out.println("neigbours/neighsize = "+usprTreeSet.size() +"/" +neighSize);
                }
            }
        }

        //leaf to non-leaf
        for (int i=0; i<extNum; i++){
            s = tree.getExternalNode(i);
            for (int j=0; j<intNum; j++){
                t = tree.getInternalNode(j);
                if (isValidUsprMove(s,t)) {
                    resultTree = createUsprTree(tree,s,t);
                    usprTreeSet.add(new TreeUnootedHolder(resultTree,idGroup));
                    // System.out.println("neigbours/neighsize = "+usprTreeSet.size() +"/" +neighSize);
                }
            }
        }

        //non-leaf and non-root to non-leaf
        for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot())
                continue;
            for (int j=0; j<intNum; j++){
                t = tree.getInternalNode(j);
                if (isValidUsprMove(s,t)) {
                    resultTree = createUsprTree(tree,s,t);
                    usprTreeSet.add(new TreeUnootedHolder(resultTree,idGroup));
                    // System.out.println("neigbours/neighsize = "+usprTreeSet.size() +"/" +neighSize);
                }
            }
        }
        int n = usprTreeSet.size();
        if (n != neighSize) {
            throw new TreeCmpException("Bad number of neighbors generated");
        }
        Tree [] usprTreeArray = new Tree[n];
        int i=0;
        for (TreeUnootedHolder th: usprTreeSet ){
            usprTreeArray[i] = th.tree;
            i++;
        }
        return usprTreeArray;
    }


    public static TreeValuePair findBestNeighbour(Tree tree, BestTreeChooser btc, double neighSizeFrac, double inputTreeValue) throws TreeCmpException{

        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int neighSize = calcSprNeighbours(tree);
        int estimatedMax = (extNum+intNum)*(extNum+intNum);
        int analyzedTreeNum = 0;
        double frac;

       // System.out.println("Neigh siez="+neighSize);
        Node s,t;
        Tree resultTree,  bestTree = null;
        double bestValue = Double.MAX_VALUE;
        double resultValue = Double.MAX_VALUE;
        //leaf to leaf
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
                    printProgress(analyzedTreeNum, neighSize, estimatedMax, bestValue);
                   frac = (double)analyzedTreeNum/(double)estimatedMax;
                   if (frac > neighSizeFrac && inputTreeValue > bestValue){
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                   }

                    // System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                }
            }
        }
        //non-leaf and non-root to leaf
         for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot())
                continue;
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
                    printProgress(analyzedTreeNum, neighSize, estimatedMax, bestValue);
                    frac = (double)analyzedTreeNum/(double)estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue){
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                   }
                    //System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                }
            }
        }
        //leaf - non-leaf
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
                    printProgress(analyzedTreeNum, neighSize, estimatedMax, bestValue);
                    frac = (double)analyzedTreeNum/(double)estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue){
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                   }
                     //System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                }
            }
        }

        //non-leaf, non-root to non-leaf

         for (int i=0; i<intNum; i++){
            s = tree.getInternalNode(i);
            if(s.isRoot())
                continue;
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
                        printProgress(analyzedTreeNum, neighSize, estimatedMax, bestValue);
                        frac = (double)analyzedTreeNum/(double)estimatedMax;
                        if (frac > neighSizeFrac){
                            TreeValuePair tvPair = new TreeValuePair();
                            tvPair.setTree(bestTree);
                            tvPair.setValue(bestValue);
                            return tvPair;
                        }
                       // System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                    }
                }
            }
        }

        TreeValuePair tvPair = new TreeValuePair();
        tvPair.setTree(bestTree);
        tvPair.setValue(bestValue);
        return tvPair;

    }

    private static void printProgress(int stepNum, int max, int estimatedMax,  double bestVale){
        if (stepNum % 100 == 0){
            System.out.println(String.format("Step: %d, estimatedMax: %d, max: %d, best value: %f",stepNum, estimatedMax, max, bestVale));
        }
    }

    public static boolean sameParent(Node n1, Node n2){
        boolean n1Root = n1.isRoot();
        boolean n2Root = n2.isRoot();

        if (n1Root && n1Root)
            return true;

        if (!n1Root && !n2Root){
            Node n1Parent = n1.getParent();
            Node n2Parent = n2.getParent();
            return (n1Parent == n2Parent);
        }
 
        return false;
    }
    
    public static boolean isChildParent(Node n1, Node n2){
      
        Node n1Parent = n1.getParent();
        Node n2Parent = n2.getParent();
        
        if (n2 == n1Parent || n1 == n2Parent)
            return true;
        
        return false;
    }
    
     public static boolean isInnerMove(Node s, Node t){
      
        Node lca = NodeUtils.getFirstCommonAncestor(s, t);       
        if (lca == s)
            return true;
        return false;
    }

    public static boolean isValidSprMove(Node s, Node t) {
        if (sameParent(s, t)) {
            return false;
        }
        if (isChildParent(s, t)) {
            return false;
        }
        if (isInnerMove(s, t)) {
            return false;
        }
        return true;
    }

    public static boolean isValidUsprMove(Node s, Node t) {
        if (sameParent(s, t)) {
            return false;
        }
        if (isChildParent(s, t)) {
            return false;
        }
        if (s.isRoot() || t.isRoot()) {
            return false;
        }
        if (distanceEqual3(s, t) && !isSmalestInNNI(s, t)) {
            return false;
        }
        if (distanceEqual2Inner(s, t) && !isSmalestInNNI(s.getParent(), t)) {
            return false;
        }
        if (distanceEqual2Inner(s, t) && !isSmalestInNNI(findOtherChild(s.getParent(), s), t)) {
            return false;
        }
        return true;
    }

    private static boolean distanceEqual3(Node s, Node t) {
        Node sParent = s.getParent();
        Node tParent = t.getParent();
        if (sParent.isRoot() || tParent.isRoot()) {
            return false;
        }
        if(sParent != null) {
            for (int i = 0; i < sParent.getChildCount(); i++) {
                if (sParent.getChild(i) == tParent) {
                    return true;
                }
            }
        }
        if(tParent != null) {
            for (int i = 0; i < tParent.getChildCount(); i++) {
                if (tParent.getChild(i) == sParent) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean distanceEqual2Inner(Node s, Node t) {
        if (!s.isLeaf()) {
            for (int i = 0; i < s.getChildCount(); i++) {
                Node child = s.getChild(i);
                for (int j = 0; j < child.getChildCount(); j++) {
                    if (child.getChild(j) == t) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSmaler(Node s, Node t) {
        if (s == null) {
            return false;
        }
        if (s.isLeaf()) {
            if (t.isLeaf()) {
                return s.getNumber() < t.getNumber();
            }
            else {
                return false;
                }
        }
        else {
            if (t.isLeaf()) {
                return true;
            }
            else {
                return s.getNumber() < t.getNumber();
            }
        }
    }

    private static boolean isSmalestInNNI(Node s, Node t) {
        if(isSmaler(t, s)) {
            return false;
        }
        Node sBrother = findOtherChild(s.getParent(), s);
        if(isSmaler(sBrother, s)) {
            return false;
        }
        Node tBrother = findOtherChild(t.getParent(), t);
        if(isSmaler(tBrother, s)) {
            return false;
        }
        return true;
    }

    public static int getNodeDepth(Node node){
        int depth=0;

        if (node.isRoot())
            return 0;

        while(!node.isRoot()){
            depth++;
            node=node.getParent();
        }

        return depth;
    }

    public static int calcSprNeighbours(Tree baseTree){
        int n= baseTree.getExternalNodeCount();
        int intNum = baseTree.getInternalNodeCount();
        Node node;
        int gammaTemp, gammaSum = 0;
        for (int i = 0; i<intNum; i++){
            node = baseTree.getInternalNode(i);
            if (node.isRoot())
                continue;
            gammaTemp = getNodeDepth(node)-1;
            gammaSum += gammaTemp;            
        }
        //based on "On the Combinatorics of Rooted Binary Phylogenetic Trees", Yun S. Song
        int neighNum = 2*(n-2)*(2*n - 5) - 2*gammaSum;
        
        return neighNum;
    }

    public static int calcUsprNeighbours(Tree baseTree){
        int n= baseTree.getExternalNodeCount();
        //based on "Subtree Transfer Operations and Their Induced Metrics on Evolutionary Trees", Benjamin L. Allen and Mike Stee
        return  2*(n - 3)*(2*n - 7);
    }

    public static Tree createSprTree(Tree baseTree, Node s, Node t){
       // if (num ==45){
            
         //   int ggg=0;
        //}

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
         
        //it should be the same tree
        if (isTargetRoot && isSourceParentRoot)
            return null;

        Node otherSourceChild = findOtherChild(source,sourceParent);
        Node sourceParent2 = null;
        int sourceParentPos = -1;
        if (!isSourceParentRoot){    
            //remove degree 2 soureceParent vertex
            sourceParent2 = sourceParent.getParent();
            sourceParentPos = findChildPos(sourceParent,sourceParent2);
        }
          
        Node newNode = new SimpleNode();
        if (!isTargetRoot){                    
            //split target edge
            int targetPos = findChildPos(target,targetParent);
            targetParent.setChild(targetPos, newNode);
        }
 
        
        if (!isSourceParentRoot){
            //remove degree 2 soureceParent vertex
            sourceParent2.setChild(sourceParentPos, otherSourceChild);
        }
        newNode.addChild(target);
        newNode.addChild(source);


        SimpleTree newTree;



        if (isTargetRoot){            
            newNode.setParent(null);
            resultTree.setRoot(newNode);
            //newTree = new SimpleTree(newNode);

        } else if (isSourceParentRoot){            
            otherSourceChild.setParent(null);
            resultTree.setRoot(otherSourceChild);
            //newTree = new SimpleTree(otherSourceChild);
        } else{
            resultRoot.setParent(null);
            resultTree.setRoot(resultRoot);
            //newTree = new SimpleTree(resultRoot);

        }
       /* int N = newTree.getInternalNodeCount();
        if (N<4){
            int gg= 0 ;
        }
        newTree.createNodeList();
        //return resultTree;

*/
        /* OutputTarget out = OutputTarget.openString();
         TreeUtils.printNH(newTree,out,false,false);
         out.close();
        String treeString = out.getString();
        System.out.println(treeString + ": " +num);
        num++;*/

        //return newTree;
        return resultTree;
    }

    public static Tree createUsprTree(Tree baseTree, Node s, Node t){
        // if (num ==45){

        //   int ggg=0;
        //}

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

        //it should be the same tree
        if (isTargetRoot && isSourceParentRoot)
            return null;


        //Node otherSourceChild = findOtherChild(source,sourceParent);
        Node[] otherSourceChildren = findOtherChildren(source,sourceParent);
        Node sourceParent2 = null;
        int sourceParentPos = -1;
        if (!isSourceParentRoot){
            //remove degree 2 soureceParent vertex
            sourceParent2 = sourceParent.getParent();
            sourceParentPos = findChildPos(sourceParent,sourceParent2);
        }

        Node newNode = new SimpleNode();
        if (!isTargetRoot){
            //split target edge
            int targetPos = findChildPos(target,targetParent);
            targetParent.setChild(targetPos, newNode);
        }


        // removing target
        if (!isSourceParentRoot){
            //remove degree 2 soureceParent vertex
            //sourceParent2.setChild(sourceParentPos, otherSourceChild);
            if(isInnerMove) {
                int sourcePos = findChildPos(source, sourceParent);
                sourceParent.removeChild(sourcePos);
            }
            else {
                for (int i = 0; i < otherSourceChildren.length; i++) {
                    sourceParent2.setChild(sourceParentPos, otherSourceChildren[i]);
                }
            }
        }

        // if it is inner move, reroot inner subtree
        if (isInnerMove) {
            //removing target node by joining it's children
            Node child0 =  target.getChild(0);
            Node child1 =  target.getChild(1);
            Node newRoot = null;
            if(child1.isLeaf()) {
                child0.setParent(null);
                child1.setParent(child1);
                child0.addChild(child1);
                newRoot = child0;
            }
            else {
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


        SimpleTree newTree;



        if (isTargetRoot){
            newNode.setParent(null);
            resultTree.setRoot(newNode);
            //newTree = new SimpleTree(newNode);

        } else if (isSourceParentRoot){
            //otherSourceChild.setParent(null);
            otherSourceChildren[0].setParent(null);
            otherSourceChildren[1].setParent(null);
            if (otherSourceChildren[0].isLeaf()) {
                otherSourceChildren[1].addChild(otherSourceChildren[0]);
                resultTree.setRoot(otherSourceChildren[1]);
            }
            else {
                otherSourceChildren[0].addChild(otherSourceChildren[1]);
                resultTree.setRoot(otherSourceChildren[0]);
            }
            //newTree = new SimpleTree(otherSourceChild);
        } else{
            resultRoot.setParent(null);
            resultTree.setRoot(resultRoot);
            //newTree = new SimpleTree(resultRoot);

        }
       /* int N = newTree.getInternalNodeCount();
        if (N<4){
            int gg= 0 ;
        }
        newTree.createNodeList();
        //return resultTree;

*/
        /* OutputTarget out = OutputTarget.openString();
         TreeUtils.printNH(newTree,out,false,false);
         out.close();
        String treeString = out.getString();
        System.out.println(treeString + ": " +num);
        num++;*/

        //return newTree;
        return resultTree;
    }

    public static int findChildPos(Node child, Node parent){
        int childNum = parent.getChildCount();

        for (int i=0;i<childNum; i++){
            Node ch = parent.getChild(i);
            if (ch == child)
                return i;
        }

        return -1;
    }

    public static Node[] findOtherChildren(Node child1, Node parent){
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


    public static Node findOtherChild(Node child1, Node parent){
        int childNum = parent.getChildCount();

        for (int i=0;i<childNum; i++){
            Node ch = parent.getChild(i);
            if (ch != child1)
                return ch;
        }

        return null;
    }
}

    abstract class TreeHolder {
    public Tree tree;
    public IdGroup idGroup;
    public int hash;



    @Override
    public int hashCode() {
        return hash;
    }

}

class TreeRootedHolder extends TreeHolder {

    public TreeRootedHolder(Tree t, IdGroup idGroup ){
        this.idGroup = idGroup;
        this.tree = t;
       //  OutputTarget out = OutputTarget.openString();
       //         TreeUtils.printNH(t,out,false,false);
        //        out.close();
         //       System.out.print(out.getString());

        BitSet[] bsArray = ClusterDist.RootedTree2BitSetArray(t, idGroup);
        BitSet bs;
        int totlalHash = 0;
        int partialHash;
        for(int i=0; i<bsArray.length; i++){
            bs = bsArray[i];
            partialHash = bs.hashCode();
            totlalHash ^= partialHash;
        }
        this.hash = totlalHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (obj.getClass() != this.getClass())) {
            return false;
        }

        TreeRootedHolder ref = (TreeRootedHolder)obj;
        double dist = RFClusterMetric.getRFClusterMetric(tree, ref.tree);
        if (dist == 0.0){
       /*     OutputTarget out1 = OutputTarget.openString();
            TreeUtils.printNH(tree,out1,false,false);
            out1.close();
            String treeString1 = out1.getString();

            OutputTarget out2 = OutputTarget.openString();
            TreeUtils.printNH(ref.tree,out2,false,false);
            out2.close();
            String treeString2 = out2.getString();

            System.out.println("drzewa rowne 1: "+treeString1);
            System.out.println("drzewa rowne 2: "+treeString2);
            */
            return true;
        }
        else
            return false;

    }
   
}

class TreeUnootedHolder extends TreeHolder {

    public TreeUnootedHolder(Tree t, IdGroup idGroup ){
        this.idGroup = idGroup;
        this.tree = t;
        //  OutputTarget out = OutputTarget.openString();
        //         TreeUtils.printNH(t,out,false,false);
        //        out.close();
        //       System.out.print(out.getString());

        BitSet[] bsArray = ClusterDist.UnuootedTree2BitSetArray(t, idGroup);
        BitSet bs;
        int totlalHash = 0;
        Integer partialHash;
        for(int i=0; i<bsArray.length; i++){
            bs = bsArray[i];
            partialHash = bs.hashCode();
            //partialHash=Integer.rotateRight(partialHash, 1);
            totlalHash ^= hash(partialHash);
            totlalHash=Integer.rotateRight(totlalHash, 1);
        }
        this.hash = totlalHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (obj.getClass() != this.getClass())) {
            return false;
        }

        TreeUnootedHolder ref = (TreeUnootedHolder)obj;
        double dist = RFMetric.getRFDistance(tree, ref.tree);
        if (dist == 0.0){
       /*     OutputTarget out1 = OutputTarget.openString();
            TreeUtils.printNH(tree,out1,false,false);
            out1.close();
            String treeString1 = out1.getString();

            OutputTarget out2 = OutputTarget.openString();
            TreeUtils.printNH(ref.tree,out2,false,false);
            out2.close();
            String treeString2 = out2.getString();

            System.out.println("drzewa rowne 1: "+treeString1);
            System.out.println("drzewa rowne 2: "+treeString2);
            */
            return true;
        }
        else
            return false;

    }

    public static final int hash(int a) {
        a ^= (a << 13);
        a ^= (a >>> 17);
        a ^= (a << 5);
        return a;
    }

}