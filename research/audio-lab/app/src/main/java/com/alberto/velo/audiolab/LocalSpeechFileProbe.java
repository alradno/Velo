package com.alberto.velo.audiolab;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic French file only: never requests microphone permission or opens the microphone. */
final class LocalSpeechFileProbe {
    static void run(Instrumentation runner, Context context, JSONObject result) throws Exception {
        File wave = new File(context.getCacheDir(), "synthetic-french.wav");
        CountDownLatch synthesized = new CountDownLatch(1);
        TextToSpeech[] tts = new TextToSpeech[1];
        runner.runOnMainSync(() -> {
            tts[0] = new TextToSpeech(context, status -> context.getMainExecutor().execute(() -> {
                try {
                    if (status != TextToSpeech.SUCCESS) throw new IllegalStateException("TTS init " + status);
                    ArrayList<Voice> candidates = new ArrayList<>();
                    for (Voice voice : tts[0].getVoices()) {
                        if (voice.getLocale().toLanguageTag().equals("fr-FR") && !voice.isNetworkConnectionRequired()
                                && !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) candidates.add(voice);
                    }
                    candidates.sort(Comparator.comparing(Voice::getName));
                    result.put("installed_offline_french_voices", candidates.size());
                    if (candidates.isEmpty()) { synthesized.countDown(); return; }
                    Voice voice = candidates.get(0);
                    result.put("tts_voice", voice.getName());
                    result.put("tts_voice_requires_network", voice.isNetworkConnectionRequired());
                    int selected = tts[0].setVoice(voice);
                    if (selected != TextToSpeech.SUCCESS) throw new IllegalStateException("Voice selection " + selected);
                    tts[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        public void onStart(String id) { }
                        public void onDone(String id) { synthesized.countDown(); }
                        public void onError(String id) { error(result, "tts_error"); synthesized.countDown(); }
                        public void onError(String id, int code) { error(result, "tts_error_" + code); synthesized.countDown(); }
                    });
                    String text = "Bonjour. Je m'appelle Marie. Je suis le livreur. Je suis devant votre porte.";
                    result.put("synthetic_source_text", text);
                    int submitted = tts[0].synthesizeToFile(text, new Bundle(), wave, "velo-local-french");
                    if (submitted != TextToSpeech.SUCCESS) throw new IllegalStateException("Synthesis submission " + submitted);
                } catch (Throwable failure) { error(result, failure.toString()); synthesized.countDown(); }
            }));
        });
        try {
            boolean done = synthesized.await(20, TimeUnit.SECONDS);
            result.put("tts_callback_received", done);
            if (!done || !wave.exists() || wave.length() == 0) {
                result.put("file_recognition", "not_run_no_synthesized_audio");
                return;
            }
            byte[] pcm = mono16k(wave, result);
            result.put("synthetic_pcm_bytes", pcm.length);
            recognizeFile(runner, context, result, pcm);
            JSONArray segments = result.optJSONArray("recognized_segments");
            if (segments != null && segments.length() > 0 && !result.has("speech_file_error")) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < segments.length(); i++) text.append(i == 0 ? "" : " ").append(segments.getString(i));
                Translator translator = Translation.getClient(new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.FRENCH).setTargetLanguage(TranslateLanguage.SPANISH).build());
                try {
                    long start = SystemClock.elapsedRealtime();
                    result.put("recognized_translation_es", Tasks.await(translator.translate(text.toString()), 20, TimeUnit.SECONDS));
                    result.put("recognized_translation_ms", SystemClock.elapsedRealtime() - start);
                } finally { translator.close(); }
            }
        } finally {
            runner.runOnMainSync(() -> { if (tts[0] != null) tts[0].shutdown(); });
            result.put("synthetic_file_deleted", !wave.exists() || wave.delete());
        }
    }

    private static void recognizeFile(Instrumentation runner, Context context, JSONObject result, byte[] pcm) throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        CountDownLatch finished = new CountDownLatch(1);
        SpeechRecognizer[] recognizer = new SpeechRecognizer[1];
        JSONArray text = new JSONArray();
        Thread writer = new Thread(() -> {
            try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                for (int offset = 0; offset < pcm.length && !Thread.currentThread().isInterrupted(); offset += 640) {
                    output.write(pcm, offset, Math.min(640, pcm.length - offset));
                    SystemClock.sleep(20);
                }
            } catch (Exception failure) { error(result, "pipe_" + failure); }
        }, "velo-synthetic-source");
        writer.setDaemon(true);
        long start = SystemClock.elapsedRealtime();
        runner.runOnMainSync(() -> {
            try {
                recognizer[0] = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                recognizer[0].setRecognitionListener(new RecognitionListener() {
                    public void onReadyForSpeech(Bundle params) { }
                    public void onBeginningOfSpeech() { }
                    public void onRmsChanged(float rms) { }
                    public void onBufferReceived(byte[] buffer) { }
                    public void onEndOfSpeech() { }
                    public void onError(int code) { error(result, "file_recognition_error_" + code); finished.countDown(); }
                    public void onResults(Bundle data) { append(data); finished.countDown(); }
                    public void onPartialResults(Bundle data) { }
                    public void onEvent(int type, Bundle params) { }
                    public void onSegmentResults(Bundle data) { append(data); }
                    public void onEndOfSegmentedSession() { finished.countDown(); }
                    private void append(Bundle data) {
                        ArrayList<String> alternatives = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (alternatives != null && !alternatives.isEmpty()) text.put(alternatives.get(0));
                    }
                });
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
                recognizer[0].startListening(intent);
                writer.start();
            } catch (Throwable failure) { error(result, failure.toString()); finished.countDown(); }
        });
        try {
            result.put("file_recognition_callback_received", finished.await(25, TimeUnit.SECONDS));
            result.put("recognized_segments", text);
            result.put("speech_test_elapsed_ms", SystemClock.elapsedRealtime() - start);
            result.put("speech_input", "synthetic_pcm_pipe_only");
        } finally {
            writer.interrupt();
            pipe[0].close();
            pipe[1].close();
            runner.runOnMainSync(() -> { if (recognizer[0] != null) recognizer[0].destroy(); });
        }
    }

    private static void error(JSONObject result, String text) {
        try { result.put("speech_file_error", text); } catch (Exception ignored) { }
    }

    private static byte[] mono16k(File wave, JSONObject result) throws Exception {
        byte[] data = Files.readAllBytes(wave.toPath());
        ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int channels = 0, rate = 0, encoding = 0, bits = 0, offset = 0, size = 0;
        for (int pos = 12; pos + 8 <= data.length;) {
            int length = input.getInt(pos + 4);
            if (length < 0 || pos + 8L + length > data.length) throw new IllegalArgumentException("Invalid WAV chunk");
            String chunk = new String(data, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (chunk.equals("fmt ")) {
                encoding = input.getShort(pos + 8); channels = input.getShort(pos + 10);
                rate = input.getInt(pos + 12); bits = input.getShort(pos + 22);
            } else if (chunk.equals("data")) { offset = pos + 8; size = length; }
            pos += 8 + length + (length & 1);
        }
        if (encoding != 1 || bits != 16 || channels < 1 || rate < 8000 || size == 0) throw new IllegalArgumentException("Unsupported WAV format");
        result.put("tts_sample_rate", rate);
        int frames = size / (channels * 2);
        int outFrames = (int) ((long) frames * 16000 / rate);
        ByteBuffer output = ByteBuffer.allocate(outFrames * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < outFrames; i++) {
            int original = (int) ((long) i * rate / 16000);
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) sum += input.getShort(offset + (original * channels + channel) * 2);
            output.putShort((short) (sum / channels));
        }
        return output.array();
    }
}
