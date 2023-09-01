package cn.atrudom.veg_classify_java.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;


public class BleService {

    /*
     * 信号触发与响应
     * */
    public interface BluetoothAdapterStateChangeCallback {
        void callback(boolean ok, int errCode, String errMsg);
    }

    public interface BluetoothDeviceFoundCallback {
        void callback(String id, String name, String mac, int rssi);
    }

    public interface BLECharacteristicValueChangeCallback {
        void callback(byte[] bytes);
    }

    public interface BLEConnectionStateChangeCallback {
        void callback(boolean ok, int errCode, String errMsg);
    }

    public static BluetoothAdapterStateChangeCallback bluetoothAdapterStateChangeCallback = (boolean ok, int errCode, String errMsg) -> {
    };
    public static BluetoothDeviceFoundCallback bluetoothDeviceFoundCallback = (String id, String name, String mac, int rssi) -> {
    };
    public static BLEConnectionStateChangeCallback bleConnectionStateChangeCallback = (boolean ok, int errCode, String errMsg) -> {
    };
    public static BLECharacteristicValueChangeCallback bleCharacteristicValueChangeCallback = (byte[] bytes) -> {
    };

    public static void setBluetoothAdapterStateChangeCallback(BluetoothAdapterStateChangeCallback cb) {
        bluetoothAdapterStateChangeCallback = cb;
    }

    public static void setBluetoothDeviceFoundCallback(BluetoothDeviceFoundCallback cb) {
        bluetoothDeviceFoundCallback = cb;
    }

    public static void setBLEConnectionStateChangeCallback(BLEConnectionStateChangeCallback cb) {
        bleConnectionStateChangeCallback = cb;
    }

    public static void setBLECharacteristicValueChangeCallback(BLECharacteristicValueChangeCallback cb) {
        bleCharacteristicValueChangeCallback = cb;
    }
    //--------------------------------------------------------------------------------------------

    /*
     * 检查蓝牙和GPS状态并回调，扫描周围的蓝牙设备，并将设备信息添加到deviceList再回调
     * */
    public static BluetoothAdapter bluetoothAdapter = null;
    public static final List<BluetoothDevice> deviceList = new ArrayList<>();
    public static boolean scanFlag = false;

    public static void openBluetoothAdapter(Context ctx) {
        // 获取默认的蓝牙适配器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 判断设备是否支持蓝牙
        if (bluetoothAdapter == null) {
            bluetoothAdapterStateChangeCallback.callback(false, 10000, "");
            return;
        }
        // 判断蓝牙是否打开
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapterStateChangeCallback.callback(false, 10001, "");
            return;
        }
        // 回调蓝牙适配器状态变化
        bluetoothAdapterStateChangeCallback.callback(true, 0, "");

    }

    public static final BluetoothAdapter.LeScanCallback leScanCallback = (BluetoothDevice bluetoothDevice, int rssi, byte[] bytes) -> {
        try {
            // 获取蓝牙名称和MAC地址
            @SuppressLint("MissingPermission") String name = bluetoothDevice.getName();
            if (name == null || name.equals("")) return;

            String mac = bluetoothDevice.getAddress();
            if (mac == null || mac.equals("")) return;
            mac = mac.replace(":", "");

            // 判断是否已经存在该设备在列表中
            boolean isExist = false;
            for (BluetoothDevice tempDevice : deviceList) {
                if (tempDevice.getAddress().replace(":", "").equals(mac)) {
                    isExist = true;
                    break;
                }
            }
            // 如果不存在，则将设备添加到列表中
            if (!isExist) {
                deviceList.add(bluetoothDevice);
            }

            // 回调发现设备的信息
            bluetoothDeviceFoundCallback.callback(mac, name, mac, rssi);
        } catch (Throwable e) {
            Log.e("LeScanCallback", "Throwable");
        }
    };

    public static void startBluetoothDevicesDiscovery(Context ctx) {
        // 如果正在进行扫描，直接返回
        if (scanFlag) {
            return;
        }
        // 检查蓝牙扫描权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        // 如果蓝牙适配器可用，则开始扫描蓝牙设备
        if (bluetoothAdapter != null) {
            bluetoothAdapter.startLeScan(leScanCallback);
            scanFlag = true;
        }
    }

    public static void stopBluetoothDevicesDiscovery(Context ctx) {
        // 如果没有在扫描中，直接返回
        if (!scanFlag) {
            return;
        }
        // 检查蓝牙扫描权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        // 停止扫描蓝牙设备
        if (bluetoothAdapter != null) {
            bluetoothAdapter.stopLeScan(leScanCallback);
            scanFlag = false;
        }
    }
    //--------------------------------------------------------------------------------------------

    /*
     * 给手机创建BluetoothGatt服务，连接设备如果成功监听特征值变化
     * */
    public static BluetoothGatt bluetoothGatt = null;
    public static final String characteristicWriteUUID = "0000fff2-0000-1000-8000-00805f9b34fb";
    public static final String characteristicNotifyUUID = "0000fff1-0000-1000-8000-00805f9b34fb";
    public static BluetoothGattCharacteristic characteristicWrite;
    public static final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        // 处理设备连接操作的回调和状态变化时的回调
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.e("onConnectionStateChange", "status=" + status + "|" + "newState=" + newState);
            // 当操作失败时，关闭连接并返回失败回调
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.close();
                bleConnectionStateChangeCallback.callback(false, 10000, "onConnectionStateChange:" + status + "|" + newState);
                return;
            }
            // 当连接成功时，执行设备服务发现操作，并返回成功回调
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices(); // 发现服务
                bleConnectionStateChangeCallback.callback(true, 0, ""); // 执行状态变化回调
                return;
            }
            // 当连接断开时，关闭连接并返回失败回调
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close();
                bleConnectionStateChangeCallback.callback(false, 0, "");
            }
        }

        // 发现这个连接的所有服务和特征，找到需要的write和notify，将值写入变量或监听
        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            bluetoothGatt = gatt;
            for (BluetoothGattService service : bluetoothGatt.getServices()) {// 遍历所有蓝牙服务
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) { // 遍历所有蓝牙特征
                    if (characteristic.getUuid().toString().equals(characteristicNotifyUUID)) {
                        boolean res = bluetoothGatt.setCharacteristicNotification(characteristic, true); // 注册通知
                        if (!res) { // 注册失败则直接返回
                            return;
                        }
                        // 配置描述符使能通知功能
                        for (BluetoothGattDescriptor dp : characteristic.getDescriptors()) {
                            dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            bluetoothGatt.writeDescriptor(dp); // 发送命令使能通知功能
                        }// 当读取到的特征的UUID与定义的通知服务UUID一致时，开始监听这个特征的变化
                    }
                    if (characteristic.getUuid().toString().equals(characteristicWriteUUID)) {
                        characteristicWrite = characteristic; // 当读取到的特征的UUID与定义的写入服务UUID一致时，把这个特征赋值给ecCharacteristicWrite变量
                    }
                }
            }
            new Thread(() -> {
                try {
                    Thread.sleep(300);
                    bluetoothGatt.requestMtu(247); // 设置MTU，此处开启一个新线程是为了防止阻塞主线程
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }).start();
        }

        // 当特征值发生变化时将新值回调给程序
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] bytes = characteristic.getValue();
            if (bytes != null) {
                bleCharacteristicValueChangeCallback.callback(bytes);
            }
        }

        // 设置MTU后输出MTU新值
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.e("BLEService", "onMtuChanged success MTU = " + mtu); // MTU设置成功，输出MTU值
            } else {
                Log.e("BLEService", "onMtuChanged fail "); // MTU设置失败
            }
        }

    };

    public static void createBLEConnection(Context ctx, String id) {
        // 判断是否有蓝牙连接的权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                bleConnectionStateChangeCallback.callback(false, 10001, "permission error"); // 没有蓝牙连接权限，回调返回错误信息
                return;
            }
        }
        // 关闭之前的连接，并重新连接指定设备
        if (bluetoothGatt != null) {
            bluetoothGatt.close(); // 先关闭当前连接
        }
        for (BluetoothDevice tempDevice : deviceList) { // 遍历已经扫描到的设备列表，找到需要连接的设备
            if (tempDevice.getAddress().replace(":", "").equals(id)) { // 判断是否为需要连接的设备
                bluetoothGatt = tempDevice.connectGatt(ctx, false, bluetoothGattCallback); // 建立新连接
                return;
            }
        }
        bleConnectionStateChangeCallback.callback(false, 10002, "id error"); // 找不到指定ID的设备，回调返回错误信息
    }

    public static void closeBLEConnection(Context ctx) {

        // 检查蓝牙扫描权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect(); // 断开当前连接
        }
    }
    //--------------------------------------------------------------------------------------------

    public static void writeBLECharacteristicValue(Context ctx, String data) {

        // 检查蓝牙扫描权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        byte[] byteArray = hexStrToBytes(data);
        characteristicWrite.setValue(byteArray);
        characteristicWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        bluetoothGatt.writeCharacteristic(characteristicWrite);
    }

    private static String bytesToHexString(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder str = new StringBuilder();
        for (byte b : bytes) {
            str.append(String.format("%02X", b));
        }
        return str.toString();
    }

    private static byte[] hexStrToBytes(@NonNull String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}
