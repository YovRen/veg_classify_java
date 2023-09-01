package cn.atrudom.veg_classify_java.util;


import static java.lang.Math.exp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;


import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Utils {

    public static Bitmap sendPacketToServer(Protocol.Packet packet) {
        String json = null;
        Gson gson = new Gson();
        json = gson.toJson(packet);
        Log.d("messagekey", json);
        byte[] decodedByteArray = Base64.decode(packet.products.get(0).imageLables.imgStr, Base64.DEFAULT);
        Bitmap restoredBitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.length);


//        OkHttpClient client = new OkHttpClient();
//        MediaType mediaType = MediaType.parse("application/json");
//        RequestBody requestBody = RequestBody.create(mediaType, json);
//
//        Request request = new Request.Builder().url("http://your-server-url").post(requestBody).build();
//
//        try {
//            Response response = client.newCall(request).execute();
//            if (response.isSuccessful()) {
//                System.out.println("JSON sent successfully to the server.");
//                InputStream inputStream = response.body().byteStream();
//                return BitmapFactory.decodeStream(inputStream);
//            } else {
//                System.out.println("Failed to send JSON to the server.");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return restoredBitmap;
    }

    public static void writeToFile(Context context, String filename, List<String> content, String mode) {
        try {
            FileOutputStream outputStream;
            if (Objects.equals(mode, "w")) {
                outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            } else {
                outputStream = context.openFileOutput(filename, Context.MODE_APPEND);
            }
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);

            for (String line : content) {
                writer.write(line);
                writer.write(System.lineSeparator()); // 添加换行符
            }
            writer.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> readFromFile(Context context, String filename) {
        List<String> content = new ArrayList<>();

        try {
            FileInputStream inputStream = context.openFileInput(filename);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader);

            String line;
            while ((line = reader.readLine()) != null) {
                content.add(line);
            }

            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return content;
    }

    public static ByteBuffer loadModelFile(Context context, int modelPath) throws IOException {
        AssetFileDescriptor file = context.getResources().openRawResourceFd(modelPath);
        FileInputStream fileInputStream = new FileInputStream(file.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = file.getStartOffset();
        long declaredLength = file.getDeclaredLength();
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static float[][] normalizeFeatures(float[][] features) {
        float[][] normalizedFeatures = new float[features.length][features[0].length];

        for (int i = 0; i < features.length; i++) {
            float norm = 0.0f;
            for (float value : features[i]) {
                norm += value * value;
            }
            norm = (float) Math.sqrt(norm);

            for (int j = 0; j < features[i].length; j++) {
                normalizedFeatures[i][j] = features[i][j] / norm;
            }
        }

        return normalizedFeatures;
    }

    public static float[][] cosineSimilarity(float[][] imageFeatures, float[][] textFeatures) {
        float[][] textNormFeatures = normalizeFeatures(textFeatures);
        float[][] imageNormFeatures = normalizeFeatures(imageFeatures);
        float logitScale = (float) exp(4.6052);
        float[][] logitsPerImage = new float[imageNormFeatures.length][textNormFeatures.length];

        for (int i = 0; i < imageNormFeatures.length; i++) {
            for (int j = 0; j < textNormFeatures.length; j++) {
                float dotProduct = 0.0f;
                for (int k = 0; k < imageNormFeatures[i].length; k++) {
                    dotProduct += imageNormFeatures[i][k] * textNormFeatures[j][k];
                }
                logitsPerImage[i][j] = (logitScale * dotProduct);
            }
        }
        return logitsPerImage;
    }

    public static float[] softmax(float[] input) {
        float max = Float.MIN_VALUE;
        for (float value : input) {
            if (value > max) {
                max = value;
            }
        }

        float expSum = 0.0f;
        for (float value : input) {
            expSum += Math.exp(value - max);
        }

        float[] softmaxValues = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            softmaxValues[i] = (float) (Math.exp(input[i] - max) / expSum);
        }

        return softmaxValues;
    }
}
