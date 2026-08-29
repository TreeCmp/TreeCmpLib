package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Klasa bazowa (Narzędziowa) dla wszystkich eksperymentów Single-Step.
 * Hermetyzuje ustawienia JMH, profilera i eksport do pliku.
 * Realizuje wzorzec DRY (Don't Repeat Yourself).
 */
public abstract class AbstractSingleStepBenchmark {

    /**
     * Główny silnik uruchamiający JMH dla podanych parametrów i przechwytujący wyniki.
     */
    public static Collection<RunResult> runJmh(String sizeStr, String[] metrics, String includeRegex, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(includeRegex)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                .jvmArgs("-Xms4g", "-Xmx16g")
                // AKTYWACJA PROFILERA PAMIĘCI GC:
                .addProfiler("gc");

        if (quickEstimate) {
            builder.warmupIterations(1)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(1)
                    .measurementTime(TimeValue.seconds(1))
                    .forks(1)
                    .warmupForks(0);
        } else {
            builder.warmupIterations(5)
                    .warmupTime(TimeValue.seconds(2))
                    .measurementIterations(5)
                    .measurementTime(TimeValue.seconds(2))
                    .forks(2)
                    .warmupForks(1);
        }

        // Zwraca zbiór przebiegów przechwyconych z profilera Javy.
        return new Runner(builder.build()).run();
    }

    /**
     * Ujednolicony system eksportu wszystkich wyników do pliku.
     */
    public static void exportToCsv(String filename, List<RunResult> results, String neighborhood) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            // Generujemy wspólny nagłówek
            pw.println("Neighborhood,Size,IsRooted,Metric,Variant,TimeUs,AllocBytesPerOp");

            for (RunResult r : results) {
                String metric = r.getParams().getParam("metricName");
                String size = r.getParams().getParam("treeSize");
                String benchmark = r.getParams().getBenchmark();

                String variant = benchmark.contains("Incremental") ? "2. Incremental" : "1. Classic";
                boolean isRooted = metric.equals("RFC") || metric.equals("MC") || metric.equals("MP");

                // Czas wykonania
                double timeUs = r.getPrimaryResult().getScore();

                // Odczyt użycia RAM z SecondaryResults Profilera
                double allocBytes = Double.NaN;
                for (Result sec : r.getSecondaryResults().values()) {
                    if (sec.getLabel().equals("gc.alloc.rate.norm")) {
                        allocBytes = sec.getScore();
                        break;
                    }
                }

                pw.printf(Locale.US, "%s,%s,%b,%s,%s,%.4f,%.2f%n",
                        neighborhood, size, isRooted, metric, variant, timeUs, allocBytes);
            }
            System.out.println("Zapisano wyniki Single-Step (" + neighborhood + ") do pliku: " + filename);
        }
    }
}