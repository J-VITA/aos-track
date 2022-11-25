package m.vita.module.track.service;


import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Debug;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import m.vita.module.track.processes.AndroidProcesses;
import m.vita.module.track.util.SystemInfo;


public class OtherData {
	private static final String TAG = "OtherData";
	
	private ActivityManager activityManager = null;

//	private int processId = -1;
	private int[] processIds = null;
	// TODO : All PID
//	private String packageName;
//	private int totalPss = 0;
//	private int totalPrivateDirty = 0;
//	private int totalSharedDirty = 0;
//	private int dalvikPss = 0;
//	private int otherPss = 0;
	private ArrayList<Integer> processIdList;
	private ArrayList<Integer> totalPssList;
	private ArrayList<Integer> totalPrivateDirtyList;
	private ArrayList<Integer> totalSharedDirtyList;
	private ArrayList<Integer> dalvikPssList;
	private ArrayList<Integer> otherPssList;
	private ArrayList<Integer> NativePssList;
	private ArrayList<String> packageNameList;
	// All PID END

	private Intent batteryIntent = null;
	private String batteryLevel = null;
	private JEBLogService context;

	SystemInfo sysInfo;
	List<RunningAppProcessInfo> localList;
	List<RunningAppProcessInfo> osUplocalList;

	public OtherData(int uid, JEBLogService context, PackageManager pm) {
		this.context = context;
		this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		this.sysInfo = SystemInfo.getInstance();
		localList = this.activityManager.getRunningAppProcesses();
		osUplocalList =  AndroidProcesses.getRunningAppProcessInfo(context);

		// TODO : All PID
		processIdList = new ArrayList<Integer>();
		totalPssList = new ArrayList<Integer>();
		totalPrivateDirtyList = new ArrayList<Integer>();
		totalSharedDirtyList = new ArrayList<Integer>();
		dalvikPssList = new ArrayList<Integer>();
		otherPssList = new ArrayList<Integer>();
		NativePssList = new ArrayList<Integer>();
		packageNameList = new ArrayList<String>();
		// All PID END
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.RELEASE.substring(0, 3).toString().equals("5.0")){
			getPackageNameUpdate(uid, pm);
		}else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Build.VERSION.RELEASE.substring(0, 3).toString().equals("5.0")){
			getPackageNameLollipop(uid, pm);
		}else{
			getPackageName(uid, pm);
		}
//		else if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build. == "5.0"){
//			getPackageNameUpdate(uid, pm);
//		}

	}

	public void getMemory() {
		try {
			if (processIds.length == 0) {
				// TODO : All PID
				this.totalPssList.add(-1);
				this.totalPrivateDirtyList.add(-1);
				this.totalSharedDirtyList.add(-1);
				this.dalvikPssList.add(-1);
				this.otherPssList.add(-1);
				this.NativePssList.add(-1);
//			} else if (processIds[0] == -1) {
//				this.totalPss = -1;
//				this.totalPrivateDirty = -1;
//				this.totalSharedDirty = -1;
				// All PID END
			} else {
				Debug.MemoryInfo[] arrayOfMemoryInfo = activityManager.getProcessMemoryInfo(processIds);
				// TODO : All PID
				for(int i = 0; i < processIds.length; i++) {
					this.totalPssList.add(arrayOfMemoryInfo[i].getTotalPss());		//Pss Total
//					Log.d(TAG, "arrayOfMemoryInfo[i].getTotalPss() : " + arrayOfMemoryInfo[i].getTotalPss());
					this.totalPrivateDirtyList.add(arrayOfMemoryInfo[i].getTotalPrivateDirty());	//Private Dirty
					this.totalSharedDirtyList.add(arrayOfMemoryInfo[i].getTotalSharedDirty());		//Shared Dirty
					this.dalvikPssList.add(arrayOfMemoryInfo[i].dalvikPss);
//					Log.d(TAG, "arrayOfMemoryInfo[i].dalvikPss : " + arrayOfMemoryInfo[i].dalvikPss);
					this.otherPssList.add(arrayOfMemoryInfo[i].otherPss);
//					Log.d(TAG, "arrayOfMemoryInfo[i].otherPss : " + arrayOfMemoryInfo[i].otherPss);
					this.NativePssList.add(arrayOfMemoryInfo[i].nativePss);
//					Log.d(TAG, "arrayOfMemoryInfo[i].nativePss : " + arrayOfMemoryInfo[i].nativePss);
				}
//				this.totalPss = arrayOfMemoryInfo[0].getTotalPss();
//				this.totalPrivateDirty = arrayOfMemoryInfo[0].getTotalPrivateDirty();
//				this.totalSharedDirty = arrayOfMemoryInfo[0].getTotalSharedDirty();
//				this.dalvikPss = arrayOfMemoryInfo[0].dalvikPss;
//				this.otherPss = arrayOfMemoryInfo[0].otherPss;
				// All PID END
			}
		} catch (NullPointerException e) {
		}
	}

	// TODO : All PID
//	public int getPid() {
//		return this.processId;
//	}

	public ArrayList<Integer> getPidList() {
		return this.processIdList;
	}

	public ArrayList<Integer> getTotalPss () {
		return this.totalPssList;
	}

	public ArrayList<Integer> getPrivateDirty() {
		return this.totalPrivateDirtyList;
	}

	public ArrayList<Integer> getSharedDirty() {
		return this.totalSharedDirtyList;
	}

	public ArrayList<Integer> getDalvikPss() {
		return this.dalvikPssList;
	}

	public ArrayList<Integer> getOtherPss() {
		return this.otherPssList;
	}

	public ArrayList<Integer> getNativePss() {
		return this.NativePssList;
	}

	public ArrayList<String> getPackageNameList() {
		return this.packageNameList;
	}

	public int getProcessCount() {
		return this.processIds.length;
	}
//	public int getTotalPss () {
//		return this.totalPss;
//	}
//
//	public int getPrivateDirty() {
//		return this.totalPrivateDirty;
//	}
//
//	public int getSharedDirty() {
//		return this.totalSharedDirty;
//	}
//
//	public int getDalvikPss() {
//		return this.dalvikPss;
//	}
//
//	public int getOtherPss() {
//		return this.otherPss;
//	}
	// All PID END

	public String getCapacity() {
		getBatteryUsage();
		return this.batteryLevel;
	}

	private void getPackageNameUpdate (int uid, PackageManager pm) {
		//////////////////////////////////////////////////////////////////////////////
		Log.d(TAG,"getPackageNameUpdate");
		String packageName = null;
		int processId = 0;

		// Get a list of all installed apps on the device.
		if (ProcessManager.getRunningProcesses() != null){
			//


			List<ProcessManager.Process> apps = ProcessManager.getRunningProcesses();

			for (ProcessManager.Process app : apps) {
				String appName = app.getPackageName();
				int uid2 = app.uid;
//				Log.d(TAG, ">>>uid : " + uid +", >>>uid2 : " + app.uid
//						+ ", \n pm : " + pm.getNameForUid(uid)
//						+ ", \n pm2 : " + pm.getNameForUid(app.uid));
//				Log.d(TAG, "app.uid : " + app.uid + ", uid : " + uid);
				long ulBytes = TrafficStats.getUidTxBytes(uid2);
				long dlBytes = TrafficStats.getUidRxBytes(uid2);
    		/* do your stuff */


				if (uid == uid2) {

					processId = app.pid;

//					processId = uid;
					Log.e(TAG, "uid : " + uid + "/ pid : " + processId);
					packageName = app.getPackageName();
//					packageName = pm.getNameForUid(uid);

					if (packageName == null) {
						// TODO : NEW
						Log.e(TAG, "PackageName Null");
						continue;
					}

					processIdList.add(processId);
					this.packageNameList.add(packageName);
					// All PID END

				}


			}
		}
//		Log.d(TAG, "processId : " + processId );

		// TODO : All PID
		if (this.packageNameList.size() == 0) {
			packageNameList.add("");
			processIdList.add(0);
		}
		int[] arrayOfInt = new int[processIdList.size()];
		for (int i = 0; i < processIdList.size(); i++) {
			arrayOfInt[i] = processIdList.get(i);
		}
		this.processIds = arrayOfInt;

	}

	private void getPackageName (int uid, PackageManager pm) {
		Log.d(TAG,"getPackageName");
		String packageName = null;
		int processId = 0;

//		ArrayList<Integer> arrayOfPid = new ArrayList<Integer>();
		for (int i = 0; i<localList.size() ; i++) {
			RunningAppProcessInfo localRunningAppProcessInfo =
									(RunningAppProcessInfo) localList.get(i);
//			packageName = localRunningAppProcessInfo.processName;

//			if (appName.equals(sysInfo.getUidName(localRunningAppProcessInfo.uid, pm))) {
			// TODO : only one app which has uid 1000

//			Log.d(TAG, "uid : " + uid +", localRunningAppProcessInfo.uid : " + localRunningAppProcessInfo.uid
//					+", localList.get(i): " +localList
//					+ ", \n pm : " + pm.getNameForUid(localRunningAppProcessInfo.uid)
//					+ ", \n pm2 : " + pm.getNameForUid(uid));
//


			/*SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
			String json = prefs.getString("pkg_nm", null);
			List<String> list2 = new ArrayList<>();
			if (json != null)
			{
				try
				{
					JSONArray array = new JSONArray(json);
					list2.clear();
					for (int k = 0; k < array.length(); k++)
					{
						String url = array.optString(k);
						list2.add(url);
					}
				}
				catch (JSONException e)
				{
					e.printStackTrace();
				}
			}


			List<String> appInfoList = new ArrayList<>();
			appInfoList.add(localRunningAppProcessInfo.processName);

			List<String> test = new ArrayList<>();
			test.add(appInfoList.get(i));

			List<String> reTrain = new ArrayList<>(list2);
			Log.d(TAG, "localRunningAppProcessInfo. test : " + test  );
			Log.d(TAG, "localRunningAppProcessInfo. list2 : " + list2  );
			reTrain.retainAll(test);
			if (!reTrain.isEmpty()) {

			}*/

				if (uid == localRunningAppProcessInfo.uid) {
//			if (uid == uid) {
					// TODO : All PID
//				if (packageName.equals("com.nhn.nni")) {
//					Log.e(TAG, String.format("Found nni PID : %d", localRunningAppProcessInfo.pid));
//					continue;
//				} else if (packageName.contains(":")) {
//					Log.e(TAG, String.format("Found Sub Process PID : %d", localRunningAppProcessInfo.pid));
//					continue;
//				}
//					Log.d(TAG, "localRunningAppProcessInfo.pid : " + localRunningAppProcessInfo.pid  );
					processId = localRunningAppProcessInfo.pid;
//					PreferenceManager.getInstance(context).setPid(processId);
//					processId = uid;
//					Log.e(TAG, "uid : " + uid + "/ pid : " + processId);
					packageName = localRunningAppProcessInfo.processName;
//					packageName = pm.getNameForUid(uid);

					if (packageName == null) {
						// TODO : NEW
						Log.e(TAG, "PackageName Null");
						continue;
					}

					// TODO : only one app which has uid 1000
//				if (!(packageName.contains("MDMService"))) {
//					continue;
//				}

//				if (processId == 0) {
//					continue;
//				}

					// TODO : only one app which has uid 1000
//				if (packageName == null) {
//					// TODO : NEW
//					Log.e(TAG, "PackageName Null");
//					continue;
//				}

					processIdList.add(processId);
					this.packageNameList.add(packageName);

//				if (this.processId == 0) {
//					Log.e(TAG, "PID 0");
//					continue;
//				}
//				arrayOfPid.add(this.processId);
//				continue;
//				int[] arrayOfInt = new int[1];
//				arrayOfInt[0] = this.processId;
//				this.processIds = arrayOfInt;
//				return;
					// All PID END
				}


		}

		// TODO : All PID
		if (this.packageNameList.size() == 0) {
			packageNameList.add("");
			processIdList.add(0);
		}
		int[] arrayOfInt = new int[processIdList.size()];
		for (int i = 0; i < processIdList.size(); i++) {
			arrayOfInt[i] = processIdList.get(i);
		}
		this.processIds = arrayOfInt;
		// All PID END
	}

	private void getPackageNameLollipop (int uid, PackageManager pm) {
		Log.d(TAG, "getPackageNameLollipop");
		String packageName = null;
		int processId = 0;

		for (int i = 0; i<osUplocalList.size() ; i++) {
			RunningAppProcessInfo localRunningAppProcessInfo =osUplocalList.get(i);
//			packageName = localRunningAppProcessInfo.processName;

			// TODO : only one app which has uid 1000

//			Log.d(TAG, "localListTest.size() : "+ osUplocalList.size() +">>uid : " + uid +", AndroidAppProcess.uid : " + localRunningAppProcessInfo.uid
//					+ ", \n pm : " + pm.getNameForUid(localRunningAppProcessInfo.uid)
//					+ ", \n pm2 : " + pm.getNameForUid(uid));
//

			if (uid == localRunningAppProcessInfo.uid) {

				processId = localRunningAppProcessInfo.pid;
//				PreferenceManager.getInstance(context).setPid(processId);
//					processId = uid;
				Log.e(TAG, "Lollipop uid : " + uid + "/ pid : " + processId);
				packageName = localRunningAppProcessInfo.processName;
//					packageName = pm.getNameForUid(uid);

				if (packageName == null) {
					// TODO : NEW
					Log.e(TAG, "PackageName Null");
					continue;
				}

				processIdList.add(processId);
				this.packageNameList.add(packageName);

			}

		}
//		Log.d(TAG, "processId : " + processId );

		// TODO : All PID
		if (this.packageNameList.size() == 0) {
			packageNameList.add("");
			processIdList.add(0);
		}
		int[] arrayOfInt = new int[processIdList.size()];
		for (int i = 0; i < processIdList.size(); i++) {
			arrayOfInt[i] = processIdList.get(i);
		}
		this.processIds = arrayOfInt;
		// All PID END
	}

	public void getBatteryUsage() {
		this.batteryIntent = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
		this.batteryLevel = (this.batteryIntent.getIntExtra("level", -1) + "%");
	}



}
