# Open Issues and Next Steps

1.  **rNNI Root Edge Blind Spot:** `rnniUtils` currently skips the top-most root edge in heavily asymmetric (caterpillar) topologies (e.g., N=6 generates 6 neighbors instead of 8). Requires explicit handling of unary-parent boundary conditions during recursive descent.
2.  **TBR Acceleration Walker:** The `SprNeighborhoodWalker` is complete and mathematically verified via coverage testing. The immediate next step is designing and implementing the `TbrNeighborhoodWalker` coupled with the `IncrementalMetric` for O(1) internal bisection-reconnection evaluations.
3.  **Metric Integration:** Hooking up the verified heuristic walkers to the fast Robinson-Foulds (RF) and Clustering metrics.
