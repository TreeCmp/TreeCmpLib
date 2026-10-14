package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.IterationType;
import pal.misc.Identifier;
import pal.tree.Node;
import pal.tree.NodeFactory;
import pal.tree.SimpleTree;
import treecmp.benchmarks.AbstractTreeCmpBenchmark;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Klasa bazowa dla benchmarków pełnego przebiegu wspinaczki (Full-Run / Distance).
 * Operuje na puli 100 par drzew w trybie Round-Robin i raportuje odsetek utknięć na płaskowyżu (NaN).
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public abstract class AbstractDistanceBenchmark extends AbstractTreeCmpBenchmark {

    public static final int NUM_PAIRS = 100;

    protected HeuristicBaseMetric classicMetric;
    protected IncrementalHeuristicBaseMetric incrementalMetric;

    protected SimpleTree[] t1Pool;
    protected SimpleTree[] t1ForIncrPool;
    protected SimpleTree[] t2Pool;
    protected int loadedPairsCount = 0;

    private int pairCursor = 0;
    private boolean measurementPhaseStarted = false;

    protected boolean classicOomReported = false;
    protected boolean incrOomReported = false;

    /**
     * Liczniki pomocnicze JMH zliczające udane obliczenia dystansu (validOps)
     * oraz przypadki utknięcia na płaskowyżu / zwrócenia Double.NaN (nanOps).
     */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class QualityCounters {
        public long validOps;
        public long nanOps;

        @Setup(Level.Iteration)
        public void reset() {
            validOps = 0;
            nanOps = 0;
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        classicOomReported = false;
        incrOomReported = false;
        pairCursor = 0;
        measurementPhaseStarted = false;
        initMetricsAndTrees(metricName, treeSize);
    }

    @Setup(Level.Iteration)
    public void setupIteration(IterationParams iterationParams) {
        // Zerujemy kursor dokładnie na starcie 1. iteracji pomiarowej (MEASUREMENT),
        // aby Classic i Incremental zawsze badały tę samą sekwencję par począwszy od pary 0.
        if (iterationParams.getType() == IterationType.MEASUREMENT && !measurementPhaseStarted) {
            pairCursor = 0;
            measurementPhaseStarted = true;
        }
    }

    protected int nextPairIndex() {
        if (loadedPairsCount <= 0) {
            return 0;
        }
        int idx = pairCursor % loadedPairsCount;
        pairCursor++;
        return idx;
    }

    /**
     * Inicjalizacja metryk i drzew charakterystyczna dla danego operatora (NNI, SPR, TBR, ECR2, ECR3).
     */
    protected abstract void initMetricsAndTrees(String metric, int size);

    @Override
    protected void loadOrGenerateTrees(int size, boolean isRooted) {
        // Wywołanie metody bazowej ustawia pierwszą parę (t1, t1ForIncr, t2)
        super.loadOrGenerateTrees(size, isRooted);

        t1Pool = new SimpleTree[NUM_PAIRS];
        t1ForIncrPool = new SimpleTree[NUM_PAIRS];
        t2Pool = new SimpleTree[NUM_PAIRS];

        t1Pool[0] = new SimpleTree(t1);
        t1ForIncrPool[0] = new SimpleTree(t1ForIncr != null ? t1ForIncr : t1);
        t2Pool[0] = new SimpleTree(t2);

        int leafCount = t1.getExternalNodeCount();
        Identifier[] identifiers = new Identifier[leafCount];
        for (int i = 0; i < leafCount; i++) {
            identifiers[i] = t1.getExternalNode(i).getIdentifier();
        }

        // Stałe ziarno losowości gwarantujące identyczny zestaw 100 par dla każdego przebiegu
        Random rand = new Random(42L + size * 10007L + (isRooted ? 1L : 0L));

        for (int p = 1; p < NUM_PAIRS; p++) {
            SimpleTree genT1 = generateRandomBinaryTree(identifiers, isRooted, rand);
            SimpleTree genT2 = generateRandomBinaryTree(identifiers, isRooted, rand);
            t1Pool[p] = genT1;
            t1ForIncrPool[p] = new SimpleTree(genT1);
            t2Pool[p] = genT2;
        }

        loadedPairsCount = NUM_PAIRS;
    }

    private SimpleTree generateRandomBinaryTree(Identifier[] identifiers, boolean isRooted, Random rand) {
        List<Node> active = new ArrayList<>(identifiers.length);
        for (Identifier id : identifiers) {
            Node leaf = NodeFactory.createNode();
            leaf.setIdentifier(id);
            active.add(leaf);
        }

        int targetRootDegree = isRooted ? 2 : 3;
        while (active.size() > targetRootDegree) {
            int i = rand.nextInt(active.size());
            Node n1 = active.remove(i);
            int j = rand.nextInt(active.size());
            Node n2 = active.remove(j);
            Node parent = NodeFactory.createNode(new Node[]{n1, n2});
            active.add(parent);
        }

        Node root = NodeFactory.createNode(active.toArray(new Node[0]));
        return new SimpleTree(root);
    }

    @Benchmark
    public double benchmarkClassicFullRun(QualityCounters counters) {
        if (classicMetric == null) {
            counters.nanOps++;
            return Double.NaN;
        }
        int idx = nextPairIndex();
        try {
            // KLUCZOWE: Zawsze nowa instancja SimpleTree, by wspinaczka nie mutowała drzewa w puli
            double dist = classicMetric.getDistance(new SimpleTree(t1Pool[idx]), t2Pool[idx]);
            if (Double.isNaN(dist)) {
                counters.nanOps++;
            } else {
                counters.validOps++;
            }
            return dist;
        } catch (OutOfMemoryError e) {
            if (!classicOomReported) {
                System.err.printf("%n[!] OOM w Classic Full-Run | Metryka: %s | N=%d [!]%n", metricName, treeSize);
                classicOomReported = true;
            }
            System.gc();
            counters.nanOps++;
            return Double.NaN;
        } catch (Throwable t) {
            counters.nanOps++;
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFullRun(QualityCounters counters) {
        if (incrementalMetric == null) {
            counters.nanOps++;
            return Double.NaN;
        }
        int idx = nextPairIndex();
        try {
            double dist = incrementalMetric.getDistance(new SimpleTree(t1ForIncrPool[idx]), t2Pool[idx]);
            if (Double.isNaN(dist)) {
                counters.nanOps++;
            } else {
                counters.validOps++;
            }
            return dist;
        } catch (OutOfMemoryError e) {
            if (!incrOomReported) {
                System.err.printf("%n[!] OOM w Incremental Full-Run | Metryka: %s | N=%d [!]%n", metricName, treeSize);
                incrOomReported = true;
            }
            System.gc();
            counters.nanOps++;
            return Double.NaN;
        } catch (Throwable t) {
            counters.nanOps++;
            return Double.NaN;
        }
    }

    /**
     * Przesłonięty eksport do CSV uwzględniający liczniki jakości wspinaczki (ValidOps, NanOps, SuccessRatePct, Status).
     */
    public static void exportToCsv(String fileName, List<RunResult> allResults, String neighborhood, String timeHeader) {
        System.out.println("\n================================================================================================================");
        System.out.printf("PODSUMOWANIE BENCHMARKU DYSTANSU (%s) - PULA %d PAR DRZEW%n", neighborhood, NUM_PAIRS);
        System.out.println("================================================================================================================");
        System.out.printf("%-6s | %-15s | %-14s | %12s | %12s | %8s | %8s | %8s | %-11s%n",
                "Size", "Metric", "Variant", timeHeader, "Alloc(MB)", "ValidOps", "NanOps", "Success%", "Status");
        System.out.println("----------------------------------------------------------------------------------------------------------------");

        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            pw.printf("Neighborhood,Size,IsRooted,Metric,Variant,%s,AllocBytesPerOp,ValidOps,NanOps,SuccessRatePct,Status%n", timeHeader);

            for (RunResult res : allResults) {
                String benchName = res.getParams().getBenchmark();
                String variant = benchName.endsWith("benchmarkClassicFullRun") ? "1. Classic" : "2. Incremental";
                String metric = res.getParams().getParam("metricName");
                int size = Integer.parseInt(res.getParams().getParam("treeSize"));
                boolean isRooted = isMetricRooted(metric);

                double timeMs = res.getPrimaryResult().getScore();
                Map<String, Result> sec = res.getSecondaryResults();

                double rawAlloc = sec.containsKey("gc.alloc.rate.norm") ? sec.get("gc.alloc.rate.norm").getScore() : 0.0;
                double allocBytes = Double.isNaN(rawAlloc) ? 0.0 : rawAlloc;

                double rawValid = sec.containsKey("validOps") ? sec.get("validOps").getScore() : 0.0;
                long validOps = Double.isNaN(rawValid) ? 0L : Math.round(rawValid);

                double rawNan = sec.containsKey("nanOps") ? sec.get("nanOps").getScore() : 0.0;
                long nanOps = Double.isNaN(rawNan) ? 0L : Math.round(rawNan);

                long totalOps = validOps + nanOps;
                double successRatePct = totalOps > 0 ? (validOps * 100.0) / totalOps : 0.0;

                String status;
                if (totalOps == 0) {
                    status = "NO_DATA";
                } else if (nanOps == 0) {
                    status = "OK";
                } else if (validOps > 0) {
                    status = "PARTIAL";
                } else {
                    status = "PLATEAU_NAN";
                }

                pw.printf(Locale.US, "%s,%d,%b,%s,%s,%.4f,%.1f,%d,%d,%.2f,%s%n",
                        neighborhood, size, isRooted, metric, variant,
                        timeMs, allocBytes, validOps, nanOps, successRatePct, status);

                System.out.printf(Locale.US, "%-6d | %-15s | %-14s | %12.4f | %12.2f | %8d | %8d | %7.2f%% | %-11s%n",
                        size, metric, variant, timeMs, allocBytes / (1024.0 * 1024.0),
                        validOps, nanOps, successRatePct, status);
            }
            System.out.println("================================================================================================================");
            System.out.println("Zapisano wyniki (" + neighborhood + ") do pliku: " + fileName);
        } catch (IOException e) {
            System.err.println("Błąd podczas zapisu do pliku CSV: " + e.getMessage());
        }
    }

    private static boolean isMetricRooted(String metric) {
        return switch (metric) {
            case "RFC", "RFCluster", "MC", "MP", "CopheneticL2", "RMAST", "NodalL2Splitted", "Triplet" -> true;
            default -> false;
        };
    }
}