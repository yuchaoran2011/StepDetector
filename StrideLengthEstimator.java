package cz.muni.fi.sandbox.service.stepdetector;

/**
 * StrideLengthEstimator class, contains model for estimation of the stride
 * length based on stride duration.
 * 
 * @author Michal Holcik
 * 
 */
public class StrideLengthEstimator {

	private static final double DEFAULT_STRIDE_LENGTH = 0.7;
	
	private double stepLength;
	private double factor;
	
	/**
	 * Constructor
	 * 
	 * @param stepLength
	 *            is a reference step length to calibrate the model
	 * @param factor
	 *            is a linear correction factor (leg length or person height can
	 *            be used as basis)
	 */
	public StrideLengthEstimator(double stepLength, double factor) {
		this.stepLength = stepLength;
		this.factor = factor;
	}

	/**
	 * The estimator uses the stride length vs. frequency dependency to compute
	 * stride length estimate from stride duration.
	 * 
	 * @param duration
	 *            stride duration
	 */
	public double getStrideLengthFromDuration(double duration) {
		double retval = DEFAULT_STRIDE_LENGTH;
		return factor * (0.3608 + 0.1639 / duration);
	}
}
