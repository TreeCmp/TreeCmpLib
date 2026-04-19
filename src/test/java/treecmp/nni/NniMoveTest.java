package treecmp.nni;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.util.TestTreeFactory;

class NniMoveTest {

    @Test
    void testNniMoveStoresCorrectNodes() {
        // Arrange: Pobieramy drzewo i konkretne węzły
        Tree tree = TestTreeFactory.fiveLeavesRootedCaterpillarTree(); // ((((1,2),3),4),5)
        Node node2 = TreeUtils.getNodeByName(tree, "2");
        Node node3 = TreeUtils.getNodeByName(tree, "3");

        // Act: Tworzymy nowy ruch NNI
        NniMove move = new NniMove(node2, node3);

        // Assert: Weryfikujemy ZAWARTOŚĆ obiektu NniMove
        assertNotNull(move, "Utworzony obiekt NniMove nie może być null");

        // 1. Sprawdzamy, czy referencje do węzłów zostały przypisane do odpowiednich pól
        assertEquals(node2, move.movingSubtree,
                "Pole movingSubtree powinno trzymać referencję do węzła 2");

        assertEquals(node3, move.swapPartner,
                "Pole swapPartner powinno trzymać referencję do węzła 3");

        // 2. Opcjonalnie: Upewniamy się na 100%, że wyciągnęliśmy dobre węzły (po nazwach)
        assertEquals("2", move.movingSubtree.getIdentifier().getName(),
                "Nazwa poruszanego węzła musi wynosić '2'");

        assertEquals("3", move.swapPartner.getIdentifier().getName(),
                "Nazwa węzła-partnera musi wynosić '3'");
    }

}