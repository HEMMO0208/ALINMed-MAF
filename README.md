# MAF 추가 알고리즘 — ALINMed

MSIT AI FAIR(MAF) 프레임워크에 추가할 **흉부 X선(기흉) 진단 AI용 공정성(Fairness) 알고리즘**입니다.
첨부받은 MAF2024_guide 양식(결과를 `aifData`로 변환 + `metrics.common_utils`)을 따릅니다.

> **제출 알고리즘: ② 그룹 균형 리샘플링 (pre-processing)**
> 학습 데이터를 (라벨 × 성별) 그룹별로 균형 맞춰 샘플링해, 적게 학습되던 집단도 골고루 학습시킵니다.

| 폴더/파일 | 내용 |
|---|---|
| `algorithms/preprocessing/SubgroupBalancedResampling.py` | ② 실행 파일 (MAF 형식: baseline/② 결과를 `aifData` → `common_utils.compute_metrics`) |
| `performance_targets/target_push.py` | 학습·평가 코드 (위 실행 파일이 그대로 호출) |
| `performance_targets/download_nih_subset.py` | 영상 다운로드 |
| `performance_targets/data/` | Four-Findings 전문가 판독 라벨 CSV |
| `performance_targets/results/` | 결과 JSON·로그 |
| `metrics/common_utils.py` | AIF360 지표 (MAF2024_guide 재사용) |

---

## 쉬운 설명

기흉 AI가 평균적으로 잘 맞혀도, **특정 성별에서만 자주 틀리면** 공정하지 않습니다.
AI는 학습 때 **많이 본 집단은 잘, 적게 본 집단은 못** 맞히므로, ②는 (라벨 × 성별) 그룹마다 학습 사례가 비슷하게 보이도록
적은 그룹을 **더 자주 뽑아** 학습합니다. (학습 '이전' 단계)

공정성 지표 **DEO** = \|TPR_여 − TPR_남\| × 100 (원 과제 `train.py`의 `evaluate_DEO` 정의 그대로).

---

## 결과 — Four-Findings 테스트 438장

rad-DINO, 학습 시드 1·2·3·4 평균. 「② 미적용」은 같은 모델을 일반 셔플로 학습한 것, 「② 적용」은 그룹 균형 리샘플링으로 학습한 것입니다.

| 성과지표 (목표) | ② 미적용 | ② 적용 | 판정 |
|---|---|---|---|
| 예측정확도 (≥ 94%) | 95.09 | **95.32** | ✅ |
| 공정성 DEO (≤ 5%) | 0.80 | **0.64** | ✅ |
| 정책반영 성능평균하락률 (≤ 3%) | — | **−0.24** | ✅ |

- 정책반영 성능평균하락률 = (정확도_미적용 − 정확도_적용) / 정확도_미적용 × 100. 음수는 ② 적용 모델이 더 정확하다는 뜻입니다.
- DEO는 낮을수록 공정합니다. ② 적용으로 평균 0.80 → 0.64로 낮아졌습니다.
- 모든 수치는 결정적(deterministic) 모드로 측정했습니다. 같은 환경에서 같은 명령을 다시 실행하면 같은 값이 나옵니다(아래 「재현성」).
- 시드별 결과와 레시피는 `performance_targets/README.md`.

---

## 실행 환경 · 설치 · 실행
- 검증 환경: Python 3.10, Linux, CUDA 12.1, GPU 1장(RTX 2080 Ti 11GB). 버전 상세는 `environment.txt`.

```bash
pip install -r requirements.txt
python performance_targets/download_nih_subset.py     # Four-Findings 영상 4,376장 (이미 있으면 NIH_ROOT=<경로>)

python algorithms/preprocessing/SubgroupBalancedResampling.py                       # rad-DINO + ②, 시드 3 (기본값)
python algorithms/preprocessing/SubgroupBalancedResampling.py --seed 1              # 다른 학습 시드 (1, 2, 4)
```
각 실행은 ② 미적용·적용 모델을 학습하고, 전후 지표(`metrics_orig`, `metrics_transf`)와 성과지표 3종을 출력합니다.
결과 JSON은 `performance_targets/results/`에 저장됩니다. 1회 약 39분(GPU 1장).

---

## 데이터
| 데이터 | 받는 곳 | 비고 |
|---|---|---|
| Four-Findings 전문가 판독 라벨 (4,376장) | `performance_targets/data/` 동봉 | NIH ChestX-ray14의 전문가 재판독 라벨 (Google, Majkowska et al., *Radiology* 2020) |
| NIH ChestX-ray14 영상 | HuggingFace `alkzar90/NIH-Chest-X-ray-dataset` (원본: NIH Clinical Center) | `download_nih_subset.py`가 필요한 4,376장만 추출 (전송 약 45GB, 보관 약 2GB) |

- 분할: 원 과제 `train.py`와 동일한 이미지 단위 분할 `train_test_split(test_size=0.10, random_state=42)` → 학습 3,938 / 테스트 438
  (테스트 기흉 양성 30장: 여 16 / 남 14).

---

## 재현성
- 버전: `environment.txt` (torch 2.3.0+cu121, transformers 4.44.2, scikit-learn 1.7.2, numpy 2.2.6, aif360 0.6.1, Pillow 12.3.0),
  rad-DINO revision `110cbc18d5133582e320b43d53bf5c44e410c936` (코드에 고정).
- **결정적 모드(기본 적용)**: 난수 시드 고정에 더해 cuDNN 결정적 연산, `torch.use_deterministic_algorithms(True)`,
  `CUBLAS_WORKSPACE_CONFIG=:4096:8`, DataLoader·샘플러 난수 생성기 고정을 적용합니다. 별도 설정 없이 위 명령만 실행하면 됩니다.
  같은 시드·같은 명령으로 반복 실행했을 때 결과 JSON과 로그가 완전히 일치함을 확인했습니다(시드 3, 2회).
- **일치 조건**: 같은 GPU 모델과 같은 라이브러리 버전(`environment.txt`)에서 반복 실행 결과가 같습니다.
  GPU 모델, CUDA/cuDNN, PyTorch 버전이 다르면 부동소수점 연산 순서가 달라져 수치가 조금 다를 수 있습니다
  (테스트 438장이라 1장 = 0.23%p). 그 경우에도 그 환경 안에서는 매번 같은 값이 나옵니다.
- `--nondeterministic`을 붙이면 결정적 모드를 끄고 이전 방식으로 실행합니다(실행마다 결과가 달라질 수 있음).

## MAF 통합 / 대상 버전
- 첨부받은 **MAF2024_guide** 양식 기준이며 `metrics/common_utils.py`를 자체 포함해 독립 실행됩니다.
- **MAF2025**가 지표를 `MAF.metric` 경로로 제공한다면 `from metrics import common_utils`를 해당 경로로 바꾸고
  같은 `compute_metrics`에 연결하면 됩니다(AIF360 `ClassificationMetric` 기반으로 의미 동일).

## 라이선스
MIT (`LICENSE`). `metrics/common_utils.py`와 `aifData` 클래스는 MAF2024_guide 코드를 재사용했습니다.
