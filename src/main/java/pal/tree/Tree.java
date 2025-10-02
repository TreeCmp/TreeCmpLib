// Tree.java
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
 * Interface for a phylogenetic or genealogical tree.
 *
 * @version $Id: Tree.java,v 1.22 2002/10/03 06:06:55 matt Exp $
 *
 * @author Alexei Drummond
 */
public interface Tree extends IdGroup, Units, Serializable, UnitsProvider {

	/**
	 * @return the root node of this tree.
	 */
	Node getRoot();

	/**
	 * This method constructs a tree from the given root node.
	 * @param root the root node of the tree to construct.
	 */
	void setRoot(Node root);

	/**
	 * @return a count of the number of external nodes (tips) in this
	 * tree.
	 */
	int getExternalNodeCount();

	/**
	 * @return a count of the number of internal nodes (and hence clades)
	 * in this tree.
	 */
	int getInternalNodeCount();

    /**
     * Returns the i-th external node (leaf) of the tree.
     * <p>
     * External nodes correspond to tips in the genealogy, i.e. sampled sequences
     * at the present time.
     * </p>
     *
     * @param i index of the external node (0-based)
     * @return the i-th external node
     */
    Node getExternalNode(int i);

    /**
     * Returns the i-th internal node of the tree.
     * <p>
     * Internal nodes correspond to coalescent events (ancestral nodes) where
     * two or more lineages merge in the genealogy.
     * </p>
     *
     * @param i index of the internal node (0-based)
     * @return the i-th internal node
     */
    Node getInternalNode(int i);

	/**
	 * This method is called to ensure that the calls to other methods
	 * in this interface are valid.
	 */
	void createNodeList();

	/**
	 * Gets the units that this tree's branch lengths and node
	 * heights are expressed in.
	 */
	int getUnits();

	/**
	 * Sets the units that this tree's branch lengths and node
	 * heights are expressed in.
	 */
	//void setUnits(int units);

	/**
	 * Sets an named attribute for a given node.
	 * @param node the node whose attribute is being set.
	 * @param name the name of the attribute.
	 * @param value the new value of the attribute.
	 */
	void setAttribute(Node node, String name, Object value);

	/**
	 * @return an object representing the named attributed for the numbered node.
	 * @param node the node being interrogated.
	 * @param name the name of the attribute of interest.
	 */
	Object getAttribute(Node node, String name);


	/**
	 * @return a clone of this tree
	 */
	 public Tree getCopy();

//====== Abstract Implementation ============================
	public static abstract class TreeBase implements Tree, Report, Units, Serializable{
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

		private boolean setupLengthsAndHeights_ = true;
		//
		// Serialization Stuff
		//

	//static final long serialVersionUID=-7330318631600898531L;

	//serialver -classpath ./classes pal.tree.Tree.AbstractTree

    /** I like doing things my self!
     * Custom serialization method used when writing this object's state to an
     * {@code ObjectOutputStream}.
     *
     * <p>This implementation manually writes a version number and then sequentially writes
     * the internal fields (`root`, `attributes`, `units`, and `setupLengthsAndHeights_`)
     * for persistence, overriding the default serialization mechanism.
     *
     * @param out The stream to write the object state to.
     * @throws java.io.IOException If an I/O error occurs during writing to the stream.
     */
		private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
			out.writeByte(2); //Version number
			out.writeObject(root);
			out.writeObject(attributes);
			out.writeInt(units);
			out.writeBoolean(setupLengthsAndHeights_);
		}

		private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException{
			byte version = in.readByte();
			switch(version) {
				case 1 : {
					root = (Node)in.readObject();
					createNodeList();
					attributes = (Hashtable[])in.readObject();
					units = in.readInt();
				}
				default : {
					root = (Node)in.readObject();
					createNodeList();
					attributes = (Hashtable[])in.readObject();
					units = in.readInt();
					setupLengthsAndHeights_ = in.readBoolean();
				}
			}
		}
		/**
		 * constructor
		 * @param theRoot the node to base tree on
		 */
		protected TreeBase(Node theRoot) {
			setRoot(theRoot);
		}

		/**
		 * constructor
		 * Note: Need to call setRoot() at some point!
		 */
		protected TreeBase() {	}

    /**
     * Cloning constructor. Initializes the tree with a new root
     * and copies the unit type from the source tree.
     *
     * @param tree The {@code TreeBase} instance to clone the unit type from.
     * @param newRoot The new root {@code Node} for this tree.
     */
    protected TreeBase(TreeBase tree, Node newRoot) {
        setRoot(newRoot);
        setUnits(tree.getUnits());
    }
    /**
     * Sets the root node of this tree and immediately updates the internal lists of nodes.
     * This method is typically called by subclasses when the root cannot be supplied during construction.
     *
     * @param theRoot The {@code Node} to be set as the root of the tree.
     */
    public void setRoot(Node theRoot) {
        this.root = theRoot;
        createNodeList();
    }
    /**
     * Returns the units (e.g., branch length type) that this tree is expressed in.
     *
     * @return An integer representing the unit type (e.g., from a defined {@code Units} class).
     */
    public final int getUnits() { return units; }

    /**
     * Sets the units that this tree is expressed in.
     *
     * @param units An integer representing the new unit type.
     */
    public final void setUnits(int units) { this.units = units; }

    /**
     * Returns the total number of external nodes (tips or leaves) in the tree.
     * The node list is created if it hasn't been already.
     *
     * @return The count of external nodes.
     */
    public final int getExternalNodeCount() {
        if(externalNode==null) {   createNodeList();     }
        return numExternalNodes;
    }

    /**
     * Returns the external node (tip) at the specified zero-based index.
     *
     * @param i The zero-based index of the external node to retrieve.
     * @return The {@code Node} object at index {@code i} in the external node list.
     */
    public final Node getExternalNode(int i) {
        if(externalNode==null) {   createNodeList();     }
        return externalNode[i];
    }

    /**
     * Returns the total number of internal nodes (non-tip nodes) in the tree, including the root.
     * The node list is created if it hasn't been already.
     *
     * @return The count of internal nodes.
     */
    public final int getInternalNodeCount() {
        if(internalNode==null) {   createNodeList();     }
        return numInternalNodes;
    }

    /**
     * Returns the internal node at the specified zero-based index.
     *
     * @param i The zero-based index of the internal node to retrieve.
     * @return The {@code Node} object at index {@code i} in the internal node list.
     */
    public final Node getInternalNode(int i) {
        if(internalNode==null) {   createNodeList();  }
        return internalNode[i];
    }

    /**
     * Returns the root node of this tree.
     *
     * @return The {@code Node} object that is the root of the tree.
     */
    public final Node getRoot() {   return root; }

    /**
     * Finds a node in the tree based on its sequential number assigned during traversal.
     * Numbers 1 to {@code numExternalNodes} refer to external nodes; subsequent numbers refer to internal nodes.
     *
     * @param num The one-based number of the node to find (as displayed in an ASCII tree or log).
     * @return The {@code Node} corresponding to the given number.
     */
    public Node findNode(int num)   {
        createNodeList();
        if (num <= numExternalNodes) {
            return externalNode[num-1];
        } else {
            return internalNode[num-1-numExternalNodes];
        }
    }

    /**
     * Traverses the tree (post-order) to count, number, and list all external and internal nodes.
     * This method initializes or refreshes the internal {@code externalNode} and {@code internalNode} arrays.
     * It also computes node heights from branch lengths if necessary.
     */
    public void createNodeList()    {
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
        if (setupLengthsAndHeights_&&root.getNodeHeight() == 0.0) {
            NodeUtils.lengths2Heights(root);
        }
    }
    /**
     * Gets the zero-based index used internally to access attributes stored in arrays.
     * This index is the node's number if it's a leaf, or {@code externalNodeCount + node.getNumber()}
     * if it's an internal node.
     *
     * @param node The node for which to compute the attribute index.
     * @return The internal array index corresponding to the given node.
     */
    private int getIndex(Node node) {
        if (node.isLeaf()) return node.getNumber();
        return getExternalNodeCount() + node.getNumber();
    }

    /**
     * Sets whether the tree should automatically calculate node heights from branch lengths
     * or vice versa whenever the node list is created or the root is set.
     *
     * @param value {@code true} to enable automatic length/height adjustment; {@code false} to disable it.
     */
    protected void setSetupLengthsAndHeights(boolean value) {
        this.setupLengthsAndHeights_ = value;
    }
    /**
     * Returns a string representation of the tree in Newick (New Hampshire) format.
     * The output includes branch lengths but excludes internal node labels.
     *
     * @return A string representing the tree in Newick format, terminated by a semicolon.
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        NodeUtils.printNH(new PrintWriter(sw), getRoot(), true, false, 0, false);
        sw.write(";");
        return sw.toString();
    }

    /**
     * Sets a named attribute (key-value pair) for a given node.
     * If the node implements {@code AttributeNode}, the attribute is stored there; otherwise, it's stored internally.
     *
     * @param node the node whose attribute is being set.
     * @param name the string name of the attribute.
     * @param value the object value of the attribute.
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

    /**
     * Retrieves the object value of a named attribute for a given node.
     *
     * @param node the node being interrogated.
     * @param name the name of the attribute of interest.
     * @return The object value of the named attribute, or {@code null} if the attribute is not set.
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
     * Reroots the tree at the node corresponding to the given sequential number.
     *
     * @param num The one-based number of the node to become the new root (as found via {@code findNode(num)}).
     */
    public void reroot(int num) {
        TreeUtils.reroot(this, findNode(num));
    }

    /**
     * Reroots the tree at the specified node.
     *
     * @param node The node that should become the new root of the tree.
     */
    public void reroot(Node node) {
        TreeUtils.reroot(this, node);
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

		// interface Report

		public void report(PrintWriter out)	{
			TreeUtils.report(this, out);
		}
	} //End of class Tree.AbstractTree
}
