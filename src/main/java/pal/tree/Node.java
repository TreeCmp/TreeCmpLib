// Node.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.misc.*;
import java.io.*;
import pal.io.*;


/**
 * interface for a node (includes branch) in a binary/non-binary
 * rooted/unrooted tree
 *
 * @version $Id: Node.java,v 1.23 2002/09/08 03:43:04 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 *
 */

public interface Node extends Serializable {

    /**
     * Returns the parent node of this node.
     *
     * @return the parent {@link Node}, or null if this node is the root
     */
    Node getParent();

    /**
     * Sets the parent node of this node.
     *
     * @param node the {@link Node} to set as parent
     */
    void setParent(Node node);

    /**
     * Returns the sequence associated with this node.
     *
     * @return a byte array representing the sequence at this node
     */
    byte[] getSequence();

    /**
     * Sets the sequence for this node.
     *
     * @param array a byte array representing the sequence
     */
    void setSequence(byte[] array);

    /**
     * Returns the index number of this node.
     *
     * @return an integer representing the node index
     */
    int getNumber();

    /**
     * Sets the index number of this node.
     *
     * @param number the integer index to assign to this node
     */
    void setNumber(int number);

    /**
     * Returns the branch length connecting this node to its parent.
     *
     * @return the branch length as a double
     */
    double getBranchLength();

    /**
     * Sets the branch length connecting this node to its parent.
     *
     * @param value the branch length to assign
     */
    void setBranchLength(double value);

    /**
     * Returns the standard error (SE) of the branch length.
     *
     * @return the branch length SE as a double
     */
    double getBranchLengthSE();

    /**
     * Sets the standard error (SE) of the branch length.
     *
     * @param value the branch length SE to assign
     */
    void setBranchLengthSE(double value);

    /**
     * Returns the height of this node relative to the most recent node.
     *
     * @return the node height as a double
     */
    double getNodeHeight();

    /**
     * Sets the height of this node relative to the most recent node.
     *
     * @param value the height to assign
     */
    void setNodeHeight(double value);

    /**
     * Sets the height of this node relative to the most recent node,
     * optionally adjusting child branch lengths to maintain tree consistency.
     *
     * @param value                    the height to assign
     * @param adjustChildBranchLengths if true, the lengths of child branches
     *                                 are adjusted to maintain relative distances
     */
    void setNodeHeight(double value, boolean adjustChildBranchLengths);

    /**
     * Returns the identifier of this node.
     *
     * @return the {@link Identifier} associated with this node
     */
    Identifier getIdentifier();

    /**
     * Sets the identifier of this node.
     *
     * @param id the {@link Identifier} to assign
     */
    void setIdentifier(Identifier id);

    /**
     * Returns the number of children this node has.
     *
     * @return an integer count of child nodes
     */
    int getChildCount();

    /**
     * check whether this node is an external node
     *
     * @return result (true or false)
     */
    boolean isLeaf();

    /**
     * check whether this node is a root node
     *
     * @return result (true or false)
     */
    boolean isRoot();

    /**
     * get child node
     *
     * @param n number of child
     * @return child node
     */
    Node getChild(int n);

    /**
     * set child node
     *
     * @param n    number
     * @param node new child node
     */
    void setChild(int n, Node node);

    /**
     * add new child node
     *
     * @param c new child node
     */
    void addChild(Node c);

    /**
     * add new child node (insertion at a specific position)
     *
     * @param c   new child node
     * @param pos position
     */
    void insertChild(Node c, int pos);

    /**
     * Removes a child node by its index.
     *
     * @param n the index of the child to be removed (0-based)
     * @return the {@link Node} that was removed
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    Node removeChild(int n);

    /**
     * Removes a specific child node.
     *
     * @param n the {@link Node} to remove
     * @return the {@link Node} that was removed, or null if the node was not a child
     */
    Node removeChild(Node n);
}
