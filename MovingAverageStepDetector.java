package cz.muni.fi.sandbox.service.stepdetector;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import cz.muni.fi.sandbox.dsp.filters.CumulativeSignalPowerTD;
import cz.muni.fi.sandbox.dsp.filters.MovingAverageTD;
import cz.muni.fi.sandbox.dsp.filters.SignalPowerTD;

/**
 * MovingAverageStepDetector class, step detection filter based on two moving averages
 * with minimum and maximum signal power thresholds 
 * 
 */
public class MovingAverageStepDetector extends StepDetector {
	
	@SuppressWarnings("unused")
	private static final String TAG = "MovingAverageStepDetector";
	private float[] maValues;
	private MovingAverageTD[] ma;
	private SignalPowerTD sp;
	private CumulativeSignalPowerTD asp;
	private boolean mMASwapState;
	private boolean stepDetected;
	private boolean signalPowerOutOfRange;
	private long mLastStepTimestamp;
	private double strideDuration;
	
	private static final long SECOND_IN_NANOSECONDS = (long) Math.pow(10, 9);
	public static final double MA1_WINDOW = 0.2;
	public static final double MA2_WINDOW = 5 * MA1_WINDOW;
	@SuppressWarnings("unused")
	private static final long POWER_WINDOW = SECOND_IN_NANOSECONDS / 10;
	
	public static final float LOW_POWER_CUTOFF_VALUE = 2000.0f;
	public static final float HIGH_POWER_CUTOFF_VALUE = 90000.0f;
	
	
	private static final double MAX_STRIDE_DURATION = 2.0; // in seconds
	private static final double MIN_STRIDE_DURATION = 0.1;
	
	
	private double mWindowMa1;
	private double mWindowMa2;
	private long mWindowPower;
	private float mLowPowerCutoff, mHighPowerCutoff;
	
	public MovingAverageStepDetector() {
		this(MA1_WINDOW, MA2_WINDOW, LOW_POWER_CUTOFF_VALUE, HIGH_POWER_CUTOFF_VALUE);
	}

	public MovingAverageStepDetector(double windowMa1, double windowMa2, double lowPowerCutoff, double highPowerCutoff) {
		
		mWindowMa1 = windowMa1;
		mWindowMa2 = windowMa2;
		mLowPowerCutoff = (float)lowPowerCutoff;
		mHighPowerCutoff = (float)highPowerCutoff;
		
		maValues = new float[4];
		mMASwapState = true;
		ma = new MovingAverageTD[] { new MovingAverageTD(mWindowMa1),
				new MovingAverageTD(mWindowMa1),
				new MovingAverageTD(mWindowMa2) };
		sp = new SignalPowerTD(mWindowPower);
		asp = new CumulativeSignalPowerTD();
		stepDetected = false;
		signalPowerOutOfRange = true;
	}

	public class MovingAverageStepDetectorState {
		public float[] values;
		public boolean[] states;
		public double duration;

		MovingAverageStepDetectorState(float[] values, boolean[] states, double duration) {
			this.values = values;
			this.states = states;
		}
	}
	

	public MovingAverageStepDetectorState getState() {
		return new MovingAverageStepDetectorState(new float[] { maValues[0],
				maValues[1], maValues[2], maValues[3] }, new boolean[] {
				stepDetected, signalPowerOutOfRange }, strideDuration);
	}

	public float getLowPowerThreshold() {
		return mLowPowerCutoff;
	}
	
	public float getHighPowerThreshold() {
		return mHighPowerCutoff;
	}

	private void processAccelerometerValues(long timestamp, float[] values) {

		float value = values[2];

		// compute moving averages
		maValues[0] = value;
		for (int i = 1; i < 3; i++) {
			ma[i].push(timestamp, value);
			maValues[i] = (float) ma[i].getAverage();
			value = maValues[i];
		}

		// detect moving average crossover
		stepDetected = false;
		boolean newSwapState = maValues[1] > maValues[2];
		if (newSwapState != mMASwapState) {
			mMASwapState = newSwapState;
			if (mMASwapState) {
				stepDetected = true;
			}
		}

		// compute signal power
		sp.push(timestamp, maValues[1] - maValues[2]);
		asp.push(timestamp, maValues[1] - maValues[2]);
		// maValues[3] = (float)sp.getPower();
		maValues[3] = (float) asp.getValue();
		signalPowerOutOfRange = (maValues[3] < mLowPowerCutoff) || (maValues[3] > mHighPowerCutoff);

		if (stepDetected) {
			asp.reset();
		}

		// step event
		if (stepDetected && signalPowerOutOfRange) {
			if (maValues[3] < mLowPowerCutoff)
				Log.d("Invalid Step", "Power too low!");
			if (maValues[3] > mHighPowerCutoff)
				Log.d("Invalid Step", "Power too high!");
		}
		
		
		if (stepDetected && !signalPowerOutOfRange) {
			
			strideDuration = getStrideDuration();
			double strideLength;
			
			if (strideDuration != Double.NaN && strideDuration <= MAX_STRIDE_DURATION && strideDuration >= MIN_STRIDE_DURATION) {
				notifyOnStep(new StepEvent(1.0, strideDuration));
				strideLength = new StrideLengthEstimator(1.76).getStrideLengthFromDuration(strideDuration);
				/* Round to 4 decimal places */
				strideLength = strideLength * 10000;
				strideLength = Math.round(strideLength);
				strideLength =  strideLength / 10000;
				Log.d("StrideLengthTest", Double.valueOf(strideLength).toString());
			}
			else {
				Log.d("Invalid Stride Duration", "Stride Duration NaN!");
			}
		}

	}

	/**
	 * call has side-effects, must call only when step is detected.
	 * 
	 * @return stride duration if the duration is less than MAX_STRIDE_DURATION,
	 *         NaN otherwise
	 */
	private double getStrideDuration() {
		// compute stride duration
		long currentStepTimestamp = System.nanoTime();
		double strideDuration;
		strideDuration = (double) (currentStepTimestamp - mLastStepTimestamp)
				/ SECOND_IN_NANOSECONDS;
		if (strideDuration > MAX_STRIDE_DURATION) {
			strideDuration = Double.NaN;
		}
		mLastStepTimestamp = currentStepTimestamp;
		return strideDuration;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// Log.d(TAG, "sensor: " + sensor + ", x: " + values[0] + ", y: " +
		// values[1] + ", z: " + values[2]);
		synchronized (this) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				processAccelerometerValues(event.timestamp, event.values);
			}
		}
	}
}
