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

package m.vita.module.track.phone;

import android.content.Context;

import java.util.List;

import m.vita.module.track.comp.Audio;
import m.vita.module.track.comp.CPU;
import m.vita.module.track.comp.GPS;
import m.vita.module.track.comp.PowerComponent;
import m.vita.module.track.comp.Sensors;
import m.vita.module.track.comp.Threeg;
import m.vita.module.track.comp.Wifi;
import m.vita.module.track.service.PowerData;
import m.vita.module.track.util.NotificationService;
import m.vita.module.track.util.SystemInfo;

public class PhoneSelector {
	private static final String TAG = "NT1PhoneSelector";

	public static final int PHONE_UNKNOWN = 0;
	public static final int PHONE_DREAM = 1; /* G1 */
	public static final int PHONE_SAPPHIRE = 2; /* G2 */
	public static final int PHONE_PASSION = 3; /* Nexus One */
	public static int PHONE_TYPE = PHONE_UNKNOWN;
	public static int CPU_TYPE = 1;
	public static boolean isOLED = false;

	/* A hard-coded list of phones that have OLED screens. */
//	public static final String[] OLED_PHONES = { "bravo", "passion",
//			"GT-I9000", "inc", "legend", "GT-I7500", "SPH-M900", "SGH-I897",
//			"SGH-T959", "desirec", };
	
//	public static final String[] OLED_PHONES = { "bravo", "passion",
//		"SHW-M250S", "SHW-M250K", "SHW-M250L", "m0skt", "c1skt", "c1ktt", "c1lgt", };

	/*
	 * This class is not supposed to be instantiated. Just use the static
	 * members.
	 */
	
	private PhoneSelector() {
	}

	public static boolean phoneSupported() {
		return getPhoneType() != PHONE_UNKNOWN;
	}

	public static boolean hasOled() {
/* Delete Start */
//		for (int i = 0; i < OLED_PHONES.length; i++) {
//			if (Build.DEVICE.equals(OLED_PHONES[i])) {
//				return true;
//			}
//		}
//		return false;
		return isOLED;
	}

	public static int getPhoneType() {
		/*
		if (Build.DEVICE.startsWith("dream"))
			return PHONE_DREAM;
		if (Build.DEVICE.startsWith("sapphire"))
			return PHONE_SAPPHIRE;
		if (Build.DEVICE.startsWith("passion"))
			return PHONE_PASSION;
		return PHONE_DREAM;
		*/
		switch(PHONE_TYPE) {
		case PHONE_DREAM:
			return PHONE_DREAM;
		case PHONE_SAPPHIRE:
			return PHONE_SAPPHIRE;
		case PHONE_PASSION:
			return PHONE_PASSION;
		default:
			return PHONE_UNKNOWN;
		}
	}

	public static PhoneConstants getConstants(Context context) {
		switch (getPhoneType()) {
		case PHONE_DREAM:
			return new DreamConstants(context);
		case PHONE_SAPPHIRE:
			return new SapphireConstants(context);
		case PHONE_PASSION:
			return new PassionConstants(context);
		default:
			return new DreamConstants(context);
		}
	}

	public static PhonePowerCalculator getCalculator(Context context) {
		switch (getPhoneType()) {
		case PHONE_DREAM:
			return new DreamPowerCalculator(context);
		case PHONE_SAPPHIRE:
			return new SapphirePowerCalculator(context);
		case PHONE_PASSION:
			return new PassionPowerCalculator(context);
		default:
			return new DreamPowerCalculator(context);
		}
	}

	public static void generateComponents(Context context,
										  List<PowerComponent> components, List<PowerFunction> functions) {
		final PhoneConstants constants = getConstants(context);
		final PhonePowerCalculator calculator = getCalculator(context);
		
//		if (batteryMode) {
		    /* Add display component. */
//
//		    if(hasOled()) {
//		      components.add(new OLED(context, constants));
//		      functions.add(new PowerFunction() {
//		        public double calculate(PowerData data) {
//		          return calculator.getOledPower((OLED.OledData)data);
//		        }});
//		    } else {
//		      components.add(new LCD(context));
//		      functions.add(new PowerFunction() {
//		        public double calculate(PowerData data) {
//		          return calculator.getLcdPower((LCD.LcdData)data);
//		        }});
//		    }
//
			/* Add CPU component. */
			components.add(new CPU(constants));
			functions.add(new PowerFunction() {
				public double calculate(PowerData data) {
					return calculator.getCpuPower((CPU.CpuData) data) * CPU_TYPE;
				}
			});
	
			/* Add Wifi component. */
			String wifiInterface = SystemInfo.getInstance().getProperty("wifi.interface");
			if (wifiInterface != null && wifiInterface.length() != 0) {
				components.add(new Wifi(context, constants));
				functions.add(new PowerFunction() {
					public double calculate(PowerData data) {
						return calculator.getWifiPower((Wifi.WifiData) data);
					}
				});
			}
	
			/* Add 3G component. */
			if (constants.threegInterface().length() != 0) {
				components.add(new Threeg(context, constants));
				functions.add(new PowerFunction() {
					public double calculate(PowerData data) {
						return calculator.getThreeGPower((Threeg.ThreegData) data);
					}
				});
			}
//		}

		/* Add GPS component. */
		components.add(new GPS(context, constants));
		functions.add(new PowerFunction() {
			public double calculate(PowerData data) {
				return calculator.getGpsPower((GPS.GpsData) data);
			}
		});

		/* Add Audio component. */
		components.add(new Audio(context));
		functions.add(new PowerFunction() {
			public double calculate(PowerData data) {
				return calculator.getAudioPower((Audio.AudioData) data);
			}
		});

		/* Add Sensors component if avaialble. */
		if (NotificationService.available()) {
			components.add(new Sensors(context));
			functions.add(new PowerFunction() {
				public double calculate(PowerData data) {
					return calculator.getSensorPower((Sensors.SensorData) data);
				}
			});
		}
	}
}
