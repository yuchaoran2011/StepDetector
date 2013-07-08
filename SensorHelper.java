package cz.muni.fi.sandbox.service.stepdetector;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

public class SensorHelper {
	private static final String TAG = "SensorHelper";
	public static Sensor getSensor(SensorManager sensorManager, int sensorType, String sensorName) {
		Sensor sensor = sensorManager.getDefaultSensor(sensorType);
		if (sensor != null) {
			Log.d(TAG, "there is a " + sensorName);
		} else {
			Log.d(TAG, "there is no " + sensorName);
		}
		return sensor;
	}
}
