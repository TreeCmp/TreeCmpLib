package treecmp.metrics.topological;

import java.util.BitSet;

/**
 * Standardowa metryka Robinson-Foulds (Symmetric Difference of Splits).
 * Traktuje drzewa jako UKORZENIONE.
 */
public class RFClusterIncrementalMetric extends BaseRFIncrementalMetric {

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {

        return rawSplit;
    }

}