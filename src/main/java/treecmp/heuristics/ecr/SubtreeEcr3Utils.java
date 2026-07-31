package treecmp.heuristics.ecr;

import java.util.*;

import pal.misc.IdGroup;
import pal.tree.*;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;

public class SubtreeEcr3Utils extends TreeNeighborhoodUtils {

    private final boolean unrooted;
    private static final List<TopologyTemplate3sECR> TEMPLATES_105 = generate105Templates();

    public SubtreeEcr3Utils(boolean unrooted) {
        this.unrooted = unrooted;
    }

    public static List<TopologyTemplate3sECR> getTemplates() {
        return TEMPLATES_105;
    }

    @Override
    public Tree[] generateNeighbours(Tree tree) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        Set<treecmp.heuristics.TreeHolder> ecrTreeSet = new HashSet<>();

        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node rootOfCluster = tree.getInternalNode(i);
            List<List<Node>> clusters = getClusters(rootOfCluster, 4);

            for (List<Node> cluster : clusters) {
                List<Node> subtreesList = getBoundarySubtrees(cluster);
                if (subtreesList.size() != 5) continue;

                Node[] s = subtreesList.toArray(new Node[0]);
                TopologyTemplate3sECR originalSignature = extractSignature(rootOfCluster, cluster, subtreesList);

                for (TopologyTemplate3sECR template : TEMPLATES_105) {
                    if (template.isIsomorphic(originalSignature)) {
                        continue;
                    }

                    Tree newTree = createEcr3Tree(tree, cluster, s, template);
                    if (newTree != null) {
                        // 1. Tworzymy obiekt ruchu i przypisujemy do zmiennej
                        treecmp.heuristics.moves.Ecr3Move move =
                                new treecmp.heuristics.moves.Ecr3Move(cluster, s, template);

                        // 2. Rejestrujemy koszt
                        registerTreeCost(newTree, move.getNniEquivalentCost());

                        // 3. Rejestrujemy ruch w mapie
                        registerTreeMove(newTree, move);

                        if (unrooted) {
                            ecrTreeSet.add(new TreeUnrootedHolder(newTree, idGroup));
                        } else {
                            ecrTreeSet.add(new TreeRootedHolder(newTree, idGroup));
                        }
                    }
                }
            }
        }

        int n = ecrTreeSet.size();
        Tree[] ecrTreeArray = new Tree[n];
        int idx = 0;
        for (treecmp.heuristics.TreeHolder th : ecrTreeSet) {
            ecrTreeArray[idx++] = th.tree;
        }
        return ecrTreeArray;
    }

    // ========================================================================
    // PUBLICZNE METODY DLA HEURYSTYKI INKREMENTALNEJ
    // ========================================================================

    public List<List<Node>> getClusters(Node node, int size) {
        List<List<Node>> res = new ArrayList<>();
        if (size == 0) { res.add(new ArrayList<Node>()); return res; }
        if (node.isLeaf()) return res;
        if (size == 1) {
            List<Node> s = new ArrayList<>(); s.add(node);
            res.add(s); return res;
        }

        int childCount = node.getChildCount();
        List<List<Integer>> distributions = getDistributions(size - 1, childCount);

        for (List<Integer> dist : distributions) {
            List<List<List<Node>>> childRes = new ArrayList<>();
            boolean valid = true;
            for (int i = 0; i < childCount; i++) {
                List<List<Node>> cr = getClusters(node.getChild(i), dist.get(i));
                if (cr.isEmpty()) { valid = false; break; }
                childRes.add(cr);
            }
            if (valid) {
                List<List<Node>> product = cartesianProduct(childRes);
                for (List<Node> p : product) {
                    List<Node> combined = new ArrayList<>();
                    combined.add(node);
                    combined.addAll(p);
                    res.add(combined);
                }
            }
        }
        return res;
    }

    public List<Node> getBoundarySubtrees(List<Node> cluster) {
        List<Node> boundary = new ArrayList<>();
        for (Node n : cluster) {
            for (int i = 0; i < n.getChildCount(); i++) {
                Node child = n.getChild(i);
                if (!cluster.contains(child)) {
                    boundary.add(child);
                }
            }
        }
        return boundary;
    }

    public TopologyTemplate3sECR extractSignature(Node current, List<Node> cluster, List<Node> boundary) {
        if (!cluster.contains(current)) {
            return new TopologyTemplate3sECR(boundary.indexOf(current));
        }
        return new TopologyTemplate3sECR(
                extractSignature(current.getChild(0), cluster, boundary),
                extractSignature(current.getChild(1), cluster, boundary)
        );
    }

    // ========================================================================
    // ODBUDOWA DRZEWA (WSTRZYKIWANIE 105 SZABLONÓW - CLONE & IN PLACE)
    // ========================================================================

    public Tree createEcr3Tree(Tree tree, List<Node> cluster, Node[] s, TopologyTemplate3sECR template) {
        try {
            List<List<Integer>> pathCluster = new ArrayList<>();
            for (Node n : cluster) {
                List<Integer> p = new ArrayList<>(); getPathToNode(tree.getRoot(), n, p);
                if (n != tree.getRoot() && p.isEmpty()) return null; // Zabezpieczenie
                pathCluster.add(p);
            }
            List<List<Integer>> pathS = new ArrayList<>();
            for (Node sub : s) {
                List<Integer> p = new ArrayList<>(); getPathToNode(tree.getRoot(), sub, p);
                if (sub != tree.getRoot() && p.isEmpty()) return null; // Zabezpieczenie
                pathS.add(p);
            }

            Tree newTree = fastTreeClone(tree);
            Node root = newTree.getRoot();

            Node[] availableNodes = new Node[4];
            for (int i = 0; i < 4; i++) availableNodes[i] = findNodeByPath(root, pathCluster.get(i));
            Node[] newS = new Node[5];
            for (int i = 0; i < 5; i++) newS[i] = findNodeByPath(root, pathS.get(i));

            Node top = availableNodes[0];
            List<Integer> topPorts = new ArrayList<>();
            for (int i = 0; i < top.getChildCount(); i++) {
                Node c = top.getChild(i);
                boolean isTarget = false;
                for (Node an : availableNodes) { if (an == c) isTarget = true; }
                for (Node ns : newS) { if (ns == c) isTarget = true; }
                if (isTarget) topPorts.add(i);
            }

            for (int i = 1; i < 4; i++) {
                Node n = availableNodes[i];
                while(n.getChildCount() > 0) n.removeChild(0);
            }

            bindPhysicalTemplate(template, top, availableNodes, 1, newS, topPorts);

            return refreshTreeInPlace(newTree);
        } catch (Exception e) {
            return null;
        }
    }

    public Tree applyPhysicalMove(Tree tree, treecmp.heuristics.moves.Ecr3Move move) {
        List<Node> cluster = move.cluster;
        Node[] s = move.boundarySubtrees;
        TopologyTemplate3sECR template = move.template;

        Node top = cluster.get(0);

        // 1. Ochrona 3-go portu korzenia: identyfikujemy porty zaangażowane w ruch
        List<Integer> topPorts = new ArrayList<>();
        for (int i = 0; i < top.getChildCount(); i++) {
            Node c = top.getChild(i);
            if (cluster.contains(c) || Arrays.asList(s).contains(c)) {
                topPorts.add(i);
            }
        }

        // 2. Czyścimy dzieci wszystkich oprócz "top"
        for (int i = 1; i < cluster.size(); i++) {
            Node n = cluster.get(i);
            while(n.getChildCount() > 0) n.removeChild(0);
        }

        // 3. Budowa nowej topologii za pomocą adaptera
        Node[] available = cluster.toArray(new Node[0]);
        bindPhysicalTemplate(template, top, available, 1, s, topPorts);

        return tree;
    }

    private int bindPhysicalTemplate(TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int nextAvailIdx, Node[] s, List<Integer> topPorts) {
        int idx = nextAvailIdx;
        boolean isTop = (currentInternal == available[0]);
        int portLeft = isTop ? topPorts.get(0) : 0;
        int portRight = isTop ? topPorts.get(1) : 1;

        // Lewe dziecko
        if (temp.left.leafIndex != -1) {
            Node child = s[temp.left.leafIndex];
            if (isTop) currentInternal.setChild(portLeft, child); else currentInternal.insertChild(child, 0);
            child.setParent(currentInternal);
        } else {
            Node nextInternal = available[idx++];
            if (isTop) currentInternal.setChild(portLeft, nextInternal); else currentInternal.insertChild(nextInternal, 0);
            nextInternal.setParent(currentInternal);
            idx = bindPhysicalTemplate(temp.left, nextInternal, available, idx, s, null);
        }

        // Prawe dziecko
        if (temp.right.leafIndex != -1) {
            Node child = s[temp.right.leafIndex];
            if (isTop) currentInternal.setChild(portRight, child); else currentInternal.insertChild(child, 1);
            child.setParent(currentInternal);
        } else {
            Node nextInternal = available[idx++];
            if (isTop) currentInternal.setChild(portRight, nextInternal); else currentInternal.insertChild(nextInternal, 1);
            nextInternal.setParent(currentInternal);
            idx = bindPhysicalTemplate(temp.right, nextInternal, available, idx, s, null);
        }
        return idx;
    }

    // ========================================================================
    // GENERATOR 105 TOPOLOGII (ALGEBRAICZNY GENERATOR DRZEW)
    // ========================================================================

    private static List<TopologyTemplate3sECR> generate105Templates() {
        List<Integer> leaves = Arrays.asList(0, 1, 2, 3, 4);
        List<TopologyTemplate3sECR> list = generateTrees(leaves);

        // BEZPIECZNY ETAP 2: Cała lista jest już gotowa w pamięci,
        // więc możemy bez ryzyka NPE wyliczyć kroki pośrednie:
        for (TopologyTemplate3sECR t : list) {
            t.nniTrajectoryTemplates = t.buildTrajectoryTemplates(list);
        }

        return list;
    }

    private static List<TopologyTemplate3sECR> generateTrees(List<Integer> elements) {
        List<TopologyTemplate3sECR> result = new ArrayList<>();
        if (elements.size() == 1) {
            result.add(new TopologyTemplate3sECR(elements.get(0)));
            return result;
        }
        int first = elements.get(0);
        List<Integer> rest = elements.subList(1, elements.size());

        int maxMask = 1 << rest.size();
        for (int mask = 0; mask < maxMask; mask++) {
            List<Integer> L = new ArrayList<>();
            List<Integer> R = new ArrayList<>();
            L.add(first);
            for (int i = 0; i < rest.size(); i++) {
                if ((mask & (1 << i)) != 0) L.add(rest.get(i));
                else R.add(rest.get(i));
            }
            if (R.isEmpty()) continue;

            List<TopologyTemplate3sECR> leftTrees = generateTrees(L);
            List<TopologyTemplate3sECR> rightTrees = generateTrees(R);
            for (TopologyTemplate3sECR lt : leftTrees) {
                for (TopologyTemplate3sECR rt : rightTrees) {
                    result.add(new TopologyTemplate3sECR(lt, rt));
                }
            }
        }
        return result;
    }
    public static class TopologyTemplate3sECR {
        public int leafIndex = -1;
        public TopologyTemplate3sECR left;
        public TopologyTemplate3sECR right;
        public final int nniCost;
        public List<TopologyTemplate3sECR> nniTrajectoryTemplates;

        // 1. Konstruktor dla liścia
        public TopologyTemplate3sECR(int leafIndex) {
            this.leafIndex = leafIndex;
            this.nniCost = 0;
            this.nniTrajectoryTemplates = Collections.emptyList();
        }

        // 2. Konstruktor dla węzła wewnętrznego
        public TopologyTemplate3sECR(TopologyTemplate3sECR l, TopologyTemplate3sECR r) {
            this.left = l;
            this.right = r;
            this.nniCost = calculateTemplateNniCost();
            this.nniTrajectoryTemplates = Collections.emptyList();
        }

        // 3. BRAKUJĄCA METODA: Sprawdzenie izomorfizmu (identyczności struktury)
        public boolean isIsomorphic(TopologyTemplate3sECR other) {
            if (other == null) return false;
            if (this.leafIndex != -1 || other.leafIndex != -1) {
                return this.leafIndex == other.leafIndex;
            }
            return (this.left != null && this.left.isIsomorphic(other.left))
                    && (this.right != null && this.right.isIsomorphic(other.right));
        }

        private int calculateTemplateNniCost() {
            List<Integer> leavesOrder = new ArrayList<>();
            collectLeafIndices(this, leavesOrder);

            int displacedCount = 0;
            for (int i = 0; i < leavesOrder.size(); i++) {
                if (leavesOrder.get(i) != i) displacedCount++;
            }

            // Prawdziwa wycena topologiczna w 5-liściowym klastrze:
            switch (displacedCount) {
                case 0:  return 0; // Identity (brak ruchu)
                case 2:  return 1; // Prawdziwa 1 rotacja NNI (wymiana 2 liści)
                case 3:
                case 4:  return 2; // 2 rotacje NNI
                case 5:  return 3; // 3 rotacje NNI
                default: return 2;
            }
        }

        public List<TopologyTemplate3sECR> buildTrajectoryTemplates(List<TopologyTemplate3sECR> allTemplates) {
            // Jeśli to szablon startowy (Identity) lub brak katalogu - brak trajektorii
            if (this.leafIndex != -1 || this.nniCost == 0 || allTemplates == null || allTemplates.isEmpty()) {
                return Collections.emptyList();
            }

            List<TopologyTemplate3sECR> trajectory = new ArrayList<>();
            List<Integer> targetOrder = new ArrayList<>();
            collectLeafIndices(this, targetOrder);

            // KROK 1: Szukamy PRAWDZIWEGO szablonu 1-NNI (o katalogowym koszcie dokładnie 1)
            TopologyTemplate3sECR step1 = findBestIntermediateTemplate(targetOrder, 1, allTemplates, this, null);
            if (step1 != null) {
                trajectory.add(step1);
            }

            // KROK 2: Jeśli ruch jest oddalony o >= 3 NNI, szukamy drugiego szablonu o koszcie 2 NNI
            if (this.nniCost >= 3) {
                TopologyTemplate3sECR step2 = findBestIntermediateTemplate(targetOrder, 2, allTemplates, this, step1);
                if (step2 != null) {
                    trajectory.add(step2);
                }
            }

            return trajectory;
        }

        private TopologyTemplate3sECR findBestIntermediateTemplate(
                List<Integer> targetOrder,
                int desiredCost,
                List<TopologyTemplate3sECR> allTemplates,
                TopologyTemplate3sECR exclude1,
                TopologyTemplate3sECR exclude2) {

            TopologyTemplate3sECR bestMatch = null;
            int bestAgreement = -1;

            for (TopologyTemplate3sECR candidate : allTemplates) {
                // WYMÓG: Kandydat MUSI mieć dokładnie żądany koszt NNI (np. 1 dla step1)
                if (candidate.nniCost != desiredCost ||
                        (exclude1 != null && (candidate == exclude1 || candidate.isIsomorphic(exclude1))) ||
                        (exclude2 != null && (candidate == exclude2 || candidate.isIsomorphic(exclude2)))) {
                    continue;
                }

                List<Integer> candidateOrder = new ArrayList<>();
                collectLeafIndices(candidate, candidateOrder);

                int agreement = 0;
                for (int i = 0; i < targetOrder.size(); i++) {
                    if (candidateOrder.get(i).equals(targetOrder.get(i))) {
                        agreement++;
                    }
                }

                if (agreement > bestAgreement) {
                    bestAgreement = agreement;
                    bestMatch = candidate;
                }
            }
            return bestMatch;
        }

        private void collectLeafIndices(TopologyTemplate3sECR node, List<Integer> list) {
            if (node.leafIndex != -1) {
                list.add(node.leafIndex);
                return;
            }
            if (node.left != null) collectLeafIndices(node.left, list);
            if (node.right != null) collectLeafIndices(node.right, list);
        }
    }

    private List<List<Integer>> getDistributions(int items, int buckets) {
        List<List<Integer>> res = new ArrayList<>();
        if (buckets == 1) {
            res.add(Arrays.asList(items)); return res;
        }
        for (int i = 0; i <= items; i++) {
            for (List<Integer> tail : getDistributions(items - i, buckets - 1)) {
                List<Integer> dist = new ArrayList<>(); dist.add(i); dist.addAll(tail);
                res.add(dist);
            }
        }
        return res;
    }

    private List<List<Node>> cartesianProduct(List<List<List<Node>>> lists) {
        List<List<Node>> res = new ArrayList<>();
        if (lists.isEmpty()) { res.add(new ArrayList<Node>()); return res; }
        List<List<Node>> firstList = lists.get(0);
        List<List<Node>> tailProduct = cartesianProduct(lists.subList(1, lists.size()));
        for (List<Node> first : firstList) {
            for (List<Node> tail : tailProduct) {
                List<Node> combined = new ArrayList<>();
                combined.addAll(first); combined.addAll(tail); res.add(combined);
            }
        }
        return res;
    }
}