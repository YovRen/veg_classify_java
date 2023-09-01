# 允探果蔬识别系统java部分分析

## 一、 代码各文件功能说明
+ util文件夹中存储工具
    - BleService提供蓝牙服务
    - Protocol提供蓝牙包协议解析服务
    - Utils提供一些常用的函数工具
+ BleActivity控制蓝牙连接界面
+ RecognitionActivity控制果蔬识别界面
+ SampleActivity控制样例展示界面
+ SampleAddActivity控制样例添加界面

## 二、 代码各文件主要结构
### 1.util文件夹
#### 1） BleService.Java
第一部分是信号的触发与响应，总共包含四组，以其中一组为例讲解。
```java
interface BluetoothAdapterStateChangeCallback errMsg) //callback函数使得cb可以触发信号

BluetoothAdapterStateChangeCallback bluetoothAdapterStateChangeCallback //全局唯一的cb实例

void setBluetoothAdapterStateChangeCallback(BluetoothAdapterStateChangeCallback cb) //在BleActivity中调用，通过改变cb来选择怎么响应信号
```
第二部分检查蓝牙和GPS状态并回调，扫描周围的蓝牙设备，并将设备信息添加到deviceList再回调
```java
BluetoothAdapter bluetoothAdapter //蓝牙适配器，蓝牙操作的基础结构

void openBluetoothAdapter(Context ctx) //创建bluetoothAdapter，检查蓝牙和GPS是否支持且打开，若有问题触发信号

void startBluetoothDevicesDiscovery(Context ctx) //主要工作是调用bluetoothAdapter.startLeScan(leScanCallback)函数开始扫描

void stopBluetoothDevicesDiscovery(Context ctx) //主要工作是调用bluetoothAdapter.stopLeScan(leScanCallback)函数停止扫描

BluetoothAdapter.LeScanCallback leScanCallback //是蓝牙扫描过程中的直接响应函数，它的响应会对结果进行处理后调用bluetoothDeviceFoundCallback函数
```
第三部分是给手机创建BluetoothGatt服务，连接设备如果成功监听特征值变化
```java
BluetoothGatt bluetoothGatt //Gatt服务的基础结构

void createBLEConnection(Context ctx, String id) //主要是调用tempDevice.connectGatt(ctx, false, bluetoothGattCallback);使得蓝牙能连接到设备，无论成功失败都触发信号

BluetoothGattCallback bluetoothGattCallback //是蓝牙连接过程中的直接响应会对结果处理后触发bleConnectionStateChangeCallback信号

void closeBLEConnection() //调用bluetoothGatt.disconnect()断开当前连接

```

#### 2）Protocol.java
第一部分主要是一些类的声明
```java
class MyTime  //为对象记录创造的时间
class MessageKey extends MyTime //记录某个包到达的时间，通过weight和unitPrice唯一化包
class ImageLables extends MyTime //记录所有识别到的图像和标签
class MyQueue<T> extends LinkedList<T> //一个数据结构，只保存某个时长的包
class Product //支付包中的product信息
class Packet //支付包中的全部信息，即为发送到后台的全部信息
```
第二部分是一些全局静态变量声明
```java
int APPROX_WEIGHT_GRAM //描述产品重量的变化尺度
int QUEUE_TIME_SECOND   //记录MyQueue的保存市场
MyQueue<MessageKey> messageKeyMyQueue //记录某场交易中状态为1的包的到达时间
byte[] longByte //长度为314的包的暂存地
```
第三部分是静态函数的声明
```java
Bitmap parseMessage(byte[] data)
//从协议包中截取需要的信息，对不同类型包进行不同的处理：
// 1. 对状态为1的秤重包将其到达的时间记录到messageKeyMyQueue中，
// 2. 对支付包首先为其中的每个product从它在messageKeyMyQueue中位置的周围找到重量变化不大的一组包的到达时间作为该product在秤上的起始时间，据此从imageLablesMyQueue中挑选该product一张合适的照片，将包内所有product的所有信息打包到packet中调用Utils.sendPacketToServer(packet)得到二维码，
// 3. 对长度为314的包可能会有一些碎包保存到longByte中
byte[] concatenateByteArrays(byte[] arr1, byte[] arr2)//将两个byte[]合并起来
int byteArrayToInt(byte[] bytes, int startIndex, int endIndex)//将信号包中的bytes类型转化为int类型
```
#### 3）Utils.java
```java
Bitmap sendPacketToServer(Protocol.Packet packet) //将Packet发送给后台并接受响应二维码

void writeToFile(Context context, String filename, List<String> content, String mode) //将sampleFeature写到文件中

List<String> readFromFile(Context context, String filename) //读取sampleFeature

ByteBuffer loadModelFile(Context context, int modelPath) //从raw中加载模型

float[][] normalizeFeatures(float[][] features) //将某个向量归一化

float[][] cosineSimilarity(float[][] imageFeatures, float[][] textFeatures) //计算两个向量的consine相似度

float[] softmax(float[] input) //softmax函数的实现
```

### 2.BleActivity.java

```java
class DeviceInfo //记录设备信息

class Adapter extends ArrayAdapter<DeviceInfo> //listview可以设置adapter类用来调整其中的内容

List<DeviceInfo> deviceListData //记录搜索到的信息

Adapter listViewAdapter //adapter的实例

void onCreate(Bundle savedInstanceState)

refreshLogo(int i) //因为异步问题，用该函数等待刷新logo

void showAlert(String content, Runnable callback) //提示框

```
### 3.RecognitionActivity.java
```java
 
interface RecognitionListener //识别结果出后被调用，

class Recognition //包含标签和概率

class RecognitionListViewModel extends ViewModel

class RecognitionViewHolder extends RecyclerView.ViewHolder

class RecognitionAdapter extends ListAdapter<Recognition, RecognitionViewHolder> //将识别到的结果设置到界面上

int MAX_RESULT_DISPLAY = 5

int REQUEST_CODE_PERMISSIONS = 999 //权限申请成功时的请求码

String[] REQUIRED_PERMISSIONS = {...} //申请的权限

ExecutorService cameraExecutor  //相机执行器，用来进行相机操作

PreviewView viewFinder //摄像头预览界面

RecognitionListViewModel recogViewModel  

MyQueue<ImageLables> imageLablesMyQueue //用来存储每笔交易过程中识别到的图片和标签

void onCreate(Bundle savedInstanceState) //先设置每个组件的listener和ble在收到信息时的响应，然后开始检查权限等后续操作

boolean allPermissionsGranted() //查看所需权限是否满足，如果满足则打开摄像头

void startCamera() //打开摄像头并且设置图片分析器

class ImageAnalyzer implements ImageAnalysis.Analyzer //图片分析器，在其内部实现了加载模型对每张图片进行预测并对结果进行保存

void showAlert(String content, Runnable callback) //提示框


```

### 4.SampleActivity.java

```java
class SampleInfo //样例信息

class Adapter extends ArrayAdapter<SampleInfo>  //listview可以设置adapter类用来调整其中的内容

List<SampleInfo> sampleListData //记录样例的信息

Adapter listViewAdapter //adapter的实例化

void onCreate(Bundle savedInstanceState) //设置组件的监听器，从文件中读取样例信息并设置到界面上

```

### 5.SampleAddActivity.java

```java
String FILENAME_FORMAT  //日期格式

ImageCapture imageCapture  //相机的操作选择为imageCapture

ByteBuffer modelBuffer; //保存模型

Interpreter imageItp //模型推理器

private PreviewView viewFinder; //摄像头预览界面

void onCreate(@Nullable Bundle savedInstanceState)

void startCamera() //打开相机，设置操作位capture，设置点击发送按钮后将搜集到的信息进行保存

void showAlert(String content, Runnable callback) //提示框
```