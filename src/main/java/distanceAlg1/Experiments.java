package distanceAlg1;

import java.util.Iterator;
import java.util.Vector;

public class Experiments {

	
	/** Help message (ie. which arguments can be used, etc.)
	 * 
	 */
	public static void displayHelp() {
		System.out.println("Command line syntax:");
		System.out.println("geodeMAPS [options] treefile");
		System.out.println("Optional arguments:");
		System.out.println("\t -a <algorithm> \t uses <algorithm> to compute the geodesic distance.  Current options are divide to run GeodeMaps-Divide, and dynamic to run GeodeMaps-Dynamic.  The default is dynamic.");
		System.out.println("\t -d \t double check results, by computing each distance with the target tree as the starting tree and vice versa; default is false");
		System.out.println("\t -h || --help \t displays this message");
		System.out.println("\t -n \t normalize (vector of the lengths of all edges has length 1)");
		System.out.println("\t -o <outfile> \t store the output in the file <outfile>");
		System.out.println("\t -u \t unrooted trees (default is rooted trees)");
		System.out.println("\t -v || --verbose \t verbose output");
	}
	
	/**
     * Main entry point of the program.
     *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		
		// variables maybe changed by program arguments
		String treeFile = "";
		String outFile = "output.txt"; // default
		String experiment = "";  // what experimental code to call.  ie. pathSpaceGeoCompare
		
		
		// last argument is always the infile, and the rest are optional

		if (args.length < 1) {
			TreeDistance.displayHelp();
			System.exit(0);
		}
		treeFile = args[args.length-1];
		for (int i = 0; i < args.length - 1; i++) {
			
			if (!args[i].startsWith("-")) {
				System.out.println("Invalid command line option");
				displayHelp();
				System.exit(0);
			}
				
			if (args[i].equals("--verbose")) {
				TreeDistance.verbose = 1;
			}
			else if (args[i].equals("--help")) {
				displayHelp();
				System.exit(0);
			}
			// output file
			else if (args[i].equals("-o")) {
				if (i < args.length -2) {
					outFile = args[i+1];
					i++;
				}
				else {
					displayHelp();
					System.exit(0);
				}
			}
			// which experiment
			else if (args[i].equals("-e")) {
				if (i < args.length -2) {
					experiment = args[i+1];
					i++;
				}
				else {
					System.out.println("Problem here");
					displayHelp();
					System.exit(0);
				}
			}
				
			// all other arguments.  Note we can have -vn
			else {
				for (int j = 1; j<args[i].length(); j++) {
					
					switch(args[i].charAt(j)) {						
						
					// display help
					case 'h':
						displayHelp();
						System.exit(0);
						break;
						
					// normalize trees?
					case 'n':
						TreeDistance.normalize = true;
						break;
						
			/*			else if (args[i].equals("-p")) {
							permutationTest = true;
							i++;
						} */
						
						// unrooted trees?
					case 'u':
						TreeDistance.rooted = false;
						break;
						
					// verbose output
					case 'v':
						TreeDistance.verbose = 1;
						break;
							
					default:
						System.out.println("Illegal command line option.\n");
						displayHelp();
						System.exit(0);
						break;
					} // end switch
				} // end for j
			} // end parsing an individual argument
		}  // end for i (looping through arguments)
			
		
		
		if (experiment.equals("pathSpaceGeoCompare") ) {
			PhyloTree[] trees = TreeDistance.readInTreesFromFile(treeFile);
			pathSpaceGeoCompare(trees, outFile);
		}
		else {
			System.out.println(experiment + " is an invalid experiment");
			displayHelp();
		}
		System.exit(0);

	}

    /**
     * Computes the combinatorial type of all path space geodesics between two phylogenetic trees.
     * <p>
     * Only the first two trees in the array are used; any additional trees are ignored.
     * The method prints information about the trees, edges, and resulting ratio sequences.
     *
     * @param trees   an array of PhyloTree objects (must contain at least two trees)
     * @param outFile the output file name (currently used for experiment output)
     */
    public static void pathSpaceGeoCompare(PhyloTree[] trees, String outFile) {
        if (trees.length < 2) {
            System.out.println("Need at least 2 trees");
            System.exit(1);
        }

        PhyloTree t1 = trees[0];
        PhyloTree t2 = trees[1];

        Vector<PhyloTreeEdge> commonEdges = PhyloTree.getCommonEdges(t1, t2);
        if (commonEdges.size() > 0) {
            System.out.println("Trees have common split: cannot run pathSpaceGeoCompare");
            System.exit(1);
        }

        System.out.println("Starting tree: " + t1.getNewick(true));
        System.out.println("Target tree: " + t2.getNewick(true));

        System.out.println("\nStarting tree edges:");
        PhyloTreeEdge.printEdgesVerbose(t1.getEdges(), t1.getLeaf2NumMap(), true);

        System.out.println("\nTarget tree edges:");
        PhyloTreeEdge.printEdgesVerbose(t2.getEdges(), t2.getLeaf2NumMap(), true);

        System.out.println("Ignoring leaf split lengths. Displaying ratio sequence, combinatorial type, and length of path space geodesic.");

        Vector<Bipartition> m = t2.getCrossingsWith(t1);
        if (m == null) {
            System.out.println("The trees " + t1 + " and " + t2 + " do not have the same leaf labels.");
            System.exit(0);
        }

        Bipartition mEl = m.get(0);
        boolean inBaseCase = true;
        for (int i = 1; i < m.size(); i++) {
            if (!m.get(i).equals(mEl)) {
                inBaseCase = false;
                break;
            }
        }

        if (inBaseCase) {
            RatioSequence rs = new RatioSequence();
            rs.add(new Ratio(TreeDistance.myVectorClonePhyloTreeEdge(t1.getEdges()), TreeDistance.myVectorClonePhyloTreeEdge(t2.getEdges())));
            System.out.println(rs.toStringCombTypeAndValue());
            System.out.println(rs.getDistance() + "\n");
        }

        getCombTypeOfPathSpaceGeos(m, new RatioSequence(), t1.getEdges(), t2.getEdges());
    }

    /**
     * Recursive method to compute all ratio sequences for path space geodesics.
     *
     * @param m        the set of Bipartitions to process
     * @param ratioSeq the current RatioSequence being built
     * @param eEdges   the edges of the starting tree
     * @param fEdges   the edges of the target tree
     */
    public static void getCombTypeOfPathSpaceGeos(Vector<Bipartition> m, RatioSequence ratioSeq,
                                                  Vector<PhyloTreeEdge> eEdges, Vector<PhyloTreeEdge> fEdges) {

        Vector<Bipartition> minEls = TreeDistance.getMinElements(m);

        if (minEls.size() == 0) {
            // Base case: no more elements to process
            System.out.println(ratioSeq.toStringCombTypeAndValue());
            System.out.println(ratioSeq.getNonDesRSWithMinDist().toStringCombTypeAndValue());
            System.out.println(ratioSeq.getNonDesRSWithMinDist().getDistance() + "\n");
            return;
        }

        Vector<Bipartition> sortedMinEls = new Vector<>();
        Vector<Ratio> sortedMinElRatios = new Vector<>();

        for (Bipartition minEl : minEls) {
            Ratio ratio = TreeDistance.calculateRatio(minEl, m, eEdges, fEdges);

            boolean inserted = false;
            for (int k = 0; k < sortedMinElRatios.size(); k++) {
                if (sortedMinElRatios.get(k).getRatio() > ratio.getRatio()) {
                    sortedMinElRatios.add(k, ratio.clone());
                    sortedMinEls.add(k, minEl.clone());
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                sortedMinElRatios.add(ratio.clone());
                sortedMinEls.add(minEl.clone());
            }
        }

        for (int j = 0; j < sortedMinElRatios.size(); j++) {
            Bipartition minEl = sortedMinEls.get(j);
            Ratio ratio = sortedMinElRatios.get(j);

            Vector<Bipartition> newM = TreeDistance.removeMinElFrom(m, minEl);

            ratioSeq.add(ratio);
            getCombTypeOfPathSpaceGeos(newM, ratioSeq, eEdges, fEdges);
            ratioSeq.remove(ratio);
        }
    }
}