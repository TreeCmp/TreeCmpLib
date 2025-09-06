package treecmp.metrics.util;

import pal.tree.ReadTree;
import pal.tree.Tree;
import pal.io.InputSource;
import pal.tree.TreeParseException;

public class TestTreeFactory {

    public static Tree fourLeavesTree1() {
        String newick = "((A,B),(C,D));";
        return parseNewick(newick);
    }

    public static Tree fourLeavesTree2() {
        String newick = "((A,C),(B,D));";
        return parseNewick(newick);
    }

    public static Tree tenLeavesBinaryRootedTree1() {
        String newick = "(((2,5),(3,6)),(4,((1,(7,8)),(9,10))));";
        return parseNewick(newick);
    }

    public static Tree tenLeavesBinaryRootedTree2() {
        String newick = "(((2,3),7),(((4,6),((1,(5,9)),10)),8));";
        return parseNewick(newick);
    }

    private static Tree parseNewick(String newick) {
            pal.io.InputSource in1 = InputSource.openString(newick);
        try {
            return new ReadTree(in1);
        } catch (TreeParseException e) {
            throw new RuntimeException(e);
        }
    }
}