// TopologyTestEnging.java
//
// (c) 2000-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.statistics;

/**
 * <p>Title: TopologyTestEngine </p>
 * <p>Description: The driver for statistical tests across a set of topologies </p>
 * @author Matthew Goode
 * @version 1.0
 */
import pal.tree.*;
import pal.alignment.*;
import pal.util.AlgorithmCallback;

public class TopologyTestEngine {

	private final StatisticsHandler statistics_;

	public TopologyTestEngine(StatisticsHandler statistics) {
		this.statistics_ = statistics;
	}

	public TestResult performTest(TopologyPool topologyPool, int numberOfReplicates, AlgorithmCallback callback) {
		double[][] replicateLogLiklihoods = new double[numberOfReplicates][];
		double[] originalLogLiklihoods = topologyPool.getOriginalOptimisedLogLikelihoods();
		for(int i = 0 ; i < numberOfReplicates ; i++) {
			final AlgorithmCallback subCallback =
				AlgorithmCallback.Utils.getSubCallback(
					callback,
					"Replicate:"+i,
					i/(double)numberOfReplicates,
					(i+1)/(double)numberOfReplicates
				);
			replicateLogLiklihoods[i] = topologyPool.getNewReplicateLogLikelihoods(subCallback);
		}
		return new TestResultImpl(
			statistics_.getOriginalTestStatistics(originalLogLiklihoods,originalLogLiklihoods.length),
			statistics_.getPValues(originalLogLiklihoods,replicateLogLiklihoods,numberOfReplicates,originalLogLiklihoods.length)
		);
	}


	// ===========================================================================================
	private static final class TestResultImpl implements TestResult {
		private final double[] baseTestStatistics_;
		private final double[] pValues_;

		/**
		 *
		 * @param relatedData The related optimised trees
		 * @param replicateAssessments The raw assessments stored in form [replicate][topology] = Log likelihood, and order of topology matches relatedData
		 */
		public TestResultImpl(double[] baseTestStatistics, double[] pValues) {
			this.baseTestStatistics_ = baseTestStatistics;
			this.pValues_ = pValues;
		}

		public int[] getSignificantTopologyIndex(double significanceLevel) {
			int count = 0;
			for(int i = 0 ; i < pValues_.length ; i++) {
				if(pValues_[i]>=significanceLevel) {
					count++;
				}
			}
			int[] result = new int[count];
			int index =0;
			for(int i = 0 ; i < pValues_.length ; i++) {
				if(pValues_[i]>=significanceLevel) {
					result[index] = i;
				}
			}
			return result;
		}
		public int[] getUnsignificantTopologyIndex(double significanceLevel) {
			int count = 0;
			for(int i = 0 ; i < pValues_.length ; i++) {
				if(pValues_[i]<significanceLevel) {
					count++;
				}
			}
			int[] result = new int[count];
			int index =0;
			for(int i = 0 ; i < pValues_.length ; i++) {
				if(pValues_[i]<significanceLevel) {
					result[index] = i;
				}
			}
			return result;
		}
		public double getPValue(int topology) { return pValues_[topology]; }
	}

	// ===========================================================================================


	public static interface TestResult {
		public int[] getSignificantTopologyIndex(double significanceLevel);
		public int[] getUnsignificantTopologyIndex(double significanceLevel);
		public double getPValue(int topology);

	}

	//===================================================================================
	// New Stuff
	public static interface TopologyPool {
		public int getNumberOfTopologies();
		public double[] getOriginalOptimisedLogLikelihoods();
		public double[] getNewReplicateLogLikelihoods(AlgorithmCallback callback);
	}

    public static interface StatisticsHandler {

        /**
         * Computes the test statistics based on the original log-likelihood values.
         * @param originalOptimisedLogLikelihoods An array containing the log-likelihood values optimized for the original data, indexed by topology.
         * @param numberOfTopologies The total number of topologies considered.
         * @return An array of calculated test statistics for each topology.
         */
        public double[] getOriginalTestStatistics(double[] originalOptimisedLogLikelihoods, int numberOfTopologies);

        /**
         * Calculates the P-values for each topology based on the comparison of the original
         * log-likelihoods to the distribution of log-likelihoods from replicate datasets.
         *
         * @param originalOptimisedLogLikelihoods An array of log-likelihood values from the original data, indexed by topology.
         * @param replicateLogLikelihoods A 2D array of log-likelihood values from the replicate (simulated) datasets, stored as [replicate][topology].
         * @param numberOfReplicates The number of replicate datasets used.
         * @param numberOfTopologies The number of topologies considered.
         * @return An array of calculated P-values for each topology.
         */
        public double[] getPValues(double[] originalOptimisedLogLikelihoods, double[][] replicateLogLikelihoods, int numberOfReplicates, int numberOfTopologies);
    }
}


