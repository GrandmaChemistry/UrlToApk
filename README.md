# URL to APK 打包工具

使用 GitHub Action 将任意网站 URL 打包成 Android APK 应用。

## 功能特性

- ✅ 自定义网站 URL
- ✅ 自定义应用名称
- ✅ 自定义应用包名
- ✅ 自定义主题色
- ✅ 自定义应用图标
- ✅ 自定义首屏加载动画（支持自定义图片或使用应用图标）
- ✅ 自定义签名证书信息
- ✅ 返回键二次确认退出（Toast 提示方式）
- ✅ JavaScript Bridge 原生功能调用
- ✅ 截图与全屏截图功能
- ✅ 保存图片到相册
- ✅ 全屏模式切换
- ✅ 系统设置快捷入口
- ✅ 按键事件监听
- ✅ 应用退出事件监听

## 使用方法

### 1. Fork 本仓库

点击右上角的 Fork 按钮，将本仓库 Fork 到您的 GitHub 账号下。

### 2. 运行 GitHub Action

1. 进入 Actions 页面
2. 选择 "Build Android APK" 工作流
3. 点击 "Run workflow"
4. 填写以下参数：

#### 基本参数
| 参数 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| 网站URL | 要打包的网站地址 | ✅ | - |
| 应用名称 | 显示在手机上的应用名称 | ✅ | My App |
| 应用包名 | Android 应用的唯一标识 | ✅ | com.webview.app |
| 主题色 | 状态栏和主题颜色 | ❌ | #2196F3 |
| 应用图标URL | 公开可访问的 PNG 图片地址 | ❌ | - |

#### 首屏加载动画参数
| 参数 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| 启用首屏图片 | 是否使用自定义首屏图片 | ❌ | false |
| 首屏图片URL | 自定义首屏图片的 URL 地址 | ❌ | - |

> **注意**: 如果未启用首屏图片，将自动使用应用图标作为首屏加载动画，显示圆角图标和应用名称。

#### 证书信息参数
| 参数 | 说明 | 默认值 |
|------|------|--------|
| 证书姓名 (CN) | 证书持有者姓名 | wppApk |
| 证书组织单位 (OU) | 组织单位名称 | hangzhoushiqi |
| 证书组织 (O) | 组织名称 | shiqi |
| 证书城市 (L) | 所在城市 | zhejiang |
| 证书省份 (ST) | 所在省份 | hangzhou |
| 证书国家 (C) | 国家代码 | CN |

### 3. 下载 APK

工作流完成后，您可以：
- 在 Actions 运行结果中下载 Artifacts
- 在 Releases 页面下载发布的 APK

## 首屏加载动画

应用启动时会显示首屏加载动画，提升用户体验：

### 自定义首屏图片模式
- 在 GitHub Action 中启用"启用首屏图片"选项
- 提供首屏图片的 URL 地址
- 图片会在打包时下载并嵌入应用资源
- 支持 PNG 和 JPEG 格式
- WebView 加载完成后显示 1 秒，然后淡出进入主界面

### 默认图标模式
- 如未启用自定义首屏图片，自动使用应用图标
- 图标带有圆角和阴影效果
- 图标下方显示应用名称
- 整体居中显示，简洁大方的设计风格
- WebView 加载完成后淡出进入主界面

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

---

### UI 相关

#### showToast(message)
显示 Toast 消息提示。

```javascript
NativeBridge.showToast('Hello World');
```

#### showLoading(message) / hideLoading()
显示/隐藏加载对话框。

```javascript
// 显示加载框
NativeBridge.showLoading('加载中...');

// 隐藏加载框
NativeBridge.hideLoading();
```

#### showAlert(title, message, callback)
显示提示对话框。

```javascript
NativeBridge.showAlert('提示', '操作成功！', function() {
  console.log('用户点击了确定');
});
```

#### showConfirm(title, message, callback)
显示确认对话框。

```javascript
NativeBridge.showConfirm('确认', '确定要删除吗？', function(result) {
  if (result) {
    console.log('用户点击了确定');
  } else {
    console.log('用户点击了取消');
  }
});
```

#### setStatusBarColor(color)
设置状态栏颜色。

```javascript
NativeBridge.setStatusBarColor('#FF5722');
```

---

### 设备功能

#### getDeviceInfo()
获取基本设备信息。

```javascript
var deviceInfo = NativeBridge.getDeviceInfo();
console.log('品牌:', deviceInfo.brand);
console.log('型号:', deviceInfo.model);
console.log('设备:', deviceInfo.device);
console.log('SDK版本:', deviceInfo.sdkVersion);
console.log('系统版本:', deviceInfo.release);
console.log('制造商:', deviceInfo.manufacturer);
console.log('产品:', deviceInfo.product);
```

#### getExtendedDeviceInfo()
获取扩展设备信息，包含更多详细信息。

```javascript
var info = NativeBridge.getExtendedDeviceInfo();

// 基本设备信息
console.log('品牌:', info.brand);
console.log('型号:', info.model);
console.log('设备:', info.device);
console.log('制造商:', info.manufacturer);
console.log('产品:', info.product);

// 系统版本信息
console.log('SDK版本:', info.sdkVersion);
console.log('系统版本:', info.release);

// 屏幕分辨率
if (info.screen) {
  console.log('屏幕宽度:', info.screen.width);
  console.log('屏幕高度:', info.screen.height);
  console.log('屏幕密度:', info.screen.density);
  console.log('DPI:', info.screen.densityDpi);
}

// 电池状态
if (info.battery) {
  console.log('电池电量:', info.battery.level + '%');
  console.log('是否充电:', info.battery.isCharging);
}

// 网络状态
console.log('网络类型:', info.networkType); // wifi/cellular/none

// 应用信息
console.log('应用版本:', info.appVersion);
console.log('应用版本号:', info.appVersionCode);
console.log('包名:', info.packageName);

// 设备唯一标识符
console.log('设备ID:', info.deviceId);
```

> **注意**: 某些信息获取失败时，对应字段值为 `null`，不会影响其他信息的获取。

#### getNetworkType()
获取当前网络类型。

```javascript
var networkType = NativeBridge.getNetworkType();
// 返回值: 'wifi' | 'cellular' | 'ethernet' | 'none'
console.log('网络类型:', networkType);
```

#### isWifiConnected()
判断是否连接 WiFi。

```javascript
var isWifi = NativeBridge.isWifiConnected();
console.log('WiFi已连接:', isWifi);
```

#### getBatteryLevel()
获取电池电量百分比。

```javascript
var battery = NativeBridge.getBatteryLevel();
console.log('电池电量:', battery + '%');
```

#### getAppVersion()
获取应用版本号。

```javascript
var version = NativeBridge.getAppVersion();
console.log('应用版本:', version);
```

#### vibrate(duration)
触发设备震动。

```javascript
NativeBridge.vibrate(200); // 震动200毫秒
```

#### playSound(soundName)
播放系统提示音。

```javascript
NativeBridge.playSound('beep');    // 普通提示音
NativeBridge.playSound('success'); // 成功提示音
NativeBridge.playSound('error');   // 错误提示音
```

#### getCurrentLocation(callback)
获取当前地理位置。

```javascript
NativeBridge.getCurrentLocation(function(result) {
  if (result.status === 'success') {
    console.log('纬度:', result.latitude);
    console.log('经度:', result.longitude);
    console.log('精度:', result.accuracy);
    console.log('海拔:', result.altitude);
    console.log('速度:', result.speed);
    console.log('时间戳:', result.timestamp);
  } else if (result.status === 'permission_denied') {
    console.log('用户拒绝了位置权限');
  } else {
    console.log('获取位置失败:', result.message);
  }
});
```

#### scanQRCode(callback)
扫描二维码。

```javascript
NativeBridge.scanQRCode(function(result) {
  if (result.status === 'success') {
    console.log('扫描结果:', result.result);
  } else if (result.status === 'cancelled') {
    console.log('用户取消了扫描');
  } else if (result.status === 'permission_denied') {
    console.log('用户拒绝了相机权限');
  } else {
    console.log('扫描失败');
  }
});
```

---

### 截图功能

#### takeScreenshot(callback)
截取当前屏幕并返回 Base64 编码的图片。

```javascript
NativeBridge.takeScreenshot(function(result) {
  if (result.status === 'success') {
    // result.data 是 PNG 格式的 Base64 编码字符串
    console.log('截图成功');
    
    // 显示截图
    var img = document.createElement('img');
    img.src = 'data:image/png;base64,' + result.data;
    document.body.appendChild(img);
    
    // 或者保存到相册
    NativeBridge.saveToGallery(result.data, function(saveResult) {
      console.log('保存结果:', saveResult.message);
    });
  } else {
    console.log('截图失败:', result.message);
  }
});
```

#### takeFullScreenshot(callback)
截取整个 WebView 内容（包括不可见的滚动区域）。

```javascript
NativeBridge.takeFullScreenshot(function(result) {
  if (result.status === 'success') {
    // result.data 是完整页面的 PNG 格式 Base64 编码字符串
    console.log('全屏截图成功');
    
    // 使用截图
    var img = new Image();
    img.src = 'data:image/png;base64,' + result.data;
    img.onload = function() {
      console.log('截图尺寸:', img.width, 'x', img.height);
    };
  } else {
    console.log('全屏截图失败:', result.message);
  }
});
```

#### saveToGallery(base64, callback)
将 Base64 编码的图片保存到相册。

```javascript
// 直接保存 Base64 图片
var base64Image = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
NativeBridge.saveToGallery(base64Image, function(result) {
  if (result.status === 'success') {
    console.log('保存成功:', result.message);
  } else {
    console.log('保存失败:', result.message);
  }
});

// 支持带有 data:image/xxx;base64, 前缀
var dataUrl = 'data:image/png;base64,iVBORw0KGgo...';
NativeBridge.saveToGallery(dataUrl, function(result) {
  console.log(result.message);
});
```

---

### 系统设置

#### openSystemSettings()
打开系统设置页面。

```javascript
NativeBridge.openSystemSettings();
```

#### openAppSettings()
打开当前应用的设置页面。

```javascript
NativeBridge.openAppSettings();
```

---

### 全屏模式

#### enterFullscreen()
进入全屏模式（隐藏状态栏和导航栏）。

```javascript
NativeBridge.enterFullscreen();
```

#### exitFullscreen()
退出全屏模式（显示状态栏和导航栏）。

```javascript
NativeBridge.exitFullscreen();
```

#### isFullscreenMode()
检查当前是否处于全屏模式。

```javascript
var isFullscreen = NativeBridge.isFullscreenMode();
console.log('全屏模式:', isFullscreen);
```

**完整示例：**
```javascript
// 切换全屏状态
function toggleFullscreen() {
  if (NativeBridge.isFullscreenMode()) {
    NativeBridge.exitFullscreen();
    console.log('已退出全屏');
  } else {
    NativeBridge.enterFullscreen();
    console.log('已进入全屏');
  }
}
```

---

### 按键事件监听

#### registerKeyListener(callback)
注册按键事件监听器，监听 Back、Home、任务键等按键。

```javascript
NativeBridge.registerKeyListener(function(event) {
  console.log('按键事件类型:', event.eventType); // 'keydown'
  console.log('按键名称:', event.key);           // 'back', 'home', 'task', 'menu', 'key_xxx'
  console.log('按键代码:', event.keyCode);       // 数字键码
  
  // 处理不同按键
  switch (event.key) {
    case 'back':
      console.log('用户按下了返回键');
      break;
    case 'home':
      console.log('用户按下了 Home 键');
      break;
    case 'task':
      console.log('用户按下了任务键');
      break;
    case 'menu':
      console.log('用户按下了菜单键');
      break;
    default:
      console.log('用户按下了其他按键:', event.key);
  }
});
```

#### unregisterKeyListener()
取消按键事件监听。

```javascript
NativeBridge.unregisterKeyListener();
```

> **注意**: Home 键和任务键的监听可能受系统限制，不保证所有设备都能监听到。

---

### 应用退出事件监听

#### registerExitListener(callback)
注册应用退出事件监听器，在应用即将退出时触发回调。

```javascript
NativeBridge.registerExitListener(function(event) {
  console.log('应用即将退出');
  console.log('退出事件:', event.event);       // 'exit'
  console.log('时间戳:', event.timestamp);     // 退出时的时间戳
  
  // 在这里可以执行清理工作
  // 例如：保存用户数据、发送统计数据等
  saveUserData();
  sendAnalytics('app_exit');
});
```

#### unregisterExitListener()
取消应用退出事件监听。

```javascript
NativeBridge.unregisterExitListener();
```

---

### 分享与通讯

#### share(title, text, url)
调用系统分享功能。

```javascript
NativeBridge.share('分享标题', '分享内容描述', 'https://example.com');
```

#### copyToClipboard(text)
复制文本到剪贴板。

```javascript
NativeBridge.copyToClipboard('要复制的文本');
```

#### openUrl(url)
在外部浏览器中打开链接。

```javascript
NativeBridge.openUrl('https://www.google.com');
```

#### makeCall(phone)
拨打电话。

```javascript
NativeBridge.makeCall('10086');
```

#### sendSMS(phone, message)
发送短信。

```javascript
NativeBridge.sendSMS('10086', '短信内容');
```

---

### 本地存储

#### setLocalStorage(key, value)
存储数据到本地。

```javascript
NativeBridge.setLocalStorage('username', 'john_doe');
NativeBridge.setLocalStorage('settings', JSON.stringify({theme: 'dark', lang: 'zh'}));
```

#### getLocalStorage(key)
读取本地存储的数据。

```javascript
var username = NativeBridge.getLocalStorage('username');
console.log('用户名:', username);

var settings = JSON.parse(NativeBridge.getLocalStorage('settings') || '{}');
console.log('设置:', settings);
```

#### removeLocalStorage(key)
删除指定的本地存储数据。

```javascript
NativeBridge.removeLocalStorage('username');
```

#### clearLocalStorage()
清空所有本地存储数据。

```javascript
NativeBridge.clearLocalStorage();
```

---

### 导航控制

#### goBack()
返回上一页。

```javascript
NativeBridge.goBack();
```

#### reload()
刷新当前页面。

```javascript
NativeBridge.reload();
```

#### clearCache()
清除 WebView 缓存。

```javascript
NativeBridge.clearCache();
```

#### exitApp()
退出应用（会弹出确认对话框）。

```javascript
NativeBridge.exitApp();
```

#### setScreenOrientation(orientation)
设置屏幕方向。

```javascript
NativeBridge.setScreenOrientation('portrait');  // 竖屏
NativeBridge.setScreenOrientation('landscape'); // 横屏
NativeBridge.setScreenOrientation('auto');      // 自动
```

#### enableBackButton(enabled)
启用/禁用返回按钮处理。

```javascript
NativeBridge.enableBackButton(true);  // 启用
NativeBridge.enableBackButton(false); // 禁用
```

---

## 完整示例

```html
<!DOCTYPE html>
<html>
<head>
  <title>NativeBridge Demo</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
  <h1>NativeBridge 功能演示</h1>
  
  <button onclick="showDeviceInfo()">获取设备信息</button>
  <button onclick="takeScreenshotDemo()">截图</button>
  <button onclick="toggleFullscreenDemo()">切换全屏</button>
  <button onclick="getLocationDemo()">获取位置</button>
  
  <div id="output"></div>
  
  <script>
    // 等待 NativeBridge 初始化
    document.addEventListener('NativeBridgeReady', function() {
      NativeBridge.showToast('NativeBridge 已就绪！');
      
      // 注册按键监听
      NativeBridge.registerKeyListener(function(event) {
        console.log('按键:', event.key);
      });
      
      // 注册退出监听
      NativeBridge.registerExitListener(function(event) {
        console.log('应用即将退出');
      });
    });
    
    function showDeviceInfo() {
      var info = NativeBridge.getExtendedDeviceInfo();
      document.getElementById('output').innerHTML = '<pre>' + JSON.stringify(info, null, 2) + '</pre>';
    }
    
    function takeScreenshotDemo() {
      NativeBridge.takeScreenshot(function(result) {
        if (result.status === 'success') {
          var img = document.createElement('img');
          img.src = 'data:image/png;base64,' + result.data;
          img.style.maxWidth = '100%';
          document.getElementById('output').appendChild(img);
        } else {
          NativeBridge.showToast('截图失败: ' + result.message);
        }
      });
    }
    
    function toggleFullscreenDemo() {
      if (NativeBridge.isFullscreenMode()) {
        NativeBridge.exitFullscreen();
        NativeBridge.showToast('已退出全屏');
      } else {
        NativeBridge.enterFullscreen();
        NativeBridge.showToast('已进入全屏');
      }
    }
    
    function getLocationDemo() {
      NativeBridge.showLoading('正在获取位置...');
      NativeBridge.getCurrentLocation(function(result) {
        NativeBridge.hideLoading();
        if (result.status === 'success') {
          document.getElementById('output').innerHTML = 
            '纬度: ' + result.latitude + '<br>' +
            '经度: ' + result.longitude + '<br>' +
            '精度: ' + result.accuracy + '米';
        } else {
          NativeBridge.showToast('获取位置失败: ' + result.message);
        }
      });
    }
  </script>
</body>
</html>
```

## 注意事项

1. 首次运行需要下载 Gradle 和依赖，可能需要几分钟时间
2. 应用图标需要是 PNG 格式，建议尺寸 512x512 像素
3. 首屏图片支持 PNG 和 JPEG 格式，建议尺寸匹配目标设备屏幕
4. 包名必须是有效的 Java 包名格式（例如：com.company.app）
5. 某些 API（如位置、相机）需要用户授权权限
6. Home 键和任务键的监听可能受 Android 系统限制

## 安装说明

生成的 APK 已经过签名，可以直接安装到 Android 设备上：

1. 将 APK 文件传输到 Android 设备
2. 在设备上打开 APK 文件进行安装
3. 如果提示"未知来源"，请在设置中允许安装未知来源应用

## 许可证

MIT License
