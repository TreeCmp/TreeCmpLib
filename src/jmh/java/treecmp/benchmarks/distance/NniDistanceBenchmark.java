package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class NniDistanceBenchmark extends AbstractDistanceBenchmark {

    // Parametr filtra remisow na plateau (Pure = bez filtra, RF_Tie = rozstrzyganie odlegloscia RF)
    @Param({"Pure", "RF_Tie"})
    public String variant;

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;
        boolean useRfFilter = "RF_Tie".equals(variant);

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric     = new NniClassicHeuristic(new RFMetric(), isRooted, "RF");
                incrementalMetric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new NniClassicHeuristic(new RFClusterMetric(), isRooted, "RFC");
                incrementalMetric = new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), isRooted, "MS_RF") :
                        new NniClassicHeuristic(new MatchingSplitMetric(), isRooted, "MS_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF") :
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), "MSinc_Pure");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), isRooted, "MC_RF") :
                        new NniClassicHeuristic(new MatchingClusterMetric(), isRooted, "MC_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF") :
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), "MCinc_Pure");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), isRooted, "MP_RF") :
                        new NniClassicHeuristic(new MatchingPairMetric(), isRooted, "MP_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF") :
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), "MPinc_Pure");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), isRooted, "M3_RF") :
                        new NniClassicHeuristic(new MatchingTripletMetric(), isRooted, "M3_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF") :
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3inc_Pure");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    protected static List<RunResult> runJmh(String size, String[] metrics, String[] variants, String target, boolean quick) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(target)
                .param("treeSize", size)
                .param("metricName", metrics)
                .param("variant", variants);

        if (quick) {
            builder.warmupIterations(1).warmupTime(TimeValue.seconds(1))
                    .measurementIterations(1).measurementTime(TimeValue.seconds(1))
                    .forks(1);
        }

        return new ArrayList<>(new Runner(builder.build()).run());
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = NniDistanceBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            // 1. RF i RFC uruchamiamy WYLACZNIE w wariancie Pure (RF_Tie dla bazy RF jest tozsamosciowy)
            allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, new String[]{"Pure"}, className, quickEstimate));

            // 2. Metryki dopasowaniowe uruchamiamy z oboma wariantami (Pure oraz RF_Tie)
            if (size <= 30) {
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP", "M3"}, new String[]{"Pure", "RF_Tie"}, className, quickEstimate));
            } else if (size <= 50) {
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP"}, new String[]{"Pure", "RF_Tie"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"M3"}, new String[]{"Pure", "RF_Tie"}, incrOnly, quickEstimate));
            } else if (size <= 80) {
                allResults.addAll(runJmh(sizeStr, new String[]{"MS"}, new String[]{"Pure", "RF_Tie"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MC", "MP", "M3"}, new String[]{"Pure", "RF_Tie"}, incrOnly, quickEstimate));
            } else {
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP", "M3"}, new String[]{"Pure", "RF_Tie"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_NNI.csv", allResults, "NNI", "TimeMs");
    }

    public static void exportToCsv(String filename, List<RunResult> results, String neighborhood, String scoreUnit) {
        if (results == null || results.isEmpty()) {
            System.out.println("Brak wynikow do zapisania.");
            return;
        }

        System.out.println("========================================================================================================================");
        System.out.printf("PODSUMOWANIE BENCHMARKU DYSTANSU (%s) - PULA 100 PAR DRZEW%n", neighborhood);
        System.out.println("========================================================================================================================");
        System.out.println("Size   | Metric          | Variant                  |       TimeMs |    Alloc(MB) | ValidOps |    NanOps | Success% | Status");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");

        List<String> metricOrder = Arrays.asList("RF", "RFC", "MS", "MC", "MP", "M3");
        List<RunResult> sorted = new ArrayList<>(results);
        sorted.sort((r1, r2) -> {
            int s1 = Integer.parseInt(r1.getParams().getParam("treeSize"));
            int s2 = Integer.parseInt(r2.getParams().getParam("treeSize"));
            if (s1 != s2) return Integer.compare(s1, s2);

            String vType1 = r1.getPrimaryResult().getLabel().contains("Classic") ? "1. Classic" : "2. Incremental";
            String vType2 = r2.getPrimaryResult().getLabel().contains("Classic") ? "1. Classic" : "2. Incremental";
            int vtc = vType1.compareTo(vType2);
            if (vtc != 0) return vtc;

            String m1 = r1.getParams().getParam("metricName");
            String m2 = r2.getParams().getParam("metricName");
            int idx1 = metricOrder.indexOf(m1);
            int idx2 = metricOrder.indexOf(m2);
            int mc = (idx1 != -1 && idx2 != -1) ? Integer.compare(idx1, idx2) : m1.compareTo(m2);
            if (mc != 0) return mc;

            String h1 = r1.getParams().getParam("variant");
            String h2 = r2.getParams().getParam("variant");
            if (h1 == null) h1 = "Pure";
            if (h2 == null) h2 = "Pure";
            return h1.compareTo(h2);
        });

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("Size,Metric,Variant,TimeMs,AllocMB,ValidOps,NanOps,SuccessPct,Status");

            for (RunResult r : sorted) {
                String size = r.getParams().getParam("treeSize");
                String metric = r.getParams().getParam("metricName");
                String hVariant = r.getParams().getParam("variant");
                if (hVariant == null) hVariant = "Pure";

                String label = r.getPrimaryResult().getLabel();
                String runType = label.contains("Classic") ? "1. Classic" : "2. Incremental";
                String displayVariant = runType + " (" + hVariant + ")";

                double timeMs = r.getPrimaryResult().getScore();

                Result<?> allocRes = r.getSecondaryResults().get("gc.alloc.rate.norm");
                double allocMb = (allocRes != null) ? allocRes.getScore() / (1024.0 * 1024.0) : 0.0;

                Result<?> validOpsRes = r.getSecondaryResults().get("validOps");
                long validOps = (validOpsRes != null) ? Math.round(validOpsRes.getScore()) : 0;

                Result<?> nanOpsRes = r.getSecondaryResults().get("nanOps");
                long nanOps = (nanOpsRes != null) ? Math.round(nanOpsRes.getScore()) : 0;

                long totalOps = validOps + nanOps;
                double successPct = (totalOps > 0) ? ((double) validOps / totalOps) * 100.0 : 100.0;
                String status = (nanOps == 0) ? "OK" : (validOps == 0 ? "PLATEAU_NAN" : "PARTIAL_NAN");

                System.out.printf(Locale.US,
                        "%-6s | %-15s | %-24s | %12.4f | %12.2f | %8d | %9d | %7.2f%% | %s%n",
                        size, metric, displayVariant, timeMs, allocMb, validOps, nanOps, successPct, status);

                pw.printf(Locale.US,
                        "%s,%s,%s,%.4f,%.2f,%d,%d,%.2f,%s%n",
                        size, metric, displayVariant, timeMs, allocMb, validOps, nanOps, successPct, status);
            }
        } catch (Exception e) {
            System.err.println("Blad podczas zapisu do pliku " + filename + ": " + e.getMessage());
        }

        System.out.println("========================================================================================================================");
        System.out.println("Zapisano wyniki (" + neighborhood + ") do pliku: " + filename);
    }
}