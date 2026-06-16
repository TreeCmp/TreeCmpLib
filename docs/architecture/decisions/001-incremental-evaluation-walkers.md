# ADR 001: Incremental Evaluation via Tree Walkers

**Status:** Accepted

**Context:** Evaluating topological distances using SPR or TBR involves generating $O(N^2)$ to $O(N^3)$ adjacent trees. Standard approaches fully clone the PAL `Tree` object for every move, leading to extreme memory allocation overhead and unacceptable execution times for large trees (N > 50).

**Decision:** We implement the `IncrementalMetric` interface and the `TreeNeighborhoodWalker` pattern. Instead of generating physical trees, the algorithm traverses the topology using virtual backtracking (e.g., "Prune", "Evaluate Regraft", "Undo"). State is maintained via polymorphically injected metrics that update mathematical cluster hashes in $O(1)$ time.

**Consequences:** * **Positive:** Massive performance gain; heap allocations are reduced to near zero during neighborhood exploration.
* **Negative:** High cyclomatic complexity in walker logic. Requires rigorous coverage tests to ensure no graph edges are skipped.
