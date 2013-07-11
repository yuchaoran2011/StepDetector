package cz.muni.fi.sandbox.service.stepdetector;

import java.util.LinkedList;
import java.util.Queue;

import thirdparty.fft.Complex;
import thirdparty.fft.FFT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import cz.muni.fi.sandbox.dsp.filters.PipedCrossCorrelation;
import cz.muni.fi.sandbox.dsp.filters.SineWaveCrossCorrelationBank;
import cz.muni.fi.sandbox.utils.ColorRamping;

/**
 *
 */
public class StepDetectionFFT extends Activity {
	/** Tag string for our debug logs */
	//private static final String TAG = "SensorsFFT";

	private SensorManager mSensorManager;
	private GraphView mGraphView;
	private Sensor mAccelerometer;
	private boolean doDrawXCorrBank = true;
	
	
	private class GraphView extends View implements SensorEventListener {
		
		private float mWidth;
		private float mHeight;
		
		private Bitmap mBitmap;
		private Canvas mCanvas = new Canvas();

		public GraphView(Context context) {
			super(context);

		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
			mCanvas.setBitmap(mBitmap);
			mCanvas.drawColor(Color.BLACK);
			
			mWidth = w;
			mHeight = h;
			super.onSizeChanged(w, h, oldw, oldh);
		}

		@SuppressLint("DrawAllocation")
		@Override
		protected void onDraw(Canvas canvas) {
			synchronized (this) {
				
				if (mBitmap != null) {
					Paint paint = new Paint();
					paint.setStyle(Paint.Style.FILL);
					paint.setColor(Color.WHITE);
					
					mCanvas.drawRect(0, 0, mWidth, mHeight / 2, paint);
					
					paint.setColor(Color.BLACK);
					mCanvas.drawLine(0, mHeight / 4, mWidth, mHeight / 4, paint); // axis
					drawSignals(mCanvas);
					drawFFT(mCanvas);
					if (doDrawXCorrBank) {
						drawXCorrBank(mCanvas);
					} else {
						drawXCorr(mCanvas);
					}
				}
				canvas.drawBitmap(mBitmap, 0, 0, null);
				
			}
		}

		
		private void drawSignals(Canvas canvas) {
			
			Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
			paint.setStyle(Paint.Style.STROKE);
			
			Path path = new Path();
			path.moveTo(0, mHeight / 4);			
			
			int size = signal2.length;
			float scale = 10.0f;
			for (int i = 0; i < size; i++) {
				path.lineTo((i) * mWidth / size, (float) (mHeight / 4 + scale * signal2[i].re()));
			}
			
			paint.setColor(Color.BLACK);
			canvas.drawPath(path, paint);
			
		}
		
		private void drawFFT(Canvas canvas) {
			
			if (lastFFT == null) {
				return;
			}
			
			Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
			paint.setStyle(Paint.Style.STROKE);
			
			Path path = new Path();

			
			paint.setColor(Color.BLACK);
			path.moveTo(0, mHeight / 2);
			
			int size = lastFFT.length;
			double max = 0;
			int maxIndex = 0;
			float scale = 1.0f;
			for (int i = 0; i < size; i++) {
				if (i > 0 && i < size / 2) { 
					if (max <= lastFFT[i].re()) {
						max = lastFFT[i].re();
						maxIndex = i;
					}
				}
				path.lineTo((i) * mWidth / size,
						(float) (mHeight / 2 - Math.abs(scale * lastFFT[i].re())));
			}
			paint.setColor(Color.BLUE);
			canvas.drawPath(path, paint);
			
			float times = size / 2;
			float top = 2 * mHeight / 4;
			float scaleY = (mHeight - top) / times;
			
			Paint paint2 = new Paint();
			for (int i = 0; i < times; i++) {
				
				double gray = Math.abs(lastFFT[i].re()) / 100.0;
				if (gray > 1) gray = 1;
				paint2.setColor(ColorRamping.blackToWhiteRamp(gray));
				canvas.drawLine(x, top + i * scaleY, x, top + (i + 1) * scaleY, paint2);
			}
			paint2.setColor(Color.RED);
			canvas.drawLine(x + 1, top, x + 1, top + mHeight, paint2);
			x = (x + 1) % (int)mWidth;
			
			canvas.drawText("frequency maximum is " + maxIndex, 0, 20, paint);
			
		}
		
		/**
		 * TODO: do something about this method.
		 * @param canvas
		 */
		private void drawXCorr(Canvas canvas) {
			
			if (xcorrValues == null) {
				return;
			}
			
			Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.MAGENTA);
			
			Path path = new Path();
			
			float yOffset = mHeight / 4;
			path.moveTo(0, yOffset);
			
			
			int size = xcorrValues.size();
			float scale = yOffset;
			int i = 0;
			for (double value: xcorrValues) {
				//Log.d(TAG, "drawXCorr: value = " + value);	
				path.lineTo((i) * mWidth / size,
						(float) (yOffset - (scale * Math.abs(value))));
				i++;
			}
			canvas.drawPath(path, paint);
		}
		
		private void drawXCorrBank(Canvas canvas) {
			
			Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.MAGENTA);
			
			double[] values = xcBank.getValues();
			int size = values.length;
			Path path = new Path();
			Path path2 = new Path();
			
			
			float yOffset = mHeight / 4;
			path.moveTo(0, yOffset);
			
			float scale = 1.0f;
			
			int i = 0;
			for (double value: values) {
				//Log.d(TAG, "drawXCorr: value = " + value);	
				path.lineTo((i) * mWidth / size,
						(float) (yOffset - (scale * value)));
				i++;
			}
			
			i = 0;
			for (double max: xcBank.getMaximums()) {
				path2.lineTo((i) * mWidth / size,
						(float) (yOffset - (scale * max)));
				i++;
			}
			canvas.drawPath(path, paint);
			paint.setColor(Color.BLACK);
			canvas.drawPath(path2, paint);
		}
		
		private int x = 0;

		@Override
		public void onSensorChanged(SensorEvent event) {
			synchronized (this) {
				if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
					processAccelerometerEvent(event);
					invalidate();
				}
			}

		}


		int stepCounter = 0;
		int STEP_SIZE = 10;
		
	
		public void processAccelerometerEvent(SensorEvent event) {
			// Log.d(TAG, "sensor: " + sensor + ", x: " + values[0] + ", y: " +
			// values[1] + ", z: " + values[2]);
			
			signal.poll();
			signal.add(new Complex(event.values[2], 0.0));
			
			//Complex[] input = signal2;

			xcorr.push(event.values[2]);
			xcorrValues.poll();
			double xcorrV = xcorr.getRelativeValue();
			xcorrValues.add(xcorrV);
			//System.out.println(xcorrV);
			
			//signal2[signal2Index] = new Complex(event.values[2], 0.0);
			//signal2Index = (signal2Index+1) % signal2.length;
			
			if (stepCounter == STEP_SIZE) {
				
				
				Object[] array = signal.toArray();
				int i = 0;
				for (Object value: array) {
					signal2[i] = (Complex)value;
					i++;
				}
				
				lastFFT = FFT.fft(signal2);
				stepCounter = 0;
				invalidate();
			} else {
				stepCounter++;
			}
			
			xcBank.push(event.values[2]);

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
		}

	}

	private Complex[] lastFFT;
	private Queue<Complex> signal;
	private static final int SIGNAL_SIZE = 64;
	private Complex[] signal2 = new Complex[SIGNAL_SIZE];
	//private int signal2Index = 0;
	private PipedCrossCorrelation xcorr;
	private Queue<Double> xcorrValues;
	private SineWaveCrossCorrelationBank xcBank;
	
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
		mAccelerometer = SensorHelper.getSensor(mSensorManager,
				Sensor.TYPE_ACCELEROMETER, "accelerometer");

		mGraphView = new GraphView(this);
		setContentView(mGraphView);

		signal = new LinkedList<Complex>();

		float f0 = 50; 
		
		for (int i = 0; i < SIGNAL_SIZE; i++) {
			signal.add(new Complex(0.0, 0.0));
			signal2[i] = new Complex(Math.sin(2 * Math.PI * f0 * i / SIGNAL_SIZE), 0.0);
		}
		
		int kernelSize = (int)(100 * 0.7);
		double[] xcorrKernel = new double[kernelSize];
		for (int i = 0; i < kernelSize; i++) {
			xcorrKernel[i] = Math.sin(i * 2.0 * Math.PI / 70);
		}
		xcorr = new PipedCrossCorrelation(xcorrKernel);
		
		xcorrValues = new LinkedList<Double>();
		for (int i = 0; i < SIGNAL_SIZE; i++) {
			xcorrValues.add(0.0);
		}
		
		xcBank = new SineWaveCrossCorrelationBank(100, new float[] {2.0f, 1.9f, 1.8f, 1.7f, 1.6f, 1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1.0f});
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
