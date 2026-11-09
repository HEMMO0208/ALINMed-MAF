"""시험구성도: 편집 가능한 pptx(원본)와 규격서용 PNG를 같은 좌표로 만든다."""
import sys, os
OUT = sys.argv[1]
FONT_DIR = sys.argv[2]

# ---- layout (inches, slide 13.333 x 7.5) ---------------------------------
W, H = 14.4, 5.992   # = HWP 그림 틀 비율 45263:18835
BOXES = [
    # key, x, y, w, h, fill, line, bold, dashed, lines
    ("pc", 0.2, 1.55, 2.5, 2.3, "FFFFFF", "404040", False, False,
     ["시험용 PC (노트북)", "", "Visual Studio Code", "+ Remote-SSH 확장", "", "※ 시험원 조작·결과 확인"]),
    ("srv", 4.6, 0.25, 9.5, 5.15, "F2F2F2", "404040", False, False, []),
    ("sw", 5.0, 0.95, 8.7, 1.95, "DCE9F7", "1F4E9A", True, False,
     ["시험 대상 SW: ALINMed V1.0  (Python, conda 가상환경)",
      "SubgroupBalancedResampling.py  →  target_push.py",
      "학습 완료 모델 2개 로드(리샘플링 적용 / 미적용)  →  테스트 438장 평가",
      "→ 예측정확도 · DEO · 정책반영 성능평균하락률 출력"]),
    ("data", 5.0, 3.4, 4.2, 1.8, "FFFFFF", "404040", False, False,
     ["시험 데이터 (서버 로컬 디스크)", "", "NIH ChestX-ray14 영상", "4,376장 (PNG)",
      "Four-Findings 라벨 CSV 2개"]),
    ("w", 9.5, 3.4, 4.2, 1.8, "FFFFFF", "404040", False, False,
     ["학습 완료 모델 / GPU", "", "fair.pt · nofair.pt (체크포인트)", "rad-DINO 구성 파일 (HF 캐시)",
      "GPU: NVIDIA RTX 2080 Ti 11GB"]),
]
SRV_TITLE = "시험용 서버 (연구실 GPU 서버, Ubuntu)"

ARROWS = [  # x1,y1,x2,y2,label,label_xy
    (2.7, 2.7, 5.0, 2.7, "SSH (TCP/IP, 22번)\n명령 입력 ↔ 결과 화면", (3.62, 2.15)),
    (7.1, 3.4, 7.1, 2.9, "파일 읽기", (7.75, 3.15)),
    (11.6, 3.4, 11.6, 2.9, "모델 로드·연산", (12.45, 3.15)),
]

# ---- pptx ----------------------------------------------------------------
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from lxml import etree

prs = Presentation()
prs.slide_width, prs.slide_height = Inches(W), Inches(H)
s = prs.slides.add_slide(prs.slide_layouts[6])

def txt(tf, lines, size=14, bold_first=True, color="000000", align=PP_ALIGN.CENTER):
    tf.word_wrap = True
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        r = p.add_run(); r.text = ln
        r.font.size = Pt(size); r.font.name = "맑은 고딕"
        r.font.bold = bold_first and i == 0
        r.font.color.rgb = RGBColor.from_string(color)

for key, x, y, w, h, fill, line, bold, dashed, lines in BOXES:
    sh = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    sh.adjustments[0] = 0.06
    sh.fill.solid(); sh.fill.fore_color.rgb = RGBColor.from_string(fill)
    sh.line.color.rgb = RGBColor.from_string(line)
    sh.line.width = Pt(3 if bold else 1.5)
    sh.shadow.inherit = False
    tf = sh.text_frame; tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    if lines:
        txt(tf, lines, size=15 if key == "sw" else 14)
    if key == "srv":
        tf.vertical_anchor = MSO_ANCHOR.TOP
        txt(tf, [SRV_TITLE], size=16)

for x1, y1, x2, y2, label, (lx, ly) in ARROWS:
    c = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    c.line.color.rgb = RGBColor(0x40, 0x40, 0x40); c.line.width = Pt(2)
    ln = c.line._get_or_add_ln()
    for end in ("headEnd", "tailEnd"):
        e = etree.SubElement(ln, qn(f"a:{end}")); e.set("type", "triangle")
    tb = s.shapes.add_textbox(Inches(lx - 0.92), Inches(ly - 0.35), Inches(1.84), Inches(0.7))
    txt(tb.text_frame, label.split("\n"), size=11, bold_first=False, color="1F1F1F")

leg = s.shapes.add_textbox(Inches(0.3), Inches(5.45), Inches(13.8), Inches(0.4))
txt(leg.text_frame, ["굵은 파란 테두리 = 시험 대상 소프트웨어(ALINMed V1.0)가 설치되는 위치. "
                     "SW는 서버 한 대에 단일 구성으로 설치되며, 시험용 PC는 SSH로만 접속한다."],
    size=12, bold_first=False, align=PP_ALIGN.LEFT)
prs.save(os.path.join(OUT, "시험구성도_ALINMed_V1.0.pptx"))

# ---- png (same coordinates) ----------------------------------------------
import matplotlib
matplotlib.use("Agg")
from matplotlib import font_manager as fm, pyplot as plt
from matplotlib.patches import FancyBboxPatch
for f in os.listdir(FONT_DIR):
    fm.fontManager.addfont(os.path.join(FONT_DIR, f))
plt.rcParams["font.family"] = "NanumGothic"

fig = plt.figure(figsize=(W, H), dpi=200)
ax = fig.add_axes([0, 0, 1, 1]); ax.set_xlim(0, W); ax.set_ylim(H, 0); ax.axis("off")
fig.patch.set_facecolor("white")
for key, x, y, w, h, fill, line, bold, dashed, lines in BOXES:
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=0.15",
                                fc="#" + fill, ec="#" + line, lw=3 if bold else 1.5, zorder=1 if key == "srv" else 2))
    if key == "srv":
        ax.text(x + w / 2, y + 0.33, SRV_TITLE, ha="center", va="center", fontsize=16, fontweight="bold", zorder=3)
        continue
    n = len(lines); lh = 0.3
    for i, ln in enumerate(lines):
        ax.text(x + w / 2, y + h / 2 + (i - (n - 1) / 2) * lh, ln, ha="center", va="center",
                fontsize=15 if key == "sw" else 14, fontweight="bold" if i == 0 else "normal", zorder=3)
for x1, y1, x2, y2, label, (lx, ly) in ARROWS:
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="<|-|>", lw=2, color="#404040", mutation_scale=18), zorder=4)
    ax.text(lx, ly, label, ha="center", va="center", fontsize=11, zorder=5,
            bbox=dict(fc="white", ec="none", pad=1.5))
ax.text(0.3, 5.68, "굵은 파란 테두리 = 시험 대상 소프트웨어(ALINMed V1.0)가 설치되는 위치. "
        "SW는 서버 한 대에 단일 구성으로 설치되며, 시험용 PC는 SSH로만 접속한다.",
        ha="left", va="center", fontsize=12)
fig.savefig(os.path.join(OUT, "시험구성도_ALINMed_V1.0.png"), facecolor="white")
print("ok")
