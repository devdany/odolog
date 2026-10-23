#!/usr/bin/env python3
"""Pleos 문서(document.pleos.ai) 본문 추출기.

일반 가이드 페이지는 서버렌더링이라 <article>만 뽑으면 되지만,
SDK API 레퍼런스(dokka) 페이지는 본문이 Docusaurus 청크 JS에 들어 있어 별도 처리.

사용: python3 tools/pleos_doc.py docs/pleos-only/connect-sdk-pleos/Vehicle/intro [더 많은 경로...]
경로 목록: https://document.pleos.ai/sitemap.xml (호스트 무시, 경로만 사용)
"""
import html, json, re, sys, urllib.request

BASE = "https://document.pleos.ai"
_bundles = None


def get(url):
    return urllib.request.urlopen(url).read().decode()


def clean(s):
    s = re.sub(r"<script.*?</script>|<style.*?</style>", "", s, flags=re.S)
    s = re.sub(r"<(br|/p|/li|/h\d|/tr|/div|/td|/th|/pre|/dt|/dd)[^>]*>", "\n", s)
    s = html.unescape(re.sub(r"<[^>]+>", " ", s))
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n\s*\n+", "\n", s)
    return "\n".join(l for l in s.splitlines() if "copied to clipboard" not in l).strip()


def bundles(page_html):
    global _bundles
    if _bundles is None:
        main = re.search(r"src=\"?(/assets/js/main\.[0-9a-f]+\.js)", page_html).group(1)
        rt = re.search(r"src=\"?(/assets/js/runtime~main\.[0-9a-f]+\.js)", page_html).group(1)
        _bundles = (get(BASE + main), get(BASE + rt))
    return _bundles


def fetch(path):
    path = "/" + path.strip("/")
    page = get(BASE + path)
    i = page.find("<article")
    art = page[i:page.find("pagination-nav", i)] if i >= 0 else page
    if "dokka-transform-contents" not in art:
        return clean(art)
    main, rt = bundles(page)
    m = re.search(r'"' + re.escape(path) + r'-[0-9a-f]{3}":\{[^}]*"content":"([0-9a-f]+)"', main)
    if not m:
        return "!! route not found: " + path
    cid = m.group(1)
    chunk = re.search(r'"(\d+)":\s*"' + cid + '"', rt).group(1)
    h = [x for x in re.findall(r'"' + chunk + r'":\s*"([0-9a-f]{8})"', rt) if x != cid][0]
    js = get(f"{BASE}/assets/js/{cid}.{h}.js")
    raws = re.findall(r'export default \*/ (?:const \w+ = )?\(?("(?:[^"\\]|\\.)*")', js, re.S)
    return clean(" ".join(json.loads(r) for r in raws) if raws else js)


if __name__ == "__main__":
    for p in sys.argv[1:]:
        print("########", p)
        print(fetch(p))
