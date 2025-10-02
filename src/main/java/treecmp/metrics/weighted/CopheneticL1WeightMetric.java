/**
 * This file is part of TreeCmp, a tool for comparing phylogenetic trees using
 * the Matching Split distance and other metrics. Copyright (C) 2011, Damian
 * Bogdanowicz
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package treecmp.metrics.weighted;

import pal.tree.Tree;
import treecmp.metrics.*;

public class CopheneticL1WeightMetric extends BaseMetric implements Metric {

    @Override
    public boolean isRooted() {
        return true;
    }

    /**
     * Calculates the distance between two phylogenetic trees, {@code t1} and {@code t2}, using
     * a cophenetic metric, as described in the paper "Cophenetic metrics for phylogenetic trees,
     * after Sokal and Rohlf" by Cardona et al.
     *
     * <p>This specific implementation currently **throws an exception** because the metric calculation
     * has **not yet been implemented**.
     *
     * @param t1 The first phylogenetic tree.
     * @param t2 The second phylogenetic tree.
     * @param indexes Optional indices that might specify which leaves or subtrees to consider (ignored in this non-functional implementation).
     * @return This method throws an exception and does not return a value in its current state.
     * @throws UnsupportedOperationException Always thrown because the cophenetic distance calculation is not yet implemented.
     * @see <a href="http://www.biomedcentral.com/1471-2105/14/3">Cophenetic metrics for phylogenetic trees, after Sokal and Rohlf</a>
     */
    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        throw new UnsupportedOperationException("This metric has not been implemented yet!");
    }
}
