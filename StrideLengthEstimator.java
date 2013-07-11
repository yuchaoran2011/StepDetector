package cz.muni.fi.sandbox.service.stepdetector;

import java.util.Random;

/**
 * StrideLengthEstimator class, contains model for estimation of the stride
 * length based on stride duration.
 * 
 */
public class StrideLengthEstimator {
	
	private double height;
	
	/**
	 * Constructor
	 * 
	 * @param stepLength
	 *            is a reference step length to calibrate the model
	 * @param factor
	 *            is a linear correction factor (leg length or person height can
	 *            be used as basis)
	 */
	public StrideLengthEstimator(double height) {
		this.height = height;
	}

	/**
	 * The estimator uses the stride length vs. frequency dependency to compute
	 * stride length estimate from stride duration.
	 * 
	 * @param duration
	 *            stride duration
	 */
	public double getStrideLengthFromDuration(double duration) {
		//return factor * (0.3608 + 0.1639 / duration) * DEFAULT_STRIDE_LENGTH;
		Random ranGen= new Random();
		double standardNoise = ranGen.nextGaussian(); 
		double zScore = 0.15;
		double correctedNoise = standardNoise * zScore; 
		double strideLength = height * 0.415 + correctedNoise;
		if (strideLength <= 1.0)
			return strideLength;
		else 
			return Double.NaN;
	}
}
