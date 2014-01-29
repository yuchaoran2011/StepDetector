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
		// http://www.javamex.com/tutorials/random_numbers/gaussian_distribution_2.shtml 
		double gaussianNoise = ranGen.nextGaussian() * 0.05;  
		double strideLength = height * 0.45 + gaussianNoise; //0.55
		if (strideLength < 0.1)
			return height * 0.415;
		else 
			return strideLength;
	}
}
