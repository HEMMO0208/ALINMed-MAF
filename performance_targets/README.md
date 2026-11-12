# 협약 성과지표 재현 — 예측정확도 · 공정성 · 정책반영 성능하락률

과제 협약 성과지표를 측정한 학습·평가 코드와 결과입니다. 상위 폴더의 ② 실행 파일(`algorithms/preprocessing/SubgroupBalancedResampling.py`)이 이 코드를 그대로 호출합니다.

| 성과지표 | 목표 | 정의 |
|---|---|---|
| 예측정확도 | ≥ 94% | 테스트 438장 전체 정확도 (argmax@0.5) |
| 공정성 (DEO) | ≤ 5% | \|TPR_여 − TPR_남\| × 100 (원 과제 `train.py`의 `evaluate_DEO` 정의 그대로: TPR은 label==1 클래스 기준) |
| 정책반영 성능평균하락률 | ≤ 3% | (acc_공정성미적용 − acc_공정성적용) / acc_공정성미적용 × 100 |

- **벤치마크**: 원 과제와 동일한 Four-Findings 전문가 판독 라벨(테스트+검증 CSV 병합 4,376장), 이미지 단위 분할
  `train_test_split(test_size=0.10, random_state=42)` → 학습 3,938 / 테스트 438 (테스트 기흉 양성 30장: 여 16 / 남 14).
- **공정성 적용 모델**: (라벨×성별) 그룹 WeightedRandomSampler. **미적용 모델**: 일반 셔플. 두 모델을 같은 레시피로 각각 학습.
- **레시피**: rad-DINO(`microsoft/rad-dino` @ `110cbc18d5133582e320b43d53bf5c44e410c936`) 파인튜닝, 15 epoch, 강한 증강,
  클래스 가중 CE, TTA(좌우반전), lr 2e-5, batch 16.

## 결과

| 학습 시드 | 정확도: ② 미적용 | 정확도: ② 적용 | DEO: ② 미적용 | DEO: ② 적용 | 정책반영 하락률 |
|---|---|---|---|---|---|
| 1 | 94.98 | 94.52 | 0.30 | 0.75 | 0.48 |
| 2 | 95.89 | 95.21 | 0.96 | 1.14 | 0.71 |
| 3 (기본) | 94.75 | 96.12 | 0.86 | 0.18 | −1.45 |
| 4 | 94.75 | 95.43 | 1.08 | 0.47 | −0.72 |
| **평균 ± 표준편차** | **95.09 ± 0.47** | **95.32 ± 0.57** | **0.80 ± 0.30** | **0.64 ± 0.35** | **−0.24 ± 0.88** |
| 목표 | | ≥ 94 | | ≤ 5 | ≤ 3 |

- 목표: 예측정확도 ≥ 94, 공정성 DEO ≤ 5, 정책반영 하락률 ≤ 3. rad-DINO + ②는 시드별로도 평균으로도 3종 달성.
- ② = (라벨 × 성별) 그룹 균형 샘플링(WeightedRandomSampler). ② 미적용 = 같은 모델·레시피를 일반 셔플로 학습한 모델.
- 정책반영 하락률 = (정확도_미적용 − 정확도_적용) / 정확도_미적용 × 100. 음수는 ② 적용 모델이 더 정확하다는 뜻입니다.
- 테스트 438장이라 1장 = 0.23%p입니다.
- 모든 수치는 결정적 모드(기본 적용)로 측정했습니다. 기본 시드는 3입니다.
- 결과 파일: `results/target_push_raddino_e15_strong_cw_tta_det_s{1,2,3,4}.json`,
  실행 로그 `results/run_raddino_det_s{1,2,3,4}.log`.

## 재현성 (결정적 모드)
- 기본으로 켜져 있습니다: 시드 고정, cuDNN 결정적 연산(`benchmark=False`), `torch.use_deterministic_algorithms(True)`,
  `CUBLAS_WORKSPACE_CONFIG=:4096:8`, DataLoader 워커·셔플·WeightedRandomSampler 난수 생성기 고정.
- rad-DINO의 위치 임베딩 bicubic 보간은 CUDA 역전파에 결정적 구현이 없어, 순전파는 그대로 두고 역전파만 같은 선형 사상의
  전치로 계산합니다(순전파 값과 가중치 구조는 동일).
- 같은 GPU 모델·같은 라이브러리 버전에서 같은 시드로 반복 실행하면 결과 JSON과 로그가 완전히 일치합니다(시드 3, 2회 확인).
  GPU 모델이나 CUDA/cuDNN/PyTorch 버전이 다르면 수치가 조금 다를 수 있습니다.
- `--nondeterministic`: 결정적 모드를 끕니다(결과 파일 이름에서 `_det`가 빠짐).
- 실행 중 `Plan failed with a cudnnException ... CUDNN_STATUS_NOT_SUPPORTED` 경고가 나올 수 있습니다. cuDNN이 다른 연산 방식으로 넘어간다는 안내이며 결과에는 영향이 없습니다.

## 데이터 준비
1. **Four-Findings 라벨 CSV**: `data/`에 동봉 (원 과제 저장소에 포함돼 있던 파일 그대로).
   NIH ChestX-ray14의 전문가 판독 라벨(Google, Majkowska et al., *Radiology* 2020).
2. **NIH ChestX-ray14 영상**(필요한 4,376장만): HuggingFace 공개 미러
   `alkzar90/NIH-Chest-X-ray-dataset`에서 아카이브(images_001~012.zip)를 하나씩 받아 필요한 파일만 풀고 지웁니다.
   총 전송량 약 45GB, 디스크 최대 사용량은 아카이브 1개 + 추출 영상(약 2GB).
   ```bash
   python download_nih_subset.py        # -> data/datasets/nih-chest-xrays/data/versions/3/images_XXX/images/
   ```
   이미 NIH 영상이 있으면 `NIH_ROOT=<.../versions/3>` 로 지정하면 됩니다.

## 실행
```bash
pip install -r ../requirements.txt     # torch·transformers 포함
python target_push.py --backbone raddino  --epochs 15 --aug strong --fix_classweight --tta --lr 2e-5 --batch_size 16 --seed 3
python target_push.py --backbone raddino --tta --smoke   # 데이터·분할·모델 점검만 (학습 없음)
```
GPU 1장(RTX 2080 Ti 11GB) 기준 rad-DINO 1회(② 적용·미적용 2개 모델) 약 39분. 출력 마지막 줄 `TARGET CHECK`에 세 지표 판정이 나옵니다.
