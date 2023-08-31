package cn.eciot.ble_demo_java.util;

import static cn.eciot.ble_demo_java.RecognitionActivity.toSaveImage;
import static cn.eciot.ble_demo_java.RecognitionActivity.toSaveLables;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.Image;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.eciot.ble_demo_java.RecognitionActivity.Recognition;

public class Protocol {

    static class Product {
        int number;
        int weight;
        int unitPrice;
        int amount;
        Value value;

        public Product(int number, int weight, int unitPrice, int amount, Value value) {
            this.number = number;
            this.weight = weight;
            this.unitPrice = unitPrice;
            this.amount = amount;
            this.value = value;
        }
    }

    static class Key {
        private final int a;
        private final int b;

        public Key(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return a == key.a && b == key.b;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }

    static class Value {
        public final String first;
        public final List<Recognition> second;

        public Value(String first, List<Recognition> second) {
            this.first = first;
            this.second = second;
        }
    }

    static class Packet {
        String timeStr;
        int transactionType;
        int totalAmount;
        int transactionCount;
        List<Product> products;
        int status;
        int checkSum;

        @SuppressLint("SimpleDateFormat")
        public Packet(int transactionType, int totalAmount, int transactionCount, List<Product> products, int status, int checkSum) {
            this.timeStr = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]").format(new Date(System.currentTimeMillis()));
            this.transactionType = transactionType;
            this.totalAmount = totalAmount;
            this.transactionCount = transactionCount;
            this.products = products;
            this.status = status;
            this.checkSum = checkSum;
        }
    }

    static Map<Key, Value> productMap = new HashMap<>();
    static byte[] longByte;

    public static Bitmap parseMessage(byte[] data) {
        // 解析协议头
        if (data.length == 20) {
            // 解析秤重协议
            int weight = byteArrayToInt(data, 9, 11);
            int unitPrice = byteArrayToInt(data, 12, 14);
            int status = data[18];
            //处理信息
            if (status == 1) {
                productMap.put(new Key(weight, unitPrice), new Value(toSaveImage, toSaveLables));
            }
        } else if (data.length == 17) {
            // 解析累计或支付协议
            if (productMap.size() == 0) return null;
            data = concatenateByteArrays(longByte, data);
            longByte = null;
            int transactionType = data[7];
            if (transactionType != 1) return null;
            int totalAmount = byteArrayToInt(data, 8, 10);
            int transactionCount = data[11];
            int offset = 12;

            List<Product> toSaveProducts = new ArrayList<>();
            for (int i = 0; i < transactionCount; i++) {
                int productNumber = data[offset];
                int productWeight = byteArrayToInt(data, offset + 1, offset + 3);
                int productUnitPrice = byteArrayToInt(data, offset + 4, offset + 6);
                int productAmount = byteArrayToInt(data, offset + 7, offset + 9);
                toSaveProducts.add(new Product(productNumber, productWeight, productUnitPrice, productAmount, productMap.get(new Key(productWeight, productUnitPrice))));
                offset += 15;
            }
            int status = data[offset];
            int checksum = data[offset + 1];
            Packet packet = new Packet(transactionType, totalAmount, transactionCount, toSaveProducts, status, checksum);
            Bitmap QrCodeBitmap = Utils.sendPacketToServer(packet);
            productMap.clear();
            return QrCodeBitmap;
        } else if (data.length == 15 || data.length == 12) {
            longByte = concatenateByteArrays(longByte, data);
        }
        return null;
    }

    public static byte[] concatenateByteArrays(byte[] arr1, byte[] arr2) {
        if (arr1 == null) {
            return arr2;
        }
        if (arr2 == null) {
            return arr1;
        }
        byte[] combined = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, combined, 0, arr1.length);
        System.arraycopy(arr2, 0, combined, arr1.length, arr2.length);
        return combined;
    }

    // 将字节数组的一段转换为整数
    public static int byteArrayToInt(byte[] bytes, int startIndex, int endIndex) {
        int value = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }
}
