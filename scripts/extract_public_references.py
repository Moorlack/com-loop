"""Run with a Python containing pypdf; record incomplete outline-only extraction."""
from pathlib import Path
import json
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1] / 'research/public'
(root/'text').mkdir(exist_ok=True)
report = []
for path in sorted((root/'sources').glob('*.pdf')):
    reader = PdfReader(path)
    pages = [page.extract_text() for page in reader.pages]
    (root/'text'/f'{path.stem}.txt').write_text('\n\n'.join(f'--- PDF PAGE {i+1} ---\n{text}' for i,text in enumerate(pages)), encoding='utf-8')
    report.append({'file':path.name, 'pages':len(pages), 'extracted_characters':sum(map(len,pages)), 'sparse_pages':[i+1 for i,p in enumerate(pages) if len(p.strip()) < 100], 'note':'Sparse text is NOT evidence of an empty PDF. Inspect rendered pages.'})
(root/'text/extraction_report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('Extracted',len(report),'PDF files; see extraction_report.json for sparse pages')
