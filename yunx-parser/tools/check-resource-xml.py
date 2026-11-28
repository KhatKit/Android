#!/usr/bin/env python3
"""Static XML/key/format validation without Gradle or external services."""
from collections import Counter
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FORMAT = re.compile(r"%(?:[0-9]+\$)?[-#+ 0,(<]*[0-9]*(?:\.[0-9]+)?[a-zA-Z%]")

def read(path):
    root = ET.parse(path).getroot()
    assert root.tag == "resources", path
    records = {}
    for entry in root:
        assert entry.tag == "string", (path, entry.tag)
        key = entry.attrib["name"]
        assert key not in records, (path, "duplicate", key)
        records[key] = entry
    return records

def markup(node):
    return (node.tag, sorted(node.attrib.items()), [markup(child) for child in node])

def text(node):
    return "".join(node.itertext())

source = read(ROOT / "tools/localization/upstream-zh.xml")
pairs = []
for name in ("yunx_strings.xml", "yunx_host_strings.xml"):
    english = read(ROOT / "src/main/res/values" / name)
    chinese = read(ROOT / "src/main/res/values-zh" / name)
    assert english.keys() == chinese.keys(), (name, "locale keys differ")
    for key in english:
        en, zh = english[key], chinese[key]
        assert markup(en) == markup(zh), (key, "attributes / rich text differ")
        assert Counter(FORMAT.findall(text(en))) == Counter(FORMAT.findall(text(zh))), (key, "format differs")
        assert text(en).strip() and text(zh).strip(), (key, "empty text")
        assert not re.search(r"[\u3400-\u9fff]", text(en)), (key, "Chinese in English")
        assert not re.search(r"\b(TODO|TBD|FIXME)\b", text(en)), (key, "placeholder translation")
    if name == "yunx_strings.xml":
        assert source.keys() == chinese.keys(), "Original keys changed"
        for key in source:
            assert markup(source[key]) == markup(chinese[key]) and text(source[key]) == text(chinese[key]), (key, "Original Chinese changed")
    pairs.append(english)
allkeys = set(pairs[0]) | set(pairs[1])
assert len(allkeys) == sum(len(p) for p in pairs), "Duplicate host/upstream key"
references = set()
for file in (ROOT / "src/main/java").rglob("*.kt"):
    references.update(re.findall(r"R\.string\.(\w+)", file.read_text()))
assert references <= allkeys, sorted(references - allkeys)
wrapper = ROOT / "src/main/java/heizige/kk/khatkit/yunx/YunxParserManager.kt"
assert not re.search(r"[\u3400-\u9fff]", wrapper.read_text()), "Hardcoded host Chinese remains"
print(json.dumps({"upstream_keys_per_locale": len(pairs[0]), "host_keys_per_locale": len(pairs[1]),
    "referenced_keys": len(references), "XML_keys_formats_markup": "passed", "original_Chinese": "preserved"}, indent=2))
