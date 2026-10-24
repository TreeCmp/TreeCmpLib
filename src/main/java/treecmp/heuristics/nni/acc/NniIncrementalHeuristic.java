package treecmp.heuristics.nni.acc;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.nni.NniUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NniIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final NniUtils nniUtils;
    private final String metricShortName;
    protected final NniIncrementalMetric nniMetric;
    protected final NniIncrementalMetric primaryMetric; // Opcjonalny filtr (np. RF)

    // 1. Konstruktor podstawowy (bez filtra)
    public NniIncrementalHeuristic(NniIncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    // 2. Konstruktor rozszerzony (z filtrem)
    public NniIncrementalHeuristic(NniIncrementalMetric metric, NniIncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.nniMetric = metric;
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.nniUtils = new NniUtils(!metric.isRooted());
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        NniIncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.nniMetric;

        this.tiedMoves.clear();
        this.bestMove = null;
        this.improved = false;

        double currentDist = activeMetric.getCurrentDistance();
        // Gdy primaryMetric == null, szukamy tylko ruchów ściśle lepszych niż currentDist.
        // Gdy primaryMetric != null (filtr RF), inicjalizujemy INFINITY, aby zebrać najlepsze ruchy
        // (nawet jeśli najlepszy ruch w otoczeniu ma dist == currentDist, czyli leży na plateau RF).
        this.bestDist = (primaryMetric == null) ? currentDist : Double.POSITIVE_INFINITY;

        exploreNniRecursive(currentTree.getRoot(), activeMetric, currentDist);
    }

    private void exploreNniRecursive(Node parent, NniIncrementalMetric activeMetric, double initialDist) {
        if (parent.isLeaf()) return;

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            Node child = parent.getChild(i);
            if (child.isLeaf()) continue;

            if (child.getChildCount() >= 2) {
                Node g1 = child.getChild(0);
                Node g2 = child.getChild(1);

                // Iterujemy po WSZYSTKICH braciach (dla korzenia nieukorzenionego o stopniu 3 wystarczy pierwszy brat,
                // dokładnie tak jak w NniUtils.addMovesForInternalEdge, ponieważ wymiana z drugim bratem daje izomorficzne NNI)
                for (int j = 0; j < childCount; j++) {
                    if (i == j) continue;
                    Node sibling = parent.getChild(j);

                    // --- SĄSIAD 1 (konwencja NniIncrementalMetric: wnuk jako pierwszy, brat jako drugi) ---
                    NniMove move1 = new NniMove(g1, sibling);
                    double dist1 = activeMetric.applyNni(move1);
                    if (primaryMetric == null) {
                        if (dist1 < initialDist - 1e-9) {
                            checkImprovementWithTies(dist1, move1);
                        }
                    } else if (dist1 <= initialDist + 1e-9) {
                        checkImprovementWithTies(dist1, move1);
                    }
                    activeMetric.undoNni(move1);

                    // --- SĄSIAD 2 ---
                    NniMove move2 = new NniMove(g2, sibling);
                    double dist2 = activeMetric.applyNni(move2);
                    if (primaryMetric == null) {
                        if (dist2 < initialDist - 1e-9) {
                            checkImprovementWithTies(dist2, move2);
                        }
                    } else if (dist2 <= initialDist + 1e-9) {
                        checkImprovementWithTies(dist2, move2);
                    }
                    activeMetric.undoNni(move2);

                    if (!this.rooted && parent.isRoot()) {
                        break;
                    }
                }
            }

            exploreNniRecursive(child, activeMetric, initialDist);
        }
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        if (move instanceof NniMove) {
            double dist = this.nniMetric.applyNni((NniMove) move);
            this.nniMetric.commit();
            return dist;
        }
        return this.nniMetric.getCurrentDistance();
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof NniMove) {
            return nniUtils.applyPhysicalMove(tree, (NniMove) move);
        }
        return tree;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double remainingDist = performLocalDescent(tree1, tree2);
        return (remainingDist == 0.0) ? (double) this.accumulatedSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new SimpleTree(startTree);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }
        TreeUtils.computeParentPointers(currentTree.getRoot());

        this.improved = false;
        this.accumulatedSteps = 0;
        this.accumulatedNniCost = 0.0;
        this.fullOptimumTrajectory.clear();
        this.nativeOptimumTrajectory.clear();
        this.lastOptimumMove = null;

        int totalSteps = 0;
        int maxSteps = 1000;

        NniIncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.nniMetric;

        // Inicjalizacja stanów metryk na startowym drzewie
        activeMetric.initCalculationState(currentTree, targetTree);
        double currentDist = activeMetric.getCurrentDistance();

        double currentHeavyDist = Double.POSITIVE_INFINITY;
        if (primaryMetric != null) {
            this.nniMetric.initCalculationState(currentTree, targetTree);
            currentHeavyDist = this.nniMetric.getCurrentDistance();
        }

        if (currentDist == 0.0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        while (currentDist > 0 && totalSteps < maxSteps) {
            this.improved = false;
            searchNeighborhood(currentTree);

            // BEZPIECZNIK 1: Brak ruchów lub pogorszenie na aktywnej metryce
            if (this.tiedMoves.isEmpty() || this.bestDist > currentDist + 1e-9) {
                break;
            }

            TreeMove bestMove = null;
            boolean isPlateau = Math.abs(this.bestDist - currentDist) <= 1e-9;

            if (primaryMetric == null) {
                if (this.bestDist < currentDist - 1e-9) {
                    bestMove = tiedMoves.get(0);
                } else {
                    break;
                }
            } else {
                // TIE-BREAKER (oraz weryfikacja ścisłej poprawy na metryce głównej w przypadku plateau RF)
                double bestHeavyDist = Double.POSITIVE_INFINITY;
                for (TreeMove tm : tiedMoves) {
                    NniMove nniM = (NniMove) tm;

                    double heavyDist = this.nniMetric.applyNni(nniM);
                    if (!isPlateau) {
                        if (heavyDist < bestHeavyDist - 1e-9) {
                            bestHeavyDist = heavyDist;
                            bestMove = nniM;
                        }
                    } else {
                        // Na plateau RF wymagamy ścisłego spadku metryki pomocniczej (np. M3, MS, MC, MP)
                        if (heavyDist < currentHeavyDist - 1e-9 && heavyDist < bestHeavyDist - 1e-9) {
                            bestHeavyDist = heavyDist;
                            bestMove = nniM;
                        }
                    }
                    this.nniMetric.undoNni(nniM);
                }
            }

            // BEZPIECZNIK 2: Jeśli nie wybrano ruchu poprawiającego, natychmiast przerywamy
            if (bestMove == null) {
                break;
            }

            NniMove finalMove = (NniMove) bestMove;

            // Fizyczna modyfikacja drzewa pod kolejną iterację
            currentTree = applyPhysicalMove(currentTree, finalMove);
            if (currentTree instanceof SimpleTree) {
                ((SimpleTree) currentTree).createNodeList();
            }
            TreeUtils.computeParentPointers(currentTree.getRoot());

            // Pełna synchronizacja stanu metryki z nową topologią drzewa
            activeMetric.initCalculationState(currentTree, targetTree);
            double newDist = activeMetric.getCurrentDistance();

            double newHeavyDist = Double.POSITIVE_INFINITY;
            if (primaryMetric != null) {
                this.nniMetric.initCalculationState(currentTree, targetTree);
                newHeavyDist = this.nniMetric.getCurrentDistance();
            }

            // BEZPIECZNIK 3: Ochrona przed zapętleniem
            if (newDist > currentDist + 1e-9) {
                break;
            }
            if (Math.abs(newDist - currentDist) <= 1e-9) {
                if (primaryMetric == null || newHeavyDist >= currentHeavyDist - 1e-9) {
                    break;
                }
            }

            // Rejestracja kroku w trajektorii certyfikatu NNI oraz natywnej
            SimpleTree stepCopy = new SimpleTree(currentTree);
            stepCopy.createNodeList();
            TreeUtils.computeParentPointers(stepCopy.getRoot());
            this.fullOptimumTrajectory.add(stepCopy);
            this.nativeOptimumTrajectory.add(stepCopy);

            totalSteps++;
            this.accumulatedSteps++;
            this.accumulatedNniCost += finalMove.getNniEquivalentCost();
            this.lastOptimumMove = finalMove;
            this.lastMoveBaseTree = currentTree;

            currentDist = newDist;
            currentHeavyDist = newHeavyDist;
            this.improved = true;
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    @Override
    public List<Tree> getLastOptimumTrajectory(Tree startTree) {
        if (this.fullOptimumTrajectory != null && !this.fullOptimumTrajectory.isEmpty()) {
            return new ArrayList<>(this.fullOptimumTrajectory);
        }
        if (this.lastOptimumTree != null) {
            return Collections.singletonList(this.lastOptimumTree);
        }
        return Collections.emptyList();
    }

    @Override
    public Tree getLastOptimumTree() {
        return this.lastOptimumTree;
    }

    @Override
    public String getName() {
        return "NNI_IncrementalHeuristic_" + metricShortName;
    }
}