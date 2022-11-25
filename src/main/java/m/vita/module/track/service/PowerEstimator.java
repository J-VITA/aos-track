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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import m.vita.module.track.comp.OLED;
import m.vita.module.track.comp.PowerComponent;
import m.vita.module.track.phone.PhoneConstants;
import m.vita.module.track.phone.PhoneSelector;
import m.vita.module.track.phone.PowerFunction;
import m.vita.module.track.util.BatteryStats;
import m.vita.module.track.util.Counter;
import m.vita.module.track.util.DateUtil;
import m.vita.module.track.util.HistoryBuffer;
import m.vita.module.track.util.SystemInfo;


/**
 * This class is responsible for starting the individual power component loggers
 * (CPU, GPS, etc...) and collecting the information they generate. This
 * information is used both to write a log file that will be send back to
 * ₩ (or looked at by the user) and to implement the ICounterService
 * IPC interface.
 */
public class PowerEstimator implements Runnable {
	private static final String TAG = "PowerEstimator";

	public static final int ALL_COMPONENTS = -1;
	//	public static int ITERATION_INTERVAL = Global.resource_time; //
	public static final int ITERATION_INTERVAL = 1000; //
	public String resultStr;
	private JEBLogService context;
	private SharedPreferences mPreferences;

	private Vector<PowerComponent> powerComponents;
	private Vector<PowerFunction> powerFunctions;
	private Vector<HistoryBuffer> histories;
	private Map<Integer, String> uidAppIds;

	private HistoryBuffer oledScoreHistory;

	private Object fileWriteLock = new Object();

	private Object iterationLock = new Object();
	private long lastWrittenIteration;

	private SparseArray<Long> uidTxArray;
	private SparseArray<Long> uidRxArray;
	private SparseArray<List<Long>> idleListForPid;
	private long lastGPS = 0;
	private long lastAudio = 0;

	public String appName = "전체";
	private boolean isAllApp;
	public int logTerm = 1;
	private boolean isFilter;
//	private boolean isAudio;
//	private boolean isAudioDeviceActivity;
	private boolean isLogTotal;
	private boolean isPid;
	private boolean isMemory;
	private boolean isCapacity;
	private boolean isTraffic;
	private int noUidMask;
	private boolean isKB;
	private boolean isCount;
	private boolean isCurrentKey;
	private boolean isGPS;
	private boolean isCpuUsage;
	private boolean isRealTimeOverlay;
	private boolean isFileWrite;
	private int cputype = 1;
	private List<Integer> targetUids;
	private int count = 0;
//	private int uploadData = -1;

	private static final double HIDE_UID_THRESHOLD = 0.1;
	public static final int KEY_CURRENT_POWER = 0;
	public static final int KEY_AVERAGE_POWER = 1;
	public static final int KEY_TOTAL_ENERGY = 2;
	private static final int MAX_NUMBER_OF_CORES = 8;

	private PackageManager pm;
	private SystemInfo sysInfo;
	private AudioManager audioManager;

	private BufferedReader brTotal;
	private BufferedReader brProc;

	private SparseArray<Long> totalTimeBefore;
	private SparseArray<Long> processTimeBefore;

	Message message;

	int _rssi = 0;
	String batteryTemp = "";

	public PowerEstimator(JEBLogService context) {
		this.context = context;

		pm = context.getPackageManager();

		mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		appName = mPreferences.getString("appname", "-1/");
		isAllApp = appName.contains("-1") ? true : false;

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

		isFilter = mPreferences.getString("pref_filter",
				"측정결과 모두 보기").equals(
				"측정결과 모두 보기") ? false : true;
		logTerm = Integer.parseInt(mPreferences.getString("pref_term", "1"));
		cputype = Integer.parseInt(mPreferences.getString("pref_cputype", "1"));
		isKB = mPreferences.getBoolean("pref_traffic_kb", true);
		isRealTimeOverlay = mPreferences.getBoolean("pref_overlay", true)
				& mPreferences.getBoolean("one_app", false);
		isFileWrite = mPreferences.getBoolean("pref_file_write", true);

		uidTxArray = new SparseArray<Long>();
		uidRxArray = new SparseArray<Long>();

		powerComponents = new Vector<PowerComponent>();
		powerFunctions = new Vector<PowerFunction>();
		uidAppIds = new HashMap<Integer, String>();

		PhoneSelector.generateComponents(context, powerComponents, powerFunctions);

		histories = new Vector<HistoryBuffer>();
		for (int i = 0; i < powerComponents.size(); i++) {
			histories.add(new HistoryBuffer(300));
		}
		oledScoreHistory = new HistoryBuffer(0);

		audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

		targetUids = new ArrayList<Integer>();
		int pos = 0;
		int end;
		while ((end = appName.indexOf("/", pos)) >= 0) {
			targetUids.add(Integer.parseInt(appName.substring(pos, end)));
			pos = end + 1;
		}

		if (isCpuUsage) {
			totalTimeBefore = new SparseArray<Long>();
			processTimeBefore = new SparseArray<Long>();
			idleListForPid = new SparseArray<List<Long>>();
		}

	}

	/**
	 * This is the loop that keeps updating the power profile
	 */
	public void run() {
		sysInfo = SystemInfo.getInstance();
		PackageManager pm = context.getPackageManager();
		BatteryStats bst = BatteryStats.getInstance();

		int components = powerComponents.size();
		long beginTime = SystemClock.elapsedRealtime();
		for (int i = 0; i < components; i++) {
			powerComponents.get(i).init(beginTime, ITERATION_INTERVAL);
			powerComponents.get(i).setDaemon(true);        //add setDaemon
			powerComponents.get(i).start();
		}
		IterationData[] dataTemp = new IterationData[components];

		int oledId = -1;
		for (int i = 0; i < components; i++) {
			// For Note 2
//			Log.e(TAG, powerComponents.get(i).getComponentName());
			if ("OLED".equals(powerComponents.get(i).getComponentName())) {
				oledId = i;
				break;
			}
		}

		double lastCurrent = -1;

		/* Indefinitely collect data on each of the power components. */
		for (long iter = -1; !Thread.interrupted(); ) {
			long curTime = SystemClock.elapsedRealtime();
			/*
			 * Compute the next iteration that we can make the ending of. We
			 * wait for the end of the iteration so that the components had a
			 * chance to collect data already.
			 */
			iter = (long) Math.max(iter + 1, (curTime - beginTime) / ITERATION_INTERVAL);
			/* Sleep until the next iteration completes. */
			try {
				Thread.currentThread();
				//Log.i(TAG,"=============================================== sleep Start : " + curTime );
				Thread.sleep(beginTime + (iter + 1) * ITERATION_INTERVAL - curTime);
			} catch (InterruptedException e) {
				break;
			}

			for (int i = 0; i < components; i++) {
				PowerComponent comp = powerComponents.get(i);
				IterationData data = comp.getData(iter);
				dataTemp[i] = data;
				if (data == null) {
					/* No data present for this timestamp. No power charged. */
					continue;
				}

				SparseArray<PowerData> uidPower = data.getUidPowerData();
				for (int j = 0; j < uidPower.size(); j++) {
					int uid = uidPower.keyAt(j);
					PowerData powerData = uidPower.valueAt(j);
					int power = (int) powerFunctions.get(i).calculate(powerData);
					powerData.setCachedPower(power);
					histories.get(i).add(uid, iter, power);

					if (i == oledId) {
						OLED.OledData oledData = (OLED.OledData) powerData;
						if (oledData.pixPower >= 0) {
							oledScoreHistory.add(uid, iter, (int) (1000 * oledData.pixPower));
						}
					}
				}
			}

			/* Update the uid set. */
			synchronized (fileWriteLock) {
				synchronized (uidAppIds) {
					for (int i = 0; i < components; i++) {
						IterationData data = dataTemp[i];
						if (data == null) {
							continue;
						}
						SparseArray<PowerData> uidPower = data.getUidPowerData();
						for (int j = 0; j < uidPower.size(); j++) {
							int uid = uidPower.keyAt(j);
							if (uid < SystemInfo.AID_APP) {
								uidAppIds.put(uid, null);
							} else {
								/*
								 * We only want to update app names when logging
								 * so the associcate message gets written.
								 */
								String newAppId = sysInfo.getAppId(uid, pm);
								uidAppIds.put(uid, newAppId);
							}
						}

						// Add targetApp
						if (!isAllApp) {
							for (Integer uid : targetUids) {
								if (!uidAppIds.containsKey(uid)) {
									if (uid < SystemInfo.AID_APP) {
										uidAppIds.put(uid, null);
									} else {
										String newAppId = sysInfo.getAppId(uid, pm);
										uidAppIds.put(uid, newAppId);
									}
								}
							}
						}
					}
				}
			}

			synchronized (iterationLock) {
				lastWrittenIteration = iter;
			}

			if (bst.hasCurrent()) {
				double current = bst.getCurrent();
				if (current != lastCurrent) {
					lastCurrent = current;
				}
			}
			if (iter % 1 == 0) {
				refresh();
			}
		}

		for (int i = 0; i < components; i++) {
			powerComponents.get(i).interrupt();
		}

		for (int i = 0; i < components; i++) {
			try {
				powerComponents.get(i).join();
			} catch (InterruptedException e) {
			}
		}
	}

	public String[] getComponents() {
		int components = powerComponents.size();
		String[] ret = new String[components];
		for (int i = 0; i < components; i++) {
			ret[i] = powerComponents.get(i).getComponentName();
		}
		return ret;
	}

	public int[] getComponentsMaxPower() {
		PhoneConstants constants = PhoneSelector.getConstants(context);
		int components = powerComponents.size();
		int[] ret = new int[components];
		for (int i = 0; i < components; i++) {
			ret[i] = (int) constants.getMaxPower(powerComponents.get(i).getComponentName());
		}
		return ret;
	}

	public int getNoUidMask() {
		int components = powerComponents.size();
		int ret = 0;
		for (int i = 0; i < components; i++) {
			if (!powerComponents.get(i).hasUidInformation()) {
				ret |= 1 << i;
			}
		}
		return ret;
	}

	public int[] getComponentHistory(int count, int componentId, int uid, long iteration) {
		if (iteration == -1)
			synchronized (iterationLock) {
				iteration = lastWrittenIteration;
			}
		int components = powerComponents.size();
		if (componentId == ALL_COMPONENTS) {
			int[] result = new int[count];
			for (int i = 0; i < components; i++) {
				int[] comp = histories.get(i).get(uid, iteration, count);
				for (int j = 0; j < count; j++) {
					result[j] += comp[j];
				}
			}
			return result;
		}
		if (componentId < 0 || components <= componentId)
			return null;
		return histories.get(componentId).get(uid, iteration, count);
	}

	public long[] getTotals(int uid, int windowType) {
		int components = powerComponents.size();
		long[] ret = new long[components];
		for (int i = 0; i < components; i++) {
			ret[i] = histories.get(i).getTotal(uid, windowType) * ITERATION_INTERVAL / 1000;
		}
		return ret;
	}

	public long getRuntime(int uid, int windowType) {
		long runningTime = 0;
		int components = powerComponents.size();
		for (int i = 0; i < components; i++) {
			long entries = histories.get(i).getCount(uid, windowType);
			runningTime = entries > runningTime ? entries : runningTime;
		}
		return runningTime * ITERATION_INTERVAL / 1000;
	}

	public long[] getMeans(int uid, int windowType) {
		long[] ret = getTotals(uid, windowType);
		long runningTime = getRuntime(uid, windowType);
		runningTime = runningTime == 0 ? 1 : runningTime;
		for (int i = 0; i < ret.length; i++) {
			ret[i] /= runningTime;
		}
		return ret;
	}

	public UidInfo[] getUidInfo(int windowType, int ignoreMask) {
		long iteration;

		synchronized (iterationLock) {
			iteration = lastWrittenIteration;
		}
		int components = powerComponents.size();

		synchronized (uidAppIds) {
			int pos = 0;

			UidInfo[] result = new UidInfo[uidAppIds.size()];
			for (Integer uid : uidAppIds.keySet()) {
				UidInfo info = UidInfo.obtain();
				int currentPower = 0;
				int[] comPower = new int[components];

				for (int i = 0; i < components; i++) {
					if ((ignoreMask & 1 << i) == 0) {
						currentPower += histories.get(i).get(uid, iteration, 1)[0];
						comPower[i] = histories.get(i).get(uid, iteration, 1)[0];
					}
				}
				info.init(uid, currentPower,
						sumArray(getTotals(uid, windowType), ignoreMask) * ITERATION_INTERVAL / 1000,
						getRuntime(uid, windowType) * ITERATION_INTERVAL / 1000, comPower);
				result[pos++] = info;
			}
			return result;
		}
	}

	private long sumArray(long[] A, int ignoreMask) {
		long ret = 0;
		for (int i = 0; i < A.length; i++) {
			if ((ignoreMask & 1 << i) == 0) {
				ret += A[i];
			}
		}
		return ret;
	}

	public long getUidExtra(String name, int uid) {
		if ("OLEDSCORE".equals(name)) {
			long entries = oledScoreHistory.getCount(uid, Counter.WINDOW_TOTAL);
			if (entries <= 0)
				return -2;
			double result = oledScoreHistory.getTotal(uid, Counter.WINDOW_TOTAL) / 1000.0;
			result /= entries;

			return (long) Math.round(result * 100);
		}
		return -1;
	}

	// Set term of writing interval
	long iter = -1;
	long beginTime = SystemClock.elapsedRealtime();

	@SuppressLint("MissingPermission")
	private void refresh() {
		UidInfo[] uidInfos = getUidInfo(mPreferences.getInt("topWindowType", Counter.WINDOW_TOTAL),
				noUidMask | mPreferences.getInt("topIgnoreMask", 0));
		double currentTotalEnergy = 0;
		for (UidInfo uidInfo : uidInfos) {
			if (uidInfo.uid == SystemInfo.AID_ALL)
				continue;

			uidInfo.currentKey = uidInfo.currentPower;
			uidInfo.totalKey = uidInfo.totalEnergy;

			currentTotalEnergy += uidInfo.currentKey;
		}

		if (currentTotalEnergy == 0)
			currentTotalEnergy = 1;

		for (UidInfo uidInfo : uidInfos) {
			uidInfo.currentPercentage = 100.0 * uidInfo.currentKey / currentTotalEnergy;
		}

		if (isAllApp && isFilter)
			Arrays.sort(uidInfos, new NewComparator());


		// Set term of writing interval
		long curTime = SystemClock.elapsedRealtime();
		iter = (long) Math.max(iter + 1, (curTime - beginTime) / ITERATION_INTERVAL);

		count++;

		// Set term of writing interval
		if (iter % logTerm == (logTerm == 1 ? 0 : 3)) {
			for (int i = 0; i < uidInfos.length; i++) {
//				if (uidInfos[i].uid == SystemInfo.AID_ALL || uidInfos[i].uid == SystemInfo.AID_SYSTEM) {
				if (uidInfos[i].uid == SystemInfo.AID_ALL) {
					continue;
				}

				// TODO : only uid 1000
				if (uidInfos[i].uid != SystemInfo.AID_SYSTEM) {
					continue;
				}


				if (isFilter && uidInfos[i].currentPercentage < HIDE_UID_THRESHOLD) {
					continue;
				}

				if (!isAllApp && !targetUids.contains(uidInfos[i].uid)) {
					continue;
				}

				if (isRealTimeOverlay) {
//					message = context.mHandler.obtainMessage();
//					message.what = 100;
				}

				StringBuilder tempStr = new StringBuilder(this.init(uidInfos[i]));
//				Log.d(TAG, "tempStr : " + tempStr + ", uidInfos[i] : " + uidInfos[i]);
				if (isRealTimeOverlay) {
//					context.mHandler.sendMessage(message);
				}

				// For GPS
				if (isGPS) {
					long totalGPS = getTotals(SystemInfo.AID_ALL, 0)[4];
					long curGPS = totalGPS >= lastGPS ? (totalGPS - lastGPS) : 0;
					lastGPS = totalGPS;

					tempStr = tempStr.append(String.format(", %d", curGPS));
				}

				// For Audio
//				if (isAudio) {
//					long totalAudio = getTotals(SystemInfo.AID_ALL, 0)[5];
//					long curAudio = totalAudio >= lastAudio ? (totalAudio - lastAudio) : 0;
//					lastAudio = totalAudio;
//
//					tempStr = tempStr.append(String.format(", %d", curAudio));
//				}

				/* For Audio */
//				if (isAudioDeviceActivity) {
//					if (audioManager.isMusicActive()) {
//						tempStr = tempStr.append(", 384");
//					} else {
//						tempStr = tempStr.append(", 0");
//					}
//				}

				tempStr = tempStr.append("\n");
				resultStr = tempStr.toString();

				List<String> temp_list = new ArrayList<>();
				String ktypeWhere = "";             //ktypeWhere는 공백상태

				String[] array = resultStr.split(",");     //콤마 구분자로 배열에 ktype저장

				for (String cha : array) {      //배열 갯수만큼 포문이 돌아간다.

					temp_list.add(cha);

				}

//				HashMap<String, String> hMap = new HashMap<>();
//
//				hMap.put("start_date", temp_list.get(0));
//				hMap.put("cnt", temp_list.get(1));
//				hMap.put("brand_nm", temp_list.get(2));
//				hMap.put("pkg_nm", temp_list.get(3));
//				hMap.put("battery_use", temp_list.get(4));
//				hMap.put("total_battery_use", temp_list.get(5));
//				hMap.put("LCD", temp_list.get(6));
//				hMap.put("CPU", temp_list.get(7));
//				hMap.put("wifi", temp_list.get(8));
//				hMap.put("threeG", temp_list.get(9));
//				hMap.put("cpu_usage", temp_list.get(10));
//				hMap.put("memory_h", temp_list.get(11));
//				hMap.put("", temp_list.get(12));
//				hMap.put("", temp_list.get(13));
//				hMap.put("memory_p", temp_list.get(14));
//				hMap.put("memory_v", temp_list.get(15));
//				hMap.put("memory_leak", temp_list.get(16));
//				hMap.put("capacity", temp_list.get(16));
//				hMap.put("PID", temp_list.get(17));
//				hMap.put("upload_kb", temp_list.get(18));
//				hMap.put("download_kb", temp_list.get(19));
//				hMap.put("gps_use", temp_list.get(20));
//				hMap.put("audio", temp_list.get(21));
//				hMap.put("audio_act", temp_list.get(22));

//				int uploadStr = 0;
//				int uploadInt = Integer.valueOf(temp_list.get(18));
//				if (uploadData == -1) {
//					uploadData = uploadInt;
//					uploadStr = uploadData;
//				} else {
//					uploadStr = uploadInt - uploadData;
//					if (uploadStr < 0) {
//						uploadStr = 0;
//					}
//					uploadData = uploadInt;
//				}
				Log.w(TAG,"############################################");
				Log.d(TAG, "#### start_date : " + temp_list.get(0) +" ####");
				Log.d(TAG, "#### cnt : " + temp_list.get(1) +" ####");
				Log.d(TAG, "#### CPU : " + temp_list.get(7) +" ####");
				Log.d(TAG, "#### wifi : " + temp_list.get(8) +" ####");
				Log.d(TAG, "#### threeG : " + temp_list.get(9) +" ####");
				Log.d(TAG, "#### memory_h : " + temp_list.get(11) +" ####");
				Log.d(TAG, "#### capacity : " + temp_list.get(16) +" ####");
				Log.d(TAG, "#### upload_kb : " + temp_list.get(18) +" ####");
//				Log.d(TAG, "#### upload_kb per Second : " + Integer.toString(uploadStr) +" ####");
				Log.d(TAG, "#### download_kb : " + temp_list.get(19) +" ####");
				Log.w(TAG,"############################################");
				/*
				ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				boolean isMobile = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnectedOrConnecting();
				boolean isWifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
				String com_type = "unknown";
				if (isMobile) com_type = "MOBILE";
				else if (isWifi) com_type = "WIFI";
				Global.logOut(TAG, "############# isMobile ###############" + isMobile);
				Global.logOut(TAG, "############# isWifi ###############" + isWifi);
				Global.logOut(TAG, "############# com_type ###############" + com_type);
				context.registerReceiver(rssiReceiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
				Global.logOut(TAG, "############# _rssi ###############" + _rssi);
				int rssiStr = (isWifi == true) ? _rssi : 0;
				Global.logOut(TAG, "############# 수정된 _rssi  ###############" + rssiStr);

				context.registerReceiver(BatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
				Global.logOut(TAG, "############# batteryTemp  ############### " + batteryTemp);
*/

//				// Acquire a reference to the system Location Manager
				try {

					final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

					// GPS 프로바이더 사용가능여부
					Boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
					// 네트워크 프로바이더 사용가능여부
					Boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//
					Log.d(TAG, "isGPSEnabled="+ isGPSEnabled);
					Log.d(TAG, "isNetworkEnabled="+ isNetworkEnabled);
//
					if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
						// TODO: Consider calling
						//    ActivityCompat#requestPermissions
						// here to request the missing permissions, and then overriding
						//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
						//                                          int[] grantResults)
						// to handle the case where the user grants the permission. See the documentation
						// for ActivityCompat#requestPermissions for more details.
						return;
					}



					// Register the listener with the Location Manager to receive location updates
					locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
					locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

//					Handler mHandler = new Handler(Looper.getMainLooper());
//					mHandler.postDelayed(new Runnable() {
//						@SuppressLint("MissingPermission")
//						@Override
//						public void run() {
//
//						}
//					}, 0);


				}catch (Exception e) {
					Log.d("location Exception", e.getMessage());
				}




				if (isFileWrite) {
//					try {
//						context.bw.write(resultStr);
//						context.bw.flush();
//					} catch (IOException e) {
//						e.printStackTrace();
//					} catch (NullPointerException e) {
//					}
				}
			}
		}
	}
	final LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			double lat = location.getLatitude();
			double lng = location.getLongitude();

//							Logging.d("latitude: "+ lat +", longitude: "+ lng);
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			Log.d(TAG,"onStatusChanged");
		}

		public void onProviderEnabled(String provider) {
			Log.d(TAG,"onProviderEnabled");
		}

		public void onProviderDisabled(String provider) {
			Log.d(TAG,"onProviderDisabled");
		}
	};

	// wifi RSSI Receiver
	private BroadcastReceiver rssiReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

//			Log.e(TAG, "Time rssiReceiver");

			WifiManager wman = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			WifiInfo info = wman.getConnectionInfo();

			_rssi = info.getRssi();
//			Log.e(TAG, "_rssi ==> " + _rssi);
		}
	};

	private BroadcastReceiver BatteryInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();

			if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
				batteryTemp = tenthsToFixedString(intent.getIntExtra("temperature", 0));
				//Global.logOut(TAG,"############ battery temperature = " + batteryTemp );
			}
		}
	};

	private final String tenthsToFixedString(int x) {
		int tens = x / 10;
		return Integer.toString(tens) + "." + (x - 10 * tens);
	}

	public String init(UidInfo uidInfo) {
		Log.d(TAG, "uid : " + uidInfo.uid + ", context : " + context + ", pm : " + pm);
		String name = sysInfo.getUidName(uidInfo.uid, pm);
		OtherData miscData = new OtherData(uidInfo.uid, context, pm);
		int pid = miscData.getPidList().get(0);

		String currentDate = String.format("%s", DateUtil.getSimpleDate());
		StringBuilder result = new StringBuilder(String.format("%s", currentDate));

		if (isCount)
			result = result.append(String.format(", %d",  count));

		// TODO : All PID
		result = result.append(String.format(", %s, %s", name, miscData.getPackageNameList().get(0)));
		// All PID END

		if (isCurrentKey)
			result = result.append(String.format(", %.1f", uidInfo.currentKey));	//현재 밧데리 사용량

		if (isLogTotal)
			result = result.append(String.format(", %.1f", uidInfo.totalKey));

		for (int i=0; i<uidInfo.comPower.length - 2; i++) {
			int tempInt = mPreferences.getInt("topIgnoreMask", 0);
			if((tempInt & 1 << i) != 0)
				continue;

			result = result.append(", ");
			result = result.append(uidInfo.comPower[i]);
		}

		if (isCpuUsage) {
			StringBuilder rtCpu = new StringBuilder(String.format("%d", (int)getCpuUsage(pid)));
			result = result.append(", ").append(rtCpu);

			if (isRealTimeOverlay) {
//				message.what += 10;
//				message.obj = rtCpu;
			}
		}

		if (isMemory) {
			miscData.getMemory();
			int rtTotalPss = miscData.getTotalPss().get(0);
			// TODO : All PID
			result = result.append(String.format(", %d, %d, %d, %d, %d, %d", rtTotalPss,
					miscData.getPrivateDirty().get(0), miscData.getSharedDirty().get(0),
					miscData.getDalvikPss().get(0), miscData.getOtherPss().get(0), miscData.getNativePss().get(0)));

//			Global.logOut(TAG, "rtTotalPss : " + rtTotalPss + ", getPrivateDirty : " + miscData.getPrivateDirty().get(0)
//			+", getSharedDirty : " + miscData.getSharedDirty().get(0) +", getDalvikPss : " + miscData.getDalvikPss().get(0) + ", getOtherPss : " + miscData.getOtherPss().get(0));
			// All PID END

			if (isRealTimeOverlay) {
//				message.what += 1;
//				message.arg1 = rtTotalPss;
			}
		}

		if (isCapacity) {
			result = result.append(String.format(", %s", miscData.getCapacity()));
		}

		if (isPid) {
//			int pid = miscData.getPid();
			result = result.append(String.format(", %d", pid));
		}
		if (isRealTimeOverlay) {
//			message.arg2 = pid;
		}

		if (isTraffic) {
			long txBytes = TrafficStats.getUidTxBytes(uidInfo.uid);
			long rxBytes = TrafficStats.getUidRxBytes(uidInfo.uid);
			if (txBytes == TrafficStats.UNSUPPORTED || rxBytes == TrafficStats.UNSUPPORTED) {
				txBytes = getUidTxBytes(uidInfo.uid);
				rxBytes = getUidRxBytes(uidInfo.uid);
			}

			if (uidRxArray.get(uidInfo.uid) != null){
				uidInfo.lastTxBytes = uidTxArray.get(uidInfo.uid);
				uidInfo.lastRxBytes = uidRxArray.get(uidInfo.uid);
			} else {
				uidInfo.lastTxBytes = txBytes;
				uidInfo.lastRxBytes = rxBytes;
			}

			uidInfo.txBytes = txBytes - uidInfo.lastTxBytes;
			uidInfo.rxBytes = rxBytes - uidInfo.lastRxBytes;

			uidTxArray.put(uidInfo.uid, txBytes);
			uidRxArray.put(uidInfo.uid, rxBytes);

			if (isKB) {
				result = result.append(String.format(", %.1f, %.1f", uidInfo.txBytes/1024.0, uidInfo.rxBytes/1024.0));
			} else {
				result = result.append(String.format(", %d, %d", uidInfo.txBytes, uidInfo.rxBytes));
			}
		}

		// TODO : All PID
//		Log.e(TAG, "Count : " + miscData.getProcessCount());
//		if (miscData.getProcessCount() > 1) {
//			for (int i = 1; i < miscData.getProcessCount(); i++) {
//				result.append(String.format("\n%s", currentDate));
//
//				if (isCount)
//					result = result.append(String.format(", %d",  count));
//				Log.e(TAG, "Count : " + miscData.getProcessCount());
//				result = result.append(String.format(", %s, %s, , , , , %.1f%%,%d, %d, %d, %d, %d, %d",
//						name,
//						miscData.getPackageNameList().get(i),
//						getCpuUsage(miscData.getPidList().get(i)),
//						miscData.getTotalPss().get(i),
//						miscData.getPrivateDirty().get(i),
//						miscData.getSharedDirty().get(i),
//						miscData.getDalvikPss().get(i),
//						miscData.getOtherPss().get(i),
//						miscData.getPidList().get(i)));
//			}
//		}
		// All PID END

		String resultStr = result.toString();

		return resultStr;
	}

	public double getCpuUsage(int pid) {
		// TODO :
//		Log.e(TAG, "PID : " + pid);
		double result = 0.0;
		try {
			this.brTotal = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")));
			this.brProc = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/" + pid + "/stat")));

			String loadTotal = this.brTotal.readLine();
			String loadProc = this.brProc.readLine();
			String[] arrayOfString1 = loadTotal.split(" ");
			String[] arrayOfString2 = loadProc.split(" ");

			Long totalTimeAfter = 0L;
			for (int index = 2; index < arrayOfString1.length - 2; index++) {	// except guest and guest_nice
				totalTimeAfter += Long.parseLong(arrayOfString1[index]);			// 'cause included on user and nice already
			}

			// For idle decreasing bug
			List<Long> cpuXIdle = new ArrayList<Long>();
			List<Long> beforeCpuXIdle = idleListForPid.get(pid, new ArrayList<Long>(MAX_NUMBER_OF_CORES) {{
				for (int i = 0; i < MAX_NUMBER_OF_CORES; i++) {
					add(0L);
				}
			}});
			Long idleTotal = 0L;
			Long idleTotalModified = 0L;
			int loc = 0;

			while ((loadTotal = brTotal.readLine()).startsWith("cpu")) {
				String[] arrayOfTotal = loadTotal.split(" ");
				Long idleJiffies = Long.parseLong(arrayOfTotal[4]);
				cpuXIdle.add(idleJiffies);
				idleTotal += idleJiffies;
				try {
					idleTotalModified += Math.max(idleJiffies, beforeCpuXIdle.get(loc++));
				} catch (Exception e) {
				}
			}
			idleListForPid.put(pid, cpuXIdle);
			//

			Long processTimeAfter = 0L;
			for (int index = 13; index < 17; index++) {
				processTimeAfter += Long.parseLong(arrayOfString2[index]);
			}

			Long processTimeDiff = 0L;
			Long totalTimeDiff = 0L;

			processTimeDiff = processTimeAfter - processTimeBefore.get(pid, processTimeAfter);
			totalTimeDiff = totalTimeAfter - totalTimeBefore.get(pid, totalTimeAfter);

			if (totalTimeDiff == 0L) {
				totalTimeDiff = 1L;
			}

			processTimeBefore.put(pid, processTimeAfter);
			totalTimeBefore.put(pid, totalTimeAfter);

			if (totalTimeDiff < 0) {			// for idle jiffies go down bug include some manual modifying
				totalTimeDiff = 4 * (totalTimeDiff - idleTotal + idleTotalModified);
				totalTimeDiff = Math.abs(totalTimeDiff);
			}

			result =  100 * processTimeDiff / (double) totalTimeDiff;

		} catch (FileNotFoundException e1) {
//			e1.printStackTrace();
			Log.e(TAG, "Fail to Find a File \"stat\" from File System.");
		} catch (IOException e2) {
//			e2.printStackTrace();
			Log.e(TAG, "Fail to Read Data From \"stat\" File.");
		} catch (NullPointerException e3) {
		} finally {
			try {
				brTotal.close();
				brProc.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
			}
		}

		return result;
	}

	private long getUidTxBytes(int uid) {
		long result = 0L;

		StringBuilder filePath = new StringBuilder("/proc/uid_stat/");
		String uidString = String.valueOf(uid);
		filePath.append(uidString).append("/tcp_snd");

		try {
			File uidStatFile = new File(filePath.toString());
			BufferedReader br = new BufferedReader(new FileReader(uidStatFile));

			String line = br.readLine();

			result = Long.parseLong(line);

			br.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "tcp_snd File Not Found\n");
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	private long getUidRxBytes(int uid) {
		long result = 0L;

		StringBuilder filePath = new StringBuilder("/proc/uid_stat/");
		String uidString = String.valueOf(uid);
		filePath.append(uidString).append("/tcp_rcv");

		try {
			File uidStatFile = new File(filePath.toString());
			BufferedReader br = new BufferedReader(new FileReader(uidStatFile));

			String line = br.readLine();

			result = Long.parseLong(line);

			br.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "tcp_rcv File Not Found\n");
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	class NewComparator implements Comparator<UidInfo> {
		public int compare(UidInfo uidInfo1, UidInfo uidInfo2) {
			if (uidInfo1.currentKey < uidInfo2.currentKey) {
				return 1;
			} else if (uidInfo1.currentKey == uidInfo2.currentKey) {
				return 0;
			} else {
				return -1;
			}
		}
	}
}
