package treecmp.heuristics;

import pal.misc.IdGroup;
import pal.tree.Tree;
import treecmp.common.ClusterDist;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

public class TreeUnrootedHolder extends TreeHolder {

    private final Set<BitSet> splits;

    public TreeUnrootedHolder(Tree t, IdGroup idGroup) {
        this.idGroup = idGroup;
        this.tree = t;

        BitSet[] bsArray = ClusterDist.UnuootedTree2BitSetArray(t, idGroup); // Zakładamy, że to działa poprawnie
        this.splits = new HashSet<>();

        int totlalHash = 0;

        for (int i = 0; i < bsArray.length; i++) {
            BitSet bs = bsArray[i];
            if (bs == null) continue;

            this.splits.add(bs);

            int partialHash = bs.hashCode();
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
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        TreeUnrootedHolder that = (TreeUnrootedHolder) obj;

        return this.splits.equals(that.splits);
    }
    public static final int hash(int a) {
        a ^= (a << 13);
        a ^= (a >>> 17);
        a ^= (a << 5);
        return a;
    }

}
