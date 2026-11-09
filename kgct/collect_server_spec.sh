#!/usr/bin/env bash
# 사용법: 시험용 서버에서 가상환경을 활성화하고 ALINMed-MAF 레포 루트로 이동한 뒤
#   bash collect_server_spec.sh <NIH_ROOT> [CKPT_DIR]
# 결과: server_spec.txt (규격서 5.1·시험조건용), nih_subset_sha256.txt (영상 4,376장 해시 목록)
NIH_ROOT="${1:?NIH_ROOT 경로를 인자로 주세요}"
CKPT_DIR="${2:-}"
{
echo "## CPU";  lscpu | grep -E "Model name|^CPU\(s\)"
echo "## GPU";  nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv
echo "## RAM";  free -h | head -2
echo "## DISK"; df -h --output=size,used,target "$NIH_ROOT" .
echo "## OS";   lsb_release -d; uname -r
echo "## NIC";  ip -br addr | grep -v "^lo"; ip route | grep default
echo "## ENV";  echo "$CONDA_DEFAULT_ENV $VIRTUAL_ENV"; conda --version 2>/dev/null; which python; python -V
echo "## PKGS"; pip list --format=freeze 2>/dev/null
echo "## HF";   ls "${HF_HOME:-$HOME/.cache/huggingface}/hub" 2>/dev/null | grep -i rad-dino
echo "## CODE"; pwd; git log -1 --format="%H %ad"
echo "## DATA"
sha256sum performance_targets/data/*.csv
[ -n "$CKPT_DIR" ] && { echo "## CKPT"; sha256sum "$CKPT_DIR"/fair.pt "$CKPT_DIR"/nofair.pt; du -m "$CKPT_DIR"/fair.pt "$CKPT_DIR"/nofair.pt; }
python - "$NIH_ROOT" <<'EOF'
import sys, os, glob, hashlib, csv
root = sys.argv[1]
names = set()
for f in glob.glob("performance_targets/data/four_findings_expert_labels_*_labels.csv"):
    names |= {r["Image Index"] for r in csv.DictReader(open(f))}
paths = sorted(p for p in glob.glob(os.path.join(root, "images_*/images/*.png"))
               if os.path.basename(p) in names)
lines = [f"{hashlib.sha256(open(p,'rb').read()).hexdigest()}  {os.path.relpath(p, root)}" for p in paths]
open("nih_subset_sha256.txt", "w").write("\n".join(lines) + "\n")
print("images:", len(paths), "/ label names:", len(names))
print("manifest sha256:", hashlib.sha256(open("nih_subset_sha256.txt", "rb").read()).hexdigest())
print("total size MB:", round(sum(os.path.getsize(p) for p in paths) / 1e6, 1))
EOF
} 2>&1 | tee server_spec.txt
