package treecmp.heuristics;

import pal.misc.IdGroup;
import pal.tree.Tree;
import treecmp.common.ClusterDist;
import treecmp.metrics.topological.RFMetric;

import java.util.BitSet;

public class TreeUnootedHolder extends TreeHolder {

    public TreeUnootedHolder(Tree t, IdGroup idGroup) {
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
        for (int i = 0; i < bsArray.length; i++) {
            bs = bsArray[i];
            partialHash = bs.hashCode();
            //partialHash=Integer.rotateRight(partialHash, 1);
            totlalHash ^= hash(partialHash);
            totlalHash = Integer.rotateRight(totlalHash, 1);
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

        TreeUnootedHolder ref = (TreeUnootedHolder) obj;
        double dist = RFMetric.getRFDistance(tree, ref.tree);
        if (dist == 0.0) {
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
        } else
            return false;

    }

    public static final int hash(int a) {
        a ^= (a << 13);
        a ^= (a >>> 17);
        a ^= (a << 5);
        return a;
    }

}
