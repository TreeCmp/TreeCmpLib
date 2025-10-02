// SimpleTree.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.misc.*;
import pal.io.*;

import java.io.*;
import java.util.*;


/**
 * data structure for a binary/non-binary rooted/unrooted trees
 *
 * @version $Id: SimpleTree.java,v 1.23 2002/12/05 04:27:28 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 *
 */
public class SimpleTree implements Tree, Report, Units, Serializable
{
	//
	// This class has explicit serialization code so if you alter any fields please alter
	// the serialization code too (make sure you use a new version number - see readObject/writeObject
	// Thanks, Matthew

	//
	// Public stuff
	//

	//
	// Private stuff
	/** root node */
	private Node root;

	/** list of internal nodes (including root) */
	private Node[] internalNode = null;

	/** number of internal nodes (including root) */
	private int numInternalNodes;

	/** list of external nodes */
	private Node[] externalNode = null;

	/** number of external nodes */
	private int numExternalNodes;

	/** attributes attached to this tree. */
	private Hashtable[] attributes = null;

	/** holds the units of the trees branches. */
	private int units = EXPECTED_SUBSTITUTIONS;

	//
	// Serialization Stuff
	//

	static final long serialVersionUID=-7330318631600898531L;

	//serialver -classpath ./classes pal.tree.SimpleTree

    /** I like doing things my self!
     * Custom serialization method used when writing this object's state to an
     * {@code ObjectOutputStream}.
     *
     * <p>This implementation manually writes a version number and then sequentially writes
     * the internal fields (`root`, `attributes`, and `units`) for persistence, overriding
     * the default serialization mechanism.
     *
     * @param out The stream to write the object state to.
     * @throws java.io.IOException If an I/O error occurs during writing to the stream.
     */
	private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(1); //Version number
		out.writeObject(root);
		out.writeObject(attributes);
		out.writeInt(units);
	}
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			default : {
				root = (Node)in.readObject();
				createNodeList();
				attributes = (Hashtable[])in.readObject();
				units = in.readInt();
			}
		}
	}


	/** constructor tree consisting solely of root node */
	public SimpleTree() {

		// Default configuration
		root = new SimpleNode();
		//root.setIdentifier(new Identifier("ROOT"));
		root.setBranchLength(0.0);
		root.setBranchLengthSE(0.0);
	}

    /**
     * Constructs a SimpleTree with the specified node as its root.
     *
     * @param r The root {@code Node} for the new tree.
     */
    public SimpleTree(Node r) {

        root = r;
        createNodeList();
    }

    /**
     * Constructs a new SimpleTree as a deep clone of the given tree.
     * The internal structure and identifiers are copied.
     *
     * @param tree The source {@code Tree} object to be cloned.
     */
    public SimpleTree(Tree tree)
    {
        root = new SimpleNode(tree.getRoot());
        setUnits(tree.getUnits());
        createNodeList();
    }

    /**
     * Constructs a new SimpleTree as a deep clone of the given tree,
     * with an option to discard or retain identifiers during the node cloning process.
     *
     * @param tree The source {@code Tree} object to be cloned.
     * @param keepIdentifiers If {@code true}, the identifiers of the nodes are retained during cloning; if {@code false}, they are discarded.
     */
    public SimpleTree(Tree tree, boolean keepIdentifiers)
    {
        root = new SimpleNode(tree.getRoot(), keepIdentifiers);
        setUnits(tree.getUnits());
        createNodeList();
    }

    /**
     * Constructs a new SimpleTree as a deep clone of the given tree,
     * applying a label mapping to potentially rename the node identifiers during cloning.
     *
     * @param tree The source {@code Tree} object to be cloned.
     * @param lm A {@code LabelMapping} used to translate the original node labels into new ones.
     */
    public SimpleTree(Tree tree, LabelMapping lm)
    {
        root = new SimpleNode(tree.getRoot(), lm);
        setUnits(tree.getUnits());
        createNodeList();
    }

    /**
     * Returns the unit of measurement (e.g., time, substitutions) in which this tree's branch lengths are expressed.
     *
     * @return The integer code representing the units of the tree.
     */
    public final int getUnits() {
        return units;
    }

    /**
     * Sets the unit of measurement (e.g., time, substitutions) in which this tree's branch lengths are expressed.
     *
     * @param units The integer code representing the new units for the tree.
     */
    public final void setUnits(int units) {
        this.units = units;
    }


    /**
     * Returns the total number of external nodes (leaves) in the tree.
     *
     * @return The count of external nodes.
     */
    public final int getExternalNodeCount() {
        if(externalNode==null) {
            createNodeList();
        }
        return numExternalNodes;
    }

    /**
     * Returns the external node (leaf) at the specified index.
     *
     * @param i The zero-based index of the external node to retrieve.
     * @return The {@code Node} object that is a leaf.
     */
    public final Node getExternalNode(int i) {
        if(externalNode==null) {
            createNodeList();
        }
        return externalNode[i];
    }

    /**
     * Returns the total number of internal nodes in the tree (excluding the root if it is considered separately).
     *
     * @return The count of internal nodes.
     */
    public final int getInternalNodeCount() {
        if(internalNode==null) {
            createNodeList();
        }
        return numInternalNodes;
    }

    /**
     * Returns the internal node at the specified index.
     *
     * @param i The zero-based index of the internal node to retrieve.
     * @return The {@code Node} object that is internal.
     */
    public final Node getInternalNode(int i) {
        if(internalNode==null) {
            createNodeList();
        }
        return internalNode[i];
    }

    /**
     * Returns the root node of this tree.
     *
     * @return The root {@code Node} of the tree.
     */
    public final Node getRoot() {
        return root;
    }

    /**
     * Sets a new node as the root of this tree and rebuilds the node lists.
     *
     * @param r The new root {@code Node}.
     */
    public final void setRoot(Node r) {
        root = r;
        createNodeList();
    }

    /**
     * Counts and lists all external and internal nodes, assigns an index number to each,
     * and computes the height of each node if node heights have not yet been calculated.
     */
    public void createNodeList()
    {
        numInternalNodes = 0;
        numExternalNodes = 0;
        Node node = root;
        do
        {
            node = NodeUtils.postorderSuccessor(node);
            if (node.isLeaf())
            {
                node.setNumber(numExternalNodes);
                numExternalNodes++;
            }
            else
            {
                node.setNumber(numInternalNodes);
                numInternalNodes++;
            }
        }
        while(node != root);

        internalNode = new Node[numInternalNodes];
        externalNode = new Node[numExternalNodes];
        node = root;
        do
        {
            node = NodeUtils.postorderSuccessor(node);
            if (node.isLeaf())
            {
                externalNode[node.getNumber()] = node;
            }
            else
            {
                internalNode[node.getNumber()] = node;
            }
        }
        while(node != root);

        // compute heights if it seems necessary
        if (root.getNodeHeight() == 0.0) {
            NodeUtils.lengths2Heights(root);
        }
    }

	public String toString() {
		StringWriter sw = new StringWriter();
		NodeUtils.printNH(new PrintWriter(sw), getRoot(), true, false, 0, false);
		sw.write(";");

		return sw.toString();
	}


	/**
	 * return node with number num (as displayed in ASCII tree)
	 *
	 * @param num number of node
	 *
	 * @return node
	 */
	public Node findNode(int num)
	{
		createNodeList();

		if (num <= numExternalNodes)
		{
			return externalNode[num-1];
		}
		else
		{
			return internalNode[num-1-numExternalNodes];
		}
	}

	private int getIndex(Node node) {
		if (node.isLeaf()) return node.getNumber();
		return getExternalNodeCount() + node.getNumber();
	}

	/**
	 * Sets an named attribute for a given node.
	 * @param node the node whose attribute is being set.
	 * @param name the name of the attribute.
	 * @param value the new value of the attribute.
	 */
	public void setAttribute(Node node, String name, Object value) {
		if (node instanceof AttributeNode) {
			((AttributeNode)node).setAttribute(name, value);
		} else {
			int index = getIndex(node);
			if (attributes == null) {
				attributes = new Hashtable[getExternalNodeCount() + getInternalNodeCount()];
			}
			if (attributes[index] == null) {
				attributes[index] = new Hashtable();
			}
			attributes[index].put(name, value);
		}
	}

// ========= IdGroup stuff ===============================
	public int getIdCount() {
		return getExternalNodeCount();
	}
	public Identifier getIdentifier(int i) {
		return getExternalNode(i).getIdentifier();
	}
	public void setIdentifier(int i, Identifier id) {
		getExternalNode(i).setIdentifier(id);
	}
	public int whichIdNumber(String s) {
		return IdGroup.Utils.whichIdNumber(this,s);
	}

//========================================================
	/**
	 * @return an object representing the named attributed for the numbered node.
	 * @param node the node being interrogated.
	 * @param name the name of the attribute of interest.
	 */
	public Object getAttribute(Node node, String name) {
		if (node instanceof AttributeNode) {
			return ((AttributeNode)node).getAttribute(name);
		} else {
			int index = getIndex(node);
			if (attributes == null || attributes[index] == null) {
				return null;
			}
			return attributes[index].get(name);
		}
	}

	/**
	 * make node with number num to root node
	 *
	 * @param num number of node
	 */
	public void reroot(int num)
	{
		TreeUtils.reroot(this, findNode(num));
	}

	/**
	 * make provided node the root node
	 *
	 * @param node the node to make the root.
	 */
	public void reroot(Node node) {
		TreeUtils.reroot(this, node);
	}

	// interface Report

	public void report(PrintWriter out)
	{
		TreeUtils.report(this, out);
	}

	public Tree getCopy() {
		return new SimpleTree(this);
	}
}
