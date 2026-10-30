/* DiziBOX TV for Nuvio v1.0.1
MIT License

Copyright (c) 2026 DiziBOX TV Nuvio provider contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.



*/
(function () {
var externalRequire = typeof require === "function" ? require : null;
var modules = {
"utils": function(module, exports, require) {
'use strict';

function entities(value) {
  return String(value || '').replace(/&#(x[\da-f]+|\d+);|&(amp|quot|apos|lt|gt|nbsp);/gi, function (all, number, named) {
    if (number) return String.fromCharCode(number.charAt(0).toLowerCase() === 'x' ? parseInt(number.slice(1), 16) : parseInt(number, 10));
    return { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ' }[named.toLowerCase()];
  });
}

function text(value) {
  return entities(String(value || '').replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
}

function attr(tag, name) {
  var re = new RegExp('(?:^|\\s)' + name + '\\s*=\\s*(?:"([^\"]*)"|\x27([^\x27]*)\x27|([^\\s>]+))', 'i');
  var match = re.exec(tag || '');
  return match ? entities(match[1] !== undefined ? match[1] : match[2] !== undefined ? match[2] : match[3]) : '';
}

function origin(url) {
  var match = /^(https?:\/\/[^/?#]+)/i.exec(url || '');
  return match ? match[1] : '';
}

function absolute(value, base) {
  value = entities(value).trim();
  if (/^https?:\/\//i.test(value)) return value;
  if (/^\/\//.test(value)) return (String(base).split(':')[0] || 'https') + ':' + value;
  if (!value || /^[\w+.-]+:/.test(value)) return '';
  var root = origin(base);
  if (!root) return '';
  var path = String(base).slice(root.length).split(/[?#]/)[0] || '/';
  if (value.charAt(0) === '?') return root + path + value;
  if (value.charAt(0) === '#') return '';
  path = value.charAt(0) === '/' ? value : path.slice(0, path.lastIndexOf('/') + 1) + value;
  var suffix = /[?#].*$/.exec(path);
  var parts = path.split(/[?#]/)[0].split('/');
  var clean = [];
  parts.forEach(function (part) {
    if (part === '..') clean.pop();
    else if (part && part !== '.') clean.push(part);
  });
  return root + '/' + clean.join('/') + (clean.length && /\/$/.test(path.split(/[?#]/)[0]) ? '/' : '') + (suffix ? suffix[0] : '');
}

function siteUrl(value) {
  return /^https:\/\/[a-z0-9.-]+(?::\d+)?\/?$/i.test(String(value || '')) ? String(value).replace(/\/+$/, '') : '';
}

function sameSite(url, base) {
  return origin(url).toLowerCase() === origin(base).toLowerCase();
}

function normalize(value) {
  return text(value).replace(/[ıİ]/g, 'i').replace(/[şŞ]/g, 's').replace(/[ğĞ]/g, 'g').replace(/[çÇ]/g, 'c').replace(/[öÖ]/g, 'o').replace(/[üÜ]/g, 'u').toLowerCase().replace(/\u0307/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
}

function classText(html, className) {
  var re = /<([a-z][\w:-]*)\b([^>]*)>([\s\S]*?)<\/\1\s*>/gi;
  var match;
  // Search only opening tags carrying this class, so outer containers do not swallow descendants.
  var opening = new RegExp('<([a-z][\\w:-]*)\\b([^>]*\\bclass\\s*=\\s*(?:"[^"]*\\b' + className + '\\b[^"]*"|\x27[^\x27]*\\b' + className + '\\b[^\x27]*\x27)[^>]*)>', 'gi');
  match = opening.exec(html);
  if (!match) return '';
  re = new RegExp('^([\\s\\S]*?)<\\/' + match[1] + '\\s*>', 'i');
  var body = re.exec(html.slice(opening.lastIndex));
  return body ? text(body[1]) : '';
}

module.exports = { entities: entities, text: text, attr: attr, origin: origin, absolute: absolute, siteUrl: siteUrl, sameSite: sameSite, normalize: normalize, classText: classText };

},
"http": function(module, exports, require) {
'use strict';
var u = require("utils");
var USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
var MAX_BYTES = 1024 * 1024;

function safeError(message, blocked, transient, cancelled) {
  var error = new Error(message);
  error.providerSafe = true;
  error.siteBlocked = !!blocked;
  error.providerTransient = !!transient;
  error.providerCancelled = !!cancelled;
  return error;
}

// Only request state is retained. Native TV redirects do not safely scope a
// manually supplied Cookie header, so this provider never copies site cookies.
function session() { return { deadline: Date.now() + 45000, blocked: {}, pending: [], cancelled: false }; }

// The native TV fetch bridge supports AbortController even when the guest has
// no timers. Stop unused alternatives once the caller has a verified source.
function cancel(state) {
  if (!state || state.cancelled) return;
  state.cancelled = true;
  var pending = (state.pending || []).slice();
  state.pending = [];
  pending.forEach(function (abort) { abort(); });
}

function byteLength(value) {
  var bytes = 0;
  for (var i = 0; i < value.length && bytes <= MAX_BYTES; i++) {
    var ch = value.charCodeAt(i);
    if (ch < 128) bytes++;
    else if (ch < 2048) bytes += 2;
    else if (ch >= 55296 && ch <= 56319 && i + 1 < value.length && value.charCodeAt(i + 1) >= 56320 && value.charCodeAt(i + 1) <= 57343) { bytes += 4; i++; }
    else bytes += 3;
  }
  return bytes;
}

function readBody(response, options) {
  if (options.binary) {
    if (typeof response.arrayBuffer !== 'function' || typeof Uint8Array === 'undefined') return Promise.reject(safeError('Runtime binary responses unavailable'));
    return response.arrayBuffer().then(function (buffer) {
      if (buffer.byteLength > 4096) throw safeError('Media verification response exceeds size limit');
      var bytes = new Uint8Array(buffer), data = [];
      for (var i = 0; i < bytes.length; i++) data.push(bytes[i]);
      return { body: '', bytes: bytes.length, data: data };
    });
  }
  return response.text().then(function (body) { return { body: body, bytes: byteLength(body) }; });
}

function request(url, options) {
  options = options || {};
  if (!/^https?:\/\/[^\s/?#@]+(?:[/?#][^\s]*)?$/i.test(url)) return Promise.reject(safeError('Unsupported request URL'));
  var site = u.origin(url);
  var state = options.session || session();
  var method = String(options.method || (options.body !== undefined ? 'POST' : 'GET')).toUpperCase();
  var timeout = Math.min(Math.max(Number(options.timeout) || 12000, 1), 12000);
  var headers = {
    'User-Agent': USER_AGENT,
    Accept: options.json ? 'application/json, text/javascript, */*; q=0.01' : 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en;q=0.8'
  };
  if (options.referer) headers.Referer = options.referer;
  if (options.origin) headers.Origin = options.origin;
  if (options.search) headers['X-Requested-With'] = 'fetch';
  if (options.bearer) headers.Authorization = 'Bearer ' + options.bearer;
  if (options.body !== undefined) headers['Content-Type'] = 'application/x-www-form-urlencoded';
  Object.keys(options.headers || {}).forEach(function (name) {
    if (/^(?:cookie|host|content-length|connection|proxy-authorization)$/i.test(name)) return;
    headers[name] = String(options.headers[name]);
  });

  function blocked(message) {
    state.blocked[site] = message;
    return safeError(message, true);
  }

  function attempt(number) {
    if (state.cancelled) return Promise.reject(safeError('Lookup cancelled: ' + site, false, false, true));
    if (state.blocked[site]) return Promise.reject(safeError(state.blocked[site], true));
    var remaining = state.deadline - Date.now();
    if (remaining <= 0) return Promise.reject(safeError('Lookup time budget exhausted: ' + site));
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = null;
    var finished = false;
    return new Promise(function (resolve, reject) {
      function cancelPending() {
        finish(safeError('Lookup cancelled: ' + site, false, false, true));
        if (controller) controller.abort();
      }
      function finish(error, value) {
        if (finished) return;
        finished = true;
        if (timer !== null && typeof clearTimeout === 'function') clearTimeout(timer);
        var pendingIndex = state.pending.indexOf(cancelPending);
        if (pendingIndex !== -1) state.pending.splice(pendingIndex, 1);
        if (error) reject(error); else resolve(value);
      }
      if (!state.pending) state.pending = [];
      state.pending.push(cancelPending);
      if (typeof setTimeout === 'function') {
        timer = setTimeout(function () {
          finish(safeError('Request timed out: ' + site, false, true));
          if (controller) controller.abort();
        }, Math.min(timeout, remaining));
      }
      var init = { method: method, headers: headers, credentials: 'same-origin' };
      if (options.body !== undefined) init.body = options.body;
      if (controller) init.signal = controller.signal;
      Promise.resolve().then(function () {
        if (finished) return null;
        return fetch(url, init);
      }).then(function (response) {
        if (finished) return;
        var mitigation = response.headers && typeof response.headers.get === 'function' ? response.headers.get('cf-mitigated') : '';
        if (response.status === 403 || response.status === 429 || response.status === 451) throw blocked('Site access blocked (HTTP ' + response.status + '): ' + site);
        if (mitigation === 'challenge') throw blocked('Cloudflare challenge: ' + site);
        if (!response.status) throw safeError('Network request failed: ' + site, false, true);
        return readBody(response, options).then(function (result) {
          if (finished) return;
          var body = result.body;
          var receivedBytes = result.bytes;
          // The native bridge can truncate exactly at its byte quota without a
          // separate truncation flag. A quota-sized body cannot be trusted.
          if (receivedBytes >= MAX_BYTES) throw safeError('Response reaches TV size limit: ' + site);
          if (/<title[^>]*>\s*(?:Just a moment|Attention Required)/i.test(body) || /cf-browser-verification|class=["']cf-turnstile/i.test(body)) throw blocked('Cloudflare challenge: ' + site);
          if (!response.ok) throw safeError('HTTP ' + response.status + ': ' + site, false, [502, 503, 504].indexOf(response.status) !== -1);
          var value = body;
          if (options.json) {
            try { value = JSON.parse(body); } catch (ignored) { throw safeError('Invalid JSON response: ' + site); }
          }
          if (options.responseInfo) value = {
            body: value,
            status: response.status,
            finalUrl: response.url || url,
            contentType: response.headers && typeof response.headers.get === 'function' ? response.headers.get('content-type') || '' : '',
            contentLength: response.headers && typeof response.headers.get === 'function' ? Number(response.headers.get('content-length')) || 0 : 0,
            contentRange: response.headers && typeof response.headers.get === 'function' ? response.headers.get('content-range') || '' : '',
            receivedBytes: receivedBytes,
            data: result.data || []
          };
          finish(null, value);
        });
      }).catch(function (error) {
        finish(error && error.providerSafe ? error : safeError('Network request failed: ' + site, false, true));
      });
    }).catch(function (error) {
      // One GET retry for a transport failure or a temporary upstream outage.
      // Never retry access restrictions, verification pages, or malformed data.
      if (!state.cancelled && method === 'GET' && number === 0 && error.providerTransient && state.deadline - Date.now() >= timeout) return attempt(1);
      throw error;
    });
  }
  return attempt(0);
}

module.exports = { request: request, USER_AGENT: USER_AGENT, session: session, cancel: cancel };

},
"media": function(module, exports, require) {
'use strict';
var u = require("utils");

function codecName(value) {
  if (/^avc[13]/i.test(value)) return 'H.264';
  if (/^(?:hvc1|hev1|hevc)/i.test(value)) return 'HEVC';
  if (/^(?:vp09|vp9)/i.test(value)) return 'VP9';
  if (/^av01/i.test(value)) return 'AV1';
  if (/^mp4a/i.test(value)) return 'AAC';
  if (/^opus/i.test(value)) return 'Opus';
  if (/^ac-3/i.test(value)) return 'AC-3';
  if (/^ec-3/i.test(value)) return 'E-AC-3';
  if (/^flac/i.test(value)) return 'FLAC';
  return '';
}
function codecs(values) {
  var found = [];
  values.forEach(function (value) {
    String(value || '').split(',').forEach(function (item) {
      var name = codecName(item.trim());
      if (name && found.indexOf(name) < 0) found.push(name);
    });
  });
  return found.join(' + ');
}
function dash(body) {
  if (typeof body !== 'string' || body.length > 1024 * 1024 || !/<(?:[\w.-]+:)?MPD\b/i.test(body)) return null;
  body = body.replace(/<!--[\s\S]*?-->/g, '');
  if (!/<(?:[\w.-]+:)?Period\b/i.test(body) || !/<\/(?:[\w.-]+:)?MPD\s*>\s*$/i.test(body)) return null;
  // This provider only exposes ordinary clear streams, not protected manifests.
  if (/<(?:[\w.-]+:)?ContentProtection\b|default_KID|<(?:[\w.-]+:)?pssh\b/i.test(body)) return null;
  var tags = /<(\/?)(?:[\w.-]+:)?(Representation|AdaptationSet)\b([^>]*)>/gi, match;
  var height = 0, width = 0, values = [], representations = 0, parent = {};
  while ((match = tags.exec(body))) {
    var adaptation = match[2].toLowerCase() === 'adaptationset';
    if (match[1]) { if (adaptation) parent = {}; continue; }
    var h = Number(u.attr(match[3], 'height')) || Number(u.attr(match[3], 'maxHeight')) || 0;
    var w = Number(u.attr(match[3], 'width')) || Number(u.attr(match[3], 'maxWidth')) || 0;
    var codec = u.attr(match[3], 'codecs');
    if (adaptation) { parent = { height: h, width: w, codec: codec }; continue; }
    representations++;
    h = h || parent.height || 0; w = w || parent.width || 0;
    if (h > height) { height = h; width = w; }
    values.push(codec || parent.codec);
  }
  if (!representations || !height || height > 8640 || width > 15360) return null;
  return { quality: height + 'p', codecSummary: codecs(values), height: height, width: width };
}
function hls(body) {
  if (typeof body !== 'string' || body.length > 1024 * 1024 || !/^\s*#EXTM3U/.test(body)) return null;
  // AES-128 HLS can be played by the native player. DRM key formats cannot.
  var keys = /#EXT-X-(?:SESSION-)?KEY:([^\r\n]*)/gi, key;
  while ((key = keys.exec(body))) {
    if (/\bMETHOD=SAMPLE-AES(?:-CTR)?\b/i.test(key[1])) return null;
    var format = /\bKEYFORMAT=(?:"([^"]+)"|'([^']+)'|([^,\s]+))/i.exec(key[1]);
    if (format && String(format[1] || format[2] || format[3]).toLowerCase() !== 'identity') return null;
  }
  var lines = body.split(/\r?\n/), playable = false;
  for (var i = 0; i < lines.length; i++) {
    if (!/^\s*#EXT(?:INF:|-X-STREAM-INF:)/i.test(lines[i])) continue;
    var next = i + 1;
    while (next < lines.length && (!lines[next].trim() || /^\s*#/.test(lines[next]))) next++;
    if (next < lines.length && /^[^#<>\s]+$/.test(lines[next].trim())) { playable = true; break; }
  }
  if (!playable) return null;
  var heights = /\bRESOLUTION=(\d+)x(\d+)/gi, match, height = 0, width = 0, values = [];
  while ((match = heights.exec(body))) if (Number(match[2]) > height) { height = Number(match[2]); width = Number(match[1]); }
  var codec = /\bCODECS="([^"]+)"/gi;
  while ((match = codec.exec(body))) values.push(match[1]);
  return { quality: height ? height + 'p' : '', codecSummary: codecs(values), height: height, width: width };
}
module.exports = { dash: dash, hls: hls };

},
"catalog": function(module, exports, require) {
'use strict';
var u = require("utils");

function readableHtml(value) {
  return typeof value === 'string' && value.length <= 4 * 1024 * 1024 ? value : '';
}

function safeUrl(value, base) {
  value = String(value || '');
  if (!value || /[\\\x00-\x20\x7f]/.test(value)) return '';
  var url = u.absolute(value, base);
  var host = /^https:\/\/([a-z0-9.-]+)(?::\d+)?(?:[/?#]|$)/i.exec(url);
  if (!host || /(?:^|\.)localhost\.?$/i.test(host[1]) || /^(?:127\.|10\.|192\.168\.|169\.254\.|0\.)/.test(host[1]) || /^172\.(?:1[6-9]|2\d|3[01])\./.test(host[1])) return '';
  return url;
}

function pathOf(url) {
  return String(url || '').slice(u.origin(url).length).split(/[?#]/)[0];
}

function localUrl(value, base) {
  var url = safeUrl(value, base);
  if (!url || !u.sameSite(url, base) || /%(?:2f|5c|00|0a|0d)/i.test(url)) return '';
  return url.split(/[?#]/)[0];
}

function cleanTitle(value) {
  return u.text(value).replace(/[\u200e\u200f]/g, '').replace(/\s+izle\s*$/i, '').trim();
}

function cleanSlug(value) {
  return String(value || '').replace(/(?:-(?:izle|hd)(?:-\d+)?)+$/i, '').toLowerCase();
}

function seriesSlug(url) {
  var match = /^\/diziler\/([a-z0-9][a-z0-9-]*)\/?$/i.exec(pathOf(url));
  if (match) return cleanSlug(match[1]);
  match = /^\/dizi\/([a-z0-9][a-z0-9-]*)\/\d+-sezon-[a-z0-9-]+\/?$/i.exec(pathOf(url));
  return match ? cleanSlug(match[1]) : '';
}

function yearValue(value) {
  var match = /\b((?:19|20)\d{2})\b/.exec(String(value || ''));
  return match ? Number(match[1]) : null;
}

function trailingYear(value) {
  var match = /\s+[([]?((?:19|20)\d{2})[)\]]?\s*$/.exec(String(value || ''));
  return match ? Number(match[1]) : null;
}

function withoutYear(value) {
  return String(value || '').replace(/\s+[([]?(?:19|20)\d{2}[)\]]?\s*$/, '').trim();
}

function parseDirectory(html, base) {
  var results = [];
  var seen = {};
  var anchors = /<a\b([^>]*)>([\s\S]*?)<\/a\s*>/gi;
  var match;
  html = readableHtml(html).replace(/<!--[\s\S]*?-->/g, '');
  while ((match = anchors.exec(html)) !== null) {
    var url = localUrl(u.attr(match[1], 'href'), base);
    if (!seriesSlug(url)) continue;
    var attributeTitle = u.attr(match[1], 'title');
    var cardTitle = u.classText(match[2], 'tv-title');
    var title = cleanTitle(attributeTitle || cardTitle || match[2]);
    if (!title || title.length > 180 || /^(?:imdb\s*:?\s*)?\d\.\d$/i.test(title)) continue;
    var quality = attributeTitle ? 3 : cardTitle ? 2 : 1;
    var item = { url: url, title: title, type: 'tv' };
    var year = trailingYear(title);
    if (year) item.year = year;
    if (seen[url]) {
      var old = seen[url];
      if (quality > old.quality || quality === old.quality && title.length < results[old.index].title.length) {
        results[old.index] = item;
        old.quality = quality;
      }
    } else {
      seen[url] = { index: results.length, quality: quality };
      results.push(item);
    }
  }
  return results;
}

function titleList(metadata) {
  var titles = [metadata.title, metadata.originalTitle, metadata.turkishTitle];
  if (Array.isArray(metadata.titles)) titles = titles.concat(metadata.titles);
  return titles.filter(function (value) { return typeof value === 'string' && value; }).map(function (value) { return u.normalize(cleanTitle(value)); }).filter(Boolean);
}

function rankResults(results, metadata) {
  metadata = metadata || {};
  if (metadata.type && metadata.type !== 'tv' && metadata.type !== 'series') return [];
  var titles = titleList(metadata);
  var targetYear = yearValue(metadata.year);
  return (Array.isArray(results) ? results : []).map(function (item, index) {
    if (!item || !item.url || item.type && item.type !== 'tv') return null;
    var title = u.normalize(cleanTitle(item.title));
    var bareTitle = u.normalize(withoutYear(cleanTitle(item.title)));
    var slug = u.normalize(seriesSlug(item.url));
    var bareSlug = u.normalize(withoutYear(slug));
    var titleMatch = titles.indexOf(title) !== -1 || titles.indexOf(bareTitle) !== -1;
    var slugMatch = titles.indexOf(slug) !== -1 || titles.indexOf(bareSlug) !== -1;
    if (!titleMatch && !slugMatch) return null;
    var year = yearValue(item.year) || trailingYear(cleanTitle(item.title)) || trailingYear(slug);
    if (targetYear && year && targetYear !== year) return null;
    var score = (titleMatch ? 100 : 0) + (slugMatch ? 80 : 0) + (targetYear && targetYear === year ? 250 : 0);
    return { item: item, score: score, index: index };
  }).filter(Boolean).sort(function (a, b) { return b.score - a.score || a.index - b.index; }).map(function (entry) { return entry.item; });
}

function integer(value) {
  return value != null && /^\d+$/.test(String(value).trim()) ? Number(value) : null;
}

function episodeIdentity(url) {
  var match = /^\/([a-z0-9][a-z0-9-]*)-(\d+)-sezon-(\d+)-bolum(?:-[a-z0-9-]+)?\/?$/i.exec(pathOf(url));
  return match ? { series: cleanSlug(match[1]), season: Number(match[2]), episode: Number(match[3]) } : null;
}

function episodeUrl(html, seriesUrl, season, episode) {
  var wantedSeason = integer(season);
  var wantedEpisode = integer(episode);
  var series = seriesSlug(seriesUrl);
  if (!series || wantedSeason === null || wantedEpisode === null) return '';
  var anchors = /<a\b[^>]*>/gi;
  var match;
  html = readableHtml(html).replace(/<!--[\s\S]*?-->/g, '');
  while ((match = anchors.exec(html)) !== null) {
    var url = localUrl(u.attr(match[0], 'href'), seriesUrl);
    var identity = episodeIdentity(url);
    if (identity && identity.series === series && identity.season === wantedSeason && identity.episode === wantedEpisode) return url;
  }
  return '';
}

function elementBody(html, id) {
  var opening = /<([a-z][\w:-]*)\b([^>]*)>/gi;
  var match;
  while ((match = opening.exec(html)) !== null) {
    if (u.attr(match[2], 'id') !== id) continue;
    var closing = new RegExp('^([\\s\\S]*?)<\\/' + match[1] + '\\s*>', 'i');
    var body = closing.exec(html.slice(opening.lastIndex));
    return body ? body[1] : '';
  }
  return '';
}

function seasonUrl(html, seriesUrl, season) {
  var wantedSeason = integer(season);
  var series = seriesSlug(seriesUrl);
  if (!series || wantedSeason === null) return '';
  var body = elementBody(readableHtml(html), 'seasons-list');
  var anchors = /<a\b[^>]*>/gi;
  var match;
  while ((match = anchors.exec(body)) !== null) {
    var url = localUrl(u.attr(match[0], 'href'), seriesUrl);
    var seasonMatch = /^\/dizi\/([a-z0-9-]+)\/(\d+)-sezon-([a-z0-9-]+)\/?$/i.exec(pathOf(url));
    if (seasonMatch && cleanSlug(seasonMatch[1]) === series && cleanSlug(seasonMatch[3]) === series && Number(seasonMatch[2]) === wantedSeason) return url;
  }
  return '';
}

function pageIdentity(html) {
  html = readableHtml(html);
  var section = /<section\b[^>]*\bid\s*=\s*(?:"single-diziler"|'single-diziler')[^>]*>/i.exec(html);
  if (!section) return {};
  var body = html.slice(section.index, section.index + 30000).split(/<[^>]+\bid\s*=\s*(?:"seasons-list"|'seasons-list')/i)[0];
  var heading = /<h1\b[^>]*>([\s\S]*?)<\/h1\s*>/i.exec(body);
  var year = /href\s*=\s*["'][^"']*\/yil\/((?:19|20)\d{2})\/?["']/i.exec(body);
  var identity = {};
  if (heading) identity.title = cleanTitle(heading[1]);
  if (year) identity.year = Number(year[1]);
  return identity;
}

function playerChoices(html, pageUrl) {
  html = readableHtml(html);
  var result = [];
  var seen = {};
  var body = elementBody(html, 'video-area');
  var label = /<!--\s*baslik\s*:\s*([^<]*?)\s*-->/i.exec(body);
  var frames = body.match(/<iframe\b[^>]*>/gi) || [];
  frames.forEach(function (tag) {
    var url = safeUrl(u.attr(tag, 'src') || u.attr(tag, 'data-src'), pageUrl);
    if (!url || seen[url]) return;
    seen[url] = true;
    result.push({ url: url, kind: 'iframe', name: label ? u.text(label[1]) : 'DiziBOX' });
  });
  var episodePath = pathOf(pageUrl).replace(/\/\d+\/?$/, '/').replace(/\/?$/, '/');
  if (!episodeIdentity(u.origin(pageUrl) + episodePath)) return result;
  var selects = /<select\b([^>]*)>([\s\S]*?)<\/select\s*>/gi;
  var match;
  while ((match = selects.exec(html)) !== null) {
    if ((' ' + u.attr(match[1], 'class') + ' ').indexOf(' woca-linkpages-dd ') === -1) continue;
    var options = /<option\b([^>]*)>([\s\S]*?)<\/option\s*>/gi;
    var option;
    while ((option = options.exec(match[2])) !== null) {
      var url = localUrl(u.attr(option[1], 'value') || u.attr(option[1], 'href'), pageUrl);
      var path = pathOf(url);
      if (!url || path !== episodePath && !new RegExp('^' + episodePath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[1-9]\\d*/?$').test(path) || seen[url] || path === pathOf(pageUrl)) continue;
      seen[url] = true;
      result.push({ url: url, kind: 'page', name: u.text(option[2]) || 'DiziBOX' });
    }
  }
  return result;
}

module.exports = { parseDirectory: parseDirectory, rankResults: rankResults, episodeUrl: episodeUrl, seasonUrl: seasonUrl, pageIdentity: pageIdentity, playerChoices: playerChoices };

},
"player": function(module, exports, require) {
'use strict';
var u = require("utils");
var http = require("http");
var manifest = require("media");
var cryptoCache;

function httpsUrl(value, base) {
  var url = u.absolute(value, base);
  return /^https:\/\/[a-z0-9.-]+(?::\d+)?(?:[/?#]|$)/i.test(url) && !/[\x00-\x20\x7f<>"'\\]/.test(url) ? url : '';
}

// Read literal strings only. Expressions and downloaded scripts are never run.
function literal(source, start) {
  var quote = source.charAt(start);
  if (quote !== '"' && quote !== "'") return null;
  var out = '';
  var i = start + 1;
  for (; i < source.length; i++) {
    var c = source.charAt(i);
    if (c === quote) return { value: out, end: i + 1 };
    if (c !== '\\') { out += c; continue; }
    c = source.charAt(++i);
    if (c === 'u' || c === 'x') {
      var size = c === 'u' ? 4 : 2;
      var code = source.slice(i + 1, i + 1 + size);
      if (!new RegExp('^[0-9a-f]{' + size + '}$', 'i').test(code)) return null;
      out += String.fromCharCode(parseInt(code, 16));
      i += size;
    } else if (c === '\n') { /* line continuation */ }
    else if (c === '\r') { if (source.charAt(i + 1) === '\n') i++; }
    else out += { n: '\n', r: '\r', t: '\t', b: '\b', f: '\f', v: '\v' }[c] || c;
    if (out.length > 512 * 1024) return null;
  }
  return null;
}

function cryptoLibrary() {
  if (cryptoCache) return cryptoCache;
  if (typeof globalThis !== 'undefined' && globalThis.CryptoJS) cryptoCache = globalThis.CryptoJS;
  else {
    try { cryptoCache = require('crypto-js'); } catch (ignored) {}
  }
  if (!cryptoCache || !cryptoCache.AES || !cryptoCache.enc || !cryptoCache.enc.Utf8) throw new Error('Nuvio CryptoJS support is required for this player');
  return cryptoCache;
}

function decode(html) {
  var match = /\bCryptoJS\s*\.\s*AES\s*\.\s*decrypt\s*\(\s*/.exec(html);
  if (!match) return html;
  var encrypted = literal(html, match.index + match[0].length);
  if (!encrypted || !/^U2FsdGVkX1[A-Za-z0-9+/=]+$/.test(encrypted.value) || encrypted.value.length > 400 * 1024) return '';
  var tail = /^\s*,\s*/.exec(html.slice(encrypted.end));
  if (!tail) return '';
  var password = literal(html, encrypted.end + tail[0].length);
  if (!password || password.value.length > 256 || !/^\s*\)/.test(html.slice(password.end))) return '';
  var crypto = cryptoLibrary();
  try {
    var plain = crypto.AES.decrypt(encrypted.value, password.value).toString(crypto.enc.Utf8);
    return plain.length <= 1024 * 1024 ? plain : '';
  } catch (ignored) { return ''; }
}

function objectRanges(source) {
  var stack = [];
  var ranges = [];
  for (var i = 0; i < source.length; i++) {
    var c = source.charAt(i);
    if (c === '"' || c === "'") {
      var str = literal(source, i);
      if (!str) break;
      i = str.end - 1;
    } else if (source.slice(i, i + 2) === '//') {
      var nl = source.indexOf('\n', i + 2);
      i = nl < 0 ? source.length : nl;
    } else if (source.slice(i, i + 2) === '/*') {
      var close = source.indexOf('*/', i + 2);
      i = close < 0 ? source.length : close + 1;
    } else if (c === '{') stack.push(i);
    else if (c === '}' && stack.length) ranges.push({ start: stack.pop(), end: i + 1 });
  }
  return ranges;
}

function property(source, name) {
  var re = new RegExp('(?:[,{]\\s*)(?:["\\\x27]?' + name + '["\\\x27]?)\\s*:\\s*', 'i');
  var match = re.exec(source);
  if (!match) return '';
  var value = literal(source, match.index + match[0].length);
  return value && /^\s*[,}]/.test(source.slice(value.end)) ? value.value : '';
}

function configurations(html) {
  var result = [];
  var re = /\bjwplayer\s*\([^)]{0,160}\)\s*\.\s*setup\s*\(\s*/g;
  var match;
  while ((match = re.exec(html)) && result.length < 3) {
    var start = re.lastIndex;
    if (html.charAt(start) !== '{') continue;
    var rest = html.slice(start);
    var ranges = objectRanges(rest);
    for (var i = ranges.length - 1; i >= 0; i--) {
      if (ranges[i].start === 0) { result.push(rest.slice(0, ranges[i].end)); break; }
    }
  }
  return result;
}

function playbackHeaders(pageURL) {
  return { Referer: pageURL, Origin: u.origin(pageURL), 'User-Agent': http.USER_AGENT };
}

function media(html, pageURL) {
  var configs = configurations(html);
  var streams = [];
  var subtitles = [];
  configs.forEach(function (config) {
    var ranges = objectRanges(config);
    var re = /(?:[,{]\s*)(?:["']?file["']?)\s*:\s*/g;
    var match;
    while ((match = re.exec(config)) && streams.length + subtitles.length < 16) {
      var value = literal(config, re.lastIndex);
      if (!value || !/^\s*[,}]/.test(config.slice(value.end))) continue;
      var url = httpsUrl(value.value, pageURL);
      if (!url) continue;
      var parent = config;
      var best = config.length + 1;
      ranges.forEach(function (range) {
        if (range.start <= match.index && range.end >= value.end && range.end - range.start < best) {
          best = range.end - range.start;
          parent = config.slice(range.start, range.end);
        }
      });
      var kind = property(parent, 'kind').toLowerCase();
      var label = property(parent, 'label');
      var type = property(parent, 'type').toLowerCase();
      if (kind === 'captions' || kind === 'subtitles' || (!kind && /\.(?:vtt|srt)(?:[?#]|$)/i.test(url))) {
        subtitles.push({ id: 'dizibox-' + subtitles.length, url: url, language: property(parent, 'srclang') || (/t[uü]rk/i.test(label) ? 'tr' : 'und'), name: label || 'Subtitle', headers: playbackHeaders(pageURL) });
      } else if (/mpegurl|^hls$/.test(type) || /\.m3u8(?:[?#]|$)|\/embed\/sheila\//i.test(url)) {
        streams.push({ url: url, quality: 'Auto', type: 'hls', headers: playbackHeaders(pageURL), subtitles: [] });
      }
    }
  });
  var sourceRe = /<(?:source|video)\b[^>]*>/gi;
  var tag;
  while ((tag = sourceRe.exec(html)) && streams.length < 8) {
    var src = httpsUrl(u.attr(tag[0], 'src'), pageURL);
    if (src && (/mpegurl/i.test(u.attr(tag[0], 'type')) || /\.m3u8(?:[?#]|$)/i.test(src))) streams.push({ url: src, quality: 'Auto', type: 'hls', headers: playbackHeaders(pageURL), subtitles: [] });
  }
  return streams.filter(function (stream, index, all) {
    stream.subtitles = subtitles.slice();
    return all.map(function (s) { return s.url; }).indexOf(stream.url) === index;
  });
}

function frameUrls(html, pageURL, siteOrigin) {
  var list = [];
  var re = /<iframe\b[^>]*>/gi;
  var tag;
  while ((tag = re.exec(html)) && list.length < 4) {
    var url = httpsUrl(u.attr(tag[0], 'src') || u.attr(tag[0], 'data-src'), pageURL);
    var root = u.origin(url).toLowerCase();
    if (!url || /\/cdn-cgi\//i.test(url)) continue;
    if (root !== siteOrigin && !/^https:\/\/(?:[a-z0-9-]+\.)*molystream\.(?:org|net)$/i.test(root)) continue;
    if (!/(?:\/(?:player\/)?(?:[a-z0-9_-]+\/)?(?:king|moly|haydi|player)\.php(?:\?|$)|\/embed\/)/i.test(url)) continue;
    if (list.indexOf(url) < 0) list.push(url);
  }
  return list;
}

function resolve(html, pageURL, options) {
  options = options || {};
  var request = options.request || http.request;
  var visited = {};
  var count = 0;
  var siteOrigin = (options.siteOrigin || u.origin(pageURL)).toLowerCase();
  var maxRequests = Math.min(8, options.maxRequests || 6);
  var maxDepth = Math.min(4, options.maxDepth || 4);
  function get(url, referer) {
    if (++count > maxRequests) return Promise.resolve('');
    return request(url, { referer: referer, origin: u.origin(referer), timeout: options.timeout || 10000 });
  }
  function verified(stream) {
    return get(stream.url, stream.headers.Referer).then(function (body) {
      var checked = manifest.hls(body);
      if (checked) { stream.quality = checked.quality || 'Auto'; return stream; }
      var value = String(body).trim();
      var target = /^https:\/\//i.test(value) ? httpsUrl(value, stream.url) : '';
      if (!target || target === stream.url) return null;
      return get(target, stream.headers.Referer).then(function (playlist) {
        var checked = manifest.hls(playlist);
        if (!checked) return null;
        stream.url = target;
        stream.quality = checked.quality || 'Auto';
        return stream;
      });
    });
  }
  function walk(body, url, depth) {
    if (depth > maxDepth || visited[url] || !body || body.length > 1024 * 1024) return Promise.resolve([]);
    visited[url] = true;
    var decoded = decode(body);
    if (!decoded) return Promise.resolve([]);
    var candidates = media(decoded, url).slice(0, 2);
    var accepted = [];
    var firstError;
    var chain = Promise.resolve();
    candidates.forEach(function (stream) {
      chain = chain.then(function () {
        if (accepted.length) return;
        return verified(stream).then(function (result) { if (result) accepted.push(result); }).catch(function (error) { firstError = firstError || error; });
      });
    });
    return chain.then(function () {
      if (accepted.length) return accepted;
      var frames = frameUrls(decoded, url, siteOrigin).slice(0, 3);
      var frameChain = Promise.resolve([]);
      frames.forEach(function (frameURL) {
        frameChain = frameChain.then(function (streams) {
          if (streams.length || visited[frameURL]) return streams;
          return get(frameURL, url).then(function (frameHTML) { return walk(frameHTML, frameURL, depth + 1); }).catch(function (error) { firstError = firstError || error; return []; });
        });
      });
      return frameChain.then(function (streams) {
        if (!streams.length && firstError) throw firstError;
        return streams;
      });
    });
  }
  return Promise.resolve().then(function () { return walk(String(html || ''), pageURL, 0); });
}

function resolveFrame(frameURL, referer, options) {
  options = options || {};
  var request = options.request || http.request;
  var url = httpsUrl(frameURL, referer);
  if (!url) return Promise.resolve([]);
  var settings = {};
  Object.keys(options).forEach(function (key) { settings[key] = options[key]; });
  settings.siteOrigin = settings.siteOrigin || u.origin(referer);
  return request(url, { referer: referer, origin: u.origin(referer), timeout: options.timeout || 10000 }).then(function (html) { return resolve(html, url, settings); });
}

module.exports = { resolve: resolve, resolveFrame: resolveFrame };

},
"index": function(module, exports, require) {
'use strict';
var u = require("utils");
var http = require("http");
var catalog = require("catalog");
var player = require("player");
var DEFAULT_SITE = 'https://www.dizibox.live';

function settings() {
  var root = typeof globalThis !== 'undefined' ? globalThis : {};
  var saved = root.SCRAPER_SETTINGS || {};
  return { site: u.siteUrl(saved.siteUrl || DEFAULT_SITE) || DEFAULT_SITE, key: String(saved.tmdbApiKey || root.TMDB_API_KEY || '').trim(), session: http.session() };
}

function metadata(id, cfg) {
  if (!cfg.key) return Promise.reject(new Error('TMDB key unavailable. Enter your own key in DiziBOX TV settings.'));
  var token = cfg.key.length > 40;
  var url = 'https://api.themoviedb.org/3/tv/' + encodeURIComponent(id) + '?language=tr-TR';
  if (!token) url += '&api_key=' + encodeURIComponent(cfg.key);
  return http.request(url, { json: true, bearer: token ? cfg.key : '', session: cfg.session }).then(function (data) {
    return { title: data.name || '', originalTitle: data.original_name || '', year: Number(String(data.first_air_date || '').slice(0, 4)) || null };
  });
}

function directory(cfg) {
  // The public directory is server-rendered; the site's search endpoints can stall.
  return http.request(cfg.site + '/', { session: cfg.session }).then(function (html) {
    var list = catalog.parseDirectory(html, cfg.site);
    try {
      var ti = html.search(/tulsa/i);
      console.warn('DBXDBG home len=' + html.length + ' anchors=' + list.length + ' tulsaIdx=' + ti + ' first=' + list.slice(0, 6).map(function (x) { return x.title; }).join(' | ') + (ti >= 0 ? ' ctx=' + html.substr(Math.max(0, ti - 250), 600).replace(/\s+/g, ' ') : ''));
      console.warn('DBXDBG tul-anchors=' + list.filter(function (x) { return /tul/i.test(x.title + ' ' + x.url); }).map(function (x) { return x.title + ' ' + x.url; }).slice(0, 5).join(' || '));
    } catch (e) { console.warn('DBXDBG err ' + e.message); }
    return list;
  });
}

function episodePage(match, meta, cfg, season, episode) {
  return http.request(match.url, { referer: cfg.site + '/', session: cfg.session }).then(function (html) {
    var identity = catalog.pageIdentity(html);
    if (identity.year && meta.year && identity.year !== meta.year) return null;
    var target = catalog.episodeUrl(html, match.url, season, episode);
    if (target) return { url: target, referer: match.url };
    var selectedSeason = catalog.seasonUrl(html, match.url, season);
    if (!selectedSeason || selectedSeason === match.url) return null;
    return http.request(selectedSeason, { referer: match.url, session: cfg.session }).then(function (body) {
      var link = catalog.episodeUrl(body, match.url, season, episode);
      return link ? { url: link, referer: selectedSeason } : null;
    });
  }).then(function (target) {
    if (!target) return null;
    return http.request(target.url, { referer: target.referer, session: cfg.session }).then(function (html) { return { html: html, url: target.url }; });
  });
}

function extract(page, cfg) {
  var choices = catalog.playerChoices(page.html, page.url);
  var visitedFrames = {};
  function request(url, options) {
    options = options || {};
    options.session = cfg.session;
    return http.request(url, options);
  }
  function frames(html, url) {
    var found = catalog.playerChoices(html, url).filter(function (choice) { return choice.kind === 'iframe'; }).slice(0, 2);
    function next(index) {
      if (index >= found.length) return Promise.resolve([]);
      var frame = found[index];
      if (visitedFrames[frame.url]) return next(index + 1);
      visitedFrames[frame.url] = true;
      return player.resolveFrame(frame.url, url, { request: request, siteOrigin: cfg.site }).then(function (streams) {
        streams.forEach(function (stream) { stream.sourceName = frame.name || 'Player'; });
        return streams.length ? streams : next(index + 1);
      }).catch(function (error) {
        console.warn('[DiziBOX TV] ' + error.message);
        return next(index + 1);
      });
    }
    return next(0);
  }
  return frames(page.html, page.url).then(function (streams) {
    if (streams.length) return streams;
    var pages = choices.filter(function (choice) { return choice.kind === 'page' && choice.url !== page.url; }).slice(0, 2);
    function next(index) {
      if (index >= pages.length) return Promise.resolve([]);
      return request(pages[index].url, { referer: page.url }).then(function (body) { return frames(body, pages[index].url); }).then(function (result) { return result.length ? result : next(index + 1); }).catch(function (error) {
        console.warn('[DiziBOX TV] ' + error.message);
        return next(index + 1);
      });
    }
    return next(0);
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  if (['tv', 'series', 'show'].indexOf(mediaType) === -1) return Promise.resolve([]);
  if (!/^\d+$/.test(String(tmdbId)) || !/^\d+$/.test(String(season)) || !/^\d+$/.test(String(episode)) || Number(episode) < 1) return Promise.resolve([]);
  var cfg = settings();
  if (!cfg.key) return metadata(tmdbId, cfg).catch(function (error) { console.warn('[DiziBOX TV] ' + error.message); return []; });
  return Promise.all([metadata(tmdbId, cfg), directory(cfg)]).then(function (values) {
    var meta = values[0];
    var matches = catalog.rankResults(values[1], meta).slice(0, 2);
    try { console.warn('DBXDBG meta title=' + meta.title + ' orig=' + meta.originalTitle + ' year=' + meta.year + ' matches=' + matches.length + ' ' + matches.map(function (x) { return x.title + ' ' + x.url; }).join(' || ')); } catch (e) {}
    return Promise.resolve().then(function () {
      function next(index) {
        if (index >= matches.length) return Promise.resolve([]);
        return episodePage(matches[index], meta, cfg, season, episode).then(function (page) { return page ? extract(page, cfg) : []; }).then(function (streams) {
          return streams.length ? streams : next(index + 1);
        });
      }
      return next(0);
    }).then(function (streams) {
      var seen = {};
      return streams.filter(function (stream) { if (!stream.url || seen[stream.url]) return false; seen[stream.url] = true; return true; }).map(function (stream) {
        var headers = stream.headers || {};
        var subtitles = (stream.subtitles || []).map(function (sub, index) {
          return { id: sub.id || 'dizibox-' + index, url: sub.url, language: sub.language || sub.lang || 'und', name: sub.name || sub.label || sub.language || 'Subtitle', headers: sub.headers || headers };
        });
        return { name: 'DiziBOX TV', provider: 'DiziBOX TV', title: meta.title + ' S' + season + 'E' + episode + ' | ' + (stream.sourceName || 'Player'), url: stream.url, quality: stream.quality || 'Auto', type: stream.type || 'hls', headers: headers, subtitles: subtitles };
      });
    });
  }).then(function (streams) { http.cancel(cfg.session); return streams; }).catch(function (error) {
    http.cancel(cfg.session);
    // Only helper errors or fixed local messages reach this log, never tokens or URLs with queries.
    console.warn('[DiziBOX TV] ' + error.message);
    return [];
  });
}

function onSettings() {
  return Promise.resolve([
    { type: 'header', label: 'DiziBOX TV' },
    { type: 'text', key: 'siteUrl', label: 'Website address', defaultValue: DEFAULT_SITE, description: 'Update this HTTPS origin if the website moves.' },
    { type: 'text', key: 'tmdbApiKey', label: 'TMDB API key (optional)', isPassword: true, defaultValue: '', description: 'Uses Nuvio\'s key when available. Otherwise enter your own v3 key or API Read Access Token.' }
  ]);
}

module.exports = { getStreams: getStreams, onSettings: onSettings };

}
};
var cache = {};
function load(id) {
 if (cache[id]) return cache[id].exports;
 if (!modules[id]) { if (id === "crypto-js" && externalRequire) return externalRequire(id); throw new Error("Runtime module unavailable: " + id); }
 var local = { exports: {} }; cache[id] = local;
 modules[id](local, local.exports, load);
 return local.exports;
}
module.exports = load("index");
}());
