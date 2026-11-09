# KGcT 공인시험 인계 — ALINMed V1.0 (2026-11-10)

kakao 클러스터 세션에서 작업한 내용을 시험 서버 쪽 세션(aidep-8c)으로 넘긴다.
**규격서 회신 마감: 2026-11-10(화)**, 회신 대상: 공인시험 담당 박사과정(유창동 교수님 연구실). 현장 시험은 11월 중순 예정.

## 1. 확정된 결정 (사용자)

| 항목 | 결정 |
|---|---|
| 시험 지표 3건 | ① 예측정확도 ≥ 94 % ② 공정성 DEO ≤ 5 % ③ 정책반영 성능평균하락률 ≤ 3 % (협약 성과지표 = 2단계 목표) |
| 대상 | 흉부 X선 기흉 분류, Four-Findings 테스트 438장 (고정 분할 `train_test_split(test_size=0.10, random_state=42)`, 기흉 30장: 여 16 / 남 14) |
| DEO 정의 | 원 과제 `evaluate_DEO` 그대로: `|TPR_여 − TPR_남| × 100`, **TPR은 label 1 = 기흉 음성(Pneumothorax != YES, 타 소견 포함) 기준**. 기흉 클래스 기준은 양성이 16/14장뿐이라 1장 = 6~7 %p 로 달성 불안정 → 채택 안 함 |
| 시험 방식 | **[첨부2] INTapt 예시와 같이 학습 완료 체크포인트 평가만** (현장 학습 안 함). 모델 2개: `fair.pt`(그룹 균형 리샘플링 적용), `nofair.pt`(미적용) |
| 영상 해시 | 4,376장 각각의 SHA-256 을 `nih_subset_sha256.txt` 한 파일로 묶고, 규격서에는 그 파일의 해시만 기재. 현장에서 `sha256sum -c` 로 전량 대조 |
| 커밋 | Claude 서명(Co-Authored-By 등) 남기지 않음 |

## 2. 코드 변경 (이 브랜치 첫 커밋)

- `performance_targets/target_push.py`
  - `--save_dir DIR`: 학습 후 `DIR/fair.pt`, `DIR/nofair.pt` (model `state_dict`) 저장, SHA-256 출력
  - `--ckpt_dir DIR`: 학습 없이 두 체크포인트를 불러와 테스트 438장만 평가. 로딩 시 `sha256=` 출력. 결과 JSON 은 `..._s42_ckpteval.json`
  - rad-DINO `revision=110cbc18d5133582e320b43d53bf5c44e410c936` 고정 (README 는 고정이라 했으나 코드에 없었음)
- `algorithms/preprocessing/SubgroupBalancedResampling.py`: `--ckpt_dir`, `--save_dir` 전달

시험 당일 명령 (규격서 6절 4단계와 동일):

    NIH_ROOT=<영상 경로> HF_HUB_OFFLINE=1 python algorithms/preprocessing/SubgroupBalancedResampling.py --backbone raddino --ckpt_dir <체크포인트 경로> 2>&1 | tee test_run.log

체크포인트 만들기 (아직 없다면, 시험 서버에서):

    NIH_ROOT=<영상 경로> python algorithms/preprocessing/SubgroupBalancedResampling.py --backbone raddino --seed 42 --save_dir <체크포인트 경로>

## 3. 지금까지의 검증 결과 (kakao A100)

| 실행 | 정확도 | DEO | 하락률 | 비고 |
|---|---|---|---|---|
| 기존 보고 (2080Ti, seed 42) | 94.52 | 0.40 | 0.24 | `performance_targets/results/` |
| kakao 리허설, 현장학습 방식 (A100, seed 42) | **94.06** | 3.29 | 1.20 | PASS, 단 정확도 여유 1장(412/438). 학습 25분 |
| 5시드 (2080Ti) | 4/5 통과 | | | seed 3 정확도 92.92 |

- 같은 시드라도 GPU/실행에 따라 값이 달라짐(cudnn 비결정성) → 체크포인트 평가로 바꾼 이유.
- 해시 (kakao 에서 확인, 영상은 `download_nih_subset.py` 로 받은 것):
  - `four_findings_expert_labels_test_labels.csv` 24911a463c0c5e40e1bcd39a5b0aa38079b89859946932fca4d5f62081a0ed9f
  - `four_findings_expert_labels_validation_labels.csv` aa4c1d092818ae9150ae72dad77e32fca070a975b9a3273a06e47f3bf7b0590d
  - `nih_subset_sha256.txt` 1376711a03a80b430e321df459a38f523dd737bf01cea637b0a779472e301464 (영상 1.7 GB, 4,376장)
  - 시험 서버의 영상이 같은 방식/같은 폴더 구조가 아니면 이 값이 달라진다 → 서버에서 다시 산출.
- 체크포인트 저장→평가 일치 검증 잡(kakao 319637)은 인계 시점에 실행 중. 결과는 메시지로 따로 보냄.

## 4. 규격서 (`kgct/spec/`)

- `의뢰자제시기준규격서_ALINMed_V1.0.hwp`: [첨부1]과 같은 양식인 [첨부2] INTapt 규격서를 틀로 만든 것. **한글에서 열림 확인됨**(이전 판). 체크포인트 방식 판은 데이터 표에 행을 하나 추가했으므로 열리는지 다시 확인 필요.
- `시험구성도_ALINMed_V1.0.pptx` (원본, 메일에서 요청) / `.png` (HWP 1.1 에 들어간 그림, 비율 2.40:1 = HWP 그림 틀)
- 남은 `[입력…]` 칸: 담당자 이름·휴대폰·서명 날짜 / 시험장소·소재지(협의 후) / 서버 CPU·RAM·STORAGE·NIC·OS·드라이버·pip list / 서버·노트북 IP·서브넷·게이트웨이 / 접속 ID / 가상환경 이름 / 코드 경로 / NIH 영상 경로 / 체크포인트 경로·용량·SHA-256 2개 / 평가 1회 소요 시간(분) / 노트북 CPU·RAM·STORAGE·NIC·OS / VS Code·Remote-SSH 버전
- 서버 값은 `kgct/collect_server_spec.sh <NIH_ROOT> <CKPT_DIR>` 출력(`server_spec.txt`)으로 채운다.

## 5. HWP 다시 만들기 (`kgct/tools/`, 자세한 것은 그 README)

- **hwplib 1.1.11 원본으로 쓰면 한글에서 안 열린다.** 체크박스(FORM_OBJECT) 길이 필드를 바이트 수로 쓰는 버그. `tools/hwplib-patch/classes` 를 jar 앞에 두고 실행할 것 (패치 4개: ForControlForm, PropertySet, ForFileHeader, ForDocInfo).
- `[입력…]` 값은 `Fill.java` 맨 위 상수(NIH, REPO, ENV, CKPT, CK_FAIR, CK_NOFAIR, CK_SIZE, RUN_MIN)와 `main()` 의 머리표·5.1·5.3·서명 줄에서 바꾼 뒤 다시 실행한다.
- 템플릿 `template/KGcT_template_from_INTapt_scrubbed.hwp` 는 [첨부2]에서 개인정보(이름·휴대폰·메일·타인 경로·문서 작성자)를 지운 것. 문단 구조는 원본과 같다(Fill 결과 본문 동일 확인).
- 결과 확인: `java -cp $CP Dump.java <hwp>` (문단 트리), `hwpcmp.py` (원본과 레코드 비교), 한글에서 직접 열어 보기.
- 필요 도구: Java 11+ (`java File.java` 실행), Python + olefile(확인용), matplotlib + python-pptx(구성도).

## 6. 남은 일

1. 시험 서버에 있는 학습 완료 체크포인트의 위치·형식 확인. 이번 `--ckpt_dir` 형식(`state_dict`, `fair.pt`/`nofair.pt`)과 다르면 변환하거나 `--save_dir` 로 새로 학습.
2. 그 체크포인트로 `--ckpt_dir` 평가 → 세 지표 통과 확인 (정확도 ≥ 94).
3. `collect_server_spec.sh` 로 서버 정보·해시 수집 → `Fill.java` 값 채우기 → HWP 재생성 → 한글에서 열기 확인.
4. 공인시험 담당자께 회신: HWP + 구성도 pptx, GitHub 통합 저장소(jiwoohong93/ai_dep) 권한 요청.
5. 최종 코드를 jiwoohong93/ai_dep 에 업로드.
