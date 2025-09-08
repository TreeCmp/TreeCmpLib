/** This file is part of TreeCmp, a tool for comparing phylogenetic trees
 using the Matching Split distance and other metrics.
 Copyright (C) 2014,  Damian Bogdanowicz

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package treecmp.metrics.topological;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.*;

public class MatchingPairUnrootedMetric extends BaseMetric implements Metric {

    protected int[] rowsol;
    protected int[] colsol;
    protected int[][] assigncost;

    public MatchingPairUnrootedMetric() {
        super();
        this.rooted = false;
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {

        if (t1.getExternalNodeCount() <= 3){
            return 0.0;
        }

        MatchingPairMetric mp = new MatchingPairMetric();

        int t1ExternalNodeCount = t1.getExternalNodeCount();
        Node removedNode, removedNodeParent;
        double sum = 0;
        IdGroup idGroup = TreeUtils.getLeafIdGroup(t1);
        int[] alias = TreeUtils.mapExternalIdentifiers(idGroup, t2);
        for (int i=0; i<t1ExternalNodeCount; i++) {
            final SimpleTree tree1 = new SimpleTree(t1);
            removedNode = tree1.getExternalNode(alias[i]);
            removedNodeParent = removedNode.getParent();
            removedNodeParent.removeChild(removedNode);
            tree1.reroot(removedNodeParent);
            final SimpleTree tree2 = new SimpleTree(t2);
            removedNode = tree2.getExternalNode(i);
            removedNodeParent = removedNode.getParent();
            removedNodeParent.removeChild(removedNode);
            tree2.reroot(removedNodeParent);
            sum += mp.getDistance(tree1, tree2);
        }

        return sum;
    }
}

