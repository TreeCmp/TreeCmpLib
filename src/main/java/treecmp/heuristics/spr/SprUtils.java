/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import pal.io.InputSource;
import pal.misc.IdGroup;
import pal.misc.Identifier;
import pal.tree.*;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;

/**
 *
 * @author Damian
 */
public class SprUtils extends TreeNeighborhoodUtils {

public int num = 0;

    @Override
    public Tree[] generateNeighbours(Tree tree){

        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int neighSize = calcSprNeighbours(tree);
        Set<treecmp.heuristics.TreeHolder> sprTreeSet = new HashSet<treecmp.heuristics.TreeHolder>((4*neighSize)/3);
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
                    sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree,idGroup));
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
                    sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree,idGroup));
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
                    sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree,idGroup));
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
                        sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree,idGroup));
                        // System.out.println("neigbours/neighsize = "+sprTreeSet.size() +"/" +neighSize);
                    }
                }
            }
        }

        int n = sprTreeSet.size();
        Tree [] sprTreeArray = new Tree[n];
        int i=0;
        for (treecmp.heuristics.TreeHolder th: sprTreeSet ){
            sprTreeArray[i] = th.tree;
            i++;
        }
        return sprTreeArray;
    }
}
