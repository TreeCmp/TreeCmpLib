//package treecmp.heuristics.tbr;
//
//import pal.tree.Tree;
//import pal.tree.TreeUtils;
//import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
//import treecmp.heuristics.moves.TreeMove;
//import treecmp.heuristics.moves.TbrMove;
//import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
//
//public class TbrHeuristicRFCAcceleratedMetric extends IncrementalHeuristicBaseMetric {
//
//    private final TbrNeighborhoodWalker walker;
//
//    // NniUtils usunięto, chyba że wykorzystujesz je do czegoś specyficznego poza Walkerem,
//    // w SPR było polem, ale nieużywanym bezpośrednio w głównych metodach.
//
//    public TbrHeuristicRFCAcceleratedMetric() {
//        super(true, new RFClusterIncrementalMetric());
//        this.walker = new TbrNeighborhoodWalker();
//    }
//
//    @Override
//    protected void searchNeighborhood(Tree currentTree) {
//        // Używamy this.incMetric z klasy bazowej.
//        // Wizytator TBR musi przyjmować 3 parametry węzłowe: odcięty korzeń, nowy wirtualny korzeń i cel
//        walker.walk(currentTree, this.incMetric, (currentDist, pruneNode, rerootNode, targetNode) -> {
//            // System.out.println("Move check: " + currentDist);
//
//            // Rejestrujemy ruch TBR zawierający informację o przekorzenieniu
//            checkImprovement(currentDist, new TbrMove(pruneNode, rerootNode, targetNode));
//        });
//    }
//
//    @Override
//    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
//        if (move instanceof TbrMove) {
//            TbrMove tm = (TbrMove) move;
//
//            // Zakładamy, że w TbrUtils masz metodę, która fizycznie tworzy drzewo po rTBR.
//            // Musi ona odciąć pruneNode, zmienić jego korzeń na rerootNode i wpiąć w targetNode.
//            Tree newTree = new TbrUtils().createTbrTree(tree, tm.movingNode, tm.rerootNode, tm.targetNode);
//
//            if (newTree != null) {
//                if (newTree instanceof pal.tree.SimpleTree) {
//                    ((pal.tree.SimpleTree) newTree).createNodeList();
//                }
//                return newTree;
//            }
//        }
//        return tree;
//    }
//
//    @Override
//    protected double commitMoveToMetric(TreeMove move) {
//        return this.incMetric.getCurrentDistance();
//    }
//
//    @Override
//    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
//        Tree currentTree = tree1;
//        this.improved = true;
//        int totalSteps = 0;
//
//        this.incMetric.initCalculationState(currentTree, tree2);
//        double currentDist = this.incMetric.getCurrentDistance();
//
//        while (this.improved && currentDist > 0) {
//            this.improved = false;
//            this.bestDist = currentDist;
//            this.bestMove = null;
//
//            searchNeighborhood(currentTree);
//
//            if (this.improved && this.bestMove != null) {
//                currentTree = applyPhysicalMove(currentTree, this.bestMove);
//                totalSteps++;
//
//                // Tak, to ręczne przeliczanie rodziców jest krytyczne,
//                // biblioteka PAL często je "gubi" po fizycznej przebudowie struktury drzewa.
//                TreeUtils.computeParentPointers(currentTree.getRoot());
//
//                this.incMetric.initCalculationState(currentTree, tree2);
//                currentDist = this.incMetric.getCurrentDistance();
//            }
//        }
//        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
//    }
//}