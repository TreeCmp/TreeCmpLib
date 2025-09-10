/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package treecmp.metrics.weighted;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.ClustIntersectInfoMatrix;
import treecmp.common.TreeCmpUtils;
import treecmp.metrics.*;

/**
 *
 * @author Damian
 */
public class RFCWeightMetric extends BaseMetric implements Metric {

    @Override
    public boolean isRooted() {
        return true;
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        int t1_ExtNodes = t1.getExternalNodeCount();
        int t2_ExtNodes = t2.getExternalNodeCount();
        int t1_IntNodes = t1.getInternalNodeCount();
        int t2_IntNodes = t2.getInternalNodeCount();
        int t1_TotNum = t1_ExtNodes + t1_IntNodes;
        int t2_TotNum = t2_ExtNodes + t2_IntNodes;
        Node[] t1Nodes = new Node[t1_TotNum - 1];
        Node[] t2Nodes = new Node[t2_TotNum - 1];
        int i, j;

        for (i = 0; i < t1_ExtNodes; i++) {
            t1Nodes[i] = t1.getExternalNode(i);
        }
        j = t1_ExtNodes;
        for (i = 0; i < t1_IntNodes; i++) {
            Node tmp = t1.getInternalNode(i);
            if (!tmp.isRoot()) {
                t1Nodes[j] = tmp;
                j++;
            }
        }

        for (i = 0; i < t2_ExtNodes; i++) {
            t2Nodes[i] = t2.getExternalNode(i);
        }
        j = t2_ExtNodes;
        for (i = 0; i < t2_IntNodes; i++) {
            Node tmp = t2.getInternalNode(i);
            if (!tmp.isRoot()) {
                t2Nodes[j] = tmp;
                j++;
            }
        }

        IdGroup idGroup = TreeUtils.getLeafIdGroup(t1);
        //first calculate the intersection matrix in order to 
        //quickly compute distance between clusters     
        ClustIntersectInfoMatrix cIntM = TreeCmpUtils.calcClustIntersectMatrix(t1, t2, idGroup);

        Node t1Node, t2Node;
        //calculate costs
        double dist = 0, e1 = 0, e2 = 0, cost;
        int s1, s2, s12;
        for (i = 0; i < t1Nodes.length; i++) {
            boolean same = false;
            t1Node = t1Nodes[i];
            s1 = cIntM.getSizeT1(t1Node);
            e1 = t1Node.getBranchLength();
            for (j = 0; j < t2Nodes.length; j++) {
                t2Node = t2Nodes[j];
                s2 = cIntM.getSizeT2(t2Node);
                s12 = cIntM.getInterSize(t1Node, t2Node);
                e2 = t2Node.getBranchLength();
                if (s1 == s2 && s1 == s12) {
                    same = true;
                    break;
                }
            }
            if (same) {
                cost = Math.abs(e1 - e2);
            } else {
                cost = e1;
            }
            dist += cost;
        }
        
        for (i = 0; i < t2Nodes.length; i++) {
            boolean same = false;
            t2Node = t2Nodes[i];
            s2 = cIntM.getSizeT2(t2Node);
            e2 = t2Node.getBranchLength();
            for (j = 0; j < t1Nodes.length; j++) {
                t1Node = t1Nodes[j];
                s1 = cIntM.getSizeT1(t1Node);
                s12 = cIntM.getInterSize(t1Node, t2Node);
                if (s1 == s2 && s1 == s12) {
                    same = true;
                    break;
                }
            }
            if (!same) {
                cost = e2;
            } else {
                cost = 0;
            }
            dist += cost;
        }
        
        return 0.5 * dist;
    }
}
