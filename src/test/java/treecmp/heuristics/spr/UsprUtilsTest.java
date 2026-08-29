package treecmp.heuristics.spr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.Tree;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.util.TreeCreator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy integralności topologicznej i składni Newick dla UsprUtils")
public class UsprUtilsTest {

    // ===========================================================================
    // NOWE TESTY METADANYCH (Księgowanie ruchów i kosztów)
    // ===========================================================================

    @Test
    @DisplayName("Test regresyjny: forEachUsprTree musi poprawnie rejestrować obiekt ruchu (TreeMove) i jego koszt NNI")
    public void testForEachUsprTreeRegistersMovesAndCosts() throws Exception {
        // Drzewo N=7
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),(3,4)),(5,(6,7)));");
        assertNotNull(baseTree);

        UsprUtils usprUtils = new UsprUtils();
        List<Tree> neighbors = new ArrayList<>();

        // Zbieramy wszystkich legalnych sąsiadów uSPR
        usprUtils.forEachUsprTree(baseTree, neighbors::add);
        assertTrue(neighbors.size() > 0, "Lista sąsiadów uSPR nie może być pusta");

        int registeredMovesCount = 0;

        for (Tree neighbor : neighbors) {
            // 1. Sprawdzamy czy dla wygenerowanego drzewa zarejestrowano odpowiedni obiekt ruchu
            TreeMove move = usprUtils.getMoveForTree(neighbor);
            assertNotNull(move, "BŁĄD: Nie zarejestrowano obiektu TreeMove dla wygenerowanego sąsiada!");
            assertTrue(move instanceof SprMove, "Ruch uSPR powinien być instancją SprMove");

            // 2. Pobieramy zarejestrowany w mapie koszt (jako double)
            double registeredCost = usprUtils.getTreeCost(neighbor);

            // 3. Asercje poprawnego księgowania
            assertTrue(registeredCost > 0.0, "Koszt rotacji NNI musi być większy od zera!");
            assertEquals((double) move.getNniEquivalentCost(), registeredCost, 0.0001,
                    "Zarejestrowany koszt w mapie różni się od kosztu fizycznego wewnątrz obiektu SprMove!");

            registeredMovesCount++;
        }

        assertEquals(neighbors.size(), registeredMovesCount,
                "Liczba zarejestrowanych ruchów musi odpowiadać dokładnie liczbie wygenerowanych unikalnych sąsiadów");
    }

    // ===========================================================================
    // TESTY REGRESYJNE DLA findBestNeighbour (Ochrona przed błędem SPR/uSPR)
    // ===========================================================================

    @Test
    @DisplayName("findBestNeighbour musi używać logiki uSPR i nigdy nie może zwrócić uszkodzonego Newicka (test na RF plateau)")
    public void testFindBestNeighbourNeverReturnsCorruptedTreeOnPlateau() throws Exception {
        // Drzewo testowe 10 liści
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),(4,5)),((6,7),(8,9)),10);");
        assertNotNull(baseTree);

        UsprUtils usprUtils = new UsprUtils();
        int expectedLeaves = baseTree.getExternalNodeCount();
        int expectedCommas = expectedLeaves - 1;

        // Tworzymy sztuczny BestTreeChooser symulujący płaskowyż metryki RF
        BestTreeChooser mockPlateauChooser = new BestTreeChooser() {
            @Override
            public double getValueForTree(Tree tree) {
                return 10.0 + (tree.toString().length() % 3);
            }
        };

        // Wywołujemy findBestNeighbour z neighSizeFrac = 1.0 (przeszukaj 100% sąsiadów)
        TreeValuePair resultPair = usprUtils.findBestNeighbour(baseTree, mockPlateauChooser, 1.0, 100.0);

        assertNotNull(resultPair, "TreeValuePair nie może być null");
        Tree bestTree = resultPair.getTree();
        assertNotNull(bestTree, "findBestNeighbour nie znalazł żadnego legalnego sąsiada!");

        String newick = bestTree.toString();

        assertFalse(newick.contains("null"), "Newick zawiera 'null': " + newick);
        assertFalse(newick.contains(",,"), "Newick zawiera podwójny przecinek ',,': " + newick);
        assertFalse(newick.contains("()"), "Newick zawiera puste nawiasy '()': " + newick);

        assertEquals(expectedLeaves, bestTree.getExternalNodeCount(), "Błędna liczba liści w najlepszym sąsiedzie!");
        assertEquals(expectedCommas, countChar(newick, ','), "Nieprawidłowa liczba przecinków w Newicku: " + newick);
        assertEquals(countChar(newick, '('), countChar(newick, ')'), "Niezbalansowane nawiasy w Newicku: " + newick);

        Set<String> leafNames = new HashSet<>();
        for (int i = 0; i < bestTree.getExternalNodeCount(); i++) {
            String name = bestTree.getExternalNode(i).getIdentifier().getName();
            assertTrue(leafNames.add(name), "Duplikat etykiety liścia '" + name + "' w najlepszym sąsiedzie: " + newick);
        }
    }

    @Test
    @DisplayName("calcUsprNeighbours musi być różne od calcSprNeighbours")
    public void testCalcUsprNeighboursIsDistinctFromSpr() throws Exception {
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),(3,4)),(5,(6,7)));");
        UsprUtils usprUtils = new UsprUtils();

        int usprCount = usprUtils.calcUsprNeighbours(baseTree);

        SprUtils sprUtils = new SprUtils();
        int sprCount = sprUtils.calcSprNeighbours(baseTree);

        assertEquals(56, usprCount, "Nieprawidłowo wyliczona liczba sąsiadów uSPR dla N=7");
        assertNotEquals(sprCount, usprCount, "Liczba sąsiadów uSPR nie może być identyczna z ukorzenionym SPR!");
    }

    // =========================================================================
    // WCZEŚNIEJSZE TESTY INTEGRALNOŚCI (createUsprTree & forEachUsprTree)
    // =========================================================================

    @Test
    @DisplayName("Każde wygenerowane drzewo z createUsprTree musi mieć unikalne liście i poprawną liczbę przecinków")
    public void testCreateUsprTreeNeverReturnsDuplicatedLeavesOrBrokenNewick() throws Exception {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),(4,5)),((6,7),(8,9)),10);");
        assertNotNull(baseTree, "Drzewo bazowe nie może być null");

        UsprUtils usprUtils = new UsprUtils();
        int expectedLeaves = baseTree.getExternalNodeCount();
        int expectedCommas = expectedLeaves - 1;

        int extCount = baseTree.getExternalNodeCount();
        int intCount = baseTree.getInternalNodeCount();
        int validMovesCount = 0;

        for (int i = 0; i < extCount + intCount; i++) {
            Node s = getNodeByIndex(baseTree, i, extCount);
            if (s.isRoot()) continue;

            for (int j = 0; j < extCount + intCount; j++) {
                Node t = getNodeByIndex(baseTree, j, extCount);
                if (t.isRoot() || s == t) continue;

                Tree resultTree = usprUtils.createUsprTree(baseTree, s, t);
                if (resultTree == null) continue;
                validMovesCount++;

                String newick = resultTree.toString();

                assertEquals(expectedLeaves, resultTree.getExternalNodeCount(),
                        "Błędna liczba liści po ruchu uSPR: " + newick);

                Set<String> uniqueLeafNames = new HashSet<>();
                for (int k = 0; k < resultTree.getExternalNodeCount(); k++) {
                    String leafName = resultTree.getExternalNode(k).getIdentifier().getName();
                    assertTrue(uniqueLeafNames.add(leafName),
                            "WYKRYTO DUPLIKAT LIŚCIA '" + leafName + "' w Newicku: " + newick);
                }

                assertEquals(expectedCommas, countChar(newick, ','),
                        "USZKODZONY NEWICK (zła liczba przecinków): " + newick);

                assertFalse(newick.contains("null"), "Newick zawiera wskaźnik 'null': " + newick);
                assertFalse(newick.contains(",,"), "Newick zawiera podwójny przecinek: " + newick);
                assertFalse(newick.contains("()"), "Newick zawiera puste nawiasy: " + newick);

                for (int k = 0; k < resultTree.getInternalNodeCount(); k++) {
                    Node internalNode = resultTree.getInternalNode(k);
                    if (!internalNode.isRoot()) {
                        assertTrue(internalNode.getChildCount() >= 2,
                                "Węzeł wewnętrzny ma stopień < 2: " + newick);
                    }
                }
            }
        }

        assertTrue(validMovesCount > 0, "Test powinien zweryfikować poprawność legalnych ruchów uSPR");
    }

    @Test
    @DisplayName("forEachUsprTree nie ma prawa zwrócić ani jednego zduplikowanego lub uszkodzonego sąsiada")
    public void testGenerateNeighboursIntegrity() throws Exception {
        Tree baseTree = TreeCreator.getTreeFromString("(((A,B),(C,D)),(E,(F,G)));");
        UsprUtils usprUtils = new UsprUtils();

        List<Tree> neighbors = new ArrayList<>();
        usprUtils.forEachUsprTree(baseTree, neighbors::add);

        assertNotNull(neighbors);
        assertTrue(neighbors.size() > 0, "Lista sąsiadów uSPR nie może być pusta");

        int expectedCommas = baseTree.getExternalNodeCount() - 1;

        for (int i = 0; i < neighbors.size(); i++) {
            Tree neighbor = neighbors.get(i);
            assertNotNull(neighbor, "Sąsiad na indeksie " + i + " jest null");

            String newick = neighbor.toString();
            assertEquals(expectedCommas, countChar(newick, ','),
                    "Sąsiad uSPR #" + i + " ma nieprawidłową strukturę Newick: " + newick);
        }
    }

    // =========================================================================
    // METODY POMOCNICZE I TESTY SZCZEGÓŁOWE (Z logów błędów)
    // =========================================================================

    private Node getNodeByIndex(Tree tree, int index, int extCount) {
        if (index < extCount) {
            return tree.getExternalNode(index);
        } else {
            return tree.getInternalNode(index - extCount);
        }
    }

    private int countChar(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) count++;
        }
        return count;
    }

    @Test
    @DisplayName("Test regresyjny: Problem zduplikowanych poddrzew dla drzew wejściowych z logów VND")
    public void testPoisonPillTreesFromVndLogs() {
        String poisonTreeRf = "(((((7:0.0000000,17:0.0000000):0.0000000,(11:0.0000000,2:0.0000000):0.0000000):0.0000000,(19:0.0000000,(1:0.0000000,(3:0.0000000,14:0.0000000):0.0000000):0.0000000):0.0000000):0.0000000,((5:0.0000000,(8:0.0000000,(18:0.0000000,20:0.0000000):0.0000000):0.0000000):0.0000000,(9:0.0000000,13:0.0000000):0.0000000):0.0000000):0.0000000,(6:0.0000000,((12:0.0000000,10:0.0000000):0.0000000,(16:0.0000000,15:0.0000000):0.0000000):0.0000000):0.0000000,4:0.0000000);";
        String poisonTreeMs = "(((12:0.0000000,((5:0.0000000,8:0.0000000):0.0000000,((9:0.0000000,17:0.0000000):0.0000000,16:0.0000000):0.0000000):0.0000000):0.0000000,13:0.0000000):0.0000000,((20:0.0000000,1:0.0000000):0.0000000,(2:0.0000000,(19:0.0000000,14:0.0000000):0.0000000):0.0000000):0.0000000,(((11:0.0000000,((3:0.0000000,10:0.0000000):0.0000000,6:0.0000000):0.0000000):0.0000000,4:0.0000000):0.0000000,(18:0.0000000,(15:0.0000000,7:0.0000000):0.0000000):0.0000000):0.0000000);";

        String[] poisonTrees = { poisonTreeRf, poisonTreeMs };
        UsprUtils usprUtils = new UsprUtils();

        for (String newickInput : poisonTrees) {
            Tree baseTree = TreeCreator.getTreeFromString(newickInput);
            assertNotNull(baseTree, "Drzewo wejściowe musi się poprawnie parsować");

            List<Tree> neighbours = new ArrayList<>();
            usprUtils.forEachUsprTree(baseTree, neighbours::add);
            assertTrue(neighbours.size() > 0, "Powinno wygenerować legalnych sąsiadów uSPR");

            for (Tree neighbour : neighbours) {
                String newickOutput = neighbour.toString();
                for (int i = 0; i < neighbour.getExternalNodeCount(); i++) {
                    String leafName = neighbour.getExternalNode(i).getIdentifier().getName();
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:\\(|,)" + java.util.regex.Pattern.quote(leafName) + ":");
                    java.util.regex.Matcher matcher = pattern.matcher(newickOutput);

                    int count = 0;
                    while (matcher.find()) {
                        count++;
                    }
                    assertEquals(1, count, "Liść '" + leafName + "' musi występować DOKŁADNIE RAZ w Newicku: " + newickOutput);
                }
            }
        }
    }

    @Test
    @DisplayName("Test regresyjny z logów VND: Ochrona przed klonowaniem poddrzew i utratą liści (Logi RF i MS)")
    public void testPoisonPillTreesFromVndLogs_FourBrokenCases() throws Exception {
        String poisonTreeRf_120800 = "(((((7:0.0000000,17:0.0000000):0.0000000,(11:0.0000000,2:0.0000000):0.0000000):0.0000000,(19:0.0000000,(1:0.0000000,(3:0.0000000,14:0.0000000):0.0000000):0.0000000):0.0000000):0.0000000,((5:0.0000000,(8:0.0000000,(18:0.0000000,20:0.0000000):0.0000000):0.0000000):0.0000000,(9:0.0000000,13:0.0000000):0.0000000):0.0000000):0.0000000,(6:0.0000000,((12:0.0000000,10:0.0000000):0.0000000,(16:0.0000000,15:0.0000000):0.0000000):0.0000000):0.0000000,4:0.0000000);";
        String poisonTreeMs_121109 = "(((12:0.0000000,((5:0.0000000,8:0.0000000):0.0000000,((9:0.0000000,17:0.0000000):0.0000000,16:0.0000000):0.0000000):0.0000000):0.0000000,13:0.0000000):0.0000000,((20:0.0000000,1:0.0000000):0.0000000,(2:0.0000000,(19:0.0000000,14:0.0000000):0.0000000):0.0000000):0.0000000,(((11:0.0000000,((3:0.0000000,10:0.0000000):0.0000000,6:0.0000000):0.0000000):0.0000000,4:0.0000000):0.0000000,(18:0.0000000,(15:0.0000000,7:0.0000000):0.0000000):0.0000000):0.0000000);";

        String[] poisonTrees = { poisonTreeRf_120800, poisonTreeMs_121109 };
        UsprUtils usprUtils = new UsprUtils();
        int expectedLeaves = 20;
        int expectedCommas = expectedLeaves - 1;

        for (String newickInput : poisonTrees) {
            Tree baseTree = TreeCreator.getTreeFromString(newickInput);
            assertNotNull(baseTree, "Drzewo startowe musi się poprawnie parsować w PAL");

            List<Tree> neighbours = new ArrayList<>();
            usprUtils.forEachUsprTree(baseTree, neighbours::add);
            assertTrue(neighbours.size() > 0, "Powinno wygenerować listę legalnych sąsiadów uSPR");

            for (int i = 0; i < neighbours.size(); i++) {
                Tree neighbour = neighbours.get(i);
                String newickOutput = neighbour.toString();

                int commaCount = countChar(newickOutput, ',');
                assertEquals(expectedCommas, commaCount,
                        "Błąd! Wykryto niedozwoloną liczbę przecinków (" + commaCount + ") w sąsiedzie #" + i + ": " + newickOutput);

                Set<String> seenLeaves = new HashSet<>();
                for (int leafIdx = 0; leafIdx < neighbour.getExternalNodeCount(); leafIdx++) {
                    String leafName = neighbour.getExternalNode(leafIdx).getIdentifier().getName();
                    assertTrue(seenLeaves.add(leafName),
                            "Wykryto zduplikowany liść '" + leafName + "' w Newicku: " + newickOutput);
                }
                assertEquals(expectedLeaves, seenLeaves.size(),
                        "Liczba unikalnych liści po ruchu uSPR musi wynosić dokładnie 20!");
            }
        }
    }

    @Test
    public void testFinalTwoBrokenCases_MS_and_RF() throws Exception {
        String validTreeBeforeCrashMS = "(((12:0.0000000,((5:0.0000000,8:0.0000000):0.0000000,((9:0.0000000,17:0.0000000):0.0000000,16:0.0000000):0.0000000):0.0000000):0.0000000,13:0.0000000):0.0000000,((20:0.0000000,1:0.0000000):0.0000000,(2:0.0000000,(19:0.0000000,14:0.0000000):0.0000000):0.0000000):0.0000000,(((11:0.0000000,((3:0.0000000,10:0.0000000):0.0000000,6:0.0000000):0.0000000):0.0000000,4:0.0000000):0.0000000,(18:0.0000000,(15:0.0000000,7:0.0000000):0.0000000):0.0000000):0.0000000);";
        String validTreeBeforeCrashRF = "(((((7:0.0000000,17:0.0000000):0.0000000,(11:0.0000000,2:0.0000000):0.0000000):0.0000000,(19:0.0000000,(1:0.0000000,(3:0.0000000,14:0.0000000):0.0000000):0.0000000):0.0000000):0.0000000,((5:0.0000000,(8:0.0000000,(18:0.0000000,20:0.0000000):0.0000000):0.0000000):0.0000000,(9:0.0000000,13:0.0000000):0.0000000):0.0000000):0.0000000,(6:0.0000000,((12:0.0000000,10:0.0000000):0.0000000,(16:0.0000000,15:0.0000000):0.0000000):0.0000000):0.0000000,4:0.0000000);";

        String[] testTrees = { validTreeBeforeCrashMS, validTreeBeforeCrashRF };
        UsprUtils usprUtils = new UsprUtils();
        int expectedLeaves = 20;
        int expectedCommas = expectedLeaves - 1;

        for (int tIndex = 0; tIndex < testTrees.length; tIndex++) {
            Tree baseTree = TreeCreator.getTreeFromString(testTrees[tIndex]);
            assertNotNull(baseTree, "Drzewo startowe musi się poprawnie parsować w PAL");

            List<Tree> neighbours = new ArrayList<>();
            usprUtils.forEachUsprTree(baseTree, neighbours::add);
            assertTrue(neighbours.size() > 0, "Lista sąsiadów uSPR nie może być pusta");

            for (int i = 0; i < neighbours.size(); i++) {
                Tree neighbour = neighbours.get(i);
                assertNotNull(neighbour, "Sąsiad uSPR nie może być null");
                String newickOutput = neighbour.toString();

                assertDoesNotThrow(() -> new ReadTree(new java.io.PushbackReader(new java.io.StringReader(newickOutput))),
                        "Parser PAL odrzucił wygenerowanego sąsiada #" + i + ": " + newickOutput);

                int commaCount = countChar(newickOutput, ',');
                assertEquals(expectedCommas, commaCount,
                        "Wykryto zduplikowane poddrzewo (zła liczba przecinków: " + commaCount + ") w Newicku: " + newickOutput);

                Set<String> seenLeaves = new HashSet<>();
                for (int leafIdx = 0; leafIdx < neighbour.getExternalNodeCount(); leafIdx++) {
                    String leafName = neighbour.getExternalNode(leafIdx).getIdentifier().getName();
                    assertTrue(seenLeaves.add(leafName),
                            "Zduplikowana etykieta liścia '" + leafName + "' w Newicku: " + newickOutput);
                }
                assertEquals(expectedLeaves, seenLeaves.size(),
                        "Liczba liści w wygenerowanym sąsiedzie musi wynosić dokładnie 20!");
            }
        }
    }

    @Test
    @DisplayName("Test regresyjny: Brute-force po wszystkich parach (s, t) nie ma prawa tworzyć cykli ani duplikatów liści")
    public void testAllPairsCreateUsprTree_MustNeverDuplicateLeaves() throws Exception {
        String validTree161106 = "(((1:0.0000000,10:0.0000000):0.0000000,(3:0.0000000,4:0.0000000):0.0000000):0.0000000,(9:0.0000000,5:0.0000000):0.0000000,(6:0.0000000,(8:0.0000000,(2:0.0000000,7:0.0000000):0.0000000):0.0000000):0.0000000);";
        String validTree161107 = "((7:0.0000000,(6:0.0000000,(9:0.0000000,(5:0.0000000,1:0.0000000):0.0000000):0.0000000):0.0000000):0.0000000,(10:0.0000000,(2:0.0000000,(8:0.0000000,4:0.0000000):0.0000000):0.0000000):0.0000000,3:0.0000000);";

        String[] testTrees = { validTree161106, validTree161107 };
        UsprUtils usprUtils = new UsprUtils();
        int expectedLeaves = 10;
        int expectedCommas = 9;

        for (String newickInput : testTrees) {
            Tree baseTree = TreeCreator.getTreeFromString(newickInput);
            assertNotNull(baseTree);

            for (int sIdx = 0; sIdx < baseTree.getInternalNodeCount(); sIdx++) {
                pal.tree.Node s = baseTree.getInternalNode(sIdx);
                if (s == baseTree.getRoot()) continue;

                for (int tIdx = 0; tIdx < baseTree.getInternalNodeCount(); tIdx++) {
                    verifyCreatedTreeSafe(usprUtils.createUsprTree(baseTree, s, baseTree.getInternalNode(tIdx)), expectedLeaves, expectedCommas);
                }
                for (int tIdx = 0; tIdx < baseTree.getExternalNodeCount(); tIdx++) {
                    verifyCreatedTreeSafe(usprUtils.createUsprTree(baseTree, s, baseTree.getExternalNode(tIdx)), expectedLeaves, expectedCommas);
                }
            }
        }
    }

    private void verifyCreatedTreeSafe(Tree neighbour, int expectedLeaves, int expectedCommas) throws Exception {
        if (neighbour == null) return;

        String newickOutput = neighbour.toString();

        assertEquals(expectedCommas, countChar(newickOutput, ','),
                "Błędna liczba przecinków (klonowanie poddrzewa!) w Newicku: " + newickOutput);

        pal.tree.ReadTree parsed = new pal.tree.ReadTree(
                new java.io.PushbackReader(new java.io.StringReader(newickOutput))
        );
        assertEquals(expectedLeaves, parsed.getExternalNodeCount(),
                "Drzewo uległo obcięciu: " + newickOutput);

        Set<String> seenLeaves = new HashSet<>();
        for (int i = 0; i < parsed.getExternalNodeCount(); i++) {
            String leafName = parsed.getExternalNode(i).getIdentifier().getName();
            assertTrue(seenLeaves.add(leafName), "Zduplikowany liść '" + leafName + "' w Newicku: " + newickOutput);
        }
        assertEquals(expectedLeaves, seenLeaves.size(), "Liczba unikalnych liści musi wynosić dokładnie 10!");
    }
}