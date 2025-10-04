/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics;

import pal.misc.IdGroup;
import pal.tree.Tree;
import treecmp.common.ClusterDist;
import treecmp.metrics.topological.RFClusterMetric;

import java.util.BitSet;

public class TreeRootedHolder extends TreeHolder {

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

