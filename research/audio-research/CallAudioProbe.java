package com.alberto.velo.research;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.AttributionSource;
import android.app.Application;
import android.app.Instrumentation;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Explicit, bounded shell diagnostic. Never places, answers or ends a call. */
public final class CallAudioProbe {
    private static final int RATE = 16000;
    private static final int FRAME = 320;
    private static final int CAPTURE_SECONDS = 16;
    private static volatile boolean finished;

    static Context context() throws Exception {
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object instance = thread.getMethod("systemMain").invoke(null);
        Context system = (Context) thread.getMethod("getSystemContext").invoke(instance);
        Context base = system.createPackageContext("com.android.shell", 0);
        Context shell = new ContextWrapper(base) {
            public String getPackageName() { return "com.android.shell"; }
            public String getOpPackageName() { return getPackageName(); }
            public Context getApplicationContext() { return this; }
            public AttributionSource getAttributionSource() {
                return new AttributionSource.Builder(Process.myUid()).setPackageName(getPackageName()).build();
            }
        };
        java.lang.reflect.Method outer = base.getClass().getDeclaredMethod("setOuterContext", Context.class);
        outer.setAccessible(true);
        outer.invoke(base, shell);
        Class<?> boundClass = Class.forName("android.app.ActivityThread$AppBindData");
        java.lang.reflect.Constructor<?> constructor = boundClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object boundData = constructor.newInstance();
        Field appInfo = boundClass.getDeclaredField("appInfo");
        appInfo.setAccessible(true);
        appInfo.set(boundData, base.getApplicationInfo());
        Field bound = thread.getDeclaredField("mBoundApplication");
        bound.setAccessible(true);
        bound.set(instance, boundData);
        Field application = thread.getDeclaredField("mInitialApplication");
        application.setAccessible(true);
        application.set(instance, Instrumentation.newApplication(Application.class, shell));
        System.out.println("attribution_package=" + shell.getAttributionSource().getPackageName());
        return shell;
    }

    static Throwable cause(Throwable error) {
        return error instanceof InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
    }

    private static boolean activeCall(AudioManager audio, TelecomManager telecom) {
        if (audio.getMode() != AudioManager.MODE_IN_CALL || !telecom.isInCall()) return false;
        try {
            return !((Boolean) TelecomManager.class.getDeclaredMethod("isRinging").invoke(telecom));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot verify non-ringing call state", cause(error));
        }
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            if (Process.myUid() != 2000) throw new IllegalStateException("Shell UID required");
            if (args.length < 1 || !(args[0].equals("idle") || args[0].equals("wait") || args[0].equals("dry-wait"))) {
                throw new IllegalArgumentException("Usage: idle | dry-wait <1..120 seconds> | wait <1..120 seconds> <16k mono s16le PCM>");
            }
            boolean idle = args[0].equals("idle");
            boolean dryRun = args[0].equals("dry-wait");
            int waitSeconds = idle ? 0 : Integer.parseInt(args[1]);
            if (!idle && (waitSeconds < 1 || waitSeconds > 120)) {
                throw new IllegalArgumentException("Wait must be 1..120 seconds");
            }
            short[] phrase = idle || dryRun ? new short[0] : loadPhrase(args[2]);
            Thread watchdog = new Thread(() -> {
                SystemClock.sleep((waitSeconds + CAPTURE_SECONDS + 12L) * 1000);
                if (!finished) {
                    System.out.println("watchdog=process_exit");
                    System.exit(3);
                }
            }, "velo-probe-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            Looper.prepareMainLooper();
            Context shell = context();
            AudioManager audio = AudioManager.class.getConstructor(Context.class).newInstance(shell);
            TelecomManager telecom = TelecomManager.class.getConstructor(Context.class).newInstance(shell);
            System.out.println("probe=call_audio_v1,uid=" + Process.myUid());
            System.out.println("initial_mode=" + audio.getMode());
            System.out.println("call_control=none,downlink_storage=none");
            if (idle) {
                if (audio.getMode() != AudioManager.MODE_NORMAL || telecom.isInCall()) {
                    throw new IllegalStateException("Idle diagnostic requires no call and MODE_NORMAL");
                }
                checkIdleGuard(audio, "getCallDownlinkExtractionAudioRecord", false);
                checkIdleGuard(audio, "getCallUplinkInjectionAudioTrack", true);
                System.out.println("idle_result=guards_passed_no_audio_started");
            } else {
                System.out.println("armed_wait_seconds=" + waitSeconds);
                long deadline = SystemClock.elapsedRealtime() + waitSeconds * 1000L;
                while (!activeCall(audio, telecom) && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(100);
                }
                if (!activeCall(audio, telecom)) {
                    System.out.println("result=no_active_call_timeout,no_audio_started=true");
                } else if (dryRun) {
                    System.out.println("result=dry_run_call_detected,no_audio_started=true");
                } else {
                    SystemClock.sleep(500);
                    if (activeCall(audio, telecom)) runAudio(audio, telecom, phrase);
                    else System.out.println("result=call_ended_before_audio,no_audio_started=true");
                }
            }
            System.out.println("final_mode=" + audio.getMode());
        } catch (Throwable error) {
            System.out.println("fatal=" + cause(error));
            cause(error).printStackTrace(System.out);
            exitCode = 2;
        } finally {
            finished = true;
            System.out.println("probe_finished=true");
            System.out.flush();
        }
        System.exit(exitCode);
    }

    private static AudioFormat format(boolean output) {
        return new AudioFormat.Builder().setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(output ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_IN_MONO)
                .build();
    }

    private static Object create(AudioManager audio, String method, boolean output) throws Exception {
        return AudioManager.class.getDeclaredMethod(method, AudioFormat.class)
                .invoke(audio, format(output));
    }

    private static void checkIdleGuard(AudioManager audio, String method, boolean output) throws Exception {
        Object object = null;
        try {
            object = create(audio, method, output);
            throw new IllegalStateException("Unexpected audio object while idle: " + method);
        } catch (InvocationTargetException error) {
            Throwable reason = cause(error);
            System.out.println("idle_guard." + method + "=" + reason);
            if (!(reason instanceof IllegalStateException)) throw error;
        } finally {
            if (object instanceof AudioRecord) ((AudioRecord) object).release();
            if (object instanceof AudioTrack) ((AudioTrack) object).release();
        }
    }

    private static short[] loadPhrase(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        if (bytes.length < 2 || bytes.length > RATE * 2 * 10 || bytes.length % 2 != 0) {
            throw new IllegalArgumentException("PCM must be nonempty and at most 10 seconds");
        }
        short[] result = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(result);
        return result;
    }

    private static void runAudio(AudioManager audio, TelecomManager telecom, short[] phrase) {
        AudioRecord record = null;
        AudioTrack track = null;
        long samples = 0, squareSum = 0, nonzero = 0;
        int peak = 0, injected = 0;
        boolean playbackStarted = false;
        long start = SystemClock.elapsedRealtime();
        try {
            try {
                record = (AudioRecord) create(audio, "getCallDownlinkExtractionAudioRecord", false);
                record.startRecording();
                System.out.println("downlink_started=" + (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING));
            } catch (Throwable error) { System.out.println("downlink_error=" + cause(error)); }
            try {
                track = (AudioTrack) create(audio, "getCallUplinkInjectionAudioTrack", true);
                System.out.println("uplink_initialized=" + (track.getState() == AudioTrack.STATE_INITIALIZED));
            } catch (Throwable error) { System.out.println("uplink_error=" + cause(error)); }
            if (record == null && track == null) return;
            short[] buffer = new short[FRAME];
            long lastReport = 0;
            long lastCallCheck = 0;
            while (SystemClock.elapsedRealtime() - start < CAPTURE_SECONDS * 1000L) {
                long elapsed = SystemClock.elapsedRealtime() - start;
                if (elapsed - lastCallCheck >= 250) {
                    if (!activeCall(audio, telecom)) break;
                    lastCallCheck = elapsed;
                }
                if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    int count = record.read(buffer, 0, FRAME, AudioRecord.READ_NON_BLOCKING);
                    if (count < 0) { System.out.println("downlink_read_error=" + count); break; }
                    for (int i = 0; i < count; i++) {
                        int value = buffer[i];
                        samples++; squareSum += (long) value * value;
                        if (value != 0) nonzero++;
                        peak = Math.max(peak, Math.abs(value));
                    }
                }
                if (elapsed >= 3000 && track != null && injected < phrase.length) {
                    if (!playbackStarted) { track.play(); playbackStarted = true; System.out.println("uplink_playback_started=true"); }
                    int written = track.write(phrase, injected, Math.min(FRAME, phrase.length - injected), AudioTrack.WRITE_NON_BLOCKING);
                    if (written < 0) { System.out.println("uplink_write_error=" + written); break; }
                    injected += written;
                }
                if (elapsed - lastReport >= 2000) {
                    System.out.println("progress_ms=" + elapsed + ",downlink_samples=" + samples + ",uplink_samples=" + injected);
                    lastReport = elapsed;
                }
                SystemClock.sleep(15);
            }
        } catch (Throwable error) {
            System.out.println("audio_error=" + cause(error));
        } finally {
            if (track != null) { try { track.pause(); track.flush(); } catch (Throwable ignored) {} track.release(); }
            if (record != null) { try { record.stop(); } catch (Throwable ignored) {} record.release(); }
            System.out.println("downlink_samples=" + samples + ",nonzero=" + nonzero + ",peak=" + peak);
            System.out.println("downlink_rms=" + String.format(Locale.ROOT, "%.3f", samples == 0 ? 0 : Math.sqrt((double) squareSum / samples)));
            System.out.println("uplink_samples_written=" + injected + ",phrase_samples=" + phrase.length);
            System.out.println("audio_resources_released=true,downlink_saved=false");
            System.out.println("remote_audibility=requires_human_confirmation");
        }
    }
}
