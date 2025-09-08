package treecmp.metrics.topological.util;

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

    public static Tree tenLeavesBinaryUnrootedTree1() {
        String newick = "(6,(((5,(4,7)),((2,(3,9)),8)),10),1);";
        return parseNewick(newick);
    }

    public static Tree tenLeavesBinaryUnrootedTree2() {
        String newick = "((1,8),(4,10),(2,(5,(3,((6,7),9)))));";
        return parseNewick(newick);
    }

    public static Tree hundredLeavesBinaryUnrootedTree1() {
        String newick = "(39,((20,((((36,(((((5,(9,57)),(16,(7,92))),(((14,(19,60)),(25,37)),88)),(63,((2,30),65))),93)),(42,((52,((10,(13,32)),59)),(61,97)))),94),100)),79),((1,(38,66)),((62,((((28,(((41,(((15,69),(((6,(64,((11,(((31,82),((58,(((49,70),((45,(55,85)),80)),73)),90)),56)),76))),(((34,((12,95),98)),48),(78,99))),89)),83)),(54,((18,(26,87)),67))),((40,44),(43,86)))),((27,((22,(((17,(33,(4,(35,96)))),(8,81)),(23,68))),(29,46))),53)),(21,(24,(((51,((47,72),77)),75),((3,74),84))))),71)),(50,91))));";
        return parseNewick(newick);
    }

    public static Tree hundredLeavesBinaryUnrootedTree2() {
        String newick = "(((1,(13,((17,(24,93)),(34,88)))),((14,(86,((42,(47,99)),97))),79)),85,(3,((23,(((((37,((((11,((7,(10,63)),65)),(((((8,27),(36,((35,70),92))),(((53,61),(89,((19,41),91))),72)),(38,((57,(25,59)),90))),74)),(39,(30,44))),55)),45),((((66,((9,(40,52)),95)),((((51,((48,73),80)),(58,((49,((56,81),(((((((6,(((26,29),54),((15,68),96))),20),((12,(((18,78),(2,94)),((28,43),((22,(16,76)),62)))),((((32,46),((31,98),100)),33),69))),21),(4,75)),5),83))),84))),71),77)),60),87)),50),82)),(64,67))));";
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