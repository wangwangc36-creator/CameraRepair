package dev.camerarepair.rtc;

import android.hardware.camera2.CaptureRequest;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CameraRepairHook implements IXposedHookLoadPackage {
    private static final Set<String> TARGETS = new HashSet<>();

    static {
        TARGETS.add("com.whatsapp");
        TARGETS.add("com.facebook.orca");
        TARGETS.add("com.instagram.android");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGETS.contains(lpparam.packageName)) return;

        XposedBridge.log("CameraRepair 3.0.1: active in " + lpparam.packageName + " / " + lpparam.processName);
        hookCamera2Safe();
    }

    private static void hookCamera2Safe() {
        try {
            XposedBridge.hookAllMethods(CaptureRequest.Builder.class, "set", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 2) return;
                    Object keyObj = param.args[0];
                    if (!(keyObj instanceof CaptureRequest.Key)) return;

                    CaptureRequest.Key<?> key = (CaptureRequest.Key<?>) keyObj;
                    String name = key.getName();
                    if (name == null) return;

                    if ("android.statistics.faceDetectMode".equals(name)) {
                        param.args[1] = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
                    } else if ("android.control.videoStabilizationMode".equals(name)) {
                        param.args[1] = CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
                    }
                }
            });
            XposedBridge.log("CameraRepair 3.0.1: safe Camera2 hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("CameraRepair 3.0.1: Camera2 hook failed: " + t);
        }
    }
}
