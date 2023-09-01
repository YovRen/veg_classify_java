package cn.atrudom.veg_classify_java;

import static cn.atrudom.veg_classify_java.util.Protocol.QUEUE_TIME_SECOND;
import static cn.atrudom.veg_classify_java.util.Protocol.parseMessage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import cn.atrudom.veg_classify_java.databinding.RecognitionItemBinding;
import cn.atrudom.veg_classify_java.util.BleService;
import cn.atrudom.veg_classify_java.util.Protocol.ImageLables;
import cn.atrudom.veg_classify_java.util.Protocol.MyQueue;
import cn.atrudom.veg_classify_java.util.Utils;

import pub.devrel.easypermissions.EasyPermissions;

import org.tensorflow.lite.Interpreter;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class RecognitionActivity extends AppCompatActivity {

    interface RecognitionListener {
        void onRecognition(List<Recognition> recognitionList);
    }

    public static class Recognition {

        public String label;
        public float confidence;
        public String probabilityString;

        @SuppressLint("DefaultLocale")
        public Recognition(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
            this.probabilityString = String.format("%.1f%%", confidence * 100.0f);
        }
    }

    public static class RecognitionListViewModel extends ViewModel {

        private final MutableLiveData<List<Recognition>> _recognitionList = new MutableLiveData<>();
        public LiveData<List<Recognition>> recognitionList = _recognitionList;

        public void updateData(List<Recognition> recognitions) {
            _recognitionList.postValue(recognitions);
        }
    }

    public static class RecognitionViewHolder extends RecyclerView.ViewHolder {

        private final RecognitionItemBinding binding;

        RecognitionViewHolder(@NonNull RecognitionItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bindTo(Recognition recognition) {
            binding.setRecognitionItem(recognition);
            binding.executePendingBindings();
        }
    }

    public static class RecognitionAdapter extends ListAdapter<Recognition, RecognitionViewHolder> {

        private final Context ctx;

        public RecognitionAdapter(Context ctx) {
            super(new RecognitionDiffUtil());
            this.ctx = ctx;
        }


        @NonNull
        @Override
        public RecognitionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(ctx);
            RecognitionItemBinding binding = RecognitionItemBinding.inflate(inflater, parent, false);
            return new RecognitionViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull RecognitionViewHolder holder, int position) {
            holder.bindTo(getItem(position));
        }

        private static class RecognitionDiffUtil extends DiffUtil.ItemCallback<Recognition> {
            @Override
            public boolean areItemsTheSame(@NonNull Recognition oldItem, @NonNull Recognition newItem) {
                return oldItem.label.equals(newItem.label);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Recognition oldItem, @NonNull Recognition newItem) {
                return oldItem.confidence == newItem.confidence;
            }
        }
    }

    private static final int MAX_RESULT_DISPLAY = 5; // Maximum number of results displayed
    private static final int REQUEST_CODE_PERMISSIONS = 999; // Return code after asking for permission
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}; // permission needed
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private PreviewView viewFinder;
    private RecognitionListViewModel recogViewModel;
    public static MyQueue<ImageLables> imageLablesMyQueue = new MyQueue<>(QUEUE_TIME_SECOND);

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recognition_activity);

        findViewById(R.id.iv_main).setOnClickListener(v -> {
            Intent intent = new Intent(this, BleActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.iv_sample).setOnClickListener(v -> {
            Intent intent = new Intent(this, SampleActivity.class);
            startActivity(intent);
        });


        BleService.setBLECharacteristicValueChangeCallback((byte[] bytes) -> runOnUiThread(() -> {
            Bitmap qrcode = null;
            try {
                qrcode = parseMessage(bytes);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            if (qrcode != null) ((ImageView) findViewById(R.id.iv_qrcode)).setImageBitmap(qrcode);
        }));

        RecyclerView resultRecyclerView = findViewById(R.id.recognitionResults);
        RecognitionAdapter viewAdapter = new RecognitionAdapter(this);
        resultRecyclerView.setAdapter(viewAdapter);
        resultRecyclerView.setItemAnimator(null);

        recogViewModel = new ViewModelProvider(this).get(RecognitionListViewModel.class);
        recogViewModel.recognitionList.observe(this, viewAdapter::submitList);

        viewFinder = findViewById(R.id.viewFinder);

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            showAlert("权限不够，请打开相应的权限", () -> {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            });
        }
    }

    private boolean allPermissionsGranted() {
        if (!EasyPermissions.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            String[] perms = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT};
            return EasyPermissions.hasPermissions(this, perms);
        }
        return true;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalyzer(this, recogViewModel::updateData));
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception exc) {
                Log.e("yeser", "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    public static class ImageAnalyzer implements ImageAnalysis.Analyzer {
        private final RecognitionListener listener;
        private final Interpreter imageItp;
        private final List<String> texts;
        private final float[][] textFeatures;

        public ImageAnalyzer(Context ctx, RecognitionListener listener) throws IOException {
            this.listener = listener;

            ByteBuffer modelBuffer = Utils.loadModelFile(ctx, R.raw.res50_img_fp32);
            imageItp = new Interpreter(modelBuffer, new Interpreter.Options().setNumThreads(4));
            List<String> samples = Utils.readFromFile(ctx, "sample.txt");
            texts = new ArrayList<>();
            List<float[]> featureList = new ArrayList<>();
            for (String sample : samples) {
                String targetName = sample.split(":")[0];
                if (texts.stream().anyMatch(s -> s.equals(targetName))) {
                    int index = IntStream.range(0, texts.size()).filter(i -> texts.get(i).equals(targetName)).findFirst().orElse(-1);
                    String[] featureString = sample.split(":")[1].split(" ");
                    for (int i = 0; i < featureString.length; i++) {
                        featureList.get(index)[i] = (Float.parseFloat(featureString[i]) + featureList.get(index)[i]) / 2;
                    }
                } else {
                    texts.add(targetName);
                    String[] featureString = sample.split(":")[1].split(" ");
                    float[] feature = new float[featureString.length];
                    for (int i = 0; i < featureString.length; i++) {
                        feature[i] = Float.parseFloat(featureString[i]);
                    }
                    featureList.add(feature);
                }
            }
            textFeatures = new float[featureList.size()][];
            featureList.toArray(textFeatures);
        }

        @ExperimentalGetImage
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (textFeatures.length == 0) {
                return;
            }
            Bitmap mapImg = Bitmap.createScaledBitmap(imageProxy.toBitmap(), 224, 224, true);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mapImg.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            String toSaveImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

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
            float[] result = Utils.softmax(Utils.cosineSimilarity(imageFeatures, textFeatures)[0]);
            List<Recognition> toSaveLables = new ArrayList<>();
            for (int i = 0; i < result.length; i++) {
                toSaveLables.add(new Recognition(texts.get(i), result[i]));
            }
            toSaveLables.sort((o1, o2) -> Float.compare(o2.confidence, o1.confidence));
            if (toSaveLables.size() > MAX_RESULT_DISPLAY) toSaveLables = toSaveLables.subList(0, 5);
            listener.onRecognition(toSaveLables);

            try {
                imageLablesMyQueue.enqueue(new ImageLables(toSaveImage, toSaveLables));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            imageProxy.close();
        }
    }

    void showAlert(String content, Runnable callback) {
        new AlertDialog.Builder(this).setTitle("提示").setMessage(content).setPositiveButton("OK", (dialogInterface, i) -> new Thread(callback).start()).setCancelable(false).create().show();
    }

}
