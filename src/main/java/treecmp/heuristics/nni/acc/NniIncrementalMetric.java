package treecmp.heuristics.nni.acc;

import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.IncrementalMetric;

public interface NniIncrementalMetric extends IncrementalMetric {
    double applyNni(NniMove move);
    void undoNni(NniMove move);
}