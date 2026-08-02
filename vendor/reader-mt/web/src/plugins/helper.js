// import { Message } from "element-ui";
import { getCache } from "../plugins/cache";

export const formatSize = function(value, scale) {
  if (value == null || value == "") {
    return "0 Bytes";
  }
  var unitArr = new Array(
    "Bytes",
    "KB",
    "MB",
    "GB",
    "TB",
    "PB",
    "EB",
    "ZB",
    "YB"
  );
  var index = 0;
  index = Math.floor(Math.log(value) / Math.log(1024));
  var size = value / Math.pow(1024, index);
  size = size.toFixed(scale || 2);
  return size + " " + unitArr[index];
};

export const LimitResquest = function(limit, process) {
  let currentSum = 0;
  let requests = [];

  async function run() {
    let err, result;
    try {
      ++currentSum;
      handler.leftCount = requests.length;
      const fn = requests.shift();
      result = await fn();
    } catch (error) {
      err = error;
      // console.log("Error", err);
      handler.errorCount++;
    } finally {
      --currentSum;
      handler.requestCount++;
      handler.leftCount = requests.length;
      process && process(handler, result, err);
      if (requests.length > 0) {
        run();
      }
    }
  }

  const handler = reqFn => {
    if (!reqFn || !(reqFn instanceof Function)) {
      return;
    }
    requests.push(reqFn);
    handler.leftCount = requests.length;
    if (currentSum < limit) {
      run();
    }
  };

  handler.requestCount = 0;
  handler.leftCount = 0;
  handler.errorCount = 0;
  handler.cancel = () => {
    requests = [];
  };
  handler.isEnd = () => {
    return !handler.leftCount && !currentSum;
  };

  return handler;
};

export const networkFirstRequest = async function(requestFunc, cacheKey) {
  cacheKey = "localCache@" + cacheKey;
  const res = await requestFunc().catch(() => {
    // 请求出错，使用缓存
    // 使用新的异步存储
    return window.$cacheStorage
      .getItem(cacheKey)
      .then(cacheResponse => {
        if (cacheResponse) {
          return { data: cacheResponse };
        }
      })
      .catch(err => {
        // 兼容旧逻辑
        const cacheResponse = getCache(cacheKey);
        if (cacheResponse) {
          return { data: cacheResponse };
        }
        throw err;
      });
  });
  if (res.data && res.data.isSuccess) {
    // 使用新的异步存储
    window.$cacheStorage.setItem(cacheKey, res.data).catch(() => {});
  }
  return res;
};

export const cacheFirstRequest = async function(
  requestFunc,
  cacheKey,
  validateCache
) {
  cacheKey = "localCache@" + cacheKey;
  // validateCache === true 时，直接刷新缓存
  if (validateCache !== true) {
    let cacheResponse = await window.$cacheStorage
      .getItem(cacheKey)
      .then(cacheResponse => {
        if (cacheResponse) {
          return cacheResponse;
        }
        // console.log("Cache not found in new cache");
        throw new Error("Cache not found");
      })
      .catch(() => {
        // 兼容旧逻辑
        const cacheResponse = getCache(cacheKey);
        return cacheResponse;
      });
    if (cacheResponse) {
      if (!validateCache || (validateCache && validateCache(cacheResponse))) {
        return { data: cacheResponse };
      }
    }
  }
  const res = await requestFunc();
  if (res.data && res.data.isSuccess) {
    // 使用新的异步存储
    window.$cacheStorage.setItem(cacheKey, res.data).catch(() => {});
  }
  return res;
};

export const isMiniInterface = () => window.innerWidth <= 750;

export const editDistance = function(strA, strB) {
  // Levenshtein Edit Distance
  if (strA === strB) {
    return 1.0;
  }
  if (!strA || !strB) {
    return 0.0;
  }
  const arr = new Array(strA.length + 1);
  for (let i1 = 0; i1 <= strA.length; i1++) {
    arr[i1] = new Array(strB.length + 1);
  }
  for (let i1 = 0; i1 <= strA.length; i1++) {
    for (let i2 = 0; i2 <= strB.length; i2++) {
      if (i1 === 0) {
        arr[0][i2] = i2;
      } else if (i2 === 0) {
        arr[i1][0] = i1;
      } else if (strA.charAt(i1 - 1) === strB.charAt(i2 - 1)) {
        arr[i1][i2] = arr[i1 - 1][i2 - 1];
      } else {
        arr[i1][i2] =
          1 +
          Math.min(
            arr[i1 - 1][i2 - 1],
            Math.min(arr[i1][i2 - 1], arr[i1 - 1][i2])
          );
      }
    }
  }
  return 1 - arr[strA.length][strB.length] / Math.max(strA.length, strB.length);
};

export const loadFont = function(fontName, fontUrl) {
  window.customFonts = window.customFonts || {};
  if (
    !window.customFonts[fontName] ||
    window.customFonts[fontName] !== fontUrl
  ) {
    // 动态插入CSS
    const style = document.createElement("style");
    style.textContent = `
    @font-face {
      font-family: "${fontName}";
      src: url("${fontUrl}");
    }`;
    style.id = "custom-font-" + fontName;
    document.head.appendChild(style);
    window.customFonts[fontName] = fontUrl;
  }
};

export const removeFont = function(fontName) {
  window.customFonts = window.customFonts || {};
  delete window.customFonts[fontName];
  const nodeList = document.querySelectorAll("#custom-font-" + fontName);
  for (let i = 0; i < nodeList.length; i++) {
    const node = nodeList[i];
    node.remove();
  }
};

// 把书源校验的原始错误信息归类成简短中文失效类型,便于在"错误信息"列一眼区分
export const classifyBookSourceError = function(rawMsg) {
  const msg = String(rawMsg || "");
  if (!msg) return "未知错误";
  if (/无搜索结果/.test(msg)) return "无搜索结果";
  if (/timeout|超时/i.test(msg)) return "请求超时";
  if (/Network Error/i.test(msg)) return "网络连接失败";
  if (/UnknownHostException|Unknown host|Name does not resolve|Failed to resolve/i.test(msg))
    return "域名解析失败";
  if (/SSLHandshakeException|SSL|certificate/i.test(msg)) return "SSL握手失败";
  if (/Connection reset|SocketException|stream reset|StreamReset/i.test(msg))
    return "连接被重置";
  if (/EOFException/i.test(msg)) return "连接异常断开";
  if (/ConnectException|Failed to connect/i.test(msg)) return "连接失败";
  if (/ProtocolException/i.test(msg)) return "协议错误";
  if (/UnsupportedEncoding|JsonSyntax|ParseException|解析失败/i.test(msg))
    return "解析失败";
  const code = msg.match(/responseCode:\s*(\d+)/);
  if (code) {
    const c = code[1];
    if (c === "403") return "403 禁止访问";
    if (c === "404") return "404 未找到";
    if (c[0] === "5") return "服务器错误 " + c;
    return "HTTP " + c;
  }
  return "其它错误";
};
