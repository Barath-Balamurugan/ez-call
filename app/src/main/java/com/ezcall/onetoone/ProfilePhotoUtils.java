package com.ezcall.onetoone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

final class ProfilePhotoUtils {
    private static final String TAG = "ProfilePhotoUtils";
    private static final int MAX_BASE64_CHARS = 600_000;
    private static final int MAX_DECODED_SIDE = 1024;

    private ProfilePhotoUtils() {
    }

    static String sanitizeBase64(String value) {
        if (value == null) {
            return "";
        }

        String text = value.trim();
        int commaIndex = text.indexOf(',');
        if (text.startsWith("data:image/") && commaIndex >= 0) {
            text = text.substring(commaIndex + 1).trim();
        }

        if (text.length() > MAX_BASE64_CHARS) {
            Log.w(TAG, "Skipping oversized profile photo: " + text.length() + " chars");
            return "";
        }

        return text;
    }

    static Bitmap decodeBase64(String value) {
        String encodedImage = sanitizeBase64(value);
        if (encodedImage.isEmpty()) {
            return null;
        }

        try {
            byte[] imageBytes = Base64.decode(encodedImage, Base64.DEFAULT);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
        } catch (IllegalArgumentException | OutOfMemoryError error) {
            Log.w(TAG, "Could not decode profile photo", error);
            return null;
        }
    }

    private static int sampleSize(int width, int height) {
        int sampleSize = 1;
        while (width / sampleSize > MAX_DECODED_SIDE || height / sampleSize > MAX_DECODED_SIDE) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
