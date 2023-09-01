package cn.atrudom.veg_classify_java;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;


import cn.atrudom.veg_classify_java.util.Utils;

public class SampleAddActivity extends AppCompatActivity {
    private final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    ImageCapture imageCapture;
    ByteBuffer modelBuffer;
    Interpreter imageItp;
    private PreviewView viewFinder;

    @SuppressLint("SetTextI18n") // 禁止用硬编码字符串进行文本框修改，避免国际化问题。
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_add_activity); // 加载布局文件
        viewFinder = findViewById(R.id.viewFinder);
        try {
            modelBuffer = Utils.loadModelFile(this, R.raw.res50_img_fp32);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        imageItp = new Interpreter(modelBuffer, new Interpreter.Options().setNumThreads(4));

        findViewById(R.id.iv_back).setOnClickListener((View view) -> {
            Intent intent = new Intent(SampleAddActivity.this, SampleActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.bt_send).setOnClickListener((View view) -> { // 设置发送按钮的点击事件
            if (imageCapture == null) {
                return;
            }

            String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis());
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");

            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();

            imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onError(@NonNull ImageCaptureException exc) {
                    Log.e("yeser", "Photo capture failed: " + exc.getMessage(), exc);
                }

                @SuppressLint("DefaultLocale")
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {

                    String msg = "Photo capture succeeded: " + output.getSavedUri();
                    Toast.makeText(SampleAddActivity.this, msg, Toast.LENGTH_SHORT).show();

                    String data = ((EditText) findViewById(R.id.et_send)).getText().toString(); // 获取要发送的数据
                    // 如果选择了字符串发送
                    if (data.length() == 0) {
                        showAlert("请输入类别名称", () -> {
                        });
                        return;
                    }
                    String targetName = data.replace("\n", "\r\n"); // 将回车符替换为回车和换行符
                    InputStream inputStream;
                    try {
                        inputStream = getContentResolver().openInputStream(Objects.requireNonNull(output.getSavedUri()));
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    Bitmap mapImg = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
                    int[] pixels = new int[224 * 224];
                    mapImg.getPixels(pixels, 0, 224, 0, 0, 224, 224);
                    float[] arrayImg = new float[3 * 224 * 224];
                    for (int index = 0; index < 224 * 224; index++) {
                        int pixel = pixels[index];
                        arrayImg[index] = ((float) ((pixel >> 16) & 0xFF) / 255.0f - 0.48145466f) / 0.26862955f;
                        arrayImg[index + 224 * 224] = ((float) ((pixel >> 8) & 0xFF) / 255.0f - 0.4578275f) / 0.26130258f;
                        arrayImg[index + 224 * 224 * 2] = ((float) (pixel & 0xFF) / 255.0f - 0.40821073f) / 0.27577711f;
                    }
                    FloatBuffer bufferImg = FloatBuffer.wrap(arrayImg);
                    float[][] imageFeatures = new float[1][1024];
                    imageItp.run(bufferImg, imageFeatures);
                    StringBuilder stringBuilder = new StringBuilder();
                    for (int j = 0; j < imageFeatures[0].length; j++) {
                        stringBuilder.append(String.format("%.4f", imageFeatures[0][j]));
                        if (j < imageFeatures[0].length - 1) {
                            stringBuilder.append(" ");
                        }
                    }
                    String result = stringBuilder.toString();
                    Utils.writeToFile(getApplicationContext(), "sample.txt", Collections.singletonList(targetName + ":" + result), "a");
                }
            });
        });

        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception exc) {
                Log.e("yeser", "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void showAlert(String content, Runnable callback) {
        new AlertDialog.Builder(this).setTitle("提示").setMessage(content).setPositiveButton("OK", (dialogInterface, i) -> new Thread(callback).start()) // 在新的线程中执行回调函数
                .setCancelable(false) // 禁止在对话框外部点击和返回键退出对话框
                .create().show();
    }
}
