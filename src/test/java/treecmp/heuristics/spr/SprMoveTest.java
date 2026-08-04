package treecmp.heuristics.spr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleNode;
import pal.tree.Tree;
import treecmp.heuristics.moves.SprMove;
import treecmp.util.TreeCreator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy jednostkowe dla operacji i bezpieczeństwa wskaźników SprMove")
public class SprMoveTest {

    @Test
    @DisplayName("Powinien wygenerować wyjątek IllegalStateException przy cyklu wskaźników 'parent' zamiast OOM")
    public void testCycleDetectionThrowsIllegalStateException() {
        // 1. Tworzymy sztuczną strukturę z cyklem wskaźników rodzic-dziecko
        SimpleNode nodeA = new SimpleNode("A", 1.0);
        SimpleNode nodeB = new SimpleNode("B", 1.0);
        SimpleNode nodeC = new SimpleNode("C", 1.0);

        nodeA.setNumber(0);
        nodeB.setNumber(1);
        nodeC.setNumber(2);

        // Ustawiamy wskaźniki w górę: C -> B -> A
        nodeC.setParent(nodeB);
        nodeB.setParent(nodeA);

        // WSTRZYKUJEMY CYKL: Rodzicem A staje się C! (A -> C -> B -> A...)
        nodeA.setParent(nodeC);

        SprMove move = new SprMove(nodeC, nodeA);

        // 2. Weryfikujemy, czy bezpiecznik natychmiast wykryje zapętlenie i rzuci IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, move::getNniEquivalentCost,
                "SprMove powinien wykryć cykl wskaźników i rzucić IllegalStateException zamiast zapętlać JVM!");

        assertTrue(exception.getMessage().contains("Wykryto cykl wskaźników"),
                "Komunikat błędu powinien precyzyjnie informować o wykrytym cyklu: " + exception.getMessage());
    }

    @Test
    @DisplayName("Powinien poprawnie wyliczać koszt ekwiwalentu NNI w standardowym drzewie")
    public void testGetNniEquivalentCostInNormalTree() throws Exception {
        // Drzewo: ((A,B)int1,(C,D)int2)root;
        Tree tree = TreeCreator.getTreeFromString("((A,B),(C,D));");
        assertNotNull(tree, "Drzewo testowe nie może być null");

        Node root = tree.getRoot();
        Node int1 = root.getChild(0);
        Node int2 = root.getChild(1);

        Node leafA = int1.getChild(0);
        Node leafC = int2.getChild(0);

        // Ruch SPR: przenosimy liść A nad liść C (w drugą gałąź drzewa)
        SprMove move = new SprMove(leafA, leafC);

        int cost = move.getNniEquivalentCost();
        assertTrue(cost >= 1, "Koszt ekwiwalentu NNI dla prawidłowego ruchu powinien być >= 1, otrzymano: " + cost);
    }

    @Test
    @DisplayName("Powinien bezpiecznie obsługiwać przypadki brzegowe (pruneParent == null lub pruneParent == targetNode)")
    public void testEdgeCasesForPruneParent() throws Exception {
        Tree tree = TreeCreator.getTreeFromString("((1,2),(3,4));");
        Node root = tree.getRoot();
        Node child = root.getChild(0);

        // Przypadek 1: Węzeł docelowy jest bezpośrednim rodzicem odcinanego węzła
        SprMove moveSameParent = new SprMove(child.getChild(0), child);
        assertDoesNotThrow(() -> moveSameParent.getNniEquivalentCost(),
                "Wyliczenie kosztu dla targetNode == pruneParent nie powinno generować wyjątku");

        // Przypadek 2: Odcinamy korzeń (pruneParent == null) - sytuacja anomalna w algorytmach
        SprMove moveRoot = new SprMove(root, child);
        assertEquals(1, moveRoot.getNniEquivalentCost(),
                "Dla pruneParent == null koszt ekwiwalentu powinien bezpiecznie zwracać 1");
    }

    @Test
    @DisplayName("Powinien generować poprawną topologicznie trajektorię NNI bez duplikatów w Newicku")
    public void testGetNniTrajectoryOnRealTree() throws Exception {
        Tree startTree = TreeCreator.getTreeFromString("(((1,2),3),(4,5));");
        assertNotNull(startTree);

        // Wybieramy liść '1' z głębokiej gałęzi i przenosimy go nad liść '5'
        Node leaf1 = null;
        Node leaf5 = null;

        for (int i = 0; i < startTree.getExternalNodeCount(); i++) {
            Node leaf = startTree.getExternalNode(i);
            String name = leaf.getIdentifier().getName();
            if ("1".equals(name)) leaf1 = leaf;
            if ("5".equals(name)) leaf5 = leaf;
        }

        assertNotNull(leaf1, "Nie znaleziono liścia 1");
        assertNotNull(leaf5, "Nie znaleziono liścia 5");

        SprMove move = new SprMove(leaf1, leaf5);
        List<Tree> trajectory = move.getNniTrajectory(startTree);

        assertNotNull(trajectory, "Trajektoria nie może być null");
        assertFalse(trajectory.isEmpty(), "Trajektoria nie może być pusta");

        // Sprawdzamy, czy żadne drzewo pośrednie w trajektorii nie ma uszkodzonego/zduplikowanego zapisu Newick
        for (int i = 0; i < trajectory.size(); i++) {
            Tree stepTree = trajectory.get(i);
            assertEquals(startTree.getExternalNodeCount(), stepTree.getExternalNodeCount(),
                    "Krok " + i + " trajektorii ma nieprawidłową liczbę liści!");

            String newick = stepTree.toString();
            assertFalse(newick.contains("null"), "Newick w kroku " + i + " zawiera wskaźnik null: " + newick);
        }
    }
}