package treecmp.metrics.topological.acc;

import java.util.BitSet;

/**
 * Standardowa metryka Robinson-Foulds (Symmetric Difference of Splits).
 * Traktuje drzewa jako NIEUKORZENIONE.
 */
public class RFIncrementalMetric extends BaseRFIncrementalMetric {

    // W RFIncrementalMetric (dla splitów)
    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        // Jeśli bit 0 jest ustawiony, odwracamy cały BitSet.
        // Dzięki temu split {1,2} i {3,4,5} zawsze będzie zapisany jako {1,2}.
        if (rawSplit.get(0)) {
            BitSet inverted = (BitSet) rawSplit.clone();
            inverted.xor(allLeavesMask); // allLeavesMask to BitSet z samymi jedynkami
            return inverted;
        }
        return rawSplit;
    }
}