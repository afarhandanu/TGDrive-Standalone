"""Release gate for missing translations and incomplete offline documents."""
import json
import pathlib
import re
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / 'tools/localization.json').read_text())
for source, pair in catalog.items():
    assert set(pair) == {'id', 'en'}, f'Missing locale: {source}'
    placeholders = sorted(re.findall(r'\{\d+\}', source))
    for lang, text in pair.items():
        assert text.strip(), f'Empty {lang}: {source}'
        assert sorted(re.findall(r'\{\d+\}', text)) == placeholders, f'Placeholder mismatch: {source}'

resources = []
for folder in ('values', 'values-en'):
    xml = ET.parse(ROOT / f'app/src/main/res/{folder}/localized_strings.xml')
    names = [item.attrib['name'] for item in xml.getroot()]
    assert len(names) == len(set(names)) == len(catalog), f'Duplicate or missing resource in {folder}'
    resources.append(set(names))
assert resources[0] == resources[1], 'Locale resource IDs differ'

for path in (ROOT / 'app/src/main/java/com/tgdrive/mobile').glob('*.java'):
    for match in re.finditer(r'\bt\(("(?:\\.|[^"\\])*")\)', path.read_text()):
        key = json.loads(match.group(1))
        assert key in catalog, f'Missing text in {path.name}: {key}'

for filename in ('FEATURES.md', 'CHANGELOG.md'):
    docs = [(ROOT / f'docs/i18n/{lang}/{filename}').read_text() for lang in ('id', 'en')]
    assert all(len(doc) > 1000 for doc in docs), f'Incomplete document: {filename}'
    assert len(re.findall(r'^## ', docs[0], re.M)) == len(re.findall(r'^## ', docs[1], re.M)), f'Missing section: {filename}'
    if filename == 'CHANGELOG.md':
        versions = [re.findall(r'^## (.+)$', doc, re.M) for doc in docs]
        assert versions[0] == versions[1], 'Release history differs by language'
        assert len(versions[0]) == len(set(versions[0])), 'Duplicate releases'
        assert docs[1] == (ROOT / 'CHANGELOG.md').read_text(), 'Root changelog is out of sync'

admin = (ROOT / 'app/src/main/java/com/tgdrive/mobile/AdminActivity.java').read_text()
assert 'Telegram' not in admin
print(f'PASS: {len(catalog)} paired translations, UI lookup coverage, and complete bilingual documents')
