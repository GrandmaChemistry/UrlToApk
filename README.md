# URL to APK 打包工具

使用 GitHub Action 将任意网站 URL 打包成 Android APK 应用。

## 功能特性

- ✅ 自定义网站 URL
- ✅ 自定义应用名称
- ✅ 自定义应用包名
- ✅ 自定义主题色
- ✅ 自定义应用图标
- ✅ 返回键二次确认退出
- ✅ JavaScript Bridge 原生功能调用

## 使用方法

### 1. Fork 本仓库

点击右上角的 Fork 按钮，将本仓库 Fork 到您的 GitHub 账号下。

### 2. 配置签名密钥（可选）

如果需要对 APK 进行签名，请在仓库的 Settings > Secrets and variables > Actions 中添加以下密钥：

- `SIGNING_KEY` - Base64 编码的签名密钥文件
- `KEY_ALIAS` - 密钥别名
- `KEY_STORE_PASSWORD` - 密钥库密码
- `KEY_PASSWORD` - 密钥密码

生成签名密钥的命令：
```bash
# 生成密钥库
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias

# 转换为 Base64
base64 -w 0 release-key.jks > signing_key.txt
```

### 3. 运行 GitHub Action

1. 进入 Actions 页面
2. 选择 "Build Android APK" 工作流
3. 点击 "Run workflow"
4. 填写以下参数：
   - **网站URL**: 要打包的网站地址（例如：https://www.example.com）
   - **应用名称**: 显示在手机上的应用名称
   - **应用包名**: Android 应用的唯一标识（例如：com.example.app）
   - **主题色**: 状态栏和主题颜色（例如：#2196F3）
   - **应用图标**: Base64 编码的 PNG 图片（可选）

### 4. 下载 APK

工作流完成后，您可以：
- 在 Actions 运行结果中下载 Artifacts
- 在 Releases 页面下载发布的 APK

## JavaScript Bridge API

WebView 中的 JavaScript 可以通过 `NativeBridge` 对象调用原生功能：

```javascript
// 等待 NativeBridge 初始化完成
document.addEventListener('NativeBridgeReady', function() {
  console.log('NativeBridge is ready!');
});

// 或者使用回调函数
window.onNativeBridgeReady = function() {
  console.log('NativeBridge is ready!');
};
```

### 可用的 API

#### UI 相关

```javascript
// 显示 Toast 消息
NativeBridge.showToast('Hello World');

// 显示加载框
NativeBridge.showLoading('加载中...');
NativeBridge.hideLoading();

// 显示提示框
NativeBridge.showAlert('标题', '消息内容', function() {
  console.log('用户点击了确定');
});

// 显示确认框
NativeBridge.showConfirm('标题', '确定要执行吗？', function(result) {
  if (result) {
    console.log('用户点击了确定');
  } else {
    console.log('用户点击了取消');
  }
});

// 设置状态栏颜色
NativeBridge.setStatusBarColor('#FF5722');
```

#### 设备功能

```javascript
// 获取设备信息
var deviceInfo = NativeBridge.getDeviceInfo();
console.log(deviceInfo.brand, deviceInfo.model);

// 获取网络类型 (wifi/cellular/none)
var networkType = NativeBridge.getNetworkType();

// 判断是否连接 WiFi
var isWifi = NativeBridge.isWifiConnected();

// 获取电池电量
var battery = NativeBridge.getBatteryLevel();

// 获取应用版本
var version = NativeBridge.getAppVersion();

// 震动
NativeBridge.vibrate(200); // 震动200毫秒

// 播放提示音
NativeBridge.playSound('beep'); // beep/success/error
```

#### 分享与通讯

```javascript
// 分享
NativeBridge.share('标题', '分享内容', 'https://example.com');

// 复制到剪贴板
NativeBridge.copyToClipboard('要复制的文本');

// 打开外部链接
NativeBridge.openUrl('https://www.google.com');

// 拨打电话
NativeBridge.makeCall('10086');

// 发送短信
NativeBridge.sendSMS('10086', '短信内容');
```

#### 本地存储

```javascript
// 存储数据
NativeBridge.setLocalStorage('key', 'value');

// 读取数据
var value = NativeBridge.getLocalStorage('key');

// 删除数据
NativeBridge.removeLocalStorage('key');

// 清空所有数据
NativeBridge.clearLocalStorage();
```

#### 导航控制

```javascript
// 后退
NativeBridge.goBack();

// 刷新页面
NativeBridge.reload();

// 清除缓存
NativeBridge.clearCache();

// 退出应用（会显示确认框）
NativeBridge.exitApp();

// 设置屏幕方向 (portrait/landscape/auto)
NativeBridge.setScreenOrientation('portrait');

// 启用/禁用返回按钮
NativeBridge.enableBackButton(true);
```

## 注意事项

1. 首次运行需要下载 Gradle 和依赖，可能需要几分钟时间
2. 如果没有配置签名密钥，生成的是未签名的 APK
3. 应用图标需要是 PNG 格式，建议尺寸 512x512 像素
4. 包名必须是有效的 Java 包名格式（例如：com.company.app）

## 许可证

MIT License
