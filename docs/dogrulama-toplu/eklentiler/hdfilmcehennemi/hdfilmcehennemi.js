/* HDFilmCehennemi for Nuvio v1.0.3 (Play fix: rplayer frames, packed seeded decoder)
Decoder adaptations: Copyright (c) 2024 enXov
MIT License

Copyright (c) 2026 HDFilmCehennemi Nuvio provider contributors

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
var modules = {
"utils": function(module, exports, require) {
'use strict';

function entities(value) {
  return String(value || '').replace(/&#(x[\da-f]+|\d+);|&([a-z][a-z0-9]+);/gi, function (all, number, named) {
    if (number) return String.fromCharCode(number.charAt(0).toLowerCase() === 'x' ? parseInt(number.slice(1), 16) : parseInt(number, 10));
    var names = { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ', Ccedil: 'Ç', ccedil: 'ç', Ouml: 'Ö', ouml: 'ö', Uuml: 'Ü', uuml: 'ü', Scedil: 'Ş', scedil: 'ş', Gbreve: 'Ğ', gbreve: 'ğ', Idot: 'İ', inodot: 'ı' };
    return names[named] || names[named.toLowerCase()] || all;
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
  error.hdfcSafe = true;
  error.hdfcBlocked = !!blocked;
  error.hdfcTransient = !!transient;
  error.hdfcCancelled = !!cancelled;
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
  var method = String(options.method || 'GET').toUpperCase();
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
        finish(error && error.hdfcSafe ? error : safeError('Network request failed: ' + site, false, true));
      });
    }).catch(function (error) {
      // One GET retry for a transport failure or a temporary upstream outage.
      // Never retry access restrictions, verification pages, or malformed data.
      if (!state.cancelled && method === 'GET' && number === 0 && error.hdfcTransient && state.deadline - Date.now() >= timeout) return attempt(1);
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

function parseResults(data, base) {
  var fragments = data && Array.isArray(data.results) ? data.results : [];
  var result = [];
  fragments.slice(0, 60).forEach(function (html) {
    if (typeof html !== 'string') return;
    var link = /<a\b[^>]*>/i.exec(html);
    var url = link ? u.absolute(u.attr(link[0], 'href'), base) : '';
    if (!url || !u.sameSite(url, base)) return;
    var img = /<img\b[^>]*>/i.exec(html);
    var title = u.classText(html, 'title') || (img ? u.attr(img[0], 'alt') : '');
    var year = /\b(?:19|20)\d{2}\b/.exec(u.classText(html, 'year'));
    var type = u.normalize(u.classText(html, 'type'));
    result.push({ url: url, title: title, year: year ? Number(year[0]) : null, type: type.indexOf('dizi') !== -1 ? 'tv' : type.indexOf('film') !== -1 ? 'movie' : null });
  });
  return result;
}

function rankResults(results, metadata, imdbQuery) {
  var titles = [metadata.title, metadata.originalTitle].filter(Boolean).map(u.normalize);
  function matchesTitle(value) {
    if (titles.indexOf(u.normalize(value)) !== -1) return true;
    // Search cards can publish "Localized title - Original title". Require
    // both complete metadata titles, so a shared word or sequel cannot match.
    var parts = String(value || '').split(/\s+-\s+/).map(u.normalize);
    return parts.length === 2 && titles.length === 2 && titles[0] !== titles[1] &&
      ((parts[0] === titles[0] && parts[1] === titles[1]) ||
       (parts[0] === titles[1] && parts[1] === titles[0]));
  }
  return results.map(function (item) {
    if (item.type && item.type !== metadata.type) return null;
    if (!imdbQuery && metadata.year && item.year && Math.abs(metadata.year - item.year) > (metadata.type === 'tv' ? 1 : 0)) return null;
    var exact = matchesTitle(item.title);
    // An IMDb lookup is exact by identifier; title lookup must match the entire title.
    if (!imdbQuery && !exact) return null;
    return { item: item, score: (imdbQuery ? 100 : 0) + (exact ? 20 : 0) + (item.year === metadata.year ? 5 : 0) };
  }).filter(Boolean).sort(function (a, b) { return b.score - a.score; }).map(function (entry) { return entry.item; });
}

function episodeUrl(html, base, season, episode) {
  var re = /<a\b[^>]*>/gi;
  var match;
  while ((match = re.exec(html)) !== null) {
    var href = u.attr(match[0], 'href');
    var numbers = /(\d+)-sezon-(\d+)-bolum/i.exec(href);
    var s = numbers ? numbers[1] : (/sezon[\/-]?(\d+)/i.exec(href) || [])[1];
    var e = numbers ? numbers[2] : (/bolum[\/-]?(\d+)/i.exec(href) || [])[1];
    var url = u.absolute(href, base);
    if (Number(s) === Number(season) && Number(e) === Number(episode) && u.sameSite(url, base)) return url;
  }
  return '';
}

function players(html, pageUrl, embedBase) {
  var result = [];
  var frames = html.match(/<iframe\b[^>]*>/gi) || [];
  frames.slice(0, 8).forEach(function (tag) {
    var url = u.absolute(u.attr(tag, 'src') || u.attr(tag, 'data-src'), pageUrl);
    // Only follow actual player/embed frames; ignore unrelated advertising iframes.
    if (url && /\/(?:(?:video\/)?embed|rplayer)\//i.test(url)) result.push({ url: url, name: 'Player' });
  });
  var first = result[0];
  if (first) {
    var id = /\/embed\/([^/?#]+)/i.exec(first.url);
    var re = /<([a-z][\w:-]*)\b([^>]*)>([\s\S]*?)<\/\1\s*>/gi;
    var opening = /<([a-z][\w:-]*)\b([^>]*\bclass\s*=\s*(?:"[^"]*\balternative-link\b[^"]*"|'[^']*\balternative-link\b[^']*')[^>]*)>/gi;
    var match;
    while (id && (match = opening.exec(html)) !== null) {
      re = new RegExp('^([\\s\\S]*?)<\\/' + match[1] + '\\s*>', 'i');
      var body = re.exec(html.slice(opening.lastIndex));
      var name = u.text(body ? body[1] : '') || 'Alternative';
      var videoId = u.attr(match[2], 'data-video');
      if (u.attr(match[2], 'data-active') === '1') { first.name = name; continue; }
      var url = embedBase + '/video/embed/' + encodeURIComponent(id[1]) + '/';
      if (/rapidrame/i.test(name)) {
        if (!videoId) continue;
        url += '?rapidrame_id=' + encodeURIComponent(videoId);
      }
      result.push({ url: url, name: name });
    }
  }
  var seen = {};
  return result.filter(function (item) { if (seen[item.url]) return false; seen[item.url] = true; return true; }).slice(0, 4);
}

module.exports = { parseResults: parseResults, rankResults: rankResults, episodeUrl: episodeUrl, players: players };

},
"player": function(module, exports, require) {
/*
 * Safe player extraction for HDFilmCehennemi. No downloaded JavaScript is run.
 * Decoder arithmetic and legacy variants adapted from the MIT-licensed
 * HDFilmCehennemi Stremio Addon, Copyright (c) 2024 enXov.
 * See THIRD_PARTY_NOTICES.md for the original license.
 */
'use strict';

var MAX_INPUT = 2 * 1024 * 1024;
var MAX_ITEMS = 512;

function trim(value) { return String(value || '').replace(/^\s+|\s+$/g, ''); }

function htmlEntities(value) {
    return String(value || '').replace(/&(#x[0-9a-f]+|#\d+|amp|quot|apos|lt|gt);/gi, function (all, name) {
        var lower = name.toLowerCase();
        var code;
        if (lower.charAt(0) === '#') {
            code = lower.charAt(1) === 'x' ? parseInt(lower.slice(2), 16) : parseInt(lower.slice(1), 10);
            return code > 0 && code <= 65535 ? String.fromCharCode(code) : all;
        }
        return { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>' }[lower] || all;
    });
}

function absoluteUrl(value, baseUrl) {
    var url = trim(htmlEntities(value));
    var base = /^(https?):\/\/([^\/?#]+)([^?#]*)/i.exec(trim(baseUrl));
    var parsed;
    var path;
    var suffix;
    var parts;
    var out = [];
    var i;
    if (!url || url.length > 16384 || /[\x00-\x20\x7f\\<>"']/.test(url)) return '';
    if (/^[a-z][a-z\d+.-]*:/i.test(url) && !/^https?:\/\//i.test(url)) return '';
    if (url.slice(0, 2) === '//') url = (base ? base[1] : 'https') + ':' + url;
    else if (!/^https?:\/\//i.test(url)) {
        if (!base) return '';
        if (url.charAt(0) === '#') return '';
        if (url.charAt(0) === '?') url = base[1] + '://' + base[2] + (base[3] || '/') + url;
        else url = base[1] + '://' + base[2] + (url.charAt(0) === '/' ? url : (base[3] || '/').replace(/[^/]*$/, '') + url);
    }
    parsed = /^(https?):\/\/([^\/?#]+)([^?#]*)([?#][\s\S]*)?$/i.exec(url);
    if (!parsed || !parsed[2] || /[@\s]/.test(parsed[2]) || !/^(?:\[[0-9a-f:.]+\]|[a-z0-9._-]+)(?::\d{1,5})?$/i.test(parsed[2])) return '';
    path = parsed[3] || '/';
    suffix = parsed[4] || '';
    parts = path.split('/');
    for (i = 0; i < parts.length; i++) {
        if (parts[i] === '..') { if (out.length > 1) out.pop(); }
        else if (parts[i] !== '.') out.push(parts[i]);
    }
    path = out.join('/');
    if (path.charAt(0) !== '/') path = '/' + path;
    return parsed[1].toLowerCase() + '://' + parsed[2] + path + suffix;
}

function looksLikeMedia(url) {
    return /\.(?:m3u8|mp4|mpd|webm|mkv|mov|m4v)(?:[?#]|$)/i.test(url) || /\/hls\//i.test(url) || /\/master\.txt(?:[?#]|$)/i.test(url);
}

/* Read a JavaScript string literal as data, including escaped quotes. */
function readString(text, start) {
    var quote = text.charAt(start);
    var result = '';
    var i = start + 1;
    var c;
    var hex;
    var escapes = { n: '\n', r: '\r', t: '\t', b: '\b', f: '\f', v: '\v', '0': '\x00' };
    if (quote !== '"' && quote !== "'") return null;
    for (; i < text.length; i++) {
        c = text.charAt(i);
        if (c === quote) return { value: result, end: i + 1 };
        if (c === '\n' || c === '\r') return null;
        if (c !== '\\') { result += c; continue; }
        i++;
        c = text.charAt(i);
        if (!c) return null;
        if (c === '\n') continue;
        if (c === '\r') { if (text.charAt(i + 1) === '\n') i++; continue; }
        if (c === 'x' || c === 'u') {
            hex = text.substr(i + 1, c === 'x' ? 2 : 4);
            if (!(c === 'x' ? /^[0-9a-f]{2}$/i : /^[0-9a-f]{4}$/i).test(hex)) return null;
            result += String.fromCharCode(parseInt(hex, 16));
            i += hex.length;
        } else if (Object.prototype.hasOwnProperty.call(escapes, c)) result += escapes[c];
        else if (/[1-9]/.test(c)) return null;
        else result += c;
    }
    return null;
}

function skipSpace(text, index) { while (/\s/.test(text.charAt(index)) && index < text.length) index++; return index; }

/* Accept only literal data: strings, numbers, booleans, null, arrays, objects. */
function readLiteral(text, start, depth) {
    var i = skipSpace(text, start);
    var c = text.charAt(i);
    var item;
    var result;
    var key;
    var match;
    var count = 0;
    if (depth > 12) return null;
    if (c === '"' || c === "'") return readString(text, i);
    if (c === '[' || c === '{') {
        result = c === '[' ? [] : {};
        i = skipSpace(text, i + 1);
        if (text.charAt(i) === (c === '[' ? ']' : '}')) return { value: result, end: i + 1 };
        while (i < text.length && count++ < MAX_ITEMS) {
            if (c === '{') {
                if (text.charAt(i) === '"' || text.charAt(i) === "'") item = readString(text, i);
                else {
                    match = /^[a-zA-Z_$][\w$]*/.exec(text.slice(i));
                    item = match ? { value: match[0], end: i + match[0].length } : null;
                }
                if (!item) return null;
                key = item.value;
                if (key === '__proto__' || key === 'constructor' || key === 'prototype') return null;
                i = skipSpace(text, item.end);
                if (text.charAt(i) !== ':') return null;
                i = skipSpace(text, i + 1);
            }
            item = readLiteral(text, i, depth + 1);
            if (!item) return null;
            if (c === '[') result.push(item.value); else result[key] = item.value;
            i = skipSpace(text, item.end);
            if (text.charAt(i) === (c === '[' ? ']' : '}')) return { value: result, end: i + 1 };
            if (text.charAt(i) !== ',') return null;
            i = skipSpace(text, i + 1);
            if (text.charAt(i) === (c === '[' ? ']' : '}')) return { value: result, end: i + 1 };
        }
        return null;
    }
    match = /^(?:true|false|null)(?![\w$])/.exec(text.slice(i));
    if (match) return { value: match[0] === 'true' ? true : match[0] === 'false' ? false : null, end: i + match[0].length };
    match = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?(?![\w$.])/.exec(text.slice(i));
    if (match && isFinite(Number(match[0]))) return { value: Number(match[0]), end: i + match[0].length };
    return null;
}

/* Find a closing function brace while ignoring strings, comments and regexes. */
function closingBrace(text, start) {
    var depth = 1;
    var i = start + 1;
    var item;
    var c;
    var j;
    var inClass;
    var previous;
    for (; i < text.length; i++) {
        c = text.charAt(i);
        if (c === '"' || c === "'") {
            item = readString(text, i);
            if (!item) return -1;
            i = item.end - 1;
        } else if (c === '/' && text.charAt(i + 1) === '/') {
            j = text.indexOf('\n', i + 2); i = j < 0 ? text.length : j;
        } else if (c === '/' && text.charAt(i + 1) === '*') {
            j = text.indexOf('*/', i + 2); if (j < 0) return -1; i = j + 1;
        } else if (c === '/') {
            previous = i - 1;
            while (previous >= 0 && /\s/.test(text.charAt(previous))) previous--;
            if (!/[=(,:!&|?;{\[]/.test(text.charAt(previous))) continue;
            inClass = false;
            for (j = i + 1; j < text.length; j++) {
                if (text.charAt(j) === '\\') { j++; continue; }
                if (text.charAt(j) === '[') inClass = true;
                else if (text.charAt(j) === ']') inClass = false;
                else if (text.charAt(j) === '/' && !inClass) break;
                else if (text.charAt(j) === '\n') return -1;
            }
            if (j >= text.length) return -1;
            i = j;
        } else if (c === '{') depth++;
        else if (c === '}' && --depth === 0) return i;
    }
    return -1;
}

function base64Decode(value) {
    var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var str = String(value).replace(/\s/g, '');
    var raw = str.replace(/=+$/, '');
    var bits = 0;
    var pending = 0;
    var result = '';
    var i;
    var n;
    if (!raw || !/^[A-Za-z0-9+/]*={0,2}$/.test(str) || raw.length % 4 === 1 || (str.indexOf('=') >= 0 && str.length % 4 !== 0)) throw new Error('Invalid base64');
    for (i = 0; i < raw.length; i++) {
        n = alphabet.indexOf(raw.charAt(i));
        pending = (pending << 6) | n;
        bits += 6;
        if (bits >= 8) { bits -= 8; result += String.fromCharCode((pending >> bits) & 255); pending &= (1 << bits) - 1; }
    }
    if (pending !== 0) throw new Error('Invalid base64 tail');
    return result;
}

function rotate(value, shift) {
    return value.replace(/[a-zA-Z]/g, function (c) {
        var n = c.charCodeAt(0);
        var base = n <= 90 ? 65 : 97;
        return String.fromCharCode(((n - base + shift) % 26 + 26) % 26 + base);
    });
}

function applyDecodeSteps(parts, steps) {
    var result = parts.join('');
    var i;
    var j;
    var step;
    var output;
    var acc;
    var byte;
    if (result.length > MAX_INPUT || steps.length > 16) throw new Error('Decoder limit');
    for (i = 0; i < steps.length; i++) {
        step = steps[i];
        if (step.op === 'base64') result = base64Decode(result);
        else if (step.op === 'reverse') result = result.split('').reverse().join('');
        else if (step.op === 'rot') result = rotate(result, step.shift);
        else if (step.op === 'unmix' || step.op === 'xor') {
            if (step.op === 'unmix' && !(step.offset > 0)) throw new Error('Invalid unmix');
            output = ''; acc = step.seed || 0;
            for (j = 0; j < result.length; j++) {
                byte = result.charCodeAt(j);
                if (step.op === 'unmix') output += String.fromCharCode(((byte - (step.magic % (j + step.offset))) % 256 + 256) % 256);
                else { acc = (acc + step.inc) % 256; output += String.fromCharCode(byte ^ acc); acc = (acc + byte) % 256; }
            }
            result = output;
        } else throw new Error('Unknown operation');
    }
    return result;
}

function parseInlineDecoder(html) {
    var decoders = [];
    var re = /function\s+(dc_\w+)\s*\(\s*[\w$]+\s*\)\s*\{/g;
    var fn;
    var end;
    var body;
    var calls;
    var call;
    var literal;
    var steps;
    var stepRe;
    var m;
    var i;
    var valid;
    var attempts = 0;
    var callAttempts;
    while ((fn = re.exec(html)) && decoders.length < 32 && attempts++ < 64) {
        end = closingBrace(html, re.lastIndex - 1);
        if (end < 0) continue;
        body = html.slice(re.lastIndex, end);
        /* Ignore comments so advertised examples cannot become decode steps. */
        body = body.replace(/\/\*[\s\S]*?\*\/|\/\/[^\r\n]*/g, '');
        steps = [];
        stepRe = /=\s*atob\(\s*[\w$]+\s*\)|[\w$]+\.split\(\s*(['"])\1\s*\)\.reverse\(\s*\)\.join\(\s*(['"])\2\s*\)|\(o\s*-\s*base\s*\+\s*(\d+)\)\s*%\s*26|charCode\s*-\s*\((\d+)\s*%\s*\(i\s*\+\s*(\d+)\)\)|(?:var|let|const)\s+acc\s*=\s*(\d+)[\s\S]{0,400}?acc\s*=\s*\(\s*acc\s*\+\s*(\d+)\s*\)\s*%\s*256/g;
        while ((m = stepRe.exec(body)) && steps.length <= 16) {
            if (/atob/.test(m[0])) steps.push({ op: 'base64' });
            else if (/reverse/.test(m[0])) steps.push({ op: 'reverse' });
            else if (m[3] !== undefined) steps.push({ op: 'rot', shift: Number(m[3]) % 26 });
            else if (m[4] !== undefined) steps.push({ op: 'unmix', magic: Number(m[4]), offset: Number(m[5]) });
            else if (/\^\s*acc\b/.test(body.slice(stepRe.lastIndex, stepRe.lastIndex + 200)) && /acc\s*=\s*\(\s*acc\s*\+\s*[\w$]+\s*\)\s*%\s*256/.test(body.slice(stepRe.lastIndex, stepRe.lastIndex + 300))) steps.push({ op: 'xor', seed: Number(m[6]) % 256, inc: Number(m[7]) % 256 });
        }
        if (!steps.length || steps.length > 16) continue;
        calls = new RegExp('\\b' + fn[1] + '\\s*\\(', 'g');
        callAttempts = 0;
        while ((call = calls.exec(html)) && decoders.length < 32 && callAttempts++ < 64) {
            literal = readLiteral(html, calls.lastIndex, 0);
            if (!literal || Object.prototype.toString.call(literal.value) !== '[object Array]' || html.charAt(skipSpace(html, literal.end)) !== ')') continue;
            valid = true;
            for (i = 0; i < literal.value.length; i++) if (typeof literal.value[i] !== 'string') valid = false;
            if (valid && literal.value.length) decoders.push({ steps: steps, parts: literal.value });
        }
        re.lastIndex = end + 1;
    }
    return decoders;
}

function unpackJS(payload, radix, count, keywords) {
    var words = keywords.split('|');
    var length = payload.length;
    if (radix < 2 || radix > 62 || radix % 1 || count % 1 || count > 4096 || count < 0 || words.length > 4096 || payload.length > MAX_INPUT || keywords.length > MAX_INPUT) return '';
    try { return payload.replace(/\b\w+\b/g, function (word) {
        var n = 0;
        var i;
        var code;
        for (i = 0; i < word.length; i++) {
            code = word.charCodeAt(i);
            code = code >= 48 && code <= 57 ? code - 48 : code >= 97 && code <= 122 ? code - 97 + 10 : code >= 65 && code <= 90 ? code - 65 + 36 : radix;
            if (code >= radix) return word;
            n = n * radix + code;
            if (n >= count) return word;
        }
        if (words[n]) {
            length += words[n].length - word.length;
            if (length > MAX_INPUT) throw new Error('Packed expansion limit');
            return words[n];
        }
        return word;
    }); } catch (ignoredLimit) { return ''; }
}

function unpackScripts(html) {
    var results = [];
    var re = /eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*(?:d|r)\s*\)\s*\{/g;
    var m;
    var end;
    var i;
    var values;
    var item;
    var n;
    var unpacked;
    var attempts = 0;
    while ((m = re.exec(html)) && results.length < 8 && attempts++ < 16) {
        end = closingBrace(html, re.lastIndex - 1);
        if (end < 0) continue;
        i = skipSpace(html, end + 1);
        if (html.charAt(i) !== '(') continue;
        i++;
        values = [];
        for (n = 0; n < 4; n++) {
            item = readLiteral(html, i, 0);
            if (!item) break;
            values.push(item.value);
            i = skipSpace(html, item.end);
            if (n < 3 && html.charAt(i++) !== ',') break;
        }
        if (values.length !== 4 || typeof values[0] !== 'string' || typeof values[1] !== 'number' || typeof values[2] !== 'number' || typeof values[3] !== 'string') continue;
        unpacked = unpackJS(values[0], values[1], values[2], values[3]);
        if (unpacked && unpacked.length <= MAX_INPUT) results.push(unpacked);
        re.lastIndex = end + 1;
    }
    return results;
}

function legacyCandidates(html) {
    var results = [];
    var re = /\bdc_\w+\s*\(/g;
    var match;
    var literal;
    var parts;
    var i;
    var j;
    var chain;
    var variants = [
        [{ op: 'base64' }, { op: 'reverse' }, { op: 'rot', shift: 13 }, { op: 'unmix', magic: 399756995, offset: 5 }],
        [{ op: 'reverse' }, { op: 'rot', shift: 13 }, { op: 'base64' }, { op: 'unmix', magic: 399756995, offset: 5 }],
        [{ op: 'reverse' }, { op: 'base64' }, { op: 'rot', shift: 13 }, { op: 'unmix', magic: 399756995, offset: 5 }]
    ];
    while ((match = re.exec(html)) && results.length < 32) {
        literal = readLiteral(html, re.lastIndex, 0);
        if (!literal || Object.prototype.toString.call(literal.value) !== '[object Array]') continue;
        parts = literal.value;
        for (i = 0; i < parts.length && typeof parts[i] === 'string'; i++) {}
        if (!parts.length || i !== parts.length) continue;
        for (j = 0; j < variants.length; j++) {
            chain = variants[j];
            try { results.push(applyDecodeSteps(parts, chain)); } catch (ignored) {}
        }
    }
    return results;
}

/*
 * October 2026 players use random identifiers and two known seeded formats.
 * Parse their data and arithmetic constants, never their JavaScript semantics.
 * The assigned format hides salt/operation strings amongst the ciphertext parts.
 */
function isHostnameProperty(expression) {
    var index = 1;
    var value = '';
    var item;
    var count = 0;
    if (expression === '.hostname') return true;
    if (expression.charAt(0) !== '[' || expression.charAt(expression.length - 1) !== ']') return false;
    while (index < expression.length - 1 && count++ < 8) {
        item = readString(expression, index);
        if (!item) return false;
        value += item.value;
        if (value.length > 8) return false;
        index = item.end;
        if (index === expression.length - 1) return value === 'hostname';
        if (expression.charAt(index++) !== '+') return false;
    }
    return false;
}

function seededProfile(body, parameter) {
    var compact = body.replace(/\b(?:var|let|const)\s+/g, ';').replace(/\s+/g, '');
    var hash = /([\w$]+)=\(\1\*(\d+)\+([\w$]+)\)%(\d+)/.exec(compact);
    var format = compact.indexOf(parameter + '.splice(') >= 0 ? 'metadata' : 'static';
    var profile = { format: format };
    var second;
    var seed;
    var increment;
    var shuffleSeed;
    var shuffle;
    var xor;
    var metadata;
    var splice;
    var operation;
    var rotation;
    var saltRead;
    var literals = {};
    var declarations;
    var declaration;
    var literal;
    var saltName;
    var operationName;
    var byte;
    var index;
    var h1;
    var h2;
    var hostnameAppend;
    var escape = function (value) { return value.replace(/[$]/g, '\\$'); };
    if (!hash) return null;
    h1 = escape(hash[1]); byte = escape(hash[3]);
    saltRead = new RegExp(byte + '=([\\w$]+)\\.charCodeAt\\(([\\w$]+)\\)').exec(compact);
    if (!saltRead) return null;
    saltName = saltRead[1]; index = escape(saltRead[2]);
    profile.hashMultiplier = Number(hash[2]); profile.hashModulus = Number(hash[4]);
    if (format === 'metadata') {
        metadata = new RegExp('([\\w$]+)=' + escape(parameter) + '\\.length-(\\d+),([\\w$]+)=\\1%(\\d+),([\\w$]+)=(\\d+)\\+\\(\\1%(\\d+)\\)').exec(compact);
        if (!metadata) return null;
        splice = new RegExp('([\\w$]+)=' + escape(parameter) + '\\.splice\\(' + escape(metadata[5]) + ',1\\)\\[0\\],([\\w$]+)=' + escape(parameter) + '\\.splice\\(' + escape(metadata[3]) + ',1\\)\\[0\\]').exec(compact);
        if (!splice || splice[2] !== saltName) return null;
        operationName = splice[1];
        /* Current embeds bind this literal salt to their own page hostname. */
        hostnameAppend = new RegExp('^;' + escape(saltName) + '\\+=(?:(?:self|window)\\.)?location(\\.hostname|\\[[^;\\r\\n]{1,128}\\]);').exec(compact.slice(splice.index + splice[0].length));
        profile.hostnameSalt = !!(hostnameAppend && isHostnameProperty(hostnameAppend[1]));
        profile.metadata = { removed: Number(metadata[2]), saltModulus: Number(metadata[4]), operationOffset: Number(metadata[6]), operationModulus: Number(metadata[7]) };
        second = new RegExp('([\\w$]+)=\\(\\1\\+\\(\\(' + byte + '<<(\\d+)\\)\\^' + index + '\\)\\)&255').exec(compact);
        if (!second) return null;
        h2 = escape(second[1]); profile.hashShift = Number(second[2]);
        seed = new RegExp('([\\w$]+)=\\(' + h1 + '\\*(\\d+)\\+' + h2 + '\\)%256').exec(compact);
        increment = new RegExp('([\\w$]+)=\\(' + h2 + '%(\\d+)\\)\\+(\\d+)').exec(compact);
        shuffleSeed = new RegExp('([\\w$]+)=\\(\\(' + h2 + '\\*(\\d+)\\+' + h1 + '\\)%(\\d+)\\)\\+(\\d+)').exec(compact);
    } else {
        second = new RegExp('([\\w$]+)=\\(\\1\\^\\(' + byte + '\\+' + index + '\\)\\)&255').exec(compact);
        if (!second) return null;
        h2 = escape(second[1]);
        seed = new RegExp('([\\w$]+)=\\(' + h1 + '\\+' + h2 + '\\)%256').exec(compact);
        increment = new RegExp('([\\w$]+)=\\(' + h1 + '%(\\d+)\\)\\+(\\d+)').exec(compact);
        shuffleSeed = new RegExp('([\\w$]+)=\\(\\(' + h1 + '\\*(\\d+)\\+' + h2 + '\\)%(\\d+)\\)\\+(\\d+)').exec(compact);
        declarations = /(?:var|let|const)\s+([\w$]+)\s*=\s*(['"])/g;
        while ((declaration = declarations.exec(body))) {
            literal = readString(body, declarations.lastIndex - 1);
            if (literal) literals[declaration[1]] = literal.value;
        }
        operation = /for\(([\w$]+)=([\w$]+)\.length-1;\1>=0;\1--\)/.exec(compact);
        if (!operation) return null;
        operationName = operation[2];
        profile.salt = literals[saltName]; profile.operations = literals[operationName];
    }
    if (!seed || !increment || !shuffleSeed) return null;
    profile.seedMultiplier = format === 'metadata' ? Number(seed[2]) : 1;
    profile.incrementModulus = Number(increment[2]); profile.incrementOffset = Number(increment[3]);
    profile.shuffleSeedMultiplier = Number(shuffleSeed[2]); profile.shuffleSeedModulus = Number(shuffleSeed[3]); profile.shuffleSeedOffset = Number(shuffleSeed[4]);
    shuffle = new RegExp(escape(shuffleSeed[1]) + '=\\(' + escape(shuffleSeed[1]) + '\\*(\\d+)\\+(\\d+)\\)%(\\d+);([\\w$]+)\\[' + index + '\\]=' + escape(shuffleSeed[1]) + '%\\(' + index + '\\+1\\)').exec(compact);
    if (!shuffle || compact.indexOf('for(' + saltRead[2] + '=1;') < 0 || !/([\w$]+)\[([\w$]+)\]=\1\[([\w$]+)\];\1\[\3\]=[\w$]+/.test(compact)) return null;
    profile.shuffleMultiplier = Number(shuffle[1]); profile.shuffleOffset = Number(shuffle[2]); profile.shuffleModulus = Number(shuffle[3]);
    operation = new RegExp('([\\w$]+)=' + escape(operationName) + '\\.charAt\\(' + index + '\\);if\\(\\1===[\'"]([^\'"])[\'"]\\)\\{([\\w$]+)=atob\\(\\3\\);?\\}elseif\\(\\1===[\'"]([^\'"])[\'"]\\)').exec(compact);
    if (!operation) return null;
    profile.base64Code = operation[2]; profile.reverseCode = operation[4];
    rotation = new RegExp('\\(26-\\(\\(' + escape(operation[1]) + '\\.charCodeAt\\(0\\)-(\\d+)\\)%26\\)\\)%26').exec(compact);
    if (!rotation) return null;
    profile.rotationBase = Number(rotation[1]);
    xor = new RegExp('([\\w$]+)=' + escape(seed[1]) + ',[\\w$]+=[\'"][\'"];[\\s\\S]*?\\1=\\(\\1(?:\\*(\\d+))?\\+' + escape(increment[1]) + '\\)%256;[\\w$]+\\+=String\\.fromCharCode\\(' + byte + '\\^\\1\\);\\1=\\(\\1\\+' + byte + '\\)%256').exec(compact);
    if (!xor) return null;
    profile.xorMultiplier = xor[2] ? Number(xor[2]) : 1;
    if (!profile.hashMultiplier || profile.hashMultiplier > 1000 || !profile.hashModulus || profile.hashModulus > 65537 || !profile.incrementModulus || !profile.shuffleSeedModulus || !profile.shuffleModulus || profile.shuffleModulus > 65537 || profile.shuffleMultiplier > 1000 || profile.shuffleOffset > 65537 || profile.hashShift > 8 || profile.xorMultiplier > 1000) return null;
    return profile;
}

function decodeSeeded(parts, profile, hostname) {
    var values = parts.slice();
    var salt = profile.salt;
    var operations = profile.operations;
    var count;
    var meta = profile.metadata;
    var h1 = 0;
    var h2 = 0;
    var i;
    var byte;
    var seed;
    var increment;
    var shuffleSeed;
    var result;
    var op;
    var swaps = [];
    var chars;
    var swap;
    var plain = '';
    var steps = [];
    if (profile.format === 'metadata') {
        count = values.length - meta.removed;
        if (meta.removed !== 2 || !meta.saltModulus || !meta.operationModulus || count < 1) throw new Error('Invalid metadata');
        i = meta.operationOffset + count % meta.operationModulus;
        if (i >= values.length) throw new Error('Invalid operation index');
        operations = values.splice(i, 1)[0];
        i = count % meta.saltModulus;
        if (i >= values.length) throw new Error('Invalid salt index');
        salt = values.splice(i, 1)[0];
    }
    if (typeof salt !== 'string' || !salt.length || salt.length > 4096 || typeof operations !== 'string' || !operations.length || operations.length > 16 || values.length > MAX_ITEMS) throw new Error('Seeded data limit');
    for (i = 0; i < values.length; i++) if (typeof values[i] !== 'string') throw new Error('Invalid part');
    if (profile.hostnameSalt) {
        if (typeof hostname !== 'string' || !/^[a-z0-9.-]{1,253}$/i.test(hostname)) throw new Error('Missing player hostname');
        salt += hostname.toLowerCase();
    }
    for (i = 0; i < salt.length; i++) {
        byte = salt.charCodeAt(i);
        h1 = (h1 * profile.hashMultiplier + byte) % profile.hashModulus;
        h2 = profile.format === 'metadata' ? (h2 + ((byte << profile.hashShift) ^ i)) & 255 : (h2 ^ (byte + i)) & 255;
    }
    seed = (h1 * profile.seedMultiplier + h2) % 256;
    increment = ((profile.format === 'metadata' ? h2 : h1) % profile.incrementModulus) + profile.incrementOffset;
    shuffleSeed = (((profile.format === 'metadata' ? h2 : h1) * profile.shuffleSeedMultiplier + (profile.format === 'metadata' ? h1 : h2)) % profile.shuffleSeedModulus) + profile.shuffleSeedOffset;
    result = values.join('');
    if (result.length > MAX_INPUT) throw new Error('Seeded data limit');
    for (i = operations.length - 1; i >= 0; i--) {
        op = operations.charAt(i);
        if (op === profile.base64Code) { result = base64Decode(result); steps.push({ op: 'base64' }); }
        else if (op === profile.reverseCode) { result = result.split('').reverse().join(''); steps.push({ op: 'reverse' }); }
        else if (/^[a-zA-Z]$/.test(op)) { byte = (26 - ((op.charCodeAt(0) - profile.rotationBase) % 26)) % 26; result = rotate(result, byte); steps.push({ op: 'rot', shift: byte }); }
        else throw new Error('Invalid seeded operation');
    }
    for (i = result.length - 1; i >= 1; i--) { shuffleSeed = (shuffleSeed * profile.shuffleMultiplier + profile.shuffleOffset) % profile.shuffleModulus; swaps[i] = shuffleSeed % (i + 1); }
    chars = result.split('');
    for (i = 1; i < chars.length; i++) { swap = chars[i]; chars[i] = chars[swaps[i]]; chars[swaps[i]] = swap; }
    steps.push({ op: 'unshuffle' });
    result = chars.join('');
    for (i = 0; i < result.length; i++) {
        byte = result.charCodeAt(i);
        seed = (seed * profile.xorMultiplier + increment) % 256;
        plain += String.fromCharCode(byte ^ seed);
        seed = (seed + byte) % 256;
    }
    steps.push({ op: 'xor', multiplier: profile.xorMultiplier });
    return { value: plain, steps: steps };
}

/* Find literal source-property references, skipping comments and string values. */
function sourceVariables(html) {
    var found = {};
    var scripts = [];
    var re = /<script\b[^>]*>([\s\S]*?)<\/script\s*>/gi;
    var match;
    var text;
    var i;
    var j;
    var s;
    var c;
    var item;
    var token;
    var previous;
    var history;
    var inClass;
    while ((match = re.exec(html)) && scripts.length < 128) scripts.push(match[1]);
    if (!scripts.length) scripts.push(html);
    for (s = 0; s < scripts.length; s++) {
        text = scripts[s]; history = [];
        for (i = 0; i < text.length;) {
            c = text.charAt(i);
            if (/\s/.test(c)) { i++; continue; }
            if (c === '/' && text.charAt(i + 1) === '/') { j = text.indexOf('\n', i + 2); i = j < 0 ? text.length : j + 1; continue; }
            if (c === '/' && text.charAt(i + 1) === '*') { j = text.indexOf('*/', i + 2); i = j < 0 ? text.length : j + 2; continue; }
            if (c === '"' || c === "'") {
                item = readString(text, i);
                if (!item) break;
                token = { type: 'string', value: item.value }; i = item.end;
            } else if (c === '`') {
                /* A template's contents are not literal player properties. */
                for (j = i + 1; j < text.length; j++) { if (text.charAt(j) === '\\') j++; else if (text.charAt(j) === '`') break; }
                token = { type: 'literal', value: '' }; i = j + 1;
            } else if (/[a-zA-Z_$]/.test(c)) {
                match = /^[a-zA-Z_$][\w$]*/.exec(text.slice(i));
                token = { type: 'identifier', value: match[0] }; i += match[0].length;
            } else {
                previous = history.length ? history[history.length - 1].value : '';
                if (c === '/' && (!previous || /^[=(,:!&|?;{\[]$/.test(previous) || /^(?:return|throw|case)$/.test(previous))) {
                    inClass = false;
                    for (j = i + 1; j < text.length; j++) {
                        if (text.charAt(j) === '\\') j++;
                        else if (text.charAt(j) === '[') inClass = true;
                        else if (text.charAt(j) === ']') inClass = false;
                        else if (text.charAt(j) === '/' && !inClass) break;
                        else if (text.charAt(j) === '\n') break;
                    }
                    token = { type: 'literal', value: '' }; i = j + 1;
                } else { token = { type: 'punctuation', value: c }; i++; }
            }
            if (token.type === 'identifier' && history.length === 3 && history[2].value === ':' && /^(?:file|src)$/.test(history[1].value) && /^(?:string|identifier)$/.test(history[1].type) && /^[{,]$/.test(history[0].value)) found[token.value] = true;
            history.push(token);
            if (history.length > 3) history.shift();
        }
    }
    return found;
}

function parseSeededDecoders(html, baseUrl) {
    var results = [];
    var functions = /(?:function\s+([\w$]+)\s*\(\s*([\w$]+)\s*\)|(?:var|let|const)\s+([\w$]+)\s*=\s*function\s*\(\s*([\w$]+)\s*\))\s*\{/g;
    var fn;
    var end;
    var profile;
    var calls;
    var call;
    var literal;
    var delimiter;
    var i;
    var parts;
    var decoded;
    var attempts = 0;
    var callAttempts;
    var hostname = /^https?:\/\/([^\/:?#]+)(?::\d+)?(?:[\/?#]|$)/i.exec(absoluteUrl(baseUrl, baseUrl));
    var referenced = sourceVariables(html);
    while ((fn = functions.exec(html)) && attempts++ < 64 && results.length < 32) {
        end = closingBrace(html, functions.lastIndex - 1);
        if (end < 0) continue;
        profile = seededProfile(html.slice(functions.lastIndex, end), fn[2] || fn[4]);
        functions.lastIndex = end + 1;
        if (!profile) continue;
        calls = new RegExp('(?:var|let|const)\\s+([\\w$]+)\\s*=\\s*' + (fn[1] || fn[3]).replace(/[$]/g, '\\$') + '\\s*\\(', 'g');
        callAttempts = 0;
        while ((call = calls.exec(html)) && callAttempts++ < 32) {
            /* Current pages also emit convincing unused decoder results. */
            if (!Object.prototype.hasOwnProperty.call(referenced, call[1])) continue;
            literal = readLiteral(html, calls.lastIndex, 0);
            if (!literal) continue;
            i = skipSpace(html, literal.end);
            parts = literal.value;
            if (typeof parts === 'string') {
                if (html.slice(i, i + 6) !== '.split') continue;
                i = skipSpace(html, i + 6);
                if (html.charAt(i++) !== '(') continue;
                delimiter = readLiteral(html, i, 0);
                if (!delimiter || typeof delimiter.value !== 'string' || !delimiter.value.length || delimiter.value.length > 8) continue;
                i = skipSpace(html, delimiter.end);
                if (html.charAt(i++) !== ')') continue;
                parts = parts.split(delimiter.value);
            }
            if (Object.prototype.toString.call(parts) !== '[object Array]' || html.charAt(skipSpace(html, i)) !== ')') continue;
            try {
                decoded = decodeSeeded(parts, profile, hostname ? hostname[1] : '');
                results.push({ value: decoded.value, steps: decoded.steps, format: profile.format, variable: call[1] });
            } catch (ignoredSeeded) {}
        }
    }
    return results;
}

function attributes(tag) {
    var result = {};
    var re = /([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
    var m;
    while ((m = re.exec(tag))) result[m[1].toLowerCase()] = htmlEntities(m[2] !== undefined ? m[2] : m[3] !== undefined ? m[3] : m[4] !== undefined ? m[4] : '');
    return result;
}

function subtitleLanguage(label) {
    var name = trim(label).toLowerCase();
    var known = { turkish: 'tr', 'türkçe': 'tr', turkce: 'tr', english: 'en', french: 'fr', 'français': 'fr', portuguese: 'pt', spanish: 'es', 'español': 'es', german: 'de', arabic: 'ar', russian: 'ru', italian: 'it' };
    return known[name] || (/^[a-z]{2,3}(?:-[a-z]{2})?$/i.test(name) ? name : 'und');
}

function extractPlayer(html, baseUrl) {
    var result = { streams: [], provisional: [], subtitles: [] };
    var seenStreams = {};
    var seenSubs = {};
    var seenProvisional = {};
    var scripts;
    var work;
    var i;
    var j;
    var re;
    var m;
    var attrs;
    var decoders;
    var seeded;
    var candidates;
    var literal;
    var arr;
    var obj;
    var ld;
    if (typeof html !== 'string' || html.length > MAX_INPUT) return result;
    function addStream(value, quality, provisional, forceMedia) {
        var url = absoluteUrl(value, baseUrl);
        var stream;
        var seen = provisional ? seenProvisional : seenStreams;
        var target = provisional ? result.provisional : result.streams;
        if (!url || (!provisional && !forceMedia && !looksLikeMedia(url)) || seen[url] || target.length >= MAX_ITEMS) return;
        seen[url] = true;
        stream = { url: url };
        if (quality && /^(?:\d{3,4}p?|4k|8k|hd|sd|auto)$/i.test(String(quality))) stream.quality = String(quality);
        target.push(stream);
    }
    function addSubtitle(value, label, lang, isDefault) {
        var url = absoluteUrl(value, baseUrl);
        if (!url || seenSubs[url] || result.subtitles.length >= MAX_ITEMS) return;
        seenSubs[url] = true;
        result.subtitles.push({ id: 'hdfc-' + result.subtitles.length, url: url, label: label || '', lang: subtitleLanguage(lang || label), default: !!isDefault });
    }
    function readLd(value, depth) {
        var k;
        if (!value || typeof value !== 'object' || depth > 12) return;
        if (typeof value.contentUrl === 'string') addStream(value.contentUrl, null, true);
        if (Object.prototype.toString.call(value) === '[object Array]') for (k = 0; k < value.length && k < MAX_ITEMS; k++) readLd(value[k], depth + 1);
        else if (value['@graph']) readLd(value['@graph'], depth + 1);
    }
    re = /<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi;
    while ((m = re.exec(html))) {
        attrs = attributes(m[1]);
        if (/^application\/ld\+json(?:;|$)/i.test(attrs.type || '')) {
            try { ld = JSON.parse(m[2]); readLd(ld, 0); } catch (ignoredLd) {}
        }
    }
    work = html.replace(/<script\b[^>]*type\s*=\s*(['"])application\/ld\+json\1[^>]*>[\s\S]*?<\/script\s*>/gi, '');
    scripts = [work].concat(unpackScripts(work));
    re = /<(source|video|track)\b([^>]*)>/gi;
    while ((m = re.exec(work))) {
        attrs = attributes(m[2]);
        if (m[1].toLowerCase() === 'track') {
            if (!attrs.kind || /^(?:captions|subtitles)$/i.test(attrs.kind)) addSubtitle(attrs.src, attrs.label, attrs.srclang, Object.prototype.hasOwnProperty.call(attrs, 'default'));
        } else addStream(attrs.src, attrs.label || attrs.res || attrs.size, false, /^(?:video\/|application\/(?:vnd\.apple\.mpegurl|x-mpegurl|dash\+xml))/i.test(attrs.type || ''));
    }
    if (scripts.length > 1) {
        seeded = parseSeededDecoders(scripts.join('\n'), baseUrl);
        for (j = 0; j < seeded.length; j++) addStream(seeded[j].value);
    }
    for (i = 0; i < scripts.length; i++) {
        seeded = parseSeededDecoders(scripts[i], baseUrl);
        for (j = 0; j < seeded.length; j++) addStream(seeded[j].value);
        decoders = parseInlineDecoder(scripts[i]);
        for (j = 0; j < decoders.length; j++) {
            try { addStream(applyDecodeSteps(decoders[j].parts, decoders[j].steps)); } catch (ignoredDecode) {}
        }
        candidates = legacyCandidates(scripts[i]);
        for (j = 0; j < candidates.length; j++) addStream(candidates[j]);
        re = /(?:["']?(sources|tracks)["']?)\s*:\s*/g;
        while ((m = re.exec(scripts[i]))) {
            literal = readLiteral(scripts[i], re.lastIndex, 0);
            if (!literal || Object.prototype.toString.call(literal.value) !== '[object Array]') continue;
            arr = literal.value;
            for (j = 0; j < arr.length; j++) {
                obj = arr[j];
                if (!obj || typeof obj !== 'object') continue;
                if (m[1] === 'tracks') {
                    if (!obj.kind || /^(?:captions|subtitles)$/i.test(obj.kind)) addSubtitle(obj.file || obj.src, obj.label, obj.srclang || obj.language, obj.default);
                } else addStream(obj.file || obj.src, obj.label || obj.quality || obj.res, false, /^(?:hls|dash|video\/|application\/)/i.test(obj.type || ''));
            }
        }
        /* A stand-alone JW file property also appears outside sources arrays. */
        re = /(?:["']?(?:file|src)["']?)\s*:\s*(["'])/g;
        while ((m = re.exec(scripts[i]))) {
            literal = readString(scripts[i], re.lastIndex - 1);
            if (literal) addStream(literal.value);
        }
    }
    for (i = 0; i < result.streams.length; i++) if (result.subtitles.length) result.streams[i].subtitles = result.subtitles.slice();
    for (i = 0; i < result.provisional.length; i++) if (result.subtitles.length) result.provisional[i].subtitles = result.subtitles.slice();
    return result;
}

module.exports = {
    extractPlayer: extractPlayer,
    _internals: { absoluteUrl: absoluteUrl, parseInlineDecoder: parseInlineDecoder, applyDecodeSteps: applyDecodeSteps, unpackJS: unpackJS, unpackScripts: unpackScripts, base64Decode: base64Decode, readLiteral: readLiteral, legacyCandidates: legacyCandidates, seededProfile: seededProfile, decodeSeeded: decodeSeeded, parseSeededDecoders: parseSeededDecoders }
};

},
"index": function(module, exports, require) {
'use strict';
var u = require("utils");
var http = require("http");
var catalog = require("catalog");
var player = require("player");
var media = require("media");

// Defaults can also be edited here and rebuilt for clients without a settings UI.
var DEFAULT_SITE = 'https://www.hdfilmcehennemi.nl';
var DEFAULT_EMBED = 'https://hdfilmcehennemi.mobi';
var DEFAULT_TMDB_KEY = '';

function config() {
  var root = typeof globalThis !== 'undefined' ? globalThis : {};
  var settings = root.SCRAPER_SETTINGS || {};
  return {
    site: u.siteUrl(settings.siteUrl || DEFAULT_SITE) || DEFAULT_SITE,
    embed: u.siteUrl(settings.embedUrl || DEFAULT_EMBED) || DEFAULT_EMBED,
    key: String(settings.tmdbApiKey || root.TMDB_API_KEY || DEFAULT_TMDB_KEY).trim()
  };
}

function metadata(id, type, cfg) {
  if (!cfg.key) return Promise.reject(new Error('No TMDB key available. Set tmdbApiKey in provider settings, or DEFAULT_TMDB_KEY in src/index.js and rebuild.'));
  var token = cfg.key.length > 40;
  var url = 'https://api.themoviedb.org/3/' + type + '/' + encodeURIComponent(id) + '?language=tr-TR&append_to_response=external_ids';
  if (!token) url += '&api_key=' + encodeURIComponent(cfg.key);
  return http.request(url, { json: true, bearer: token ? cfg.key : '', session: cfg.session }).then(function (data) {
    var imdb = data.external_ids && data.external_ids.imdb_id || data.imdb_id || '';
    return {
      type: type,
      title: data.title || data.name || '',
      originalTitle: data.original_title || data.original_name || '',
      year: Number(String(data.release_date || data.first_air_date || '').slice(0, 4)) || null,
      imdb: /^tt\d{7,10}$/.test(imdb) ? imdb : ''
    };
  });
}

function search(meta, cfg) {
  var queries = [meta.imdb, meta.title, meta.originalTitle].filter(Boolean);
  queries = queries.filter(function (query, index) { return queries.indexOf(query) === index; });
  function next(index) {
    if (index >= queries.length) return Promise.resolve([]);
    var query = queries[index];
    return http.request(cfg.site + '/search/?q=' + encodeURIComponent(query), { json: true, search: true, referer: cfg.site + '/', session: cfg.session }).then(function (data) {
      var matches = catalog.rankResults(catalog.parseResults(data, cfg.site), meta, query === meta.imdb);
      if (!matches.length) return next(index + 1);
      return matches.slice(0, 2).map(function (match) {
        match.referer = cfg.site + '/search/?q=' + encodeURIComponent(query);
        return match;
      });
    }).catch(function (error) {
      if (error.hdfcBlocked || /budget exhausted/i.test(error.message)) throw error;
      return next(index + 1);
    });
  }
  return next(0);
}

function formatStream(stream, frame, meta) {
  var embedOrigin = u.origin(frame.url);
  var headers = { Referer: embedOrigin + '/', Origin: embedOrigin, 'User-Agent': http.USER_AGENT };
  var subtitles = (stream.subtitles || []).map(function (sub, index) {
    return { id: sub.id || 'hdfc-' + index, url: sub.url, language: sub.language || sub.lang || 'und', name: sub.name || sub.label || sub.language || 'Subtitle', headers: headers };
  });
  var extension = /\.(m3u8|mp4|mkv|mpd|webm|mov|m4v)(?:[?#]|$)/i.exec(stream.url);
  var kind = extension ? extension[1].toLowerCase() : 'hls';
  if (kind === 'm3u8') kind = 'hls';
  if (kind === 'mpd') kind = 'dash';
  return {
    name: 'HDFilmCehennemi',
    provider: 'HDFilmCehennemi',
    title: meta.title + ' | ' + frame.name + ' | TR / original audio when available',
    url: stream.url,
    quality: stream.quality || 'Auto',
    type: kind,
    headers: headers,
    subtitles: subtitles
  };
}

// Resolve as soon as one task produces a usable source. Rejections are handled
// individually, and an empty early response cannot hide a slower working one.
function firstUsable(tasks) {
  if (!tasks.length) return Promise.resolve([]);
  return new Promise(function (resolve) {
    var remaining = tasks.length, finished = false;
    tasks.forEach(function (task) {
      Promise.resolve().then(task).catch(function () { return []; }).then(function (streams) {
        if (!finished && streams && streams.length) { finished = true; resolve(streams); }
        remaining--;
        if (!finished && !remaining) { finished = true; resolve([]); }
      });
    });
  });
}

function verifyStream(stream, frame, cfg, meta) {
  var formatted = formatStream(stream, frame, meta);
  var file = /\.(mp4|m4v|mov|mkv|webm)(?:[?#]|$)/i.exec(stream.url);
  var options = { referer: formatted.headers.Referer, origin: formatted.headers.Origin, session: cfg.session, responseInfo: true };
  if (file) { options.binary = true; options.headers = { Range: 'bytes=0-63' }; }
  return http.request(stream.url, options).then(function (response) {
    if (file) {
      var bytes = response.data || [];
      var mp4 = bytes.length >= 12 && String.fromCharCode.apply(null, bytes.slice(4, 8)) === 'ftyp';
      var ebml = bytes.length >= 4 && bytes[0] === 26 && bytes[1] === 69 && bytes[2] === 223 && bytes[3] === 163;
      if (response.status !== 206 || !/^bytes 0-63\/\d+$/i.test(response.contentRange) || response.receivedBytes !== 64) return [];
      if (!(/^(?:mkv|webm)$/i.test(file[1]) ? ebml : mp4)) return [];
    } else {
      var hls = media.hls(response.body), dash = hls ? null : media.dash(response.body);
      var info = hls || dash;
      if (!info) return [];
      formatted.type = hls ? 'hls' : 'dash';
      formatted.quality = info.quality || 'Auto';
    }
    return [formatted];
  }).catch(function () { return []; });
}

function extractPage(html, pageUrl, cfg, meta) {
  var frames = catalog.players(html, pageUrl, cfg.embed);
  var local = player.extractPlayer(html, pageUrl);
  var provisional = local.provisional.map(function (stream) { return { frame: { url: pageUrl, name: 'Direct' }, stream: stream }; });
  var errors = [];
  var tasks = local.streams.slice(0, 4).map(function (stream) {
    return function () { return verifyStream(stream, { url: pageUrl, name: 'Direct' }, cfg, meta); };
  });
  frames.forEach(function (frame) {
    tasks.push(function () {
      return http.request(frame.url, { referer: pageUrl, session: cfg.session, responseInfo: true }).then(function (response) {
        // The site's decoder binds its salt to the actual player hostname.
        var current = { url: response.finalUrl || frame.url, name: frame.name };
        var data = player.extractPlayer(response.body, current.url);
        data.provisional.slice(0, 4).forEach(function (stream) { provisional.push({ frame: current, stream: stream }); });
        return firstUsable(data.streams.slice(0, 4).map(function (stream) {
          return function () { return verifyStream(stream, current, cfg, meta); };
        }));
      }).catch(function (error) {
        if (error.hdfcSafe && !error.hdfcCancelled) errors.push(error.message);
        return [];
      });
    });
  });
  return firstUsable(tasks).then(function (streams) {
    if (streams.length) return streams;
    // Metadata links can be stale. Only inspect a bounded playlist fallback
    // after actual players fail, never a metadata video file.
    return firstUsable(provisional.filter(function (item) { return !/\.(?:mp4|mkv|webm|mov|m4v)(?:[?#]|$)/i.test(item.stream.url); }).slice(0, 2).map(function (item) {
      return function () { return verifyStream(item.stream, item.frame, cfg, meta); };
    })).then(function (fallback) {
      if (!fallback.length && errors.length) console.warn('[HDFilmCehennemi] ' + errors[0]);
      return fallback;
    });
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  if (!/^\d+$/.test(String(tmdbId)) || ['movie', 'tv'].indexOf(mediaType) === -1) return Promise.resolve([]);
  if (mediaType === 'tv' && (!/^\d+$/.test(String(season)) || !/^\d+$/.test(String(episode)) || Number(season) < 0 || Number(episode) < 1)) return Promise.resolve([]);
  var cfg = config();
  cfg.session = http.session();
  return metadata(String(tmdbId), mediaType, cfg).then(function (meta) {
    return search(meta, cfg).then(function (matches) {
      function tryMatch(index) {
        if (index >= matches.length) return Promise.resolve([]);
        var match = matches[index];
        return http.request(match.url, { referer: match.referer || cfg.site + '/', session: cfg.session }).then(function (html) {
          if (mediaType === 'movie') return { html: html, url: match.url };
          var target = catalog.episodeUrl(html, match.url, season, episode);
          if (!target) return null;
          return http.request(target, { referer: match.url, session: cfg.session }).then(function (body) { return { html: body, url: target }; });
        }).then(function (page) {
          if (!page) return [];
          return extractPage(page.html, page.url, cfg, meta);
        }).then(function (streams) { return streams.length ? streams : tryMatch(index + 1); }).catch(function (error) {
          if (error.hdfcBlocked || /budget exhausted/i.test(error.message)) throw error;
          console.warn('[HDFilmCehennemi] ' + error.message);
          return tryMatch(index + 1);
        });
      }
      return tryMatch(0);
    });
  }).then(function (streams) {
    http.cancel(cfg.session);
    var seen = {};
    return streams.filter(function (stream) { if (seen[stream.url]) return false; seen[stream.url] = true; return true; });
  }).catch(function (error) {
    http.cancel(cfg.session);
    // Never print request URLs: TMDB query parameters and CDN links may contain tokens.
    console.warn('[HDFilmCehennemi] ' + (error.hdfcSafe ? error.message : /No TMDB key/.test(error.message || '') ? 'No TMDB key available. Enter your own key in plugin settings.' : 'Source could not be resolved.'));
    return [];
  });
}

function onSettings() {
  return Promise.resolve([
    { type: 'header', label: 'HDFilmCehennemi' },
    { type: 'text', key: 'siteUrl', label: 'Website address', defaultValue: DEFAULT_SITE, description: 'HTTPS site origin. Change this if the website moves.' },
    { type: 'text', key: 'embedUrl', label: 'Player address', defaultValue: DEFAULT_EMBED, description: 'HTTPS player origin used for alternative sources.' },
    { type: 'text', key: 'tmdbApiKey', label: 'TMDB API key (optional)', isPassword: true, defaultValue: '', description: 'Uses Nuvio\'s TMDB key when available. Otherwise enter your own v3 API key or API Read Access Token.' }
  ]);
}

module.exports = { getStreams: getStreams, onSettings: onSettings };

}
};
var cache = {};
function load(id) {
 if (cache[id]) return cache[id].exports;
 if (!modules[id]) throw new Error("Unknown module: " + id);
 var local = { exports: {} }; cache[id] = local;
 modules[id](local, local.exports, load);
 return local.exports;
}
module.exports = load("index");
}());
