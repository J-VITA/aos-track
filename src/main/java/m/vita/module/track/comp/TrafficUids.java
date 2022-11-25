package m.vita.module.track.comp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;


public class TrafficUids {
	private static final String TAG = "TrafficUids";
	private static final String QTAGUID_UID_STATS = "/proc/net/xt_qtaguid/stats";
		
	private static BufferedReader br;
	
	public static int[] getUids(int[] lastUids) {
		try {		
			br = new BufferedReader(new InputStreamReader(new FileInputStream(QTAGUID_UID_STATS)));
			br.readLine();
						
			String loadIface = null;
			
			ArrayList<Integer> uids = new ArrayList<Integer>();
			
			while ((loadIface = br.readLine()) != null) {
				String[] tokenOfLine = loadIface.split(" ");
				int uid = Integer.parseInt(tokenOfLine[3]);
				
				if (!(uids.contains(uid))) {
					uids.add(uid);
				}
			}
			
			int sz = uids.size();
			
			if (lastUids == null || lastUids.length < sz) {
				lastUids = new int[sz];
			} else if (2 * sz < lastUids.length) {
				lastUids = new int[sz];
			}
			
			int pos = 0;
			
			for (int i = 0; i < sz; i++) {
				lastUids[pos++] = uids.get(i);
			}
			
			while (pos < lastUids.length) {
				lastUids[pos++] = -1;
			}

			br.close();
		} catch (FileNotFoundException e1) {
		} catch (IOException e2) {
		}
		
		return lastUids;
	}
}
