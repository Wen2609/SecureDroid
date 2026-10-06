#!/usr/bin/env node
/**
 * ClamAV CVD → 项目兼容签名库转换工具
 * ------------------------------------------------------------
 * 用法: node cvd2clamav.js <daily.cvd> <输出目录>
 *
 * 从 ClamAV 官方数据库(database.clamav.net/daily.cvd 或 main.cvd)解包,
 * 提取全部 .ndb/.ndu 与 .hsb/.hsu 签名,合并去重后输出:
 *   - clamav.ndb  (字节特征,支持查杀)
 *   - clamav.hsb   (整文件 SHA-256 哈希)
 *
 * 生成的 clamav.ndb / clamav.hsb 是真实病毒库(数十万~百万级签名),
 * 接入方式二选一:
 *   1. 放入应用私有目录 files/clamav/ 后重启(ClamAvSignatures 自动加载);
 *   2. 通过应用内「特征库在线更新」(URL + SHA-256)下载到 files/clamav/。
 *
 * 依赖:仅 Node 内置模块(zlib),无需第三方包。
 */
'use strict';

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const [, , cvdPath, outDir] = process.argv;
if (!cvdPath || !outDir) {
  console.error('用法: node cvd2clamav.js <daily.cvd> <输出目录>');
  process.exit(1);
}
fs.mkdirSync(outDir, { recursive: true });

const buf = fs.readFileSync(cvdPath);
if (buf.toString('ascii', 0, 6) !== 'ClamAV') {
  console.error('不是有效的 ClamAV CVD 文件(缺少 "ClamAV" 魔数): ' + cvdPath);
  process.exit(1);
}

// CVD = 512 字节头 + zlib 压缩的 tar 归档
const HEADER = 512;
let payload;
try {
  payload = zlib.inflateSync(buf.subarray(HEADER));
} catch (_) {
  try {
    payload = zlib.inflateRawSync(buf.subarray(HEADER));
  } catch (e) {
    console.error('CVD 解压失败: ' + e.message);
    process.exit(1);
  }
}

// 解析 tar(512 字节块,ustar 或 GNU 格式)
const ndbLines = [];
const hsbLines = [];
let off = 0;
while (off + 512 <= payload.length) {
  const name = payload.subarray(off, off + 100).toString('utf8').replace(/\0.*$/, '');
  if (!name || name.length === 0) break;
  const size = parseInt(
    payload.subarray(off + 124, off + 136).toString('ascii').replace(/\0.*$/, '').trim(),
    8
  ) || 0;
  const dataStart = off + 512;
  const dataEnd = dataStart + size;
  if (dataEnd > payload.length) break;
  const content = payload.subarray(dataStart, dataEnd).toString('utf8');
  const lower = name.toLowerCase();
  if (lower.endsWith('.ndb') || lower.endsWith('.ndu')) {
    for (const line of content.split(/\r?\n/)) {
      const t = line.trim();
      if (t && !t.startsWith('#')) ndbLines.push(t);
    }
  } else if (lower.endsWith('.hsb') || lower.endsWith('.hsu')) {
    for (const line of content.split(/\r?\n/)) {
      const t = line.trim();
      if (t && !t.startsWith('#')) hsbLines.push(t);
    }
  }
  // 前进到下一文件头(数据按 512 对齐)
  off = dataEnd + ((512 - (dataEnd % 512)) % 512);
}

const uniq = (arr) => Array.from(new Set(arr));
const ndb = uniq(ndbLines);
const hsb = uniq(hsbLines);
const ndbOut = path.join(outDir, 'clamav.ndb');
const hsbOut = path.join(outDir, 'clamav.hsb');

fs.writeFileSync(ndbOut, ndb.join('\n') + '\n');
fs.writeFileSync(hsbOut, hsb.join('\n') + '\n');

console.log('CVD 解包完成:');
console.log('  .ndb 字节特征: ' + ndb.length + ' 条 -> ' + ndbOut);
console.log('  .hsb 哈希特征: ' + hsb.length + ' 条 -> ' + hsbOut);
console.log('');
console.log('接入应用(二选一):');
console.log('  1. 把 clamav.ndb / clamav.hsb 放入应用私有目录 files/clamav/ 后重启;');
console.log('  2. 用应用内「特征库在线更新」分别填写文件 URL 与 SHA-256 校验值。');