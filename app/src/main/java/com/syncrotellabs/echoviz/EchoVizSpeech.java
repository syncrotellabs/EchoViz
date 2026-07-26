package com.syncrotellabs.echoviz;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class EchoVizSpeech implements TextToSpeech.OnInitListener {
    private TextToSpeech textToSpeech;
    private boolean ready;
    private boolean failed;
    private String pendingText;

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
            failed = true;
            pendingText = null;
            return;
        }

        textToSpeech.setLanguage(Locale.getDefault());
        ready = true;

        if (pendingText != null) {
            speakNow(pendingText);
            pendingText = null;
        }
    }

    boolean speak(Context context, String text) {
        String spokenText = clean(text);
        if (spokenText.isEmpty() || failed) {
            return false;
        }

        ensureStarted(context);
        if (ready) {
            speakNow(spokenText);
        } else {
            pendingText = spokenText;
        }
        return true;
    }

    boolean isSpeaking() {
        return textToSpeech != null && textToSpeech.isSpeaking();
    }

    void stop() {
        pendingText = null;
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    void shutdown() {
        pendingText = null;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        ready = false;
        failed = false;
    }

    private void ensureStarted(Context context) {
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(context.getApplicationContext(), this);
        }
    }

    private void speakNow(String text) {
        Bundle params = new Bundle();
        List<String> chunks = splitForSpeech(text);
        for (int i = 0; i < chunks.size(); i++) {
            int queueMode = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            textToSpeech.speak(chunks.get(i), queueMode, params, "echoviz_read_aloud_" + i);
        }
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private List<String> splitForSpeech(String text) {
        int maxChunkLength = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 100);
        List<String> chunks = new ArrayList<>();
        String remaining = text;

        while (remaining.length() > maxChunkLength) {
            int splitAt = remaining.lastIndexOf(". ", maxChunkLength);
            if (splitAt < maxChunkLength / 2) {
                splitAt = remaining.lastIndexOf(" ", maxChunkLength);
            }
            if (splitAt <= 0) {
                splitAt = maxChunkLength;
            }

            chunks.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(Math.min(splitAt + 1, remaining.length())).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }
}
