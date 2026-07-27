package treecmp.util;

import pal.distance.DistanceMatrix;
import pal.misc.IdGroup;
import pal.misc.SimpleIdGroup;
import pal.tree.ReadTree;
import pal.tree.Tree;
import pal.tree.TreeParseException;
import pal.tree.TreeTool;
import treecmp.common.TreeCmpUtils;

import java.util.HashMap;
import java.util.Map;

public class TestTreeFactory {

    // Cache, który przechowuje IdGroup dla danego rozmiaru
    private static final Map<Integer, IdGroup> idGroupCache = new HashMap<>();

    private static IdGroup getOrCreateIdGroup(int size) {
        if (!idGroupCache.containsKey(size)) {
            String[] names = new String[size];
            for (int i = 0; i < size; i++) names[i] = "L" + (i + 1);
            idGroupCache.put(size, new SimpleIdGroup(names));
        }
        return idGroupCache.get(size);
    }

    public static Tree randomRootedBinaryTree(int numLeaves, long seed) {
        IdGroup sharedIdGroup = getOrCreateIdGroup(numLeaves);

        // 1. Generujemy macierz dystansów
        double[][] matrix = new double[numLeaves][numLeaves];
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < numLeaves; i++) {
            for (int j = i + 1; j < numLeaves; j++) {
                matrix[i][j] = matrix[j][i] = rng.nextDouble() * 10.0;
            }
        }

        // 2. Tworzymy bazowe drzewo NJ (PAL stworzy tu trifurkację w korzeniu)
        DistanceMatrix dm = new DistanceMatrix(matrix, sharedIdGroup);
        Tree njTree = TreeTool.createNeighbourJoiningTree(dm);

        // 3. NAPRAWA: Przekształcamy trifurkację w bifurkację (korzeń binarny)
        // Metoda ta wstawia nowy węzeł korzenia, który ma dokładnie 2 dzieci.
        Tree binaryRootedTree = TreeTool.getMidPointRooted(njTree);

        // 4. Weryfikacja (opcjonalna, dla pewności)
        if (binaryRootedTree.getRoot().getChildCount() != 2) {
            throw new RuntimeException("Błąd: Wygenerowane drzewo nie jest binarne w korzeniu!");
        }

        return binaryRootedTree;
    }

    public static Tree randomUnrootedBinaryTree(int numLeaves, long seed) {
        // 1. Najpierw tworzymy drzewo UKORZENIONE (tak jak poprzednio)
        String[] names = new String[numLeaves];
        for (int i = 0; i < numLeaves; i++) names[i] = "L" + (i + 1);

        double[][] matrix = new double[numLeaves][numLeaves];
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < numLeaves; i++) {
            for (int j = i + 1; j < numLeaves; j++) {
                matrix[i][j] = matrix[j][i] = rng.nextDouble() * 10.0;
            }
        }

        // 2. Tworzymy bazowe drzewo algorytmem NJ
        Tree rooted = TreeTool.createNeighbourJoiningTree(matrix, names);

        // 3. KLUCZOWE: Używamy TreeTool, aby usunąć korzeń.
        // To przekształca strukturę bifurkacyjną korzenia w trifurkację (split).
        return TreeTool.getUnrooted(rooted);
    }

    private static String[] getNamesFromIdGroup(IdGroup idGroup) {
        String[] names = new String[idGroup.getIdCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = idGroup.getIdentifier(i).getName();
        }
        return names;
    }

    // ==========================================
    // TESTOWE DRZEWA NIEUKORZENIONE (Unrooted)
    // ==========================================
    public static Tree fourLeavesUnrootedStarTree() { return parseNewick("((1,2),3,4);", true); }
    public static Tree fiveLeavesUnrootedCaterpillarTree() { return parseNewick("(((1,2),3),4,5);", true); }
    public static Tree sixLeavesUnrootedBalancedTree() { return parseNewick("(((1,2),(3,4)),5,6);", true); }
    public static Tree sixLeavesUnrootedTree1() {
        String newick = "(((1,2),(3,4)),(5,6));";
        return parseNewick(newick, true);
    }
    public static Tree sixLeavesUnrootedTree2() {
        String newick = "(((1,6),(2,5)),(3,4));";
        return parseNewick(newick, true);
    }
    public static Tree sixLeavesUnrootedCaterpillarTree() { return parseNewick("((((1,2),3),4),5,6);", true); }
    public static Tree sixLeavesUnrootedTargetTree() { return parseNewick("(((1,(3,4)),2),5,6);", true); }
    public static Tree eightLeavesUnrootedBalancedTree() { return parseNewick("((1,2),(3,4),((5,6),(7,8)));", true); }
    public static Tree eightLeavesUnrootedCaterpillarTree() { return parseNewick("((((((1,2),3),4),5),6),7,8);", true); }
    public static Tree tenLeavesUnrootedBalancedTree() { return parseNewick("(((1,2),(3,4)),(5,6),((7,8),9,10));", true); }
    public static Tree tenLeavesUnrootedCaterpillarTree() { return parseNewick("((((((((1,2),3),4),5),6),7),8),9,10);", true); }
    public static Tree tenLeavesUnrootedTree1() {
        String newick = "(((1,2),3),((4,5),6),(7,(8,(9,10))));";
        return parseNewick(newick, true);
    }
    public static Tree tenLeavesUnrootedTree2() {
        String newick = "(((1,3),2),((4,6),5),(7,(9,(8,10))));";
        return parseNewick(newick, true);
    }
    public static Tree fifteenLeavesUnrootedComplexTree() { return parseNewick("((((1,2),3),(4,5)),((6,7),(8,9)),(10,((11,12),(13,(14,15)))));", true); }

    // ==========================================
    // TESTOWE DRZEWA UKORZENIONE (Rooted)
    // ==========================================
    public static Tree fourLeavesRootedCaterpillarTree() { return parseNewick("(((1,2),3),4);"); }
    public static Tree fiveLeavesRootedBalancedTree() { return parseNewick("(((1,2),(3,4)),5);"); }
    public static Tree fiveLeavesRootedCaterpillarTree() { return parseNewick("((((1,2),3),4),5);"); }
    public static Tree fiveLeavesRootedTree1() {
        return parseNewick("(((1,2),3),(4,5));");
    }
    public static Tree sixLeavesRootedBalancedTree() { return parseNewick("(((1,2),(3,4)),(5,6));"); }
    public static Tree sixLeavesRootedCaterpillarTree() { return parseNewick("(((((1,2),3),4),5),6);"); }
    public static Tree sixLeavesRootedTargetTree1() {
        return parseNewick("(((1,(2,(3,4))),5),6);");
    }
    public static Tree eightLeavesRootedBalancedTree() { return parseNewick("(((1,2),(3,4)),((5,6),(7,8)));"); }
    public static Tree eightLeavesRootedCaterpillarTree() { return parseNewick("(((((((1,2),3),4),5),6),7),8);"); }
    public static Tree tenLeavesRootedBalancedTree() { return parseNewick("((((1,2),(3,4)),(5,6)),(((7,8),9),10));"); }
    public static Tree tenLeavesRootedCaterpillarTree() { return parseNewick("(((((((((1,2),3),4),5),6),7),8),9),10);"); }
    public static Tree tenLeavesRootedTree1() {
        return parseNewick("((((((1,2),3),4),5),6),(((7,8),9),10));");
    }
    public static Tree tenLeavesRootedTree2() {
        return parseNewick("((((((1,3),2),4),5),6),(((7,8),9),10));");
    }
    public static Tree fifteenLeavesRootedComplexTree() { return parseNewick("(((((1,2),3),(4,5)),((6,7),(8,9))),((10,11),((12,13),(14,15))));"); }
    public static Tree fourLeavesUnrootedTargetTree() {
        // Drzewo oddalone o dokładnie 1 krok NNI od powyższego
        return parseNewick("((1,3),2,4);");
    }

    public static Tree fiveLeavesTargetTree() {
        return parseNewick("((((1,3),2),4),5);");
    }

    public static Tree fourLeavesBalancedTree1() {
        return parseNewick("((1,2),(3,4));");
    }

    public static Tree fourLeavesBalancedTree2() {
        return parseNewick("((1,3),(2,4));");
    }

    public static Tree fourLeavesCaterpillarTree1() {
        return parseNewick("(((1,2),3),4);");
    }

    public static Tree fourLeavesRootedTree1() {
        String newick = "((A,B),(C,D));";
        return parseNewick(newick);
    }

    public static Tree fourLeavesRootedTree2() {
        String newick = "((A,C),(B,D));";
        return parseNewick(newick);
    }

    public static Tree fourLeavesUnrootedTree1() {
        String newick = "((A,B),(C,D));";
        return parseNewick(newick, true);
    }

    public static Tree fourLeavesUnrootedTree2() {
        String newick = "((A,C),(B,D));";
        return parseNewick(newick, true);
    }

    public static Tree fourLeavesRootedWeightedTree1() {
        String newick = "((2:82,3:91):95,(1:2,4:9):58):45;";
        return parseNewick(newick);
    }

    public static Tree fourLeavesRootedWeightedTree2() {
        String newick = "(2:83,(1:7,(3:33,4:29):12):60):93;";
        return parseNewick(newick);
    }

    public static Tree fourLeavesUnrootedWeightedTree1() {
        String newick = "(3:17,(1:10,4:29):66,2:89):77;";
        return parseNewick(newick);
    }

    public static Tree fourLeavesUnrootedWeightedTree2() {
        String newick = "(3:16,(2:73,4:21):23,1:41):50;";
        return parseNewick(newick);
    }

    public static Tree fourLeavesZeroWeightedTree1() {
        String newick = "((2:0,3:0):0,(1:0,4:0):0):0;";
        return parseNewick(newick);
    }

    public static Tree fourLeavesZeroWeightedTree2() {
        String newick = "((2:0,3:0):0,(1:0,4:0):0):0;";
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
        return parseNewick(newick, true);
    }

    public static Tree tenLeavesBinaryUnrootedTree2() {
        String newick = "((1,8),(4,10),(2,(5,(3,((6,7),9)))));";
        return parseNewick(newick, true);
    }

    public static Tree tenLeavesWeightedBinaryRootedTree1() {
        String newick = "((((3:84,(2:87,4:21):94):46,8:8):50,(6:92,10:20):29):86,(7:80,((1:67,5:93):64,9:93):21):73):67;";
        return parseNewick(newick);
    }

    public static Tree tenLeavesWeightedBinaryRootedTree2() {
        String newick = "(1:49,((2:15,(((3:28,4:77):24,6:76):93,7:67):97):32,((8:86,(5:63,9:31):93):94,10:29):40):69):85;";
        return parseNewick(newick);
    }

    public static Tree tenLeavesWeightedBinaryUnrootedTree1() {
        String newick = "(5:63,((((1:19,4:24):27,(3:53,(7:91,10:8):83):43):70,(6:1,9:10):21):55,8:56):19,2:59):27;";
        return parseNewick(newick, true);
    }

    public static Tree tenLeavesWeightedBinaryUnrootedTree2() {
        String newick = "((((4:1,(6:34,7:36):92):37,((3:38,(1:23,10:48):42):62,9:58):44):48,5:46):41,8:46,2:61):96;";
        return parseNewick(newick, true);
    }


    public static Tree hundredLeavesBinaryUnrootedTree1() {
        String newick = "(39,((20,((((36,(((((5,(9,57)),(16,(7,92))),(((14,(19,60)),(25,37)),88)),(63,((2,30),65))),93)),(42,((52,((10,(13,32)),59)),(61,97)))),94),100)),79),((1,(38,66)),((62,((((28,(((41,(((15,69),(((6,(64,((11,(((31,82),((58,(((49,70),((45,(55,85)),80)),73)),90)),56)),76))),(((34,((12,95),98)),48),(78,99))),89)),83)),(54,((18,(26,87)),67))),((40,44),(43,86)))),((27,((22,(((17,(33,(4,(35,96)))),(8,81)),(23,68))),(29,46))),53)),(21,(24,(((51,((47,72),77)),75),((3,74),84))))),71)),(50,91))));";
        return parseNewick(newick, true);
    }

    public static Tree hundredLeavesBinaryUnrootedTree2() {
        String newick = "(((1,(13,((17,(24,93)),(34,88)))),((14,(86,((42,(47,99)),97))),79)),85,(3,((23,(((((37,((((11,((7,(10,63)),65)),(((((8,27),(36,((35,70),92))),(((53,61),(89,((19,41),91))),72)),(38,((57,(25,59)),90))),74)),(39,(30,44))),55)),45),((((66,((9,(40,52)),95)),((((51,((48,73),80)),(58,((49,((56,81),(((((((6,(((26,29),54),((15,68),96))),20),((12,(((18,78),(2,94)),((28,43),((22,(16,76)),62)))),((((32,46),((31,98),100)),33),69))),21),(4,75)),5),83))),84))),71),77)),60),87)),50),82)),(64,67))));";
        return parseNewick(newick, true);
    }

    private static Tree parseNewick(String newick) {
        return parseNewick(newick, false);
    }

    // 2. Główna metoda z flagą unrootIfNeeded
    private static Tree parseNewick(String newick, boolean unrootIfNeeded) {
        pal.io.InputSource in1 = pal.io.InputSource.openString(newick);
        try {
            Tree tree = new ReadTree(in1);
            if (unrootIfNeeded) {
                // Konwertuje widmowy korzeń na poprawną strukturę nieukorzenioną
                return TreeCmpUtils.unrootTreeIfNeeded(tree);
            }
            // Zwraca drzewo z oryginalnym korzeniem (nawet jeśli to korzeń stopnia 2)
            return tree;
        } catch (TreeParseException e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================
    // DODATKOWE DRZEWA DLA TESTÓW PAR (Rooted)
    // ==========================================
    public static Tree fiveLeavesRootedTarget1() { return parseNewick("((((1,2),4),3),5);"); }
    public static Tree fiveLeavesRootedTarget2() { return parseNewick("((((1,3),2),4),5);"); }
    public static Tree fiveLeavesRootedTarget3() { return parseNewick("((1,4),((2,3),5));"); }

    public static Tree sixLeavesRootedTree1() { return parseNewick("((((1,2),3),4),(5,6));"); }
    public static Tree sixLeavesRootedTarget1() { return parseNewick("(((1,2),3),(4,(5,6)));"); }
    public static Tree sixLeavesRootedTarget2() { return parseNewick("(((1,3),(2,4)),(5,6));"); }
    public static Tree sixLeavesRootedTarget3() { return parseNewick("(((1,2),(5,6)),(3,4));"); }
    public static Tree sixLeavesRootedTarget4() { return parseNewick("(((1,6),(2,4)),(3,5));"); }

    public static Tree tenLeavesRootedTree3() { return parseNewick("(((1,2),(3,4)),((5,6),((7,8),(9,10))));"); }
    public static Tree tenLeavesRootedTarget3() { return parseNewick("(((1,4),(2,3)),((5,6),((7,8),(9,10))));"); }

    public static Tree twelveLeavesRootedTree1() { return parseNewick("(((((((1,2),3),4),5),6),7),((((8,9),10),11),12));"); }
    public static Tree twelveLeavesRootedTarget1() { return parseNewick("(((((((1,3),2),4),5),6),7),((((8,9),10),11),12));"); }

    public static Tree fifteenLeavesRootedTree1() { return parseNewick("((((1,2),(3,4)),((5,6),(7,8))),((((9,10),(11,12)),13),(14,15)));"); }
    public static Tree fifteenLeavesRootedTarget1() { return parseNewick("((((1,4),(2,3)),((5,6),(7,8))),((((9,10),(11,12)),13),(14,15)));"); }

    public static Tree twentyLeavesRootedTree1() { return parseNewick("(((((((((1,2),3),4),5),6),7),8),9),((((((((10,11),12),13),14),15),16),17),((18,19),20)));"); }
    public static Tree twentyLeavesRootedTarget1() { return parseNewick("(((((((((1,3),2),4),5),6),7),8),9),((((((((10,11),12),13),14),15),16),17),((18,19),20)));"); }

    public static Tree fiveLeavesUnrooted0Based() {
        return parseNewick("(((0,1),2),3,4);");
    }

    public static Tree sixLeavesUnrooted0BasedBaseTree() {
        return parseNewick("(0,1,(2,(3,(4,5))));");
    }

    public static Tree sixLeavesUnrooted1BasedBaseTree() {
        return parseNewick("(1,2,(3,(4,(5,6))));");
    }

    public static Tree sevenLeavesUnrooted0Based() {
        return parseNewick("((0,((1,2),(3,4))),5,6);");
    }

    public static Tree eightLeavesUnrootedComplex1() {
        return parseNewick("((4,(((2,5),(3,6)),7)),1,8);");
    }

    public static Tree twelveLeavesUnrootedZeroLengths() {
        return parseNewick("(0:0.0000000,(((2:0.0000000,3:0.0000000):0.0000000,((5:0.0000000,6:0.0000000):0.0000000,(1:0.0000000,((7:0.0000000,8:0.0000000):0.0000000,(9:0.0000000,10:0.0000000):0.0000000):0.0000000):0.0000000):0.0000000):0.0000000,4:0.0000000):0.0000000,11:0.0000000);");
    }
}