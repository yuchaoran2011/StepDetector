package cz.muni.fi.sandbox.service.stepdetector;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import cz.muni.fi.sandbox.dsp.filters.ContinuousConvolution;
import cz.muni.fi.sandbox.dsp.filters.FrequencyCounter;
import cz.muni.fi.sandbox.dsp.filters.SinXPiWindow;
import cz.muni.fi.sandbox.service.stepdetector.MovingAverageStepDetector.MovingAverageStepDetectorState;

public class StepDetectionDemo extends Activity {

	private static final String TAG = "Sensors";

	private SensorManager mSensorManager;
	private GraphView mGraphView;
	private Sensor mAccelerometer;

	
	private Sensor getSensor(int sensorType, String sensorName) {

		Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
		if (sensor != null) {
			Log.d(TAG, "there is a " + sensorName);
		} else {
			Log.d(TAG, "there is no " + sensorName);
		}
		return sensor;
	}

	
	
	private class GraphView extends View implements SensorEventListener {
		private Bitmap mBitmap;
		private Paint mPaint = new Paint();
		private Canvas mCanvas = new Canvas();
		private Path mPath = new Path();
		private RectF mRect = new RectF();
		private float mLastValues[] = new float[3 * 2];
		private int mColors[] = new int[3 * 2];
		private float mLastX;
		private float mScale[] = new float[3];
		private float[] mYOffset = new float[4];
		private float mYOffset2;
		private float mMaxX;
		private float mSpeed = 1f;
		private float mWidth;
		private float mHeight;
		private int mMASize = 20;

		private MovingAverageStepDetector mStepDetector;
		private ContinuousConvolution mCC;
		private FrequencyCounter freqCounter;
		private boolean mTouched;

		
		public GraphView(Context context) {
			super(context);
			mColors[0] = Color.argb(192, 255, 64, 64);
			mColors[1] = Color.argb(192, 64, 128, 64);
			mColors[2] = Color.argb(192, 64, 64, 255);
			mColors[3] = Color.argb(192, 64, 255, 255);
			mColors[4] = Color.argb(192, 128, 64, 128);
			mColors[5] = Color.argb(192, 255, 255, 64);

			mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
			mRect.set(-0.5f, -0.5f, 0.5f, 0.5f);
			mPath.arcTo(mRect, 0, 180);

			
			// load parameters from configuration
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			double movingAverage1 = MovingAverageStepDetector.MA1_WINDOW;
			double movingAverage2 = MovingAverageStepDetector.MA2_WINDOW;
			
			double lowPowerCutoff = MovingAverageStepDetector.LOW_POWER_CUTOFF_VALUE;
			double highPowerCutoff = MovingAverageStepDetector.HIGH_POWER_CUTOFF_VALUE;
			
			if (prefs != null) {
				try {
					movingAverage1 = Double.valueOf(prefs.getString("short_moving_average_window_preference", "0.2"));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
				try {
					movingAverage2 = Double.valueOf(prefs.getString("long_moving_average_window_preference", "1.0"));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
				try {
					lowPowerCutoff = Double.valueOf(prefs.getString("step_detection_low_power_cutoff_preference", "200"));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
				try {
					highPowerCutoff = Double.valueOf(prefs.getString("step_detection_upper_power_cutoff_preference", "20000"));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
			}
			
			mStepDetector = new MovingAverageStepDetector(movingAverage1, movingAverage2, lowPowerCutoff, highPowerCutoff);
			// mStepDetector = new MovingAverageStepDetector();

			mCC = new ContinuousConvolution(new SinXPiWindow(mMASize));
			freqCounter = new FrequencyCounter(20);
		}
		
		
		
		@Override
		public boolean onTouchEvent(MotionEvent event) {
				
			switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
				mTouched = true;
				Log.d(TAG, "touch event detected");
				break;
			}
			return true;
		}
		
		

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
			mCanvas.setBitmap(mBitmap);
			mCanvas.drawColor(0xFFFFFFFF);
			mYOffset[0] = h * 0.5f;
			mYOffset[1] = h * 0.25f;
			mYOffset[2] = h * 0.25f;
			mYOffset[3] = h * 0.75f;
			
			mScale[0] = -(h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
			mScale[1] = -(h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
			mScale[2] = -(h * 0.5f * (1.0f / 100000));
			
			mWidth = w;
			mHeight = h;
			if (mWidth < mHeight) {
				mMaxX = w;
			} else {
				mMaxX = w - 50;
			}
			mLastX = mMaxX;
			super.onSizeChanged(w, h, oldw, oldh);
		}

		
		
		@Override
		protected void onDraw(Canvas canvas) {
			synchronized (this) {
				if (mBitmap != null) {
					final Paint paint = mPaint;

					if (mLastX >= mMaxX) {
						mLastX = 0;
						final Canvas cavas = mCanvas;
						final float yoffset = mYOffset2;
						final float maxx = mMaxX;
						final float oneG = SensorManager.STANDARD_GRAVITY * mScale[0];
						paint.setColor(0xFFAAAAAA);
						cavas.drawColor(0xFFFFFFFF);
						cavas.drawLine(0, yoffset, maxx, yoffset, paint);
						cavas.drawLine(0, yoffset + oneG, maxx, yoffset + oneG,
								paint);
						cavas.drawLine(0, yoffset - oneG, maxx, yoffset - oneG,
								paint);
					}
					canvas.drawBitmap(mBitmap, 0, 0, null);
				}
			}
		}

		
		
		
		

		float mConvolution, mLastConvolution;

		private void displayStepDetectorState(
				MovingAverageStepDetectorState state) {

			final Canvas canvas = mCanvas;
			final Paint paint = mPaint;

			float deltaX = mSpeed;
			float newX = mLastX + deltaX;

			float[] maValues = new float[4];
			boolean stepDetected = false;
			boolean signalPowerOutOfRange = true;

			maValues[0] = -state.values[0];
			maValues[1] = -state.values[1];
			maValues[2] = -state.values[2];
			maValues[3] = state.values[3];

			stepDetected = state.states[0];
			signalPowerOutOfRange = state.states[1];

			float v = 0;

			
			
			
			// draw convolution
			v = mYOffset[1] + mConvolution * mScale[0]; // (float)(-mHeight *
														// 0.5 / 1000.0);
			paint.setColor(Color.BLACK);
			canvas.drawLine(mLastX, mLastConvolution, newX, v, paint);
			mLastConvolution = v;
			

			
			
			
			// draw power
			v = mYOffset[1] + maValues[3] * mScale[2];
			paint.setColor(mColors[4]);
			canvas.drawLine(mLastX, mLastValues[3], newX, v, paint);
			mLastValues[3] = v;
			
			
			
			
			
			// draw power cutoff threshold
			
			v = mYOffset[1] + mStepDetector.getLowPowerThreshold() * mScale[2];
			paint.setColor(Color.RED);
			canvas.drawLine(0, v, mWidth, v, paint);
			
			
			
			// draw lines
			for (int i = 0; i < 3; i++) {
				v = mYOffset[i] + maValues[i] * mScale[0];
				paint.setColor(mColors[i]);
				canvas.drawLine(mLastX, mLastValues[i], newX, v, paint);
				mLastValues[i] = v;
			}
			
			

			
			// draw step
			if (stepDetected) {
				if (signalPowerOutOfRange) {
					paint.setColor(Color.RED);
				} else {
					paint.setColor(Color.GREEN);
				}
				canvas.drawCircle(newX, v, 5, paint);
			}
			
			
			
			
			// draw touch event
			if (mTouched) {
				mTouched = false;
				//paint.setStyle(Style.STROKE);
				paint.setColor(Color.GREEN);
				Path path = new Path();
				path.moveTo(newX, mYOffset[1] + 50);
				path.lineTo(newX + 10, mYOffset[1] + 36);
				path.lineTo(newX - 10, mYOffset[1] + 36);
				path.close();
				canvas.drawPath(path, paint);
				//canvas.drawCircle(newX, mYOffset[1], 10, paint);
				paint.setStyle(Style.FILL);
			}
			
			
			paint.setColor(Color.WHITE);
			canvas.drawRect(0, 0, mWidth, 30, paint);
			paint.setColor(Color.BLACK);
			canvas.drawText("sensor rate: " + freqCounter.getRateF(), 0, 20, paint);
			

			mLastX += mSpeed;

			invalidate();
		}
		
		
		
		

		@Override
		public void onSensorChanged(SensorEvent event) {
			synchronized (this) {
				if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
					processAccelerometerEvent(event);
					freqCounter.push(event.timestamp);
					float rate = freqCounter.getRateF();
					if (rate != 0.0f)
						mSpeed = 100f / rate;
					invalidate();
				}
			}
		}
		
		
		

		public void processAccelerometerEvent(SensorEvent event) {
			// Log.d(TAG, "sensor: " + sensor + ", x: " + values[0] + ", y: " +
			// values[1] + ", z: " + values[2]);

			if (mBitmap != null) {
				mConvolution = (float) (mCC.process(event.values[2]));
				mStepDetector.onSensorChanged(event);
				displayStepDetectorState(mStepDetector.getState());
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
		}
	}
	
	
	

	/**
	 * Initialization of the Activity after it is first created. Must at least
	 * call {@link android.app.Activity#setContentView setContentView()} to
	 * describe what is to be displayed in the screen.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Be sure to call the super class.
		super.onCreate(savedInstanceState);

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = getSensor(Sensor.TYPE_ACCELEROMETER, "accelerometer");
		
		mGraphView = new GraphView(this);
		setContentView(mGraphView);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mAccelerometer != null)
			mSensorManager.registerListener(mGraphView, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		/*
		 * if (mMagnetometer != null) mSensorManager.registerListener(this,
		 * mMagnetometer, SensorManager.SENSOR_DELAY_FASTEST); if
		 * (mAccelerometer != null) mSensorManager.registerListener(mGraphView,
		 * mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
		 */

	}

	@Override
	protected void onStop() {
		mSensorManager.unregisterListener(mGraphView);
		super.onStop();
	}
}
