package com.example.smsto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;
    private static final int MAX_WIDTH = 200;
    private static final int MAX_HEIGHT = 200;
    private static final String TAG = "MainActivity";

    private ImageView imageView;
    private Button chooseBtn, encodeBtn, copyBtn, switchBtn;
    private TextView smsOutput;
    private ProgressBar progressBar;
    private Bitmap selectedImage;
    private String encodedResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            imageView = findViewById(R.id.imageView);
            chooseBtn = findViewById(R.id.choose_button);
            encodeBtn = findViewById(R.id.encode_button);
            copyBtn = findViewById(R.id.copy_button);
            switchBtn = findViewById(R.id.btn_switch_to_decoder);
            smsOutput = findViewById(R.id.sms_output);
            progressBar = findViewById(R.id.progress_bar);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize views: " + e.getMessage(), e);
            Toast.makeText(this, "Initialization error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        chooseBtn.setOnClickListener(v -> pickImage());

        encodeBtn.setOnClickListener(v -> encodeImage());

        copyBtn.setOnClickListener(v -> {
            if (encodedResult != null && !encodedResult.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Encoded SMS", encodedResult);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to Clipboard!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No encoded text to copy!", Toast.LENGTH_SHORT).show();
            }
        });

        switchBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DecodeActivity.class);
            startActivity(intent);
        });
    }

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start image picker: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening image picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void encodeImage() {
        if (selectedImage == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (progressBar == null || smsOutput == null || copyBtn == null) {
            Log.e(TAG, "UI components are null");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        copyBtn.setEnabled(false);

        new Thread(() -> {
            Bitmap resized = null;
            try {
                Log.d(TAG, "Starting image resize");
                resized = resizeBitmap(selectedImage, MAX_WIDTH, MAX_HEIGHT);
                if (resized == null) {
                    throw new IllegalStateException("Failed to resize image");
                }

                Log.d(TAG, "Compressing to JPEG");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 30, baos);
                byte[] imageBytes = baos.toByteArray();
                if (imageBytes.length == 0) {
                    throw new IllegalStateException("Failed to compress image to JPEG");
                }

                Log.d(TAG, "Converting to Base64");
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                if (base64Image.isEmpty()) {
                    throw new IllegalStateException("Base64 encoding failed");
                }
                Log.d(TAG, "Base64 length: " + base64Image.length() + ", sample: " + base64Image.substring(0, Math.min(50, base64Image.length())));

                Log.d(TAG, "Compressing with GZIP");
                String compressedSms = compressString(base64Image);
                if (compressedSms == null || compressedSms.isEmpty()) {
                    throw new IllegalStateException("GZIP compression failed");
                }
                Log.d(TAG, "Compressed SMS length: " + compressedSms.length() + ", sample: " + compressedSms.substring(0, Math.min(50, compressedSms.length())));

                Log.d(TAG, "Splitting for SMS");
                List<String> smsSegments = splitForSms(compressedSms);
                encodedResult = compressedSms;
                Log.d(TAG, "Encoded output segments: " + smsSegments.size());

                runOnUiThread(() -> {
                    smsOutput.setText(String.join("\n", smsSegments));
                    copyBtn.setEnabled(true);
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Encoded SMS", compressedSms);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Encoded & Copied to Clipboard! Segments: " + smsSegments.size(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Encoding failed at step: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Encoding failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (resized != null && resized != selectedImage) {
                    resized.recycle();
                }
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    copyBtn.setEnabled(encodedResult != null && !encodedResult.isEmpty());
                });
            }
        }).start();
    }

    private Bitmap resizeBitmap(Bitmap original, int maxWidth, int maxHeight) {
        try {
            int width = original.getWidth();
            int height = original.getHeight();
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException("Invalid bitmap dimensions");
            }
            float ratio = Math.min((float) maxWidth / width, (float) maxHeight / height);
            int newWidth = Math.round(width * ratio);
            int newHeight = Math.round(height * ratio);
            return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to resize bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    private String compressString(String str) {
        if (str == null || str.isEmpty()) {
            Log.e(TAG, "Input string for compression is null or empty");
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            gzip.write(str.getBytes("UTF-8"));
            gzip.close();
            byte[] compressed = baos.toByteArray();
            String result = Base64.encodeToString(compressed, Base64.NO_WRAP);
            Log.d(TAG, "Compressed string length: " + result.length());
            return result;
        } catch (IOException e) {
            Log.e(TAG, "GZIP compression failed: " + e.getMessage(), e);
            return null;
        }
    }

    private List<String> splitForSms(String text) {
        List<String> segments = new ArrayList<>();
        int maxLength = 153; // GSM-7 segment length
        if (text == null) {
            Log.e(TAG, "Text to split for SMS is null");
            return segments;
        }
        for (int i = 0; i < text.length(); i += maxLength) {
            segments.add(text.substring(i, Math.min(i + maxLength, text.length())));
        }
        return segments;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri imageUri = data.getData();
                if (imageUri == null) {
                    throw new IllegalStateException("Image URI is null");
                }
                InputStream imageStream = getContentResolver().openInputStream(imageUri);
                if (imageStream == null) {
                    throw new IllegalStateException("Failed to open image stream");
                }
                if (selectedImage != null && !selectedImage.isRecycled()) {
                    selectedImage.recycle();
                }
                selectedImage = BitmapFactory.decodeStream(imageStream);
                imageStream.close();
                if (selectedImage == null) {
                    throw new IllegalStateException("Failed to decode bitmap");
                }
                Bitmap resized = resizeBitmap(selectedImage, MAX_WIDTH, MAX_HEIGHT);
                if (resized == null) {
                    throw new IllegalStateException("Failed to resize image for display");
                }
                imageView.setImageBitmap(resized);
                Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}