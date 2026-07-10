package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.*;

public class M3IncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree currentVirtualTree;
    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private IdGroup baseIdGroup;
    private int[][] targetLcaMatrix;
    private int[] targetIdToCol;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;

    private int[] u;
    private int[] v;

    private int[] currentT1TripletCount;
    private int[] t2IntTripletCount;

    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();
    private final Stack<StateRecord> history = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        history.clear();

        if (baseTree != null && targetTree != null) {
            this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.baseTree = createCleanCopy(baseTree);
            this.targetTree = createCleanCopy(targetTree);
            this.currentVirtualTree = createCleanCopy(this.baseTree);

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.N = this.baseTree.getExternalNodeCount();

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];

            // 1. Translacja Target Tree (Unikalne PAL ID -> Kolumna LAP)
            int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
            this.targetIdToCol = new int[maxNodesT2];
            Arrays.fill(this.targetIdToCol, -1);
            for (int i = 0; i < intT2Num; i++) {
                this.targetIdToCol[this.targetTree.getInternalNode(i).getNumber()] = i;
            }

            // 2. Translacja Base Tree (Unikalne PAL ID -> Wiersz LAP)
            int maxNodesT1 = getSafeMaxNodeId(this.baseTree);
            int[] baseIdToRow = new int[maxNodesT1];
            Arrays.fill(baseIdToRow, -1);
            for (int i = 0; i < intT1Num; i++) {
                baseIdToRow[this.baseTree.getInternalNode(i).getNumber()] = i;
            }

            // ROZWIĄZANIE: Obie macierze budujemy na bazie TEJ SAMEJ globalnej grupy ID!
            // Dzięki temu indeksy i, j, k w pętlach oznaczają fizycznie te same liście w obu drzewach.
            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(this.baseTree, this.baseIdGroup);
            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);

            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, cSize2);

            this.t2IntTripletCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                this.t2IntTripletCount[i] = coutTriplets(this.targetTree.getInternalNode(i), cSize2);
            }

            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, cSize1);

            this.currentT1TripletCount = new int[dim];
            for (int i = 0; i < intT1Num; i++) {
                this.currentT1TripletCount[i] = coutTriplets(this.baseTree.getInternalNode(i), cSize1);
            }

            // 3. Bezbłędne liczenie Intersection na bezpiecznych i spójnych osiach N
            int[][] initialIntersection = new int[dim][dim];
            for (int i = 0; i < N; i++) {
                for (int j = i + 1; j < N; j++) {
                    for (int k = j + 1; k < N; k++) {
                        int ind1 = getNcvByCanonicalId(i, j, k, lcaMatrix1);
                        int ind2 = getNcvByCanonicalId(i, j, k, this.targetLcaMatrix);

                        if (ind1 >= 0 && ind1 < baseIdToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                            int r = baseIdToRow[ind1];
                            int c = this.targetIdToCol[ind2];
                            if (r >= 0 && c >= 0) {
                                initialIntersection[r][c]++;
                            }
                        }
                    }
                }
            }

            for (int r = 0; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        assigncost[r][c] = this.currentT1TripletCount[r] + t2IntTripletCount[c] - (initialIntersection[r][c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        assigncost[r][c] = t2IntTripletCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        assigncost[r][c] = this.currentT1TripletCount[r];
                    } else {
                        assigncost[r][c] = 0;
                    }
                }
            }

            int[][] lapCost = new int[dim][dim];
            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
            }

            this.u = new int[dim];
            this.v = new int[dim];

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, this.u, this.v);
            this.currentDistance = 0.5 * rawMetric;
        } else {
            this.currentDistance = 0;
        }
    }

    private double calculateCleanSlateDistance(SimpleTree tNew, boolean isCommit, int maxCostBound) {

        // 1. Zapisujemy sygnatury przed czyszczeniem
        Map<String, Integer> sigToOldRow = new HashMap<>();
        for (int i = 0; i < intT1Num; i++) {
            Node n = tNew.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            sigToOldRow.put(sig.hash, i);
        }

        // 2. Klonujemy drzewo (tym razem bezpiecznie, bez gmerania w ID)
        Tree tPerfect = createCleanCopy(tNew);

        // 3. Permutacje oparte na odpornych na wszystko Sygnaturach
        int[] newToOld = new int[dim];
        int[] oldToNew = new int[dim];
        for (int i = 0; i < dim; i++) { newToOld[i] = i; oldToNew[i] = i; }

        for (int r_new = 0; r_new < intT1Num; r_new++) {
            Node n = tPerfect.getInternalNode(r_new);
            Signature sig = new Signature(n, N, baseIdGroup);
            Integer r_old = sigToOldRow.get(sig.hash);
            if (r_old != null) {
                newToOld[r_new] = r_old;
                oldToNew[r_old] = r_new;
            }
        }

        // 4. Translacja unikalnych ID biblioteki PAL -> Wiersze LAP
        int maxNodesNew = getSafeMaxNodeId(tPerfect);
        int[] idToRow = new int[maxNodesNew];
        Arrays.fill(idToRow, -1);
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            idToRow[tPerfect.getInternalNode(r_new).getNumber()] = r_new;
        }

        // 5. Bezbłędne LCA wymuszone na globalnej, uniwersalnej osi baseIdGroup!
        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tPerfect, this.baseIdGroup);
        int[][] newIntersection = new int[dim][dim];

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                for (int k = j + 1; k < N; k++) {
                    int ind1 = getNcvByCanonicalId(i, j, k, lcaNew);
                    int ind2 = getNcvByCanonicalId(i, j, k, this.targetLcaMatrix);

                    if (ind1 >= 0 && ind1 < idToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                        int r_new = idToRow[ind1];
                        int c = this.targetIdToCol[ind2];
                        if (r_new >= 0 && c >= 0) {
                            newIntersection[r_new][c]++;
                        }
                    }
                }
            }
        }

        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tPerfect);
        TreeCmpUtils.calcCladeSizes(tPerfect, postOrderNew, cSizeNew);

        int[] newT1TripletCount = new int[dim];
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            newT1TripletCount[r_new] = coutTriplets(tPerfect.getInternalNode(r_new), cSizeNew);
        }

        // 6. Składanie przypisań kosztów
        int[][] tempAssigncost = new int[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = newT1TripletCount[r] + t2IntTripletCount[c] - (newIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t2IntTripletCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    tempAssigncost[r][c] = newT1TripletCount[r];
                } else {
                    tempAssigncost[r][c] = 0;
                }
            }
        }

        int[][] lapCost = new int[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(tempAssigncost[i], 0, lapCost[i], 0, dim);
        }

        // 7. Bezpieczna permutacja Zmiennych Dualnych LAP
        int[] tempRowsol = new int[dim];
        int[] tempColsol = new int[dim];
        int[] tempU = new int[dim];
        int[] tempV = this.v.clone();

        for (int r_new = 0; r_new < dim; r_new++) {
            int r_old = newToOld[r_new];
            tempU[r_new] = this.u[r_old];
            tempRowsol[r_new] = this.rowsol[r_old];
        }

        for (int c = 0; c < dim; c++) {
            int r_old = this.colsol[c];
            tempColsol[c] = oldToNew[r_old];
        }

        int[][] mappedOldAssigncost = new int[dim][dim];
        for (int r_new = 0; r_new < dim; r_new++) {
            mappedOldAssigncost[r_new] = this.assigncost[newToOld[r_new]];
        }

        List<Integer> changedRowsList = new ArrayList<>();
        for (int r_new = 0; r_new < dim; r_new++) {
            if (!Arrays.equals(mappedOldAssigncost[r_new], tempAssigncost[r_new])) {
                changedRowsList.add(r_new);
            }
        }
        int[] changedRows = changedRowsList.stream().mapToInt(i -> i).toArray();

        int rawMetric;
        if (changedRows.length > 0 && changedRows.length < dim && maxCostBound >= 0) {
            rawMetric = LapSolver.lapUpdateBoundedInt(dim, lapCost, tempRowsol, tempColsol, tempU, tempV, changedRows, maxCostBound);
        } else {
            rawMetric = LapSolver.lap(dim, lapCost, tempRowsol, tempColsol, tempU, tempV);
        }

        double dist = 0.5 * rawMetric;

        // 8. Commit
        if (isCommit) {
            history.push(new StateRecord(this.assigncost, this.rowsol, this.colsol, this.u, this.v, this.currentDistance, this.currentVirtualTree, this.currentT1TripletCount));
            this.assigncost = tempAssigncost;
            this.currentVirtualTree = tPerfect;
            this.currentT1TripletCount = newT1TripletCount;
            this.rowsol = tempRowsol;
            this.colsol = tempColsol;
            this.u = tempU;
            this.v = tempV;
            this.currentDistance = dist;
        }

        return dist;
    }

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentT1TripletCount));
        return this.currentDistance;
    }

    @Override
    public double applyNni(NniMove move) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(this.currentVirtualTree);
        Node virtMoving = getMappedNode(tNew, move.movingSubtree);
        Node virtSwapPartner = getMappedNode(tNew, move.swapPartner);

        if (virtMoving == null || virtSwapPartner == null) return pushUnchangedState();

        Node p1 = virtMoving.getParent();
        Node p2 = virtSwapPartner.getParent();
        if (p1 == null || p2 == null || p1 == p2) return pushUnchangedState();

        int idx1 = findChildPos(virtMoving, p1);
        int idx2 = findChildPos(virtSwapPartner, p2);
        if (idx1 == -1 || idx2 == -1) return pushUnchangedState();

        p1.setChild(idx1, virtSwapPartner); virtSwapPartner.setParent(p1);
        p2.setChild(idx2, virtMoving); virtMoving.setParent(p2);

        int maxCostBound = N * N * N;
        return calculateCleanSlateDistance(tNew, true, maxCostBound);
    }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, template, false);
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, template, true);
    }

    private double internalApply2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

        Node vTop = getMappedNode(tNew, top);
        Node vM1 = getMappedNode(tNew, m1);
        Node vM2 = getMappedNode(tNew, m2);

        if (vTop == null || vM1 == null || vM2 == null) return isCommit ? pushUnchangedState() : this.currentDistance;

        Node[] vBounds = new Node[4];
        for (int i = 0; i < 4; i++) {
            vBounds[i] = getMappedNode(tNew, boundarySubtrees[i]);
            if (vBounds[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        boolean isOriginalFork = (m2.getParent() == top);
        int portA = -1, portB = -1;
        for (int i = 0; i < vTop.getChildCount(); i++) {
            if (vTop.getChild(i) == (isOriginalFork ? vM1 : vBounds[0])) portA = i;
            if (vTop.getChild(i) == (isOriginalFork ? vM2 : vM1)) portB = i;
        }
        if (portA == -1) portA = 0;
        if (portB == -1) portB = 1;
        if (portA == portB) { portA = 0; portB = 1; }

        if (template.isFork) {
            vTop.setChild(portA, vM1); vM1.setParent(vTop);
            vTop.setChild(portB, vM2); vM2.setParent(vTop);
            vM1.setChild(0, vBounds[template.indices[0]]); vBounds[template.indices[0]].setParent(vM1);
            vM1.setChild(1, vBounds[template.indices[1]]); vBounds[template.indices[1]].setParent(vM1);
            vM2.setChild(0, vBounds[template.indices[2]]); vBounds[template.indices[2]].setParent(vM2);
            vM2.setChild(1, vBounds[template.indices[3]]); vBounds[template.indices[3]].setParent(vM2);
        } else {
            vTop.setChild(portA, vBounds[template.indices[0]]); vBounds[template.indices[0]].setParent(vTop);
            vTop.setChild(portB, vM1); vM1.setParent(vTop);
            vM1.setChild(0, vBounds[template.indices[1]]); vBounds[template.indices[1]].setParent(vM1);
            vM1.setChild(1, vM2); vM2.setParent(vM1);
            vM2.setChild(0, vBounds[template.indices[2]]); vBounds[template.indices[2]].setParent(vM2);
            vM2.setChild(1, vBounds[template.indices[3]]); vBounds[template.indices[3]].setParent(vM2);
        }

        return calculateCleanSlateDistance(tNew, isCommit, N * N * N);
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, template, false);
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, template, true);
    }

    private double internalApply3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

        Node[] vAvailable = new Node[4];
        for (int i = 0; i < 4; i++) {
            vAvailable[i] = getMappedNode(tNew, cluster.get(i));
            if (vAvailable[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        Node[] vBounds = new Node[5];
        for (int i = 0; i < 5; i++) {
            vBounds[i] = getMappedNode(tNew, boundarySubtrees[i]);
            if (vBounds[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        for (int i = 0; i < 4; i++) {
            while (vAvailable[i].getChildCount() > 0) vAvailable[i].removeChild(0);
        }

        bindMapped3sEcrTemplate(template, vAvailable[0], vAvailable, 1, vBounds);

        return calculateCleanSlateDistance(tNew, isCommit, N * N * N);
    }

    private int bindMapped3sEcrTemplate(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int nextAvailIdx, Node[] newS) {
        int idx = nextAvailIdx;
        if (temp.left.leafIndex != -1) {
            currentInternal.insertChild(newS[temp.left.leafIndex], 0);
            newS[temp.left.leafIndex].setParent(currentInternal);
        } else {
            Node nextInt = available[idx++];
            currentInternal.insertChild(nextInt, 0); nextInt.setParent(currentInternal);
            idx = bindMapped3sEcrTemplate(temp.left, nextInt, available, idx, newS);
        }
        if (temp.right.leafIndex != -1) {
            currentInternal.insertChild(newS[temp.right.leafIndex], 1);
            newS[temp.right.leafIndex].setParent(currentInternal);
        } else {
            Node nextInt = available[idx++];
            currentInternal.insertChild(nextInt, 1); nextInt.setParent(currentInternal);
            idx = bindMapped3sEcrTemplate(temp.right, nextInt, available, idx, newS);
        }
        return idx;
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            this.assigncost = r.oldAssigncost;
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.u = r.u;
            this.v = r.v;
            this.currentDistance = r.distance;
            this.currentVirtualTree = r.oldTree;
            this.currentT1TripletCount = r.oldTripletCount;
        }
    }

    // Bezpieczna kopia szanująca strukturę ID wymuszaną przez bibliotekę PAL.
    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    private int findChildPos(Node child, Node parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    private Node getMappedNode(Tree destTree, Node srcNode) {
        if (srcNode.isLeaf()) return TreeUtils.getNodeByName(destTree, srcNode.getIdentifier().getName());
        Signature targetSig = new Signature(srcNode, N, baseIdGroup);
        for (int i = 0; i < destTree.getInternalNodeCount(); i++) {
            Signature sig = new Signature(destTree.getInternalNode(i), N, baseIdGroup);
            if (sig.equals(targetSig)) return destTree.getInternalNode(i);
        }
        return null;
    }

    private static class Signature {
        String hash;
        public Signature(Node n, int N, IdGroup idGroup) {
            List<BitSet> parts = new ArrayList<>();
            for (int i = 0; i < n.getChildCount(); i++) {
                parts.add(getLeaves(n.getChild(i), idGroup));
            }
            if (n.getParent() != null) {
                BitSet parentPart = new BitSet(N);
                parentPart.set(0, N);
                for (int i = 0; i < n.getChildCount(); i++) {
                    parentPart.andNot(parts.get(i));
                }
                if (!parentPart.isEmpty()) {
                    parts.add(parentPart);
                }
            }
            String[] strParts = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                strParts[i] = parts.get(i).toString();
            }
            Arrays.sort(strParts);
            this.hash = Arrays.toString(strParts);
        }
        @Override
        public int hashCode() { return hash.hashCode(); }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Signature)) return false;
            return this.hash.equals(((Signature)obj).hash);
        }
        @Override
        public String toString() { return hash; }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet(); populate(n, bs, idGroup); return bs;
    }

    private static void populate(Node n, BitSet bs, IdGroup idGroup) {
        if (n.isLeaf()) bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        else for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), bs, idGroup);
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) maxId = n.getNumber();
        }
        return maxId + 1;
    }

    private int coutTriplets(Node n, short[] clustSizeTab) {
        int chCount = n.getChildCount();
        int[] chSize = new int[chCount + 1];

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            if (chNode.isLeaf()) chSize[i] = 1;
            else chSize[i] = clustSizeTab[chNode.getNumber()];
        }

        chSize[chCount] = this.N - clustSizeTab[n.getNumber()];

        int pairCount = 0;
        for (int i = 0; i < chSize.length; i++) {
            for (int j = i + 1; j < chSize.length; j++) {
                for (int k = j + 1; k < chSize.length; k++) {
                    pairCount += (chSize[i] * chSize[j] * chSize[k]);
                }
            }
        }
        return pairCount;
    }

    private int getNcvByCanonicalId(int i, int j, int k, int[][] lcaMatrix) {
        int i_j_lca = lcaMatrix[i][j];
        int i_k_lca = lcaMatrix[i][k];
        int j_k_lca = lcaMatrix[j][k];

        if (i_j_lca == i_k_lca) {
            return j_k_lca;
        } else if (i_j_lca == j_k_lca) {
            return i_k_lca;
        } else {
            return i_j_lca;
        }
    }

    private static class StateRecord {
        int[][] oldAssigncost;
        int[] rowsol, colsol, u, v;
        double distance;
        Tree oldTree;
        int[] oldTripletCount;

        StateRecord(int[][] assigncost, int[] rs, int[] cs, int[] u, int[] v, double d, Tree oldTree, int[] tc) {
            this.oldAssigncost = assigncost;
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.u = u.clone();
            this.v = v.clone();
            this.distance = d;
            this.oldTree = oldTree;
            this.oldTripletCount = tc.clone();
        }
    }

    @Override public double evaluateSprRegraft(Node pruneNode, Node targetNode) { return Double.POSITIVE_INFINITY; }
    @Override public void applySprPrune(Node pruneNode) {}
    @Override public void undoSprPrune(Node pruneNode) {}
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) {}
    @Override public void undoSprRegraftStep() { }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Zero-Alloc SPR " + mtMetricFull.getName(); }
    @Override public String getCommandLineName() { return mtMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mtMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mtMetricFull.setName(name); }
    @Override public String getDescription() { return mtMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mtMetricFull.setDescription(d); }
    @Override public void initData() { mtMetricFull.initData(); }
    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mtMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mtMetricFull.getAlignment(); }
}