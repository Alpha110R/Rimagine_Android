package com.example.rimagine.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TFLiteModelRunner implements AutoCloseable {
    private static final String TAG = "TFLiteModelRunner";
    private static final String MODEL_FILE = "best_float32.tflite";
    private static final float CONFIDENCE_THRESHOLD = 0.6f; // Lower threshold for more detections
    private static final String[] CLASS_LABELS = {"back_disk", "front_disk", "front_disk"}; // Add your class labels here
    private static final float IOU_THRESHOLD = 0.2f; // IoU threshold for NMS

    private final Context context;
    private Interpreter interpreter;
    private ImageProcessor imageProcessor;
    private GpuDelegate gpuDelegate;
    private int modelInputWidth;
    private int modelInputHeight;

    public TFLiteModelRunner(Context context) {
        this.context = context;
        initializeInterpreter();
    }

    private void initializeInterpreter() {
        try {
            Log.d(TAG, "Starting interpreter initialization");
            Interpreter.Options options = new Interpreter.Options();
            
            // Try to initialize GPU delegate if available
            try {
                CompatibilityList compatList = new CompatibilityList();
                if (compatList.isDelegateSupportedOnThisDevice()) {
                    gpuDelegate = new GpuDelegate();
                    options.addDelegate(gpuDelegate);
                    Log.d(TAG, "GPU delegate added successfully");
                } else {
                    Log.d(TAG, "GPU delegate not supported on this device");
                }
            } catch (Exception e) {
                Log.w(TAG, "GPU acceleration not available: " + e.getMessage());
            }

            // Load model file
            Log.d(TAG, "Loading model file: " + MODEL_FILE);
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE), options);
            Log.d(TAG, "Model file loaded successfully");
            
            // Get model input dimensions
            int[] inputShape = interpreter.getInputTensor(0).shape();
            modelInputHeight = inputShape[1];
            modelInputWidth = inputShape[2];
            Log.d(TAG, "Model input dimensions: " + modelInputWidth + "x" + modelInputHeight);
            
            // Initialize image processor with normalization
            imageProcessor = new ImageProcessor.Builder()
                    .add(new NormalizeOp(127.5f, 127.5f))
                    .build();
            Log.d(TAG, "Image processor initialized");

            Log.d(TAG, "TFLite interpreter initialized successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error initializing TFLite interpreter: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error initializing TFLite interpreter: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> runInference(Bitmap inputImage) {
        Map<String, Object> result = new HashMap<>();
        
        if (interpreter == null) {
            result.put("status", "error");
            result.put("message", "Interpreter is not initialized");
            return result;
        }

        try {
            Log.d(TAG, "Starting inference process");
            Log.d(TAG, "Input image size: " + inputImage.getWidth() + "x" + inputImage.getHeight());

            // Create input tensor buffer
            int[] inputShape = interpreter.getInputTensor(0).shape();
            Log.d(TAG, "Input tensor shape: " + java.util.Arrays.toString(inputShape));
            
            // Calculate buffer size based on model input shape
            int batchSize = inputShape[0];
            int height = modelInputHeight;
            int width = modelInputWidth;
            int channels = inputShape[3];
            
            Log.d(TAG, String.format("Creating buffer for: batch=%d, height=%d, width=%d, channels=%d", 
                                    batchSize, height, width, channels));
            
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(batchSize * height * width * channels * 4);
            inputBuffer.order(ByteOrder.nativeOrder());
            
            // Create a scaled bitmap for model input
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(inputImage, width, height, true);
            
            // Convert bitmap to byte buffer
            int[] pixels = new int[width * height];
            scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            Log.d(TAG, "Buffer capacity: " + inputBuffer.capacity() + " bytes");
            Log.d(TAG, "Number of pixels: " + pixels.length);
            
            // Process each pixel
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    
                    // Extract RGB values and normalize to [0, 1]
                    float r = ((pixel >> 16) & 0xFF) / 255.0f;
                    float g = ((pixel >> 8) & 0xFF) / 255.0f;
                    float b = (pixel & 0xFF) / 255.0f;
                    
                    // Add to buffer
                    inputBuffer.putFloat(r);
                    inputBuffer.putFloat(g);
                    inputBuffer.putFloat(b);
                }
            }
            
            inputBuffer.rewind();
            
            // Clean up scaled bitmap
            scaledBitmap.recycle();
            
            Log.d(TAG, "Input buffer size: " + inputBuffer.capacity() + " bytes");
            
            // Get output tensor shape
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            Log.d(TAG, "Model output shape: " + java.util.Arrays.toString(outputShape));

            // Create output array with correct shape [1, 8, 8400]
            float[][][] outputArray = new float[1][8][8400];

            // Run inference
            Object[] inputs = new Object[]{inputBuffer};
            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, outputArray);

            interpreter.runForMultipleInputsOutputs(inputs, outputs);

            // Save the processed image
            String outputPath = saveProcessedImage(inputImage, outputArray);
            
            // Return success result
            result.put("status", "success");
            result.put("output_image", outputPath);
            result.put("raw_output", outputArray);
            result.put("output_shape", outputShape);
            
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error running inference: " + e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Error running inference: " + e.getMessage());
            return result;
        }
    }

    private String saveProcessedImage(Bitmap inputImage, float[][][] detections) throws IOException {
        // Create a mutable copy of the input image to draw on
        Bitmap outputBitmap = inputImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(outputBitmap);
        
        // Setup paint for drawing boxes
        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(Math.max(inputImage.getWidth(), inputImage.getHeight()) / 150f);
        boxPaint.setColor(Color.RED);
        
        // Setup paint for text
        Paint textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(Math.max(inputImage.getWidth(), inputImage.getHeight()) / 30f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShadowLayer(5.0f, 0f, 0f, Color.BLACK);
        
        float imageWidth = inputImage.getWidth();
        float imageHeight = inputImage.getHeight();
        
        // Process detections
        float[][] predictions = detections[0]; // [8, 8400]
        
        Log.d(TAG, "Processing " + predictions[0].length + " potential detections");
        Log.d(TAG, String.format("Image dimensions: %.0fx%.0f", imageWidth, imageHeight));
        
        // List to store all valid detections
        List<Detection> validDetections = new ArrayList<>();
        
        // First pass: collect all valid detections
        for (int i = 0; i < predictions[0].length; i++) {
            // Find the highest confidence among all classes
            float maxConfidence = 0;
            int bestClass = -1;
            for (int c = 4; c < predictions.length; c++) {
                if (predictions[c][i] > maxConfidence) {
                    maxConfidence = predictions[c][i];
                    bestClass = c - 4;
                }
            }
            
            // Only consider detections above confidence threshold
            if (maxConfidence > CONFIDENCE_THRESHOLD) {
                // Extract bounding box coordinates
                float x = predictions[0][i] * imageWidth; // Center X
                float y = predictions[1][i] * imageHeight; // Center Y
                float w = predictions[2][i] * imageWidth; // Width
                float h = predictions[3][i] * imageHeight; // Height
                
                // Calculate corner coordinates
                float left = x - (w / 2);
                float top = y - (h / 2);
                float right = x + (w / 2);
                float bottom = y + (h / 2);
                
                // Ensure coordinates are within image bounds
                left = Math.max(0, Math.min(left, imageWidth));
                top = Math.max(0, Math.min(top, imageHeight));
                right = Math.max(0, Math.min(right, imageWidth));
                bottom = Math.max(0, Math.min(bottom, imageHeight));
                
                validDetections.add(new Detection(left, top, right, bottom, maxConfidence, bestClass));
            }
        }
        
        // Sort detections by confidence
        Collections.sort(validDetections, (a, b) -> Float.compare(b.confidence, a.confidence));
        
        // Apply Non-Maximum Suppression
        List<Detection> nmsDetections = new ArrayList<>();
        boolean[] suppressed = new boolean[validDetections.size()];
        
        for (int i = 0; i < validDetections.size(); i++) {
            if (suppressed[i]) continue;
            
            Detection detection = validDetections.get(i);
            nmsDetections.add(detection);
            
            // Suppress overlapping detections of the same class
            for (int j = i + 1; j < validDetections.size(); j++) {
                if (suppressed[j]) continue;
                
                Detection other = validDetections.get(j);
                if (detection.classId == other.classId && calculateIoU(detection, other) > IOU_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }
        
        // Draw the filtered detections
        for (Detection detection : nmsDetections) {
            Log.d(TAG, String.format("Final Detection: class=%d, conf=%.2f, box=[%.1f, %.1f, %.1f, %.1f]",
                detection.classId, detection.confidence, detection.left, detection.top, detection.right, detection.bottom));
            
            // Draw bounding box
            canvas.drawRect(new RectF(detection.left, detection.top, detection.right, detection.bottom), boxPaint);
            
            // Draw class label and confidence
            String label = String.format("%s %.2f", 
                detection.classId < CLASS_LABELS.length ? CLASS_LABELS[detection.classId] : "Class " + detection.classId,
                detection.confidence);
                
            float textWidth = textPaint.measureText(label);
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.argb(160, 0, 0, 0));
            canvas.drawRect(detection.left, detection.top - textPaint.getTextSize(), 
                          detection.left + textWidth, detection.top, bgPaint);
            canvas.drawText(label, detection.left, detection.top - textPaint.getTextSize()/4, textPaint);
        }
        
        // Save the processed image
        String outputFileName = "processed_" + System.currentTimeMillis() + ".jpg";
        File outputFile = new File(context.getFilesDir(), outputFileName);
        
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        }
        
        // Clean up
        outputBitmap.recycle();
        
        return outputFile.getAbsolutePath();
    }

    private static class Detection {
        float left, top, right, bottom;
        float confidence;
        int classId;

        Detection(float left, float top, float right, float bottom, float confidence, int classId) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.confidence = confidence;
            this.classId = classId;
        }
    }

    private float calculateIoU(Detection a, Detection b) {
        float intersectionLeft = Math.max(a.left, b.left);
        float intersectionTop = Math.max(a.top, b.top);
        float intersectionRight = Math.min(a.right, b.right);
        float intersectionBottom = Math.min(a.bottom, b.bottom);
        
        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0.0f;
        }
        
        float intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop);
        float aArea = (a.right - a.left) * (a.bottom - a.top);
        float bArea = (b.right - b.left) * (b.bottom - b.top);
        
        return intersectionArea / (aArea + bArea - intersectionArea);
    }

    @Override
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
        }
    }
} 