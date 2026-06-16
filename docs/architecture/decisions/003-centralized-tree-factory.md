# ADR 003: Centralized Strongly-Typed Tree Factory

**Status:** Accepted

**Context:** Test classes previously relied on raw Newick strings parsed locally via `TreeCreator.getTreeFromString()`. This led to duplicated topologies, undetected unary root violations (excessive parentheses), and brittle string-based DataProviders in JUnit 5 `@ParameterizedTest`s.

**Decision:** Extract all test tree generation into a centralized `TestTreeFactory`. All tests and Parameterized `@MethodSource` providers must request strongly-typed PAL `Tree` objects using highly descriptive method names (e.g., `sixLeavesRootedCaterpillarTree()`, `fiveLeavesUnrootedBalancedTree()`).

**Consequences:** * **Positive:** Enforces the DRY principle. Eliminates `TreeParseException` instances caused by local typos. Parameterized tests natively handle `Tree` injection without requiring string-to-object converters.
* **Positive:** Provides a documented dictionary of topological edge-cases for future developers.