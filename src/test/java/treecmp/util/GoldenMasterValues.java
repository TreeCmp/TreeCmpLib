package treecmp.util;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;

import java.util.ArrayList;
import java.util.List;

public final class GoldenMasterValues {

    private GoldenMasterValues() {}

    /**
     * Calculates the mathematical size of the SPR neighborhood for UNROOTED trees.
     * The formula is completely independent of the tree's topology.
     * * @param n number of leaves (must be >= 4)
     * @return number of unique SPR topologies
     */
    public static int calculateUnrootedSprSize(int n) {
        if (n < 4) {
            return 0; // Below 4 leaves, SPR moves do not create new structures
        }
        return 2 * (n - 3) * (2 * n - 7);
    }

    /**
     * Dynamically calculates the EXACT number of unique SPR topologies for a given ROOTED tree.
     * Since no pure mathematical formula based on 'n' exists for rooted trees due to
     * isomorphism dependencies, this method utilizes the reliable O(N^2) Oracle (SprUtils).
     *
     * @param tree The starting rooted tree
     * @param sprUtils The legacy Oracle generator
     * @return The exact size of the unique SPR neighborhood
     */
    public static int calculateExactRootedSprSize(Tree tree, SprUtils sprUtils) {
        // Zoptymalizowana metoda forEachSprTree posiada wbudowaną deduplikację,
        // więc callback jest wywoływany dokładnie raz dla każdej unikalnej topologii SPR.
        final int[] uniqueCount = {0};

        sprUtils.forEachSprTree(tree, neighbor -> {
            uniqueCount[0]++;
        });

        return uniqueCount[0];
    }

    /**
     * Calculates the exact number of evaluations (evaluateSprRegraft calls)
     * that the optimized SprNeighborhoodWalker will perform for a GIVEN rooted tree.
     * * This calculates the "Structural Jumps". It is independent of isomorphisms
     * and strictly counts topologically valid pruning and regrafting points.
     *
     * @param tree The starting rooted tree
     * @param sprUtils Utility instance (contains the isValidSprMove logic)
     * @return Expected number of evaluation calls for the Walker
     */
    public static int calculateExpectedSprWalkerEvaluations(Tree tree, SprUtils sprUtils) {
        int expectedEvals = 0;
        List<Node> allNodes = getAllNodes(tree);

        for (Node pruneNode : allNodes) {
            // Pruning logic exactly mirroring the Walker
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            for (Node currentNode : allNodes) {
                // Regrafting logic exactly mirroring the Walker
                if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
                    if (sprUtils.isValidSprMove(pruneNode, currentNode)) {
                        expectedEvals++;
                    }
                }
            }
        }
        return expectedEvals;
    }

    /**
     * Calculates the exact number of evaluations that the optimized UsprNeighborhoodWalker
     * will perform for an UNROOTED tree, using the logic from USprUtils.
     */
    public static int calculateExpectedUsprWalkerEvaluations(Tree tree, UsprUtils usprUtils) {
        int expectedEvals = 0;
        List<pal.tree.Node> allNodes = getAllNodes(tree);

        for (pal.tree.Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            for (pal.tree.Node currentNode : allNodes) {
                if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
                    // Używamy dedykowanej metody dla uSPR!
                    if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {
                        expectedEvals++;
                    }
                }
            }
        }
        return expectedEvals;
    }

    // --- Helper functions to retrieve nodes ---

    private static List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    private static void collectNodes(Node node, List<Node> list) {
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), list);
        }
    }

    // Constants for rooted trees as a fallback / verification

    public static final int SPR_SIZE_SIX_LEAVES_CATERPILLAR = 44;

    public static final int SPR_EVALUATIONS_SIX_LEAVES_CATERPILLAR = 60;

    public static final int TBR_SIZE_SIX_LEAVES_CATERPILLAR = 105;

    public static final int NNI_SIZE_EIGHT_LEAVES_SYMMETRIC = 14;
}