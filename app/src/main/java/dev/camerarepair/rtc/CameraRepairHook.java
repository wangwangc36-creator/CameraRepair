package dev.camerarepair.rtc;

import android.hardware.camera2.CaptureRequest;
import android.util.Range;

import java.util.HashSet;
import java.util.Locale;
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

        XposedBridge.log("CameraRepair: active in " + lpparam.packageName + " / " + lpparam.processName);

        hookWzAv1Loaders(lpparam.classLoader);
        hookCamera2();
    }

    private static void hookWzAv1Loaders(ClassLoader appClassLoader) {
        XC_MethodHook blocker = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (containsWzAv1(param.args)) {
                    XposedBridge.log("CameraRepair: blocked WZAV1 load: " + stringify(param.args));
                    param.setThrowable(new UnsatisfiedLinkError("CameraRepair blocked software WZAV1 encoder"));
                }
            }
        };

        hookAllSafe(System.class, "load", blocker);
        hookAllSafe(System.class, "loadLibrary", blocker);
        hookAllSafe(Runtime.class, "load", blocker);
        hookAllSafe(Runtime.class, "loadLibrary", blocker);
        hookAllSafe(Runtime.class, "load0", blocker);
        hookAllSafe(Runtime.class, "loadLibrary0", blocker);
        hookAllSafe(Runtime.class, "nativeLoad", blocker);

        hookClassMethodsSafe(appClassLoader, "com.facebook.soloader.SoLoader", blocker,
                "loadLibrary", "loadLibraryBySoName", "doLoadLibraryBySoName", "loadLibraryUnsafe");
        hookClassMethodsSafe(appClassLoader, "com.facebook.soloader.nativeloader.NativeLoader", blocker,
                "loadLibrary", "loadLibraryBySoName");
    }

    private static void hookCamera2() {
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
                        case "android.control.aeTargetFpsRange":
                            param.args[1] = new Range<>(15, 15);
                            break;
                        default:
                            break;
                    }
                }
            });
            XposedBridge.log("CameraRepair: Camera2 optimization hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("CameraRepair: Camera2 hook failed: " + t);
        }
    }

    private static void hookAllSafe(Class<?> clazz, String methodName, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, hook);
        } catch (Throwable ignored) {
        }
    }

    private static void hookClassMethodsSafe(ClassLoader cl, String className, XC_MethodHook hook, String... methods) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            for (String method : methods) hookAllSafe(cls, method, hook);
        } catch (Throwable ignored) {
        }
    }

    private static boolean containsWzAv1(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) {
            if (arg instanceof String) {
                String s = ((String) arg).toLowerCase(Locale.ROOT);
                if (s.contains("wzav1") || s.contains("libwzav1.so") || s.contains("libwzav1_v2.so")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stringify(Object[] args) {
        if (args == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(String.valueOf(arg));
        }
        return sb.toString();
    }
}
