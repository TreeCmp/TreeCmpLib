package treecmp.heuristics;

import pal.misc.IdGroup;
import pal.tree.Tree;

public abstract class TreeHolder {
    public Tree tree;
    public IdGroup idGroup;
    public int hash;


    @Override
    public int hashCode() {
        return hash;
    }

}
