/*
 * Copyright (c) 2011-2012, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://www.boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.numerics.optimization.impl;

import boofcv.numerics.optimization.OptimizationDerivative;
import boofcv.numerics.optimization.OptimizationResidual;
import org.ejml.UtilEjml;
import org.ejml.alg.dense.decomposition.DecompositionFactory;
import org.ejml.alg.dense.decomposition.QRPDecomposition;
import org.ejml.alg.dense.decomposition.TriangularSolver;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;
import org.ejml.ops.NormOps;

import java.util.List;

/**
 * A generic version of Levenberg Marquardt algorithm to minimize a function.
 *
 * TODO Document
 *
 */
public class LevenbergMarquardt<Observed,State> {

	private static double DEFAULT_XTOL = Math.sqrt(UtilEjml.EPS);

	// initial step size
	private double initialLambda = 0.001;
	// maximum number of iterations it will perform
	private int maxIterations;

	// tolerance for testing convergence
	private double xtol;
	private double ftol;

	// number of parameters being optimized
	private int numModelParam;
	// number of functions
	private int numFunctions;
	// total number of observations
	private int totalObservations;

	// Computes observation residuals given a model
	private OptimizationResidual<Observed,State> function;
	// Computes the derivatives of observation residuals given a model
	private OptimizationDerivative<State> derivative;


	// the parameters being optimized
	private double []workModel;
	private double []candidateModel;
	// cost before any optimization
	private double initialCost;
	// the cost after the best set of parameters has been found
	private double finalCost;

	// variables used by the optimization algorithm
	private DenseMatrix64F d;
	private DenseMatrix64F H;
	private DenseMatrix64F diag;
	private DenseMatrix64F correction;
	private DenseMatrix64F A;
	private QRPDecomposition<DenseMatrix64F> decomposition;

	// array to store gradient
	private double[][]gradient;
	// wraps around gradient
	private DenseMatrix64F J = new DenseMatrix64F();

	// stores residuals
	private DenseMatrix64F R;

	// the full jacobian.  Required by the F-test to compute the predicted residuals
	private DenseMatrix64F Jacobian = new DenseMatrix64F(1,1);
	private DenseMatrix64F Residuals = new DenseMatrix64F(1,1);

	/**
	 * Creates a optimizer using default parameters and numerical Jacobian estimate.
	 *
	 * @param numModelParam Number of model parameters to be optimized.
	 * @param function Function that is being optimized.
	 */
	// todo numModelParam is specified in the function
	// todo make this setFunction() instead
	public LevenbergMarquardt( int numModelParam,
							   OptimizationResidual<Observed,State> function )
	{
		this(numModelParam,function,new NumericalJacobian<Observed,State>(function),DEFAULT_XTOL,DEFAULT_XTOL,100);
	}

	public LevenbergMarquardt( int numModelParam,
							   OptimizationResidual<Observed,State> function ,
							   OptimizationDerivative<State> derivative)
	{
		this(numModelParam,function,derivative,DEFAULT_XTOL,DEFAULT_XTOL,100);
	}

	public LevenbergMarquardt( int numModelParam,
							   OptimizationResidual<Observed,State> function ,
							   OptimizationDerivative<State> derivative,
							   double xtol , double ftol , int maxIterations )
	{

		this.numModelParam = numModelParam;
		this.function = function;
		this.derivative = derivative;
		this.xtol = xtol;
		this.ftol = ftol;
		this.maxIterations = maxIterations;
		this.numFunctions = function.getNumberOfFunctions();

		this.workModel = new double[numModelParam];
		this.candidateModel = new double[numModelParam];

		d = new DenseMatrix64F(numModelParam,1);
		H = new DenseMatrix64F(numModelParam, numModelParam);
		diag = new DenseMatrix64F(numModelParam, numModelParam);
		gradient = new double[numFunctions][numModelParam];
		correction = new DenseMatrix64F(numModelParam,1);
		A = new DenseMatrix64F(numModelParam, numModelParam);
		J.numRows = numModelParam;
		J.numCols = 1;

		decomposition = DecompositionFactory.qrp(numModelParam, numModelParam);

		R = new DenseMatrix64F(function.getNumberOfFunctions() ,1);

	}

	public double getInitialCost() {
		return initialCost;
	}

	public double getFinalCost() {
		return finalCost;
	}

	public double[] getModelParameters() {
		return workModel;
	}

	/**
	 * Optimizes the system given an initial estimate of the model parameters.
	 *
	 * @param initialModel Initial estimate of model parameters.
	 * @param dataObserved List of model observations.  Can be null.
	 * @param dataState List of system states.  Can be null.
	 * @return true it was able to optimize the system or not.
	 */
	public boolean process( double[] initialModel ,
							List<Observed> dataObserved ,
							List<State> dataState )
	{
		totalObservations = dataObserved == null ? dataState.size() : dataObserved.size();

		System.arraycopy(initialModel, 0, workModel, 0, numModelParam);
		System.arraycopy(initialModel, 0, candidateModel, 0, numModelParam);

		Jacobian.reshape(totalObservations*numFunctions, numModelParam,false);
		Residuals.reshape(totalObservations*numFunctions,1,false);

		double lambda = initialLambda;

		diag.zero();

		double prevCost = cost(workModel,dataObserved,dataState);

		initialCost = prevCost;

		if( Double.isNaN(initialCost) || Double.isInfinite(initialCost)) {
			throw new IllegalArgumentException("Initial parameters are bad");
		}

		boolean recomputeDerivatives = true;

		for( int iter = 0; iter < maxIterations && prevCost > 0; iter++ ) {
            System.out.println("Iteration: "+iter+"  recomp "+recomputeDerivatives);

			// TODO don't compute A directly   pg 119
			if( recomputeDerivatives ) {
				computeMatrices(dataObserved,dataState, numFunctions);
			}

			computeA(A, H, lambda);

			if( !solve() ) {
				lambda *= 10;
				continue;
			}

//			correction.print();
//			System.out.println("--------------------");
			// apply the found correction
			for( int i = 0; i < numModelParam; i++ ) {
				candidateModel[i] = workModel[i] + correction.data[i];
			}

			double cost = cost(candidateModel,dataObserved,dataState);

//			System.out.println("lambda = "+lambda+"  correction mag = "+NormOps.normF(correction)+"  "+recomputeDerivatives+" maxStep "+maxStep);

			System.out.println("  prevCost = "+prevCost+"  candidate cost = "+cost);

			// did this correction improve the results?
			if( cost < prevCost ) {
				System.arraycopy(candidateModel, 0, workModel, 0, numModelParam);
				lambda /= 10;
				recomputeDerivatives = true;
			} else {
				lambda *= 10;
				recomputeDerivatives = false;
			}
			// check to see if it has converged
			if( performFTest(prevCost,cost))
				break;
			if( performXTest(workModel,lambda))
				break;

			if( recomputeDerivatives ) {
				prevCost = cost;
			}
		}

		finalCost = prevCost;

		return true;
	}

	/**
	 * A*x=d
	 */
	private boolean solve() {
		if( !decomposition.decompose(A))
			throw new RuntimeException("Should never fail");

		DenseMatrix64F R = decomposition.getR(null,true);
		
		// see if it is positive definite
		double det = 1.0;
		int rank = decomposition.getRank();
		for( int i = 0; i < rank; i++ ) {
			det *= R.get(i,i);
		}
		if( det <= 0 )  {
			A.print();
			R.print();
			return false;
		}
//
		DenseMatrix64F Q = decomposition.getQ(null,true);

		// get the pivots and transpose them
		int pivotTran[] = new int[numModelParam];
		int pivots[] = decomposition.getPivots();
		for( int i = 0; i < numModelParam; i++ ) {
			pivotTran[pivots[i]] = i;
		}

		DenseMatrix64F y = new DenseMatrix64F(d.numRows,d.numCols);
		CommonOps.multTransA(Q,d,y);

		TriangularSolver.solveU(R.data, y.data, rank);

		correction.zero();

		// apply larger corrections along the direction it is most confident in
		for( int i = 0; i < rank; i++ ) {
			double val = y.data[i];//*(Math.abs(R.get(i,i))/max);
			correction.data[pivotTran[i]] = val;
		}
		return true;
	}

	/**
	 * Computes the linear system which will be solved
	 */
	private void computeMatrices(List<Observed> dataObserved , List<State> dataState, int numFunctions) {
		d.zero();
		H.zero();
		function.setModel(workModel);
		derivative.setModel(workModel);

		int rowJacobian = 0;
		for( int i = 0; i < totalObservations; i++ ) {
			Observed o = dataObserved != null ? dataObserved.get(i) : null;
			State s = dataState != null ? dataState.get(i) : null;

			// compute the approximate hessian and derivative
			function.computeResiduals(o,s,R.data);
			CommonOps.insert(R, Residuals, rowJacobian, 0);

			derivative.computeDerivative(s,gradient);

			for( int j = 0; j < numFunctions; j++ ) {
				// get function for this function
				double r = R.data[j];

				J.data = gradient[j];

				CommonOps.multAddTransB(J,J,H);
				CommonOps.addEquals(d,r,J);

				insertRowIntoJacobian(rowJacobian++);
			}
		}
	}

	private double norm( double[] data , int size )
	{
		double ret = 0;
		for( int i = 0; i < size; i++ ) {
			double a = data[i];
			ret += a*a;
		}
		return Math.sqrt(ret);
	}

	/**
	 * J is a column vector, but it needs to a row vector for insertion purposes.
	 *
	 * @param rowJacobian The row J is inserted into Jacobian
	 */
	private void insertRowIntoJacobian(int rowJacobian) {
		J.numCols= numModelParam; J.numRows=1;
		CommonOps.insert(J, Jacobian, rowJacobian, 0);
		J.numCols=1; J.numRows= numModelParam;
	}

	/**
	 * F-Test checks to see if the function has become approximately linear and is likely
	 * to be near the optimal solution
	 */
	private boolean performFTest( double prevCost , double currentCost ) {
		prevCost = Math.sqrt(prevCost);
		currentCost = Math.sqrt(currentCost);

		CommonOps.multAdd(Jacobian,correction,Residuals);
		double predictedCost = NormOps.normF(Residuals);

		// relative actual reduction
		double actred = 1-currentCost/prevCost;
		// relative predicted reduction
		double prered = 1-predictedCost/prevCost;

//		System.out.println("actred = "+actred+" prered = "+prered);

		if( prered > ftol )
			return false;

		if( Math.abs(actred) > ftol )
			return false;

		return( actred > 2*prered );
	}

	private boolean performXTest( double param[] , double lambda ) {
		double normParam = norm(param, numModelParam);
//		System.out.println("lambda = "+lambda+"  right = "+(xtol*normParam*lambda));
		return 1 <= xtol*normParam*lambda;
	}

	private void computeA( DenseMatrix64F A , DenseMatrix64F H , double lambda )
	{
		for( int i = 0; i < numModelParam; i++ ) {
			for( int j = 0; j < numModelParam; j++ ) {
				if( i==j) {
					A.set(i,j, H.get(i,j)*(1+lambda));
				} else {
					A.set(i,j, H.get(i,j));
				}
			}
		}
	}

	private double cost( double[] model , List<Observed> dataObserved , List<State> dataState )
	{
		function.setModel(model);

		int N = function.getNumberOfFunctions();

		double total = 0;
		for( int i = 0; i < totalObservations; i++ ) {
			Observed o = dataObserved != null ? dataObserved.get(i) : null;
			State s = dataState != null ? dataState.get(i) : null;

			if( !function.computeResiduals(o,s,R.data) )
				return Double.MAX_VALUE;

			for( int j = 0; j < N; j++ ) {
				double r = R.data[j];
				total += r*r;
			}
		}
		return total;
	}

	public double getInitialLambda() {
		return initialLambda;
	}

	public void setInitialLambda(double initialLambda) {
		this.initialLambda = initialLambda;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}
}