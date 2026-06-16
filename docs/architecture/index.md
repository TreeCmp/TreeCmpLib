# Single Source of Truth (SSOT) - TreeCmpLib Architecture

## 1. Purpose and Goals
Visual TreeCmpLib is a comprehensive, high-performance Java libraty and library designed to compute distances between arbitrary phylogenetic trees. Its primary goal is to provide polynomial-time, highly efficient implementations of metrics, enabling the comparison of trees with a massive number of leaves (both bifurcating and multifurcating).

TreeCmpLib stands as the first efficient implementation of Matching Split/Cluster/Pair/Triplets metrics and the Nodal Splitted metric. Recently, the library has been expanded with a highly optimized **Heuristic Search Engine** to support topological structural moves (NNI, SPR, TBR) for massive neighborhood evaluations.

## 2. Core Capabilities & Metric Suite
The library implements 19 core metrics, categorized by tree topology and branch weights:

### Rooted Tree Metrics (11)
* **Topological:** Triples, Robinson-Foulds (Cluster-based), Matching Pair, Matching Cluster, Rooted Maximum Agreement Subtree (MAST).
* **Quantitative / Weighted:** Nodal Splitted (L2 norm), Cophenetic (L2 norm), Nodal Splitted Weighted, Cophenetic Weighted, Geodesic Rooted, Weighted Robinson-Foulds.

### Unrooted Tree Metrics (8)
* **Topological:** Matching Triples, Quartet, Path Difference, Robinson-Foulds, Matching Split, Unrooted MAST.
* **Quantitative / Weighted:** Weighted Robinson-Foulds, Geodesic Unrooted.

## 3. High-Level Package Structure
* **`treecmp.metrics`** / **`treecmp.metrics.topological`** / **`treecmp.metrics.quantitative`**
    * Contains the core implementations of the 19 standard polynomial-time metrics.
* **`treecmp.heuristics`** * The advanced module for navigating topological spaces (NNI, SPR, TBR) via virtual structural moves and $O(1)$ incremental evaluations. *(See `heuristic-engine.md` for deep-dive)*.
* **`treecmp.util`**
    * Shared utilities, including tree parsing, mathematical helpers, and the centralized `TestTreeFactory` for robust topology testing.