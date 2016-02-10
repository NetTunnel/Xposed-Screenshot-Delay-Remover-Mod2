package bin.xposed.screenshotdelayremover.mod;

import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {

    /* We might be configurable later on, but not now, no */
    private static final long DELAY = 0L;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android"))
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            XposedHelpers.findAndHookMethod("com.android.server.policy.PhoneWindowManager",
                    lpparam.classLoader, "getScreenshotChordLongPressDelay", XC_MethodReplacement.returnConstant(DELAY));

        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManagerr",
                    lpparam.classLoader, "getScreenshotChordLongPressDelay", XC_MethodReplacement.returnConstant(DELAY));

        else {
            final boolean isLowerThanJB = Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;
            XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager",
                    lpparam.classLoader, "interceptScreenshotChord", new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {

                            //only JB+ has this field, so assume it's true under JB
                            boolean mScreenshotChordEnabled = isLowerThanJB || XposedHelpers.getBooleanField(param.thisObject, "mScreenshotChordEnabled");

                            boolean mVolumeDownKeyTriggered = XposedHelpers.getBooleanField(param.thisObject,
                                    "mVolumeDownKeyTriggered");
                            boolean mPowerKeyTriggered = XposedHelpers.getBooleanField(param.thisObject,
                                    "mPowerKeyTriggered");
                            boolean mVolumeUpKeyTriggered = XposedHelpers.getBooleanField(param.thisObject,
                                    "mVolumeUpKeyTriggered");

                            if (mScreenshotChordEnabled
                                    && mVolumeDownKeyTriggered && mPowerKeyTriggered && !mVolumeUpKeyTriggered) {
                                long mVolumeDownKeyTime = XposedHelpers.getLongField(param.thisObject,
                                        "mVolumeDownKeyTime");
                                long mPowerKeyTime = XposedHelpers.getLongField(param.thisObject,
                                        "mPowerKeyTime");
                                long SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS = XposedHelpers.getLongField(param.thisObject,
                                        "SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS");

                                final long now = SystemClock.uptimeMillis();
                                if (now <= mVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS
                                        && now <= mPowerKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
                                    XposedHelpers.setBooleanField(param.thisObject, "mVolumeDownKeyConsumedByScreenshotChord", true);
                                    XposedHelpers.callMethod(param.thisObject, "cancelPendingPowerKeyAction");

                                    Handler mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
                                    Runnable mScreenshotChordLongPress = (Runnable) XposedHelpers.getObjectField(param.thisObject,
                                            "mScreenshotChordLongPress");
                                    mHandler.postDelayed(mScreenshotChordLongPress, DELAY);
                                }
                            }
                            return null;
                        }
                    });
        }
    }
}