package cz.muni.fi.sandbox.service.stepdetector;

import java.util.ArrayList;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

/**
 * Step detector base class. Can be used as a dummy step detector. Doesn't
 * detect any steps.
 * 
 */
public class StepDetector implements SensorEventListener, IStepDetector {

	protected ArrayList<IStepListener> mStepListeners = new ArrayList<IStepListener>();
	

	public void addStepListener(IStepListener sl) {
		if (sl != null) {
			mStepListeners.add(sl);
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
	}

	protected void notifyOnStep(StepEvent event) {
		for (IStepListener stepListener : mStepListeners) {
			stepListener.onStepEvent(event);
		}
		Log.d("TAG", event.toString());
	}

	public static StepDetector stepDetectorFactory(String detectorName) {

		if (detectorName.equals("moving_average")) {
			return new MovingAverageStepDetector();
		} else if (detectorName.equals("null")) {
			return new StepDetector();
		}
		return new StepDetector();
	}

}
