package cz.muni.fi.sandbox.service.stepdetector;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import cz.muni.fi.sandbox.dsp.filters.CumulativeSignalPowerTD;
import cz.muni.fi.sandbox.dsp.filters.MovingAverageTD;
import cz.muni.fi.sandbox.dsp.filters.SignalPowerTD;

/**
 * MovingAverageStepDetector class, step detection filter based on two moving averages
 * with minimum signal power threshold 
 * 
 * @author Michal Holcik
 * 
 */
public class MovingAverageStepDetector extends StepDetector {
	
	private static final String TAG = "MovingAverageStepDetector";
	private float[] maValues;
	private MovingAverageTD[] ma;
	private SignalPowerTD sp;
	private CumulativeSignalPowerTD asp;
	private boolean mMASwapState;
	private boolean stepDetected;
	private boolean signalPowerCutoff;
	private long mLastStepTimestamp;
	private double strideDuration;
	
	private static final long SECOND_IN_NANOSECONDS = (long) Math.pow(10, 9);
	public static final double MA1_WINDOW = 0.2;
	public static final double MA2_WINDOW = 5 * MA1_WINDOW;
	private static final long POWER_WINDOW = SECOND_IN_NANOSECONDS / 10;
	public static final float POWER_CUTOFF_VALUE = 1000.0f;
	private static final double MAX_STRIDE_DURATION = 2.0; // in seconds
	
	private double mWindowMa1;
	private double mWindowMa2;
	private long mWindowPower;
	private float mPowerCutoff;
	
	public MovingAverageStepDetector() {
		this(MA1_WINDOW, MA2_WINDOW, POWER_CUTOFF_VALUE);
	}

	public MovingAverageStepDetector(double windowMa1, double windowMa2, double powerCutoff) {
		
		mWindowMa1 = windowMa1;
		mWindowMa2 = windowMa2;
		mPowerCutoff = (float)powerCutoff;
		
		maValues = new float[4];
		mMASwapState = true;
		ma = new MovingAverageTD[] { new MovingAverageTD(mWindowMa1),
				new MovingAverageTD(mWindowMa1),
				new MovingAverageTD(mWindowMa2) };
		sp = new SignalPowerTD(mWindowPower);
		asp = new CumulativeSignalPowerTD();
		stepDetected = false;
		signalPowerCutoff = true;
	}

	public class MovingAverageStepDetectorState {
		float[] values;
		boolean[] states;
		double duration;

		MovingAverageStepDetectorState(float[] values, boolean[] states, double duration) {
			this.values = values;
			this.states = states;
		}
	}

	public MovingAverageStepDetectorState getState() {
		return new MovingAverageStepDetectorState(new float[] { maValues[0],
				maValues[1], maValues[2], maValues[3] }, new boolean[] {
				stepDetected, signalPowerCutoff }, strideDuration);
	}

	public float getPowerThreshold() {
		return mPowerCutoff;
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
		signalPowerCutoff = maValues[3] < mPowerCutoff;

		if (stepDetected) {
			asp.reset();
		}

		// step event
		if (stepDetected && !signalPowerCutoff) {
			strideDuration = getStrideDuration();
			Log.d("Stride Length", Double.valueOf(new StrideLengthEstimator(0.5, 1.75).getStrideLengthFromDuration(strideDuration)).toString());
			notifyOnStep(new StepEvent(1.0, strideDuration));
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
