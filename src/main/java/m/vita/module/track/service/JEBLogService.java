/*
Copyright (C) 2011 The University of Michigan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Please send inquiries to powertutor@umich.edu
 */

package m.vita.module.track.service;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import m.vita.module.track.phone.PhoneSelector;
import m.vita.module.track.util.DateUtil;

public class JEBLogService extends Service implements SensorEventListener {
	private static final String TAG = "JEBLogService";
	private static final int NOTIFICATION_ID = 1;

	Context mContext;
	private Thread estimatorThread;
	private PowerEstimator powerEstimator;
	private Thread screenshotThread;

	private boolean running = false;

	private NotificationCompat.Builder notification;
	private NotificationManager notificationManager;
	private List<Integer> targetAppUidList;

	private Boolean debug = true;

	PowerManager powerManager;
	PowerManager.WakeLock wakeLock;
	WifiManager wifiManager;
	WifiLock wifiLock;
	SharedPreferences mPreferences;
//	FileOutputStream fos;
//	OutputStreamWriter osw;
//	BufferedWriter bw;
	String time;
	private boolean isGPS;
//	private boolean isAudio;
//	private boolean isAudioDeviceActivity;
	private boolean isCount;
	private boolean isCurrentKey;
	private boolean isLogTotal;
	private boolean isCpuUsage;
	private boolean isMemory;
	private boolean isPid;
	private boolean isCapacity;
	private boolean isTraffic;
	private boolean isWakeLock;
	private boolean isWifiLock;
	private boolean isKB;
	private boolean isRealTimeOverlay;
	private boolean isFileWrite;

	private WindowManager.LayoutParams params;
	private WindowManager wm;
	private float START_X, START_Y;
	private int PREV_X, PREV_Y;
	private int MAX_X = -1, MAX_Y = -1;
	private static int oldPid;
	private static int pidChangeCount;
	private static LayoutInflater layoutInflater;
//
	private boolean myTextOn = true;

	/*
	* 자이로 스코프 센서 감지
	* */
	private long lastTime;
	private float speed;
	private float lastX;
	private float lastY;
	private float lastZ;
	private float x, y, z;
	int SHAKE_THRESHOLD = 2000;
	private static final int DATA_X = SensorManager.DATA_X;
	private static final int DATA_Y = SensorManager.DATA_Y;
	private static final int DATA_Z = SensorManager.DATA_Z;
//	SensorManager sensorManager;
//	Sensor accelerormeterSensor;
//	Sensor oriSensor;
//	Sensor cpuTempSensor;

	private boolean statFlag = false;
	private boolean flagService = false;
	/**
	 * Service Class Var
	 * **/
//	Intent str;
	//
	private static final int LEFT_SLIDE_TRY_OPTIONS_DURATION = 1000;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);

		if (intent.getBooleanExtra("stop", false)) {
			this.stopSelf();
			return START_REDELIVER_INTENT;
		} else if (estimatorThread != null) {
			return START_REDELIVER_INTENT;
		}
//		else if(intent.getStringExtra("serviceIntent").equals("start")){
//			str = intent;
//		}

		if (isWakeLock)
			wakeLock.acquire();
		if (isWifiLock)
			wifiLock.acquire();

		estimatorThread = new Thread(powerEstimator);
		estimatorThread.setDaemon(true);
		estimatorThread.start();

		setRunning(true);

//		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
//			Toast.makeText(mContext, "OS Version : " + android.os.Build.VERSION.SDK_INT, Toast.LENGTH_SHORT).show();
//		}else{
//			Toast.makeText(mContext, "OS Version : " + android.os.Build.VERSION.SDK_INT, Toast.LENGTH_SHORT).show();
//		}


		if (isRealTimeOverlay) {
//			overlayMemoryUnit = (TextView) oView.findViewById(R.id.rt_mem_unit);
		}

		return START_REDELIVER_INTENT;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
//		onScreenshotEventReg = new OnScreenshotEventReg(this);
//		screenshotThread = new Thread(onScreenshotEventReg);
//		screenshotThread.setDaemon(true);
//		screenshotThread.start();
	}

	@SuppressLint("InvalidWakeLockTag")
	@Override
	public void onCreate() {

		mContext = getApplicationContext();
		mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

		isCurrentKey = mPreferences.getBoolean("pref_current_battery", true);
		isLogTotal = mPreferences.getBoolean("pref_total_battery", true);
		isGPS = mPreferences.getBoolean("pref_gps", true);
//		isAudio = mPreferences.getBoolean("pref_audio", true);

		isCpuUsage = mPreferences.getBoolean("pref_cpuusage", true);
		isMemory = mPreferences.getBoolean("pref_memory", true);
		isTraffic = mPreferences.getBoolean("pref_traffic", true);

		isCount = mPreferences.getBoolean("pref_count", true);
//		isAudioDeviceActivity = mPreferences.getBoolean("pref_audio_device", true);
		isPid = mPreferences.getBoolean("pref_pid", true);
		isCapacity = mPreferences.getBoolean("pref_capacity", true);

		String powerModel = mPreferences.getString("pref_powermodel", "DREAM");
		if (powerModel.equals("SAPPHIRE")) {
			PhoneSelector.PHONE_TYPE = PhoneSelector.PHONE_SAPPHIRE;
			PhoneSelector.isOLED = false;
		} else if (powerModel.equals("PASSION")) {
			PhoneSelector.PHONE_TYPE = PhoneSelector.PHONE_PASSION;
			PhoneSelector.isOLED = true;
		} else {
			PhoneSelector.PHONE_TYPE = PhoneSelector.PHONE_DREAM;
			PhoneSelector.isOLED = false;
		}
		isWakeLock = mPreferences.getBoolean("pref_wakelock", true);
		isWifiLock = mPreferences.getBoolean("pref_wifilock", true);
		isKB = mPreferences.getBoolean("pref_traffic_kb", true);
		isRealTimeOverlay = mPreferences.getBoolean("pref_overlay", true)
					& mPreferences.getBoolean("one_app", false);
		isFileWrite = mPreferences.getBoolean("pref_file_write", true);

		oldPid = -1;
		pidChangeCount = 0;

		powerEstimator = new PowerEstimator(this);

		/* Register to receive airplane mode and battery low messages. */
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
		filter.addAction(Intent.ACTION_BATTERY_LOW);
		filter.addAction(Intent.ACTION_BATTERY_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addAction(Intent.ACTION_PACKAGE_REPLACED);

		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		time = DateUtil.getSimpleDate();
		Log.d("Time", "time : " + time);
		powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
		wifiLock = wifiManager.createWifiLock(TAG);

		if (isRealTimeOverlay) {
			////////////////////////////////////////////////////////

	        params = new WindowManager.LayoutParams(
	                WindowManager.LayoutParams.WRAP_CONTENT,
//	        		(int) getResources().getDimension(R.dimen.overlay_view),
	                WindowManager.LayoutParams.WRAP_CONTENT,
	                WindowManager.LayoutParams.TYPE_PHONE,
//	                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
//					WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|
					WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|
	                0 | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
	                PixelFormat.TRANSLUCENT);
	        params.gravity = Gravity.TOP | Gravity.RIGHT;

	        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

	        layoutInflater = (LayoutInflater) mContext.getSystemService(
	        											Context.LAYOUT_INFLATER_SERVICE);

		}

	}

	void setRunning(boolean b) {
		running = b;
	}

	// Handler 에서 호출하는 함수
	private void handleMessage(Message msg) {

		if (myTextOn) {
			myTextOn = false;
//			ll_over_view.setBackgroundResource(R.drawable.apptheme_scrubber_control_disabled_holo);

		} else {
			myTextOn = true;
//			ll_over_view.setBackgroundResource(R.drawable.apptheme_scrubber_control_focused_holo);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
//		if (isRealTimeOverlay) {
//			setMaxPosition();
//			optimizePosition();
//		}
	}

//	private void setMaxPosition() {
//		DisplayMetrics matrix = new DisplayMetrics();
//		wm.getDefaultDisplay().getMetrics(matrix);
//
//		MAX_X = matrix.widthPixels - oView.getWidth();
//		MAX_Y = matrix.heightPixels - oView.getHeight();
//	}
//
//
//	private void optimizePosition() {
//		if(params.x > MAX_X) params.x = MAX_X;
//		if(params.y > MAX_Y) params.y = MAX_Y;
//		if(params.x < 0) params.x = 0;
//		if(params.y < 0) params.y = 0;
//	}

	/*
	* Overlay View FAB(Floating Action Buttong) SIZE
	* x : 420 , y : 119
	* x : 50 , y : 50
	*/
	private OnTouchListener mViewTouchListener = new OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if (MAX_X == -1) {
//					setMaxPosition();
				}
				START_X = event.getRawX();
				START_Y = event.getRawY();
				PREV_X = params.x;
				PREV_Y = params.y;

				break;
			case MotionEvent.ACTION_MOVE:
				int x = (int) (START_X - event.getRawX());
				int y = (int) (event.getRawY() - START_Y);

				params.x = PREV_X + x;
				params.y = PREV_Y + y ;

//				optimizePosition();
//				wm.updateViewLayout(oView, params);
				break;
			}
			return true;
		}
	};

	@Override
	public void onDestroy() {

		if (estimatorThread != null) {
			estimatorThread.interrupt();
		}
		this.stopSelf();

		super.onDestroy();
		Log.d(TAG, "onDestroy()");
		//
//		if(rightLowerMenu != null && rightLowerMenu.isOpen()) rightLowerMenu.close(false);
//		if(rightLowerButton != null) rightLowerButton.detach();

		/*
		 * See comments in showNotification() for why we are using reflection
		 * here.
		 */
		boolean foregroundSet = false;
		try {
			Method stopForeground = getClass().getMethod("stopForeground", boolean.class);
			stopForeground.invoke(this, true);
			foregroundSet = true;
		} catch (InvocationTargetException e) {
		} catch (IllegalAccessException e) {
		} catch (NoSuchMethodException e) {
		}
		if (!foregroundSet) {
			stopForeground(true);
			notificationManager.cancel(NOTIFICATION_ID);
		}

//		try {
//			bw.close();
//			osw.close();
//			fos.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (NullPointerException e) {
//		}
		try{
			if (wakeLock.isHeld()) wakeLock.release();
		}
		catch(Exception e){
			//probably already released
			Log.e(TAG, e.getMessage());
		}

		try {
			if (isWifiLock) wifiLock.release();
		}catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}



//		super.onDestroy();


//		killRunningApps(statFlag, flagService);
	}

	@Override
	public IBinder onBind(Intent intent) {

		String[] filter = powerEstimator.getComponents();

		// UTF-8 BOM for Excel CSV
		byte[] utf8Bom = { (byte)0xEF, (byte)0xBB, (byte)0xBF };
		String utf8String = new String(utf8Bom);
		StringBuilder filterStrBuilder = new StringBuilder(utf8String);


		filterStrBuilder.append(	"시간");
//		StringBuilder filterStrBuilder = new StringBuilder("시간");

		if (isCount)
			filterStrBuilder = filterStrBuilder.append(", 카운트");

		filterStrBuilder = filterStrBuilder.append(", 이름, PackageName");

		if (isCurrentKey)
			filterStrBuilder = filterStrBuilder.append(", 현재");

		if (isLogTotal)
			filterStrBuilder = filterStrBuilder.append(", 누적");

		for(int i = 0; i < filter.length - 2; i++) {
			if ((mPreferences.getInt("topIgnoreMask", 0) & 1 << i) != 0)
				continue;
			filterStrBuilder = filterStrBuilder.append(", ");
			filterStrBuilder = filterStrBuilder.append(filter[i]);
		}

		if (isCpuUsage)
			filterStrBuilder = filterStrBuilder.append(", CPU Usage");

		if (isMemory)
			filterStrBuilder = filterStrBuilder.append(", totalPSS, totalPD, totalSD, dalvikPSS, otherPSS");

		if (isCapacity)
			filterStrBuilder = filterStrBuilder.append(", Capacity");

		if (isPid)
			filterStrBuilder = filterStrBuilder.append(", PID");

		if (isTraffic) {
			if (isKB)
				filterStrBuilder = filterStrBuilder.append(", Upload(kb), Download(kb)");
			else
				filterStrBuilder = filterStrBuilder.append(", Upload(byte), Download(byte)");
		}

		if (isGPS)
			filterStrBuilder = filterStrBuilder.append(", GPS");

//		if (isAudio)
//			filterStrBuilder = filterStrBuilder.append(", Audio");

//		if (isAudioDeviceActivity)
//			filterStrBuilder = filterStrBuilder.append(", AudioActive");

		filterStrBuilder = filterStrBuilder.append("\n");
		String filterStr = filterStrBuilder.toString();

		Log.w(TAG, filterStr);

		if (isFileWrite) {
//			try {
//				File filePath = new File(Environment.getExternalStorageDirectory() + "/Log/");
//				if(!filePath.exists())
//					filePath.mkdir();
//
//				fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory() + "/Log/",
////						"rMon" + time + ".txt"), true);
//						"eNDer" + time + ".csv"), true);
//				osw = new OutputStreamWriter(fos, "UTF-8");
//				bw = new BufferedWriter(osw);
//
//				bw.write(filterStr);
//				bw.flush();
//			} catch (FileNotFoundException e) {
//				e.printStackTrace();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
		}
		return binder;
	}



	private final ICounterService.Stub binder = new ICounterService.Stub() {
		public String[] getComponents() {
			return powerEstimator.getComponents();
		}

		public int[] getComponentsMaxPower() {
			return powerEstimator.getComponentsMaxPower();
		}

		public int getNoUidMask() {
			return powerEstimator.getNoUidMask();
		}

		public int[] getComponentHistory(int count, int componentId, int uid) {
			return powerEstimator.getComponentHistory(count, componentId, uid, -1);
		}

		public long[] getTotals(int uid, int windowType) {
			return powerEstimator.getTotals(uid, windowType);
		}

		public long getRuntime(int uid, int windowType) {
			return powerEstimator.getRuntime(uid, windowType);
		}

		public long[] getMeans(int uid, int windowType) {
			return powerEstimator.getMeans(uid, windowType);
		}

		public byte[] getUidInfo(int windowType, int ignoreMask) {
			UidInfo[] infos = powerEstimator.getUidInfo(windowType, ignoreMask);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			try {
				new ObjectOutputStream(output).writeObject(infos);
			} catch (IOException e) {
				return null;
			}
			for (UidInfo info : infos) {
				info.recycle();
			}
			return output.toByteArray();
		}

		public long getUidExtra(String name, int uid) {
			return powerEstimator.getUidExtra(name, uid);
		}
	};

	private int count = 0;
	@Override
	public void onSensorChanged(SensorEvent event) {

		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			long currentTime = System.currentTimeMillis();
			long gabOfTime = (currentTime - lastTime);
			if (gabOfTime > 100) {
				lastTime = currentTime;
				x = event.values[SensorManager.DATA_X];
				y = event.values[SensorManager.DATA_Y];
				z = event.values[SensorManager.DATA_Z];
				speed = Math.abs(x + y + z - lastX - lastY - lastZ) / gabOfTime
						* 10000;
				if (speed > SHAKE_THRESHOLD) {

					count++;
					if(count>1 && count<=2){
						count-=1;
					}if(count>2){
						count=1;
					}

					startHandler(count);

				}
				lastX = event.values[DATA_X];
				lastY = event.values[DATA_Y];
				lastZ = event.values[DATA_Z];
			}
		} else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
//			Log.i(TAG, "================ Orientation X: " + event.values[0]
//					+ ", Orientation Y: " + event.values[1]
//					+ ", Orientation Z: " + event.values[2]);

		} else if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE ) {
//			Log.i(TAG, "================ TYPE_AMBIENT_TEMPERATURE : " + event.values[0]);

		} else if (event.sensor.getType() == Sensor.TYPE_TEMPERATURE ) {
//			Log.i(TAG, "================ TYPE_TEMPERATURE : " + event.values[0]);

		}



	}

	private void startHandler(final int ct) {
		Handler startHandler = new Handler();

		startHandler.postDelayed(new Runnable() {
			public void run() {
				if(ct==count){
						Log.e(TAG, "흔들감지");

						count=0;

					if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP){
						Handler mHandler = new Handler(Looper.getMainLooper());
						mHandler.postDelayed(new Runnable() {
							@Override
							public void run() {Toast.makeText(JEBLogService.this, "This function is not supported by the device.", Toast.LENGTH_SHORT).show();}
						}, 0);
					}
				}
			}
		}, 500);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public class BackgroundThread extends Thread {

		boolean running = false;

		void setRunning(boolean b) {
			running = b;
		}

		@Override
		public void run() {
			while (running) {
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
//				mHandler.sendMessage(mHandler.obtainMessage());
			}
		}
	}

	static class UiHandler extends Handler {

		//
		private WeakReference<JEBLogService> umLoggerServiceWeakReference;
		public UiHandler(JEBLogService JEBLogService){
			umLoggerServiceWeakReference = new WeakReference<JEBLogService>(JEBLogService);
		}


		public void handleMessage(Message message) {
			super.handleMessage(message);

//			Log.d(TAG, "UiHandlerMessage : " + message.toString());
			JEBLogService JEBLogService = umLoggerServiceWeakReference.get();
			if (JEBLogService != null) JEBLogService.handleMessage(message);

			switch (message.what) {
			case 100 :		// No Data
				Log.d(TAG, "CPU message : " + "No Data");
				Log.d(TAG, "Memory message : " + "No Data");
				break;
			case 110 :		// CPU On. Memory Off
				Log.d(TAG, "CPU message : " + (CharSequence) message.obj);
				break;
			case 101 :		// CPU Off. Memory On
				Log.d(TAG, "Memory message : " + message.arg1 / 1024.0);
				break;
			case 111 :		// CPU On. Memory On
				Log.d(TAG, "Cpu message : " + (CharSequence) message.obj);
				Log.d(TAG, "Memory message : " + String.format("%.2f", message.arg1 / 1024.0) +" MB");
				break;
			default :
				Log.d(TAG, "CPU message : " + "No Data");
				Log.d(TAG, "Memory message : " + "No Data");
			}

			// when Pid has changed
			if ((JEBLogService.oldPid != message.arg2) && (JEBLogService.oldPid != -1)) {
//				UMLoggerService.oView.setBackgroundColor(0xAAff0000);
//				UMLoggerService.overlayPidChangeView.setText(
//							String.format("PID 변경 %d회", ++UMLoggerService.pidChangeCount));
				Log.d(TAG, "PID 변경 : " + " "+ (++JEBLogService.pidChangeCount));

				Log.d(TAG, "Tools.getDate() 변경 : " + " "+ (DateUtil.getSimpleDate()));
//				}
			} else {
//				UMLoggerService.oView.setBackgroundColor(0xAA555555);

			}
			JEBLogService.oldPid = message.arg2;
			Log.d(TAG, "oldPid message : " + JEBLogService.oldPid);
		}
	}


	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		Log.d(TAG, "statFlag : " + statFlag + ", flagService : " + flagService +"\n rootIntent : " + rootIntent);
	}


}