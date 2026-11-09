# 규격서 HWP 생성 도구

hwplib 1.1.11 에는 체크박스(FORM_OBJECT) 길이 필드를 바이트 수로 쓰는 버그가 있어, 원본 jar 로 쓴 HWP 는 한글에서 안 열린다.
`hwplib-patch/classes` 를 jar 보다 앞에 두고 실행한다 (패치 4개: ForControlForm, PropertySet, ForFileHeader, ForDocInfo — 소스는 hwplib-patch/src).

    cd kgct/tools
    CP=hwplib-patch/classes:hwplib-1.1.11.jar          # Windows 는 ; 로 구분
    java -cp $CP Fill.java template/KGcT_template_from_INTapt_scrubbed.hwp ../spec/시험구성도_ALINMed_V1.0.png ../spec/의뢰자제시기준규격서_ALINMed_V1.0.hwp
    java -cp $CP Dump.java ../spec/의뢰자제시기준규격서_ALINMed_V1.0.hwp     # 문단 트리 확인
    python make_diagram.py ../spec fonts                                        # 구성도 pptx + png 다시 그리기 (matplotlib, python-pptx)

- `[입력…]` 값: `Fill.java` 맨 위 상수(NIH, REPO, ENV, CKPT, CK_FAIR, CK_NOFAIR, CK_SIZE, RUN_MIN)와 `main()` 의 머리표·5.1·5.3·서명 줄.
- `Scrub.java`: [첨부2] 원본에서 개인정보를 지워 템플릿을 만든 도구 (이미 적용됨).
- `hwpcmp.py a.hwp b.hwp`: 두 HWP 의 레코드 비교, `hwpdump.py x.hwp`: 텍스트 덤프 (olefile 필요).
- 패치 재컴파일: `cd hwplib-patch/src && java ../../Compile.java ../../hwplib-1.1.11.jar ../classes $(find . -name "*.java")`
