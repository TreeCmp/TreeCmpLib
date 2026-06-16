# ADR 002: Golden Master & Formulaic Coverage Testing

**Status:** Accepted

**Context:** Verifying the correctness of heuristic neighborhood generators is prone to human error. While SPR neighborhood size can be precisely calculated via the Allen and Steel formula ($2(n-2)(2n-5) - \dots$), TBR neighborhood sizes vary drastically depending on tree topology (balanced vs. caterpillar) and cannot be derived from a simple $N$-based formula. Additionally, unrooted topologies enforce strict degree-3 root constraints that break naive implementations.

**Decision:** 1.  **SPR Validation:** Use strict programmatic equivalence against the mathematical formula (`calcSprNeighbours`).
2.  **TBR Validation:** Implement "Golden Master" property-based testing. Expected neighborhood sizes for specific topologies are pre-calculated, hardcoded as invariants, and verified against generated sets to detect duplication, isomorphism (distance 0), or missing branches.
3.  **Walker Coverage:** The `SprNeighborhoodWalker` is tested using a Mock Metric forcing a 100% traversal. Its output vector must perfectly match the naive nested-loop $O(N^2)$ generator.

**Consequences:** * **Positive:** Bulletproof regression detection. Any structural optimization that accidentally prunes valid topological moves will immediately fail the Golden Master exact-size assertions.
* **Negative:** Hardcoded integers require manual recalculation if the underlying fundamental rules of topological legality change.