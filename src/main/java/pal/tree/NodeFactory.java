// NodeFactory.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;


/**
 * Creates nodes
 * <b>
 * The purpose of this class is to decouple the creation of
 * a class of type "Node" from its actual implementation.  This
 * class should be used instead of calling the constructor
 * of an implementation of "Node"
 * (at the moment "SimpleNode") as it may change in the future.</b><p>
 *
 * Other plans: add features here to recyle old nodes rather than
 * leaving them to the Java garbage collector
 *
 * @author Korbinian Strimmer
 */
import pal.misc.Identifier;
public class NodeFactory
{
    /**
     * Creates a new, uninitialized node with default properties.
     *
     * @return A new {@code Node} instance (typically a {@code SimpleNode}).
     */
    public static final Node createNode()
    {
        return new SimpleNode();
    }

    /**
     * Creates a new node with the specified identifier.
     *
     * @param id The {@code Identifier} to be assigned to the new node.
     * @return A new {@code Node} instance with the given identifier.
     */
    public static final Node createNode(Identifier id)
    {
        return new SimpleNode(id.getName(),0);
    }

    /**
     * Creates a new node with the specified identifier and node height.
     *
     * @param id The {@code Identifier} to be assigned to the new node.
     * @param height The height to be set for the new node.
     * @return A new {@code Node} instance with the specified identifier and height.
     */
    public static final Node createNode(Identifier id, double height)
    {
        SimpleNode sn = new SimpleNode(id.getName(),0);
        sn.setNodeHeight(height);
        return sn;
    }

    /**
     * Creates a new node with the specified identifier and branch length.
     *
     * @param branchLength The length of the branch leading to the new node.
     * @param id The {@code Identifier} to be assigned to the new node.
     * @return A new {@code Node} instance with the specified branch length and identifier.
     */
    public static final Node createNodeBranchLength(double branchLength, Identifier id)
    {
        SimpleNode sn = new SimpleNode(id.getName(),0);
        sn.setBranchLength(branchLength);
        return sn;
    }

    /**
     * Creates a deep clone of the specified node and all its descendants.
     *
     * @param node The source {@code Node} to be cloned.
     * @return A new {@code Node} instance that is a clone of the source node and its subtree.
     */
    public static final Node createNode(Node node)
    {
        return new SimpleNode(node);
    }

    /**
     * Creates a new internal node with the specified array of children.
     *
     * @param children An array of {@code Node} objects that will become children of the new node.
     * @return A new internal {@code Node} instance.
     */
    public static final Node createNode(Node[] children) {
        return new SimpleNode(children);
    }

    /**
     * Creates a new internal node with the specified children and the specified node height.
     *
     * @param children An array of {@code Node} objects that will become children of the new node.
     * @param height The height to be set for the new node.
     * @return A new internal {@code Node} instance with the specified height.
     */
    public static final Node createNode(Node[] children, double height) {
        SimpleNode sn = new SimpleNode(children);
        sn.setNodeHeight(height);
        return sn;
    }

    /**
     * Creates a new internal node with the specified children and the specified branch length.
     *
     * @param branchLength The length of the branch leading to the new internal node.
     * @param children An array of {@code Node} objects that will become children of the new node.
     * @return A new internal {@code Node} instance with the specified branch length.
     */
    public static final Node createNodeBranchLength(double branchLength, Node[] children) {
        SimpleNode sn = new SimpleNode(children);
        sn.setBranchLength(branchLength);
        return sn;
    }
}
