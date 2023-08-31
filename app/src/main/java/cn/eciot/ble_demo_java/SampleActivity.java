package cn.eciot.ble_demo_java;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import cn.eciot.ble_demo_java.util.Utils;


public class SampleActivity extends AppCompatActivity {

    static List<SampleInfo> sampleListData = new ArrayList<>();
    static Adapter listViewAdapter = null;

    static class SampleInfo {
        String name;
        int num;

        SampleInfo(String name, int num) {
            this.name = name;
            this.num = num;
        }
    }

    static class Adapter extends ArrayAdapter<SampleInfo> {
        private final int myResource;

        public Adapter(@NonNull Context context, int resource, List<SampleInfo> sampleListData) {
            super(context, resource, sampleListData);
            myResource = resource;
        }

        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            SampleInfo sampleInfo = getItem(position);
            String name = "";   // 设备名称
            int num = 0;       // 信号强度
            if (sampleInfo != null) {
                name = sampleInfo.name;
                num = sampleInfo.num;
            }
            // 根据 myResource 指定的布局文件，管理列表项的视图
            @SuppressLint("ViewHolder") View view = LayoutInflater.from(getContext()).inflate(myResource, parent, false);

            ((TextView) view.findViewById(R.id.tv_sample_name)).setText(name);
            ((TextView) view.findViewById(R.id.tv_sample_num)).setText(num + "张");

            view.findViewById(R.id.iv_delete_sample).setOnClickListener((v) -> {
                String deleteName = sampleListData.get(position).name;
                List<String> samples = Utils.readFromFile(this.getContext(), "sample.txt");
                List<String> writeSamples = new ArrayList<>();
                for (String sample : samples) {
                    if (!Objects.equals(sample.split(":")[0], deleteName)) {
                        writeSamples.add(sample);
                    }
                }
                Utils.writeToFile(this.getContext(), "sample.txt", writeSamples, "w");
                int index = IntStream.range(0, sampleListData.size()).filter(i -> sampleListData.get(i).name.equals(deleteName)).findFirst().orElse(-1);
                sampleListData.remove(index);
                listViewAdapter.notifyDataSetChanged();
            });
            return view;
        }
    }

    //设置页面机制，重点是自动刷新和点击连接
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_activity);
        //设置返回识别页面
        ImageView ivRecognize = findViewById(R.id.iv_recognize);
        ivRecognize.setOnClickListener((View view) -> {
            Intent intent = new Intent(SampleActivity.this, RecognitionActivity.class);
            startActivity(intent);
        });
        //设置添加模板页面
        ImageView ivAddSample = findViewById(R.id.iv_add_sample);
        ivAddSample.setOnClickListener((View view) -> {
            Intent intent = new Intent(SampleActivity.this, SampleAddActivity.class);
            startActivity(intent);
        });

        //设置手动刷新后清空数据，从检验权限开始操作
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_layout);
        swipeRefreshLayout.setColorSchemeColors(0x01a4ef);
        swipeRefreshLayout.setEnabled(false);

        //设置deviceListDataShow的数据填充到list_item中为listViewAdapter，listView根据listViewAdapter调整，每个条目点击后连接，并监听状态回调进行操作
        ListView listView = findViewById(R.id.lv_sample_list);
        listViewAdapter = new Adapter(this, R.layout.sample_item, sampleListData);
        listView.setAdapter(listViewAdapter);

        sampleListData.clear();
        List<String> samples = Utils.readFromFile(this, "sample.txt");
        for (String sample : samples) {
            String targetName = sample.split(":")[0];
            if (sampleListData.stream().anyMatch(s -> s.name.equals(targetName))) {
                int index = IntStream.range(0, sampleListData.size()).filter(i -> sampleListData.get(i).name.equals(targetName)).findFirst().orElse(-1);
                sampleListData.get(index).num += 1;
            } else {
                sampleListData.add(new SampleInfo(targetName, 1));
            }
        }
        listViewAdapter.notifyDataSetChanged();
    }
}
