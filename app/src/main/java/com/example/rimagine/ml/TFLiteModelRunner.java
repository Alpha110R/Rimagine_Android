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
import java.util.HashMap;
import java.util.Map;

public class TFLiteModelRunner implements AutoCloseable {
    private static final String TAG = "TFLiteModelRunner";
    private static final String MODEL_FILE = "best_float16.tflite";
    private static final int INPUT_SIZE = 1280; // Updated to match model's expected input size
    private static final float MEAN = 127.5f;
    private static final float STD = 127.5f;

    private final Context context;
    private Interpreter interpreter;
    private ImageProcessor imageProcessor;
    private GpuDelegate gpuDelegate;

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
            
            // Initialize image processor
            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(MEAN, STD))
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

            // Create input tensor buffer
            int[] inputShape = interpreter.getInputTensor(0).shape();
            Log.d(TAG, "Input tensor shape: " + java.util.Arrays.toString(inputShape));
            
            // Calculate buffer size based on input shape
            int batchSize = inputShape[0];
            int height = inputShape[1];
            int width = inputShape[2];
            int channels = inputShape[3];
            
            Log.d(TAG, String.format("Creating buffer for: batch=%d, height=%d, width=%d, channels=%d", 
                                    batchSize, height, width, channels));
            
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(batchSize * height * width * channels * 4); // 4 bytes per float
            inputBuffer.order(ByteOrder.nativeOrder());
            
            // Resize and normalize the image
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
                    
                    // Extract and normalize RGB values
                    float r = ((pixel >> 16) & 0xFF) / 255.0f;
                    float g = ((pixel >> 8) & 0xFF) / 255.0f;
                    float b = (pixel & 0xFF) / 255.0f;
                    
                    // Normalize using mean and std
                    r = (r - MEAN/255.0f) / (STD/255.0f);
                    g = (g - MEAN/255.0f) / (STD/255.0f);
                    b = (b - MEAN/255.0f) / (STD/255.0f);
                    
                    // Add to buffer
                    inputBuffer.putFloat(r);
                    inputBuffer.putFloat(g);
                    inputBuffer.putFloat(b);
                }
            }
            
            inputBuffer.rewind();
            
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

            // Clean up
            scaledBitmap.recycle();

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
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5.0f);
        paint.setColor(Color.RED);
        
        // Setup paint for text
        Paint textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(50f);
        textPaint.setStyle(Paint.Style.FILL);
        
        float imageWidth = inputImage.getWidth();
        float imageHeight = inputImage.getHeight();
        
        // Process detections
        float[][] predictions = detections[0]; // [8, 8400]
        
        // For each detection
        for (int i = 0; i < predictions[0].length; i++) {
            // Extract bounding box coordinates (normalized)
            float x = predictions[0][i];
            float y = predictions[1][i];
            float w = predictions[2][i];
            float h = predictions[3][i];
            
            // Find the highest confidence among all classes
            float maxConfidence = 0;
            int bestClass = -1;
            for (int c = 4; c < predictions.length; c++) {
                if (predictions[c][i] > maxConfidence) {
                    maxConfidence = predictions[c][i];
                    bestClass = c - 4;
                }
            }
            
            // Only draw boxes for confident detections
            if (maxConfidence > 0.5f) {
                // Convert normalized coordinates to actual pixel coordinates
                float left = (x - w/2) * imageWidth;
                float top = (y - h/2) * imageHeight;
                float right = (x + w/2) * imageWidth;
                float bottom = (y + h/2) * imageHeight;
                
                // Draw bounding box
                canvas.drawRect(new RectF(left, top, right, bottom), paint);
                
                // Draw class label and confidence
                String label = String.format("Class %d: %.2f", bestClass, maxConfidence);
                canvas.drawText(label, left, top - 10, textPaint);
            }
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