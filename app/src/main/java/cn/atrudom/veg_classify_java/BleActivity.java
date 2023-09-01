package cn.atrudom.veg_classify_java;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


import cn.atrudom.veg_classify_java.util.BleService;

public class BleActivity extends AppCompatActivity {
    public static class DeviceInfo {
        public String id;
        public String name;
        String mac;
        public int rssi;

        public DeviceInfo(String id, String name, String mac, int rssi) {
            this.id = id;
            this.name = name;
            this.mac = mac;
            this.rssi = rssi;
        }
    }

    static class Adapter extends ArrayAdapter<DeviceInfo> {
        private final int myResource;

        public Adapter(@NonNull Context context, int resource, List<DeviceInfo> deviceListData) {
            super(context, resource, deviceListData);
            myResource = resource;
        }

        @SuppressLint("DefaultLocale")
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            // 获取在 position 位置上对应的 DeviceInfo 对象
            DeviceInfo deviceInfo = getItem(position);
            String name = "";   // 设备名称
            int rssi = 0;       // 信号强度
            if (deviceInfo != null) {
                name = deviceInfo.name;
                rssi = deviceInfo.rssi;
            }
            // 根据 myResource 指定的布局文件，管理列表项的视图
            @SuppressLint("ViewHolder") View view = LayoutInflater.from(getContext()).inflate(myResource, parent, false);

            // 根据设备名称设置类型图标
            ImageView headImg = view.findViewById(R.id.iv_type);
            if (name == null || name.equals("")) {
                headImg.setImageResource(R.drawable.ble);
            } else if ((name.startsWith("@") && (name.length() == 11)) || (name.startsWith("BT_") && (name.length() == 15))) {
                headImg.setImageResource(R.drawable.ecble);
            } else {
                headImg.setImageResource(R.drawable.ble);
            }

            // 设置设备名称和信号强度值
            ((TextView) view.findViewById(R.id.tv_name)).setText(name);
            ((TextView) view.findViewById(R.id.tv_rssi)).setText(String.format("%d", rssi));

            // 根据信号强度设置信号图标
            ImageView rssiImg = view.findViewById(R.id.iv_rssi);
            if (rssi >= -41) rssiImg.setImageResource(R.drawable.s5);
            else if (rssi >= -55) rssiImg.setImageResource(R.drawable.s4);
            else if (rssi >= -65) rssiImg.setImageResource(R.drawable.s3);
            else if (rssi >= -75) rssiImg.setImageResource(R.drawable.s2);
            else rssiImg.setImageResource(R.drawable.s1);

            return view;
        }
    }

    public static List<DeviceInfo> deviceListData = new ArrayList<>();
    public static int connectedPos = -1;
    public static int connectedFlag = -1;
    Adapter listViewAdapter = null;

    //设置页面机制，重点是自动刷新和点击连接
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_activity);
        //设置手动刷新后清空数据，从检验权限开始操作
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_layout);
        swipeRefreshLayout.setColorSchemeColors(0x01a4ef);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            new Handler().postDelayed(() -> {
                swipeRefreshLayout.setRefreshing(false);
                listViewAdapter.notifyDataSetChanged();
                refreshLogo(-1);
            }, 1000);
        });

        //设置返回识别页面
        findViewById(R.id.iv_recognize).setOnClickListener((View view) -> {
            Intent intent = new Intent(BleActivity.this, RecognitionActivity.class);
            startActivity(intent);
        });

        //设置deviceListData的数据填充到list_item中为listViewAdapter，listView根据listViewAdapter调整，每个条目点击后连接，并监听状态回调进行操作
        ListView listView = findViewById(R.id.list_view);
        listViewAdapter = new Adapter(this, R.layout.ble_item, deviceListData);
        listView.setAdapter(listViewAdapter);
        listViewAdapter.notifyDataSetChanged();
        refreshLogo(-1);

        listView.setOnItemClickListener((AdapterView<?> adapterView, View view, int i, long l) -> {
            DeviceInfo deviceInfo = (DeviceInfo) listView.getItemAtPosition(i);
            if (connectedPos != -1) {
                ((ImageView) listView.getChildAt(connectedPos).findViewById(R.id.iv_type)).setImageResource(R.drawable.ble);
            }
            connectedFlag = -1;
            connectedPos = -1;
            BleService.createBLEConnection(this, deviceInfo.id);
            refreshLogo(i);
        });

        BleService.setBLEConnectionStateChangeCallback((boolean ok, int errCode, String errMsg) -> runOnUiThread(() -> {
            if (ok) {
                showAlert("蓝牙连接成功", () -> {
                });
                connectedFlag = 1;
            } else {
                showAlert("蓝牙连接失败", () -> {
                });
                connectedFlag = 0;
                if (connectedPos != -1) {
                    ((ImageView) listView.getChildAt(connectedPos).findViewById(R.id.iv_type)).setImageResource(R.drawable.ble);
                }
            }
        }));
        BleService.setBluetoothAdapterStateChangeCallback((boolean ok, int errCode, String errMsg) -> runOnUiThread(() -> {
            if (!ok) {
                if (errCode == 1000) {
                    showAlert("此设备不支持蓝牙", () -> {
                    });
                }
                if (errCode == 10001) {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    showAlert("请打开蓝牙开关", () -> {
                    });
                }
            } else {
                BleService.startBluetoothDevicesDiscovery(this);
            }
        }));
        BleService.setBluetoothDeviceFoundCallback((String id, String name, String mac, int rssi) -> runOnUiThread(() -> {
            boolean isExist = false;
            for (BleActivity.DeviceInfo tempDevice : deviceListData) {
                if (tempDevice.id.equals(id)) {
                    tempDevice.rssi = rssi;
                    tempDevice.name = name;
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                deviceListData.add(new BleActivity.DeviceInfo(id, name, mac, rssi));
            }
        }));

        BleService.openBluetoothAdapter(this);
    }


    void refreshLogo(int i) {
        new Handler().postDelayed(() -> {
            if (i == -1) {
                if (connectedPos == -1 || connectedFlag != 1) {
                    return;
                }
                if (((ListView) findViewById(R.id.list_view)).getChildAt(connectedPos) != null) {
                    ((ImageView) ((ListView) findViewById(R.id.list_view)).getChildAt(connectedPos).findViewById(R.id.iv_type)).setImageResource(R.drawable.ecble);
                    return;
                }
                refreshLogo(i);
            } else {
                if (connectedPos != -1 || connectedFlag == 0) {
                    return;
                }
                if (connectedFlag == 1) {
                    ((ImageView) ((ListView) findViewById(R.id.list_view)).getChildAt(i).findViewById(R.id.iv_type)).setImageResource(R.drawable.ecble);
                    connectedPos = i;
                    return;
                }
                refreshLogo(i);
            }
        }, 100);
    }

    void showAlert(String content, Runnable callback) {
        new AlertDialog.Builder(this).setTitle("提示").setMessage(content).setPositiveButton("OK", (dialogInterface, i) -> new Thread(callback).start()).setCancelable(false).create().show();
    }
}
