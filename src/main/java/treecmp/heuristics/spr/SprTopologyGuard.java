package treecmp.heuristics.spr;

import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.ReadTree;

import java.io.PushbackReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Wspólna tarcza topologiczna dla heurystyk SPR i uSPR.
 * Gwarantuje poprawność składniową i strukturalną drzew przed ich oceną w metrykach.
 */
public final class SprTopologyGuard {

    private SprTopologyGuard() {
        // Klasa narzędziowa - brak możliwości instancjonowania
    }

    /**
     * Kompletna walidacja topologii:
     * - weryfikuje liczbę liści w strukturze PAL
     * - wyklucza węzły zdegenerowane (stopień < 2 dla węzłów wewnętrznych)
     * - sprawdza zbalansowanie nawiasów '(' vs ')' w Newicku
     * - wymaga dokładnie L - 1 przecinków (ochrona przed klonowaniem i zapaścią poddrzew)
     * - parsowanie zwrotne z pamięci RAM i kontrola unikalności etykiet liści
     */
    public static boolean isStrictlyValidUnrootedTree(Tree tree, int expectedLeafCount) {
        if (tree == null || tree.getRoot() == null) return false;
        if (tree.getExternalNodeCount() != expectedLeafCount) return false;
        if (tree.getRoot().getChildCount() < 2) return false;

        // 1. Wykluczenie węzłów wewnętrznych o stopniu 1 (zdegenerowanych)
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node node = tree.getInternalNode(i);
            if (!node.isRoot() && node.getChildCount() < 2) {
                return false;
            }
        }

        String newick = tree.toString();
        if (newick == null || newick.isEmpty()) return false;

        // 2. Zakazane podciągi składniowe
        if (newick.contains("null") ||
                newick.contains("()") ||
                newick.contains("(,") ||
                newick.contains(",)") ||
                newick.contains(",,")) {
            return false;
        }

        // 3. Weryfikacja zbalansowania nawiasów oraz liczby przecinków (zawsze L - 1)
        int openParens = 0, closeParens = 0, commaCount = 0;
        for (int i = 0; i < newick.length(); i++) {
            char ch = newick.charAt(i);
            if (ch == '(') openParens++;
            else if (ch == ')') closeParens++;
            else if (ch == ',') commaCount++;
        }

        if (openParens != closeParens || commaCount != expectedLeafCount - 1) {
            return false;
        }

        // 4. Absolutny bezpiecznik: parsowanie z RAM + kontrola unikalności liści w napisie Newick
        try {
            Tree parsedTree = new ReadTree(
                    new PushbackReader(new StringReader(newick))
            );
            if (parsedTree == null || parsedTree.getExternalNodeCount() != expectedLeafCount) {
                return false;
            }

            Set<String> newickLeaves = new HashSet<>();
            for (int i = 0; i < parsedTree.getExternalNodeCount(); i++) {
                newickLeaves.add(parsedTree.getExternalNode(i).getIdentifier().getName());
            }
            if (newickLeaves.size() != expectedLeafCount) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}