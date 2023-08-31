package cn.eciot.ble_demo_java.util;


import static cn.eciot.ble_demo_java.RecognitionActivity.imageLablesMyQueue;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.util.Log;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import cn.eciot.ble_demo_java.RecognitionActivity.Recognition;

public class Protocol {

    public static int APPROX_WEIGHT_GRAM = 20;
    public static int QUEUE_TIME_SECOND = 600;

    public static class MyTime {
        String timeStr;
        Long timeLong;

        @SuppressLint("SimpleDateFormat")
        public MyTime() throws ParseException {
            DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
            this.timeStr = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]").format(new Date(System.currentTimeMillis()));
            this.timeLong = dateFormat.parse(this.timeStr).getTime();
        }
    }

    public static class MessageKey extends MyTime {
        private final int weight;
        private final int unitPrice;

        public MessageKey(int weight, int unitPrice) throws ParseException {
            super();
            this.weight = weight;
            this.unitPrice = unitPrice;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageKey messageKey = (MessageKey) o;
            return weight == messageKey.weight && unitPrice == messageKey.unitPrice;
        }

        @Override
        public int hashCode() {
            return Objects.hash(weight, unitPrice);
        }
    }

    public static class ImageLables extends MyTime {
        public final String imgStr;
        public final List<Recognition> lablesList;

        public ImageLables(String imgStr, List<Recognition> lablesList) throws ParseException {
            super();
            this.imgStr = imgStr;
            this.lablesList = lablesList;
        }
    }

    public static class MyQueue<T> extends LinkedList<T> {

        public int seconds;

        public MyQueue(int seconds) {
            this.seconds = seconds;
        }

        public void enqueue(T item) throws ParseException {
            while (this.size() != 0 && (((MyTime) item).timeLong - ((MyTime) getFirst()).timeLong) / 1000 > seconds) {
                removeFirst();
            }
            addLast(item);
        }

        public List<T> timeBetween(Long startTime, Long endTime) {
            if (this.size() == 0)
                return null;
            assert (startTime <= endTime);
            int startIndex = Math.max(IntStream.range(0, this.size()).filter(idx -> ((MyTime) get(idx)).timeLong > startTime).findFirst().orElse(-1) - 1, 0);
            int endIndex = Math.min(IntStream.range(0, this.size()).filter(idx -> ((MyTime) get(idx)).timeLong > endTime).findFirst().orElse(this.size()), this.size() - 1);
            return this.subList(startIndex, endIndex);
        }
    }

    public static class Product {
        int number;
        int weight;
        int unitPrice;
        int amount;
        ImageLables imageLables;

        public Product(int number, int weight, int unitPrice, int amount, ImageLables imageLables) {
            this.number = number;
            this.weight = weight;
            this.unitPrice = unitPrice;
            this.amount = amount;
            this.imageLables = imageLables;
        }
    }

    public static class Packet {
        int transactionType;
        int totalAmount;
        int transactionCount;
        List<Product> products;
        int status;
        int checkSum;

        public Packet(int transactionType, int totalAmount, int transactionCount, List<Product> products, int status, int checkSum) throws ParseException {
            this.transactionType = transactionType;
            this.totalAmount = totalAmount;
            this.transactionCount = transactionCount;
            this.products = products;
            this.status = status;
            this.checkSum = checkSum;
        }
    }

    public static MyQueue<MessageKey> messageKeyMyQueue = new MyQueue<>(QUEUE_TIME_SECOND);
    public static byte[] longByte;

    public static Bitmap parseMessage(byte[] data) throws ParseException {
        Long lastZero = 0L;
        // 解析协议头
        if (data.length == 20) {
            // 解析秤重协议
            int weight = byteArrayToInt(data, 9, 11);
            int unitPrice = byteArrayToInt(data, 12, 14);
            int status = data[18];
            //处理信息
            if (status == 1) {
                messageKeyMyQueue.enqueue(new MessageKey(weight, unitPrice));
                Log.d("messagekey", String.valueOf(weight) + " " + String.valueOf(unitPrice));
            }
            if (weight < 0.2)
                lastZero = new MyTime().timeLong;
        } else if (data.length == 17) {
            // 解析累计或支付协议
            if (messageKeyMyQueue.size() == 0) return null;
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

                int productIndex = IntStream.range(0, messageKeyMyQueue.size()).filter(idx -> messageKeyMyQueue.get(idx).weight == productWeight && messageKeyMyQueue.get(idx).unitPrice == productUnitPrice).findFirst().orElse(-1);
                Long productStartTime = lastZero, productEndTime = new MyTime().timeLong;
                for (int j = productIndex; j >= 0; j--) {
                    int weight = messageKeyMyQueue.get(j).weight;
                    if (Math.abs(weight - productWeight) > APPROX_WEIGHT_GRAM) {
                        productStartTime = messageKeyMyQueue.get(j).timeLong;
                        break;
                    }
                }
                for (int j = productIndex; j < messageKeyMyQueue.size(); j++) {
                    int weight = messageKeyMyQueue.get(j).weight;
                    if (Math.abs(weight - productWeight) > APPROX_WEIGHT_GRAM) {
                        productEndTime = messageKeyMyQueue.get(j).timeLong;
                        break;
                    }
                }
                List<ImageLables> imageLablesList = imageLablesMyQueue.timeBetween(productStartTime, productEndTime);
                //TODO: Decide how to choose the best Image;
                toSaveProducts.add(new Product(productNumber, productWeight, productUnitPrice, productAmount, imageLablesList.get(imageLablesList.size() - 1)));
                offset += 15;
            }
            int status = data[offset];
            int checksum = data[offset + 1];
            Packet packet = new Packet(transactionType, totalAmount, transactionCount, toSaveProducts, status, checksum);
            Bitmap QrCodeBitmap = Utils.sendPacketToServer(packet);
            imageLablesMyQueue.clear();
            messageKeyMyQueue.clear();
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

    public static int byteArrayToInt(byte[] bytes, int startIndex, int endIndex) {
        int value = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }
}
