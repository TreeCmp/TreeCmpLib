package treecmp.heuristics.spr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.MPIncrementalMetric;

public class SprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final ClassicSprWalker standardWalker;
    private final IncrementalSprWalker rootedWalker;
    protected final SprUtils sprUtils;
    private final String metricShortName;

    protected IncrementalMetric primaryMetric;
    private int sprStepsCount = 0; // Licznik legalnych kroków SPR

    public SprIncrementalHeuristicMetric(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(true, metric); // true dla drzew ukorzenionych (rb)
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.standardWalker = new ClassicSprWalker();
        this.rootedWalker = new IncrementalSprWalker();
        this.sprUtils = new SprUtils();
    }

    public SprIncrementalHeuristicMetric(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;
        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        // Sprawdzamy i jawnie rzutujemy na interfejs RootedMetric
        if (activeMetric instanceof IncrementalSprWalker.RootedMetric) {
            rootedWalker.walk(currentTree, (IncrementalSprWalker.RootedMetric) activeMetric, (currentDist, movingNode, targetNode) -> {
                // KRYTYCZNY FILTR: Odrzucamy nielegalne topologicznie ruchy SPR (s jest przodkiem t)
                if (!sprUtils.isValidSprMove(movingNode, targetNode)) {
                    return;
                }
                checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
            });
        } else {
            standardWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                if (!sprUtils.isValidSprMove(movingNode, targetNode)) {
                    return;
                }
                checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
            });
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sprMove = (SprMove) move;
            // BEZPIECZNIK: Podwójna weryfikacja przed fizyczną przebudową
            if (!sprUtils.isValidSprMove(sprMove.movingNode, sprMove.targetNode)) {
                return null;
            }
            Tree newTree = sprUtils.createSprTree(tree, sprMove.movingNode, sprMove.targetNode);
            if (newTree != null) {
                newTree.createNodeList();
                return newTree;
            }
        }
        return null;
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
        this.sprStepsCount = 0;
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
                        // OCHRONA PRZED CYKLAMI: Pomijamy nielegalne lub uszkodzone drzewa!
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
                    // Sprawdzamy czy wybrany ruch da się legalnie zaaplikować
                    Tree nextTree = applyPhysicalMove(currentTree, bestMove);
                    if (nextTree == null || nextTree == currentTree) {
                        break;
                    }

                    this.accumulatedNniCost += bestMove.getNniEquivalentCost();
                    this.sprStepsCount++;

                    this.lastOptimumMove = bestMove;
                    this.lastMoveBaseTree = currentTree;
                    currentTree = nextTree;

                    TreeUtils.computeParentPointers(currentTree.getRoot());
                    activeMetric.initCalculationState(currentTree, targetTree);
                    double newDist = activeMetric.getCurrentDistance();

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
        return dist == 0.0 ? this.sprStepsCount : Double.POSITIVE_INFINITY;
    }

    @Override public boolean isRooted() { return true; }
    @Override public String getName() { return "Heur. rSPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "hspr_" + this.incMetric.getCommandLineName(); }
}