package com.alberto.velo.audiolab;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionSupport;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Test-only instrumentation. No Activity, microphone access, call control or audio capture. */
public final class OfflineProbeInstrumentation extends Instrumentation {
    private String mode;

    public void onCreate(Bundle args) {
        mode = args == null ? "support" : args.getString("mode", "support");
        super.onCreate(args);
        start();
    }

    public void onStart() {
        JSONObject result = new JSONObject();
        int code = 0;
        try {
            Context context = getTargetContext();
            result.put("mode", mode);
            result.put("package", context.getPackageName());
            result.put("uid", android.os.Process.myUid());
            result.put("internet_permission", context.checkSelfPermission("android.permission.INTERNET") == PackageManager.PERMISSION_GRANTED);
            result.put("microphone_permission", context.checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED);
            result.put("microphone_opened", false);
            if (mode.equals("support")) checkSpeechSupport(context, result);
            else if (mode.equals("translation")) translate(result);
            else if (mode.equals("prepare-speech")) prepareSpeech(context, result);
            else if (mode.equals("speech-file")) LocalSpeechFileProbe.run(this, context, result);
            else throw new IllegalArgumentException("Unknown mode " + mode);
            result.put("completed", true);
        } catch (Throwable error) {
            code = 1;
            try { result.put("error", error.toString()); } catch (Exception ignored) { }
        }
        Bundle output = new Bundle();
        output.putString("result_json", result.toString());
        finish(code, output);
    }

    private void checkSpeechSupport(Context context, JSONObject result) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SpeechRecognizer[] handle = new SpeechRecognizer[1];
        runOnMainSync(() -> {
            try {
                boolean local = SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
                boolean any = SpeechRecognizer.isRecognitionAvailable(context);
                result.put("on_device_recognizer_available", local);
                result.put("default_recognizer_available", any);
                if (!local && !any) { latch.countDown(); return; }
                handle[0] = local ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        : SpeechRecognizer.createSpeechRecognizer(context);
                result.put("support_provider", local ? "on_device" : "default_query_only");
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                handle[0].checkRecognitionSupport(intent, context.getMainExecutor(), new RecognitionSupportCallback() {
                    public void onSupportResult(RecognitionSupport support) {
                        try {
                            result.put("installed_local_languages", new JSONArray(support.getInstalledOnDeviceLanguages()));
                            result.put("pending_local_languages", new JSONArray(support.getPendingOnDeviceLanguages()));
                            result.put("supported_local_languages", new JSONArray(support.getSupportedOnDeviceLanguages()));
                        } catch (Exception error) { addError(result, error.toString()); }
                        latch.countDown();
                    }
                    public void onError(int error) { addError(result, "support_error_" + error); latch.countDown(); }
                });
            } catch (Throwable error) { addError(result, error.toString()); latch.countDown(); }
        });
        boolean completed = latch.await(15, TimeUnit.SECONDS);
        result.put("support_callback_received", completed);
        runOnMainSync(() -> { if (handle[0] != null) handle[0].destroy(); });
    }

    private static void addError(JSONObject result, String error) {
        try { result.put("support_error", error); } catch (Exception ignored) { }
    }

    private void prepareSpeech(Context context, JSONObject result) throws Exception {
        if (!BuildConfig.ALLOW_DOWNLOADS) throw new IllegalStateException("Downloads disabled in offline build");
        android.net.ConnectivityManager connectivity = context.getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (network == null || !network.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
            throw new IllegalStateException("Model preparation requires Wi-Fi");
        }
        CountDownLatch latch = new CountDownLatch(1);
        SpeechRecognizer[] handle = new SpeechRecognizer[1];
        runOnMainSync(() -> {
            try {
                handle[0] = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                handle[0].triggerModelDownload(intent, context.getMainExecutor(), new ModelDownloadListener() {
                    public void onProgress(int progress) {
                        try { result.put("model_download_progress", progress); } catch (Exception ignored) { }
                    }
                    public void onSuccess() {
                        try { result.put("french_model_download", "success"); } catch (Exception ignored) { }
                        latch.countDown();
                    }
                    public void onScheduled() {
                        try { result.put("french_model_download", "scheduled"); } catch (Exception ignored) { }
                        latch.countDown();
                    }
                    public void onError(int error) { addError(result, "model_download_error_" + error); latch.countDown(); }
                });
            } catch (Throwable error) { addError(result, error.toString()); latch.countDown(); }
        });
        result.put("download_callback_received", latch.await(90, TimeUnit.SECONDS));
        runOnMainSync(() -> { if (handle[0] != null) handle[0].destroy(); });
    }

    private void translate(JSONObject result) throws Exception {
        Translator translator = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.FRENCH).setTargetLanguage(TranslateLanguage.SPANISH).build());
        try {
            long start = SystemClock.elapsedRealtime();
            if (BuildConfig.ALLOW_DOWNLOADS) {
                Tasks.await(translator.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build()), 120, TimeUnit.SECONDS);
                result.put("model_prepare_ms", SystemClock.elapsedRealtime() - start);
            } else result.put("download_attempted", false);
            String[] texts = {
                "Bonjour, je m'appelle Marie. Je vous appelle pour confirmer notre rendez-vous demain à dix heures.",
                "Je suis le livreur. Je suis devant votre porte.",
                "Pouvez-vous me rappeler cet après-midi ?"
            };
            JSONArray examples = new JSONArray();
            for (String text : texts) {
                start = SystemClock.elapsedRealtime();
                String translated = Tasks.await(translator.translate(text), 20, TimeUnit.SECONDS);
                examples.put(new JSONObject().put("fr", text).put("es", translated)
                        .put("elapsed_ms", SystemClock.elapsedRealtime() - start));
            }
            result.put("translations", examples);
        } finally { translator.close(); }
    }
}
