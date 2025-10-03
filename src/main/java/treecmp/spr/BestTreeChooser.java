/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.spr;

import pal.tree.Tree;
import treecmp.common.TreeCmpException;

/**
 *
 * @author Damian
 */
public interface BestTreeChooser {

    double getValueForTree(Tree tree) throws TreeCmpException;
}
