package treecmp.util;

import pal.tree.ReadTree;
import pal.tree.Tree;
import pal.io.InputSource;
import pal.tree.TreeParseException;

public class TestTreeFactory {

    public static Tree simpleTreeA() {
        String newick = "((A,B),(C,D));";
        return parseNewick(newick);
    }

    public static Tree simpleTreeB() {
        String newick = "((A,C),(B,D));";
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