package treecmp.metrics.topological;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.ClustIntersectInfoMatrix;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.metrics.*;

public class MatchingSpliMetricFree extends BaseMetric implements Metric {

    protected int[] costId2NumT1;
    protected int[] costId2NumT2;
    protected int[] rowsol;
    protected int[] colsol;
    protected short[][] assigncost;
    protected ClustIntersectInfoMatrix cIntM;

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers) - eliminacja alokacji w gorących ścieżkach
    private int currentLapCapacity = 0;
    private int currentNodeCapacity = 0;
    private int[] u;
    private int[] v;
    private Node[] scratchNodesT1;
    private Node[] scratchNodesT2;
    private int[] t1_aCsize;
    private int[] t1_bCsize;
    private int[] t1_ar2;
    private int[] t2_cCsize;
    private int[] t2_dCsize;
    private int[] t2_r1c;

    private void ensureCapacity(int lapSize, int nodeSize) {
        if (assigncost == null || currentLapCapacity < lapSize) {
            int newLap = Math.max(lapSize, (currentLapCapacity == 0 ? 32 : currentLapCapacity * 2));
            assigncost = new short[newLap][newLap];
            rowsol = new int[newLap];
            colsol = new int[newLap];
            u = new int[newLap];
            v = new int[newLap];
            currentLapCapacity = newLap;
        }
        if (scratchNodesT1 == null || currentNodeCapacity < nodeSize) {
            int newNodes = Math.max(nodeSize, (currentNodeCapacity == 0 ? 32 : currentNodeCapacity * 2));
            scratchNodesT1 = new Node[newNodes];
            scratchNodesT2 = new Node[newNodes];
            t1_aCsize = new int[newNodes];
            t1_bCsize = new int[newNodes];
            t1_ar2 = new int[newNodes];
            t2_cCsize = new int[newNodes];
            t2_dCsize = new int[newNodes];
            t2_r1c = new int[newNodes];
            currentNodeCapacity = newNodes;
        }
    }

    public double getDistance(Tree t1, Tree t2, int... indexes) {

        IdGroup idGroup1 = TreeUtils.getLeafIdGroup(t1);
        IdGroup idGroup2 = TreeUtils.getLeafIdGroup(t2);
        // OPTYMALIZACJA 3: Brak duplikacji IdGroup dla tych samych zbiorów liści
        IdGroup idGroup = (idGroup1 == idGroup2 || idGroup1.equals(idGroup2)) ? idGroup1 : TreeCmpUtils.mergeIdGroups(idGroup1, idGroup2);
        cIntM = TreeCmpUtils.calcClustIntersectMatrix(t1, t2, idGroup);

        int totSize1 = t1.getInternalNodeCount() + t1.getExternalNodeCount();
        int totSize2 = t2.getInternalNodeCount() + t2.getExternalNodeCount();
        int sizeIt = Math.max(totSize1, totSize2);
        int size = sizeIt - 1; // Pomijamy korzenie (root)

        if (size <= 0) {
            return 0;
        }

        // OPTYMALIZACJA 1: Zero-Allocation — używamy buforów instancji
        ensureCapacity(size, sizeIt);

        Node t1Root = t1.getRoot();
        Node t2Root = t2.getRoot();
        int rootsInterSize = cIntM.getInterSize(t1Root, t2Root);

        Node[] nodeT1 = TreeCmpUtils.getAllNodes(t1);
        Node[] nodeT2 = TreeCmpUtils.getAllNodes(t2);
        int extSize1 = t1.getExternalNodeCount();
        int extSize2 = t2.getExternalNodeCount();
        int extSum = extSize1 + extSize2;

        // OPTYMALIZACJA 2a: Hoisting + wyciągnięcie zapytań o przecięcia z korzeniem
        int n1 = 0;
        for (int i = 0; i < totSize1; i++) {
            Node node = nodeT1[i];
            if (node.isRoot()) continue;
            scratchNodesT1[n1] = node;
            int aSize = node.isLeaf() ? 1 : cIntM.cSize1[node.getNumber()];
            t1_aCsize[n1] = aSize;
            t1_bCsize[n1] = extSize1 - aSize;
            t1_ar2[n1] = cIntM.getInterSize(node, t2Root);
            n1++;
        }

        int n2 = 0;
        for (int j = 0; j < totSize2; j++) {
            Node node = nodeT2[j];
            if (node.isRoot()) continue;
            scratchNodesT2[n2] = node;
            int cSize = node.isLeaf() ? 1 : cIntM.cSize2[node.getNumber()];
            t2_cCsize[n2] = cSize;
            t2_dCsize[n2] = extSize2 - cSize;
            t2_r1c[n2] = cIntM.getInterSize(t1Root, node);
            n2++;
        }

        // OPTYMALIZACJA 2b: 4-Quadrant Branchless Matrix Filling dla Free MS
        // Kwadrant 1: Prawdziwe pary węzłów [0..n1-1][0..n2-1]
        for (int r = 0; r < n1; r++) {
            Node node1 = scratchNodesT1[r];
            int ar2 = t1_ar2[r];
            short[] row = assigncost[r];
            for (int c = 0; c < n2; c++) {
                int ac = cIntM.getInterSize(node1, scratchNodesT2[c]);
                int bc = t2_r1c[c] - ac;
                int ad = ar2 - ac;
                int bd = rootsInterSize - ac - bc - ad;
                int max = Math.max(ac + bd, ad + bc);
                row[c] = (short) (extSum - (max << 1));
            }
        }

        // Kwadrant 2: Widmowe T1 / koszt węzłów T2 [n1..size-1][0..n2-1]
        for (int r = n1; r < size; r++) {
            short[] row = assigncost[r];
            for (int c = 0; c < n2; c++) {
                int cC = t2_cCsize[c];
                int dC = t2_dCsize[c];
                row[c] = (short) Math.min(extSum + cC - dC, extSum + dC - cC);
            }
        }

        // Kwadrant 3: Widmowe T2 / koszt węzłów T1 [0..n1-1][n2..size-1]
        for (int r = 0; r < n1; r++) {
            short[] row = assigncost[r];
            int aC = t1_aCsize[r];
            int bC = t1_bCsize[r];
            short cost = (short) Math.min(extSum + aC - bC, extSum + bC - aC);
            for (int c = n2; c < size; c++) {
                row[c] = cost;
            }
        }

        // Kwadrant 4: Zera [n1..size-1][n2..size-1]
        for (int r = n1; r < size; r++) {
            short[] row = assigncost[r];
            for (int c = n2; c < size; c++) {
                row[c] = 0;
            }
        }

        int metric = LapSolver.lapShort(size, assigncost, rowsol, colsol, u, v);
        return metric / 2.0;
    }
}