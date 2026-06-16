# Module Sub-Architecture: Heuristic Search Engine

## 1. Overview
As an extension to the core TreeCmp metrics, the Heuristic Search Engine provides a framework for generating and evaluating massive topological neighborhoods efficiently. It implements operations for Nearest Neighbor Interchange (NNI), Subtree Pruning and Regrafting (SPR), and Tree Bisection and Reconnection (TBR) on both rooted and unrooted trees.

## 2. Architectural Design: Incremental Walkers
To circumvent the massive memory overhead of generating $O(N^2)$ or $O(N^3)$ physical `Tree` instances (deep cloning) during neighborhood searches, the engine utilizes a **Virtual Walker Pattern**.

### Key Components
* **`IncrementalMetric` (Interface):** The core acceleration contract. It allows downstream metrics (like Robinson-Foulds or Clustering) to update their internal state (e.g., hash maps of clusters) incrementally based on a specific topological move, evaluating distances in $O(1)$ time per transition.
* **`TreeNeighborhoodWalker` (e.g., `SprNeighborhoodWalker`):** Navigates the topological neighborhood using virtual steps. It recursively traverses the base tree, applies simulated graph operations (`applySprPrune`, `evaluateSprRegraft`), and immediately reverts them (`undoSprRegraftStep`) without instantiating new tree objects.
* **`treecmp.heuristics.moves.*`:**
  Immutable data structures representing physical modifications (`NniMove`, `SprMove`, `TbrMove`) when actual tree manipulation is required.
* **`*Utils` Generators (`SprUtils`, `UTbrUtils`, `TbrUtils`):** Fallback generators that produce exact, deduplicated arrays of neighboring `Tree` objects for testing and validation purposes.

## 3. Mathematical Integrity & Testing
Due to the complexity of topological graph spaces (especially for unrooted TBR where sizes depend drastically on the "Caterpillar" vs. "Balanced" backbone), the engine relies heavily on:
* **Formulaic Validation:** Asserting generated SPR sizes against the strict Allen & Steel theoretical formula.
* **Golden Master Topology Tests:** Hardcoding strict expected neighbor counts for carefully constructed boundary-case trees within `TestTreeFactory`.
* **Coverage Mocking:** Proving that the `Walker` algorithm exactly matches the naive $O(N^2)$ theoretical edge-exploration without skipping any valid combinations.