package dev.camerarepair.rtc;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * CameraRepair v4
 *
 * Conservative Camera2-side optimization for Meta RTC apps.  This version
 * deliberately does NOT block or fake-load libwzav1/libwzav1_v2 because the
 * previous experiment proved those libraries are required during app startup.
 */
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

        XposedBridge.log("CameraRepair 4.0.0: active in " + lpparam.packageName + " / " + lpparam.processName);
        hookCharacteristics();
        hookCaptureRequests();
    }

    /**
     * Hide face-detection capability from the client.  This prevents many apps
     * from enabling FD in the first place and is stronger than only rewriting
     * the request after the pipeline has already been configured.
     */
    private static void hookCharacteristics() {
        try {
            XposedBridge.hookAllMethods(CameraCharacteristics.class, "get", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length != 1) return;
                    Object keyObj = param.args[0];
                    if (!(keyObj instanceof CameraCharacteristics.Key)) return;

                    CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) keyObj;
                    String name = key.getName();
                    if (name == null) return;

                    if ("android.statistics.info.availableFaceDetectModes".equals(name)) {
                        param.setResult(new int[]{CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF});
                    } else if ("android.statistics.info.maxFaceCount".equals(name)) {
                        param.setResult(0);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("CameraRepair 4.0.0: characteristics hook failed: " + t);
        }
    }

    /**
     * Rewrite only standard Camera2 controls with safe low-overhead values.
     * No native loader interception, no codec creation failure and no forced
     * unsupported FPS range are used here.
     */
    private static void hookCaptureRequests() {
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

                    switch (name) {
                        case "android.statistics.faceDetectMode":
                            param.args[1] = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
                            break;
                        case "android.control.videoStabilizationMode":
                            param.args[1] = CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
                            break;
                        case "android.noiseReduction.mode":
                            param.args[1] = CaptureRequest.NOISE_REDUCTION_MODE_FAST;
                            break;
                        case "android.edge.mode":
                            param.args[1] = CaptureRequest.EDGE_MODE_FAST;
                            break;
                        case "android.colorCorrection.aberrationMode":
                            param.args[1] = CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST;
                            break;
                        case "android.shading.mode":
                            param.args[1] = CaptureRequest.SHADING_MODE_FAST;
                            break;
                        case "android.tonemap.mode":
                            param.args[1] = CaptureRequest.TONEMAP_MODE_FAST;
                            break;
                        case "android.statistics.lensShadingMapMode":
                            param.args[1] = CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF;
                            break;
                        default:
                            break;
                    }
                }
            });
            XposedBridge.log("CameraRepair 4.0.0: Camera2 optimization hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("CameraRepair 4.0.0: Camera2 hook failed: " + t);
        }
    }
}
