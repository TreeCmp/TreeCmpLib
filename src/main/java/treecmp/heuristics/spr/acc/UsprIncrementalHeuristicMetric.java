package treecmp.heuristics.spr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

public class UsprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final ClassicUsprWalker standardWalker;
    private final IncrementalUsprWalker unrootedWalker;
    protected final UsprUtils usprUtils;
    private final String metricShortName;

    protected IncrementalMetric primaryMetric;
    private int sprStepsCount = 0; // NOWOŚĆ: Śledzi czystą liczbę wykonanych kroków SPR

    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(false, metric); // false dla drzew nieukorzenionych
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.standardWalker = new ClassicUsprWalker();
        this.unrootedWalker = new IncrementalUsprWalker();
        this.usprUtils = new UsprUtils();
    }

    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;
        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        // Wybór zoptymalizowanego walkera dla metryk wspieranych przez IncrementalUsprWalker
        if (activeMetric instanceof MSIncrementalMetric ||
                activeMetric instanceof M3IncrementalMetric) {
            unrootedWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
            });
        } else {
            standardWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
            });
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sprMove = (SprMove) move;
            Tree newTree = usprUtils.createUsprTree(tree, sprMove.movingNode, sprMove.targetNode);
            if (newTree != null) {
                TreeCmpUtils.unrootTreeIfNeeded(newTree);
                newTree.createNodeList();
                return newTree;
            }
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        return this.incMetric.getCurrentDistance();
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new pal.tree.SimpleTree(startTree);
        if (currentTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) currentTree).createNodeList();
        }

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.sprStepsCount = 0; // Zerujemy licznik przed startem
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        activeMetric.initCalculationState(currentTree, targetTree);
        double currentDist = activeMetric.getCurrentDistance();

        if (currentDist == 0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        while (this.improved && currentDist > 0) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist <= currentDist) {
                TreeMove bestMove = null;

                if (primaryMetric == null || tiedMoves.size() == 1) {
                    // SCENARIUSZ 1: Brak drugorzędnej metryki. Remisy rozstrzygamy wyłącznie KOSZTEM NNI.
                    if (tiedMoves.size() > 1 && this.bestDist < currentDist) {
                        double lowestNniCost = Double.POSITIVE_INFINITY;
                        for (TreeMove move : tiedMoves) {
                            double currentMoveCost = move.getNniEquivalentCost();
                            if (currentMoveCost < lowestNniCost) {
                                lowestNniCost = currentMoveCost;
                                bestMove = move;
                            }
                        }
                    } else if (this.bestDist < currentDist) {
                        bestMove = tiedMoves.get(0);
                    }
                } else {
                    // SCENARIUSZ 2: Ewaluacja metryką drugorzędną (Secondary Metric)
                    double bestSecondaryDist = Double.POSITIVE_INFINITY;
                    double bestNniCostForTie = Double.POSITIVE_INFINITY; // NOWOŚĆ: Śledzenie kosztu przy remisach

                    for (TreeMove move : tiedMoves) {
                        Tree candidateTree = applyPhysicalMove(currentTree, move);
                        // OCHRONA PRZED CYKLAMI
                        if (candidateTree == null || candidateTree == currentTree) {
                            continue;
                        }
                        pal.tree.TreeUtils.computeParentPointers(candidateTree.getRoot());
                        this.incMetric.initCalculationState(candidateTree, targetTree);

                        double secDist = this.incMetric.getCurrentDistance();
                        double moveNniCost = move.getNniEquivalentCost();

                        // 1. Wyraźna poprawa w metryce drugorzędnej
                        if (secDist < bestSecondaryDist - 1e-9) {
                            bestSecondaryDist = secDist;
                            bestMove = move;
                            bestNniCostForTie = moveNniCost;
                        }
                        // 2. KRYTERIUM NNI: Remis w metryce drugorzędnej -> wybieramy tańszą trajektorię
                        else if (Math.abs(secDist - bestSecondaryDist) <= 1e-9 && moveNniCost < bestNniCostForTie) {
                            bestMove = move;
                            bestNniCostForTie = moveNniCost;
                        }
                    }
                }

                if (bestMove != null) {
                    this.accumulatedNniCost += bestMove.getNniEquivalentCost();
                    this.sprStepsCount++; // Zliczamy poprawny krok SPR

                    this.lastOptimumMove = bestMove;
                    this.lastMoveBaseTree = currentTree;
                    currentTree = applyPhysicalMove(currentTree, bestMove);

                    TreeUtils.computeParentPointers(currentTree.getRoot());
                    activeMetric.initCalculationState(currentTree, targetTree);
                    double newDist = activeMetric.getCurrentDistance();

                    // Bezpiecznik: Przerywamy na ślepym płaskowyżu bez wsparcia Tie-breakera
                    if (primaryMetric == null && newDist >= currentDist) {
                        break;
                    }

                    currentDist = newDist;
                    this.improved = true;
                }
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double dist = performLocalDescent(tree1, tree2);
        // NOWOŚĆ: Zwracamy liczbę kroków SPR (sprStepsCount) zamiast ekwiwalentu NNI,
        // co ujednolica wyniki w tabelach z wersją Classic!
        return dist == 0.0 ? this.sprStepsCount : Double.POSITIVE_INFINITY;
    }

    @Override public boolean isRooted() { return false; }
    @Override public String getName() { return "Heur. uSPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "huspr_" + this.incMetric.getCommandLineName(); }
}