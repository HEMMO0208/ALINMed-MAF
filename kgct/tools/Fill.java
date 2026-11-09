import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.*;
import kr.dogfoot.hwplib.object.bodytext.paragraph.charshape.CharPositionShapeIdPair;
import kr.dogfoot.hwplib.object.bodytext.control.*;
import kr.dogfoot.hwplib.object.bodytext.control.table.*;
import kr.dogfoot.hwplib.object.bodytext.control.form.properties.*;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.writer.HWPWriter;
import java.nio.file.*;
import java.util.*;

/**
 * [첨부2] INTapt V1.0 규격서(KGcT-QPF-08-03(03), 올해와 같은 양식)를 기반으로
 * ALINMed V1.0 규격서를 만든다.  java -cp hwplib.jar Fill.java <in.hwp> <diagram.png> <out.hwp>
 * '[입력: …]' = 서버/노트북/담당자 정보를 받아 채울 자리.
 */
public class Fill {
    // ------------------------------------------------------------------ 경로 탐색
    static Object walk(ParagraphListInterface root, String path) {
        Object cur = root;
        for (String tok : path.split("/")) {
            if (tok.startsWith("p")) {
                cur = ((ParagraphListInterface) cur).getParagraph(Integer.parseInt(tok.substring(1)));
            } else if (tok.startsWith("c")) {
                cur = ((Paragraph) cur).getControlList().get(Integer.parseInt(tok.substring(1)));
            } else if (tok.startsWith("r")) {
                String[] rc = tok.substring(1).split("c");
                cur = cell((ControlTable) cur, Integer.parseInt(rc[0]), Integer.parseInt(rc[1])).getParagraphList();
            }
        }
        return cur;
    }
    static Paragraph P(ParagraphListInterface root, String path) { return (Paragraph) walk(root, path); }
    static ParagraphListInterface L(ParagraphListInterface root, String path) { return (ParagraphListInterface) walk(root, path); }
    static ControlTable T(ParagraphListInterface root, String path) { return (ControlTable) walk(root, path); }

    static Cell cell(ControlTable t, int r, int c) {
        for (Row row : t.getRowList())
            for (Cell cl : row.getCellList())
                if (cl.getListHeader().getRowIndex() == r && cl.getListHeader().getColIndex() == c) return cl;
        throw new RuntimeException("no cell r" + r + "c" + c);
    }
    static ParagraphListInterface C(ControlTable t, int r, int c) { return cell(t, r, c).getParagraphList(); }

    // ------------------------------------------------------------------ 글자 바꾸기
    /** 일반 글자만 지우고 같은 자리에 새 글자를 넣는다. 컨트롤 문자(표·양식·필드·단 정의)는 그대로 둔다. */
    static void setText(Paragraph p, String s) {
        ParaText t = p.getText();
        if (t == null) {
            p.createText(); t = p.getText();
            t.getCharList().add(new HWPCharControlChar(13));
        }
        ArrayList<HWPChar> list = t.getCharList();
        int ins = -1;
        for (int i = 0; i < list.size(); ) {
            if (list.get(i).getType() == HWPCharType.Normal) { if (ins < 0) ins = i; list.remove(i); }
            else i++;
        }
        if (ins < 0) ins = list.size() - 1;               // 문단 끝(13) 앞
        for (int k = 0; k < s.length(); k++) list.add(ins + k, new HWPCharNormal(s.charAt(k)));
        if (p.getCharShape() != null) {
            ArrayList<CharPositionShapeIdPair> cs = p.getCharShape().getPositonShapeIdPairList();
            while (cs.size() > 1) cs.remove(cs.size() - 1);
            cs.get(0).setPosition(0);
        }
        p.deleteLineSeg();
    }
    /** 컨트롤을 뺀 순수 글자 문단 사본(줄 복제용 틀). */
    static Paragraph plain(Paragraph src) {
        Paragraph q = src.clone();
        if (q.getText() != null) q.getText().getCharList().removeIf(c ->
                c.getType() == HWPCharType.ControlExtend || c.getType() == HWPCharType.ControlInline);
        if (q.getControlList() != null) q.getControlList().clear();
        return q;
    }
    /** 칸(문단 목록)을 lines 로 다시 채운다. 틀은 첫 문단의 순수 글자 사본. */
    static void setLines(ParagraphListInterface pl, String... lines) {
        Paragraph tpl = plain(pl.getParagraph(0));
        pl.deleteAllParagraphs();
        for (String ln : lines) { Paragraph q = tpl.clone(); setText(q, ln); pl.insertParagraph(pl.getParagraphCount(), q); }
    }
    static void cellLines(ControlTable t, int r, int c, String... lines) { setLines(C(t, r, c), lines); }
    static Paragraph withText(Paragraph tpl, String s) { Paragraph q = tpl.clone(); setText(q, s); return q; }
    /** 표 하나가 든 문단(코드 상자 등) 사본을 만들고 그 표 첫 칸을 lines 로 채운다. */
    static Paragraph box(Paragraph tplWithTable, String... lines) {
        Paragraph q = tplWithTable.clone();
        for (Control c : q.getControlList())
            if (c instanceof ControlTable) { setLines(C((ControlTable) c, 0, 0), lines); break; }
        return q;
    }

    // ------------------------------------------------------------------ 체크박스
    static int check(ParagraphListInterface pl, String caption, int v) {
        int n = 0;
        for (Paragraph p : pl.getParagraphs()) {
            if (p.getControlList() == null) continue;
            for (Control c : p.getControlList()) {
                if (!(c instanceof ControlForm)) continue;
                PropertySet bs = (PropertySet) ((ControlForm) c).getFormObject().getProperties().getProperty("ButtonSet");
                String cap = ((PropertyNormal) bs.getProperty("Caption")).getValue().trim();
                if (cap.startsWith(caption)) { ((PropertyNormal) bs.getProperty("Value")).setValue(String.valueOf(v)); n++; }
            }
        }
        if (n == 0) throw new RuntimeException("checkbox not found: " + caption);
        return n;
    }
    static void only(ParagraphListInterface pl, String... on) {        // 칸 안의 체크박스를 모두 끄고 on 만 켠다
        for (Paragraph p : pl.getParagraphs()) {
            if (p.getControlList() == null) continue;
            for (Control c : p.getControlList())
                if (c instanceof ControlForm) {
                    PropertySet bs = (PropertySet) ((ControlForm) c).getFormObject().getProperties().getProperty("ButtonSet");
                    ((PropertyNormal) bs.getProperty("Value")).setValue("0");
                }
        }
        for (String s : on) check(pl, s, 1);
    }

    // ------------------------------------------------------------------ 표 행 줄이기
    static void keepRows(ControlTable t, int keep) {
        long h = 0;
        while (t.getRowList().size() > keep) t.getRowList().remove(t.getRowList().size() - 1);
        for (Row r : t.getRowList()) h += r.getCellList().get(0).getListHeader().getHeight();
        t.getTable().setRowCount(keep);
        ArrayList<Integer> cc = t.getTable().getCellCountOfRowList();
        while (cc.size() > keep) cc.remove(cc.size() - 1);
        t.getHeader().setHeight(h);
    }

    // ================================================================== 내용
    static final String NIH = "/home/hyeonmokang/aidep/data/nih-chest-xrays/versions/3";
    static final String REPO = "/home/hyeonmokang/aidep/ALINMed-MAF";
    static final String ENV = "alinmed";
    static final String ENV_PATH = "/home/hyeonmokang/aidep/envs/alinmed";
    static final String CKPT = "/home/hyeonmokang/aidep/checkpoints/alinmed_s42";
    static final String CK_FAIR = "e8a0b196de29b6db6fa89df542ec2d831cd29f2e2a82b62f8921cc95425b7143";
    static final String CK_NOFAIR = "8bbc7c46af651fa35fadfeabef5167372cbfd072234c2ee9104a367c9e4fcb78";
    static final String CK_SIZE = "331 MB × 2";
    static final String RUN_MIN = "1";        // 평가 1회 소요 시간(분)

    static String[][] ITEMS = {
        {"[의료] 흉부 X선 기흉 진단 AI 예측정확도",
         "- 시험용 PC에서 Visual Studio Code를 이용하여 시험용 서버에 접속한 후, 시험 데이터셋(테스트 영상 438장)에 대하여 사전에 학습이 완료된 ALINMed V1.0(그룹 균형 리샘플링 적용 모델)으로 기흉 분류를 수행한 결과와 정답 라벨을 비교하여 화면에 출력된 예측정확도(accuracy(fair)) 값이 94 % 이상인지 확인",
         " * 예측정확도 산출식: 예측정확도(%) = (정답과 일치한 영상 수 / 전체 테스트 영상 수 438) × 100" +
         "|- 예측값: 원본 영상과 좌우 반전 영상의 출력 로짓을 더한 뒤 큰 쪽 클래스(기흉 양성/기흉 음성)를 선택" +
         "|- 대상 모델: 그룹 균형 리샘플링 적용 모델 (fair.pt)",
         "6. 화면에 출력된 accuracy(fair) 값을 확인 후, 예측정확도가 94 % 이상인지 확인",
         "예측정확도 94 % 이상",
         "1) 시험결과 표기 단위 : % (소수점 둘째 자리, 예: 94.52 %)|2) 적합성 진술 : 측정불확도 무관. 산출값이 94.00 % 이상이면 적합, 미만이면 부적합"},
        {"[의료] 흉부 X선 기흉 진단 AI 공정성(성별 간 DEO)",
         "- 시험용 PC에서 Visual Studio Code를 이용하여 시험용 서버에 접속한 후, 시험 데이터셋(테스트 영상 438장)에 대하여 사전에 학습이 완료된 ALINMed V1.0(그룹 균형 리샘플링 적용 모델)으로 기흉 분류를 수행한 결과로 여성·남성 집단의 진양성률(TPR) 차이를 산출하여 화면에 출력된 DEO(fair) 값이 5 % 이하인지 확인",
         " * DEO(Difference of Equal Opportunity) 산출식: DEO(%) = | TPR_여 − TPR_남 | × 100" +
         "|- TPR_g = (성별 g의 기흉 음성(label 1) 영상 중 기흉 음성으로 예측된 영상 수) / (성별 g의 기흉 음성 영상 수)" +
         "|- 원 과제 평가 코드(evaluate_DEO)의 정의를 그대로 따르며, TPR은 label 1(기흉 음성: 기흉 소견 없음, 타 소견 포함 가능) 클래스 기준" +
         "|- 테스트 기흉 음성 영상 수: 여 149장, 남 259장 / 대상 모델: 그룹 균형 리샘플링 적용 모델 (fair.pt)",
         "6. 화면에 출력된 DEO(fair) 값(같은 줄에 TPR_f, TPR_m 함께 출력)을 확인 후, 5 % 이하인지 확인",
         "성별 간 DEO 5 % 이하",
         "1) 시험결과 표기 단위 : % (소수점 둘째 자리, 예: 0.40 %)|2) 적합성 진술 : 측정불확도 무관. 산출값이 5.00 % 이하이면 적합, 초과하면 부적합"},
        {"[의료] 공정성 정책 반영 성능평균하락률",
         "- 시험용 PC에서 Visual Studio Code를 이용하여 시험용 서버에 접속한 후, 시험 데이터셋(테스트 영상 438장)에 대하여 사전에 학습이 완료된 공정성 정책(그룹 균형 리샘플링) 적용 모델과 미적용 모델의 예측정확도를 각각 산출하여, 화면에 출력된 정책반영 성능평균하락률(policy-drop) 값이 3 % 이하인지 확인",
         " * 정책반영 성능평균하락률 산출식: 성능평균하락률(%) = (Acc_미적용 − Acc_적용) / Acc_미적용 × 100" +
         "|- Acc_적용: 그룹 균형 리샘플링 적용 모델(fair.pt)의 예측정확도 (accuracy(fair))" +
         "|- Acc_미적용: 같은 조건에서 일반 셔플로 학습한 모델(nofair.pt)의 예측정확도 (accuracy(nofair))" +
         "|- 음수 값은 공정성 정책 적용 모델의 정확도가 더 높음을 뜻함",
         "6. 화면에 출력된 policy-drop 값을 확인 후, 3 % 이하인지 확인",
         "정책반영 성능평균하락률 3 % 이하",
         "1) 시험결과 표기 단위 : % (소수점 둘째 자리, 예: 0.24 %)|2) 적합성 진술 : 측정불확도 무관. 산출값이 3.00 % 이하이면 적합, 초과하면 부적합"},
    };

    static void fillItem(Paragraph tablePara, String[] it, Map<String, Paragraph> tpl) {
        ControlTable t = null;
        for (Control c : tablePara.getControlList()) if (c instanceof ControlTable) t = (ControlTable) c;
        setText(C(t, 1, 1).getParagraph(0), it[0]);
        setText(C(t, 2, 1).getParagraph(0), it[1]);

        // ---- 시험조건
        ParagraphListInterface cond = C(t, 5, 1);
        ControlTable net = (ControlTable) cond.getParagraph(1).getControlList().get(0);
        String[][] netv = {
            {"143.248.230.94", "255.255.255.0", "143.248.230.1"},
            {"172.168.44.158", "255.255.248.0", "172.168.40.2"}};
        for (int r = 1; r <= 2; r++) for (int c = 1; c <= 3; c++) cellLines(net, r, c, netv[r - 1][c - 1], "(시험장 당일 기재)");
        setText(cond.getParagraph(2), "시험 데이터 셋은 시험용 서버의 “" + NIH + "/images_001 ~ images_012/images/”(영상 4,376장)와 “" + REPO + "/performance_targets/data/”(라벨 CSV 2개)에, 학습 완료 모델은 “" + CKPT + "/”에 저장되어 있으며, Hash 값은 아래와 같음.");
        Paragraph src = cond.getParagraph(3);
        setText(src, "시험 데이터 셋 출처: NIH Clinical Center (NIH ChestX-ray14), Google Health (Four-Findings 전문가 판독 라벨)");
        ControlTable hash = null;
        for (Control c : src.getControlList()) if (c instanceof ControlTable) hash = (ControlTable) c;
        keepRows(hash, 4);
        cellLines(hash, 1, 0, "Four-Findings 라벨 CSV");
        cellLines(hash, 1, 1, "four_findings_expert_labels_test_labels.csv", "four_findings_expert_labels_validation_labels.csv");
        cellLines(hash, 1, 2, "24911a463c0c5e40e1bcd39a5b0aa38079b89859946932fca4d5f62081a0ed9f",
                              "aa4c1d092818ae9150ae72dad77e32fca070a975b9a3273a06e47f3bf7b0590d");
        cellLines(hash, 2, 0, "NIH 영상 해시 목록");
        cellLines(hash, 2, 1, "nih_subset_sha256.txt", "(영상 4,376장 각각의 SHA-256 목록)");
        cellLines(hash, 2, 2, "1376711a03a80b430e321df459a38f523dd737bf01cea637b0a779472e301464");
        cellLines(hash, 3, 0, "학습 완료 모델");
        cellLines(hash, 3, 1, "fair.pt (공정성 정책 적용)", "nofair.pt (공정성 정책 미적용)");
        cellLines(hash, 3, 2, CK_FAIR, CK_NOFAIR);
        ControlTable data = (ControlTable) cond.getParagraph(5).getControlList().get(0);
        Row r2 = data.getRowList().get(1).clone();                     // 2행 추가: 학습 완료 모델
        for (Cell cl : r2.getCellList()) cl.getListHeader().setRowIndex(2);
        data.getRowList().add(r2);
        data.getTable().setRowCount(3);
        data.getTable().getCellCountOfRowList().add(data.getTable().getCellCountOfRowList().get(1));
        data.getHeader().setHeight(data.getHeader().getHeight() + r2.getCellList().get(0).getListHeader().getHeight());
        cellLines(data, 1, 0, "시험 데이터");
        cellLines(data, 1, 1, "images_001 ~ images_012");
        cellLines(data, 1, 2, "1.7 GB");
        cellLines(data, 1, 3, "png");
        cellLines(data, 2, 0, "학습 완료 모델");
        cellLines(data, 2, 1, CKPT);
        cellLines(data, 2, 2, CK_SIZE);
        cellLines(data, 2, 3, "pt (PyTorch state_dict)");
        Paragraph c45 = plain(cond.getParagraph(6));
        while (cond.getParagraphCount() > 6) cond.deleteParagraph(6);
        for (String s : new String[]{
                "사전에 의뢰기관에서 AI 모델 학습을 완료한 상태 (그룹 균형 리샘플링 적용 모델 fair.pt / 미적용 모델 nofair.pt, rad-DINO 백본, 15 epoch, 학습 시드 42)",
                "사전에 의뢰기관이 시험 데이터를 AI 모델 학습에 사용하지 않은 것을 전제로 함 (4,376장을 고정 분할 train_test_split(test_size=0.10, random_state=42)로 학습 3,938장 / 테스트 438장으로 나누고, 테스트 438장(기흉 30장: 여 16 / 남 14)만 시험에 사용)",
                "rad-DINO 모델 구성 파일(microsoft/rad-dino, revision 110cbc18)은 시험용 서버의 Hugging Face 캐시에 사전 저장된 상태",
                "AI 모델 시험은 사전에 의뢰기관에서 구축해놓은 Python 가상환경(venv) ‘" + ENV + "’(" + ENV_PATH + ")에서 실행"})
            cond.insertParagraph(cond.getParagraphCount(), withText(c45, s));

        // ---- 시험방법 ([첨부2] 예시처럼 단계별)
        ParagraphListInterface m = C(t, 6, 1);
        Paragraph access = tpl.get("access").clone();
        ControlTable at = (ControlTable) access.getControlList().get(0);
        cellLines(at, 1, 1, "143.248.230.94"); cellLines(at, 2, 1, "hyeonmokang"); cellLines(at, 3, 0, "인증"); cellLines(at, 3, 1, "SSH 키 인증 (비밀번호 사용 안 함, 공개키는 시험 당일 서버에 등록)");
        List<Paragraph> out = new ArrayList<>();
        out.add(withText(tpl.get("n"), "시험용 PC에서 Visual Studio Code를 실행하고, Remote-SSH(‘Connect to Host’)로 아래의 접속 정보를 이용하여 시험용 서버에 원격 접속"));
        out.add(access);
        out.add(withText(tpl.get("n"), "Visual Studio Code의 터미널에서 아래 명령어를 입력하여 Python 가상환경 ‘" + ENV + "’을 활성화하고 코드 경로로 이동"));
        out.add(box(tpl.get("code"), "source " + ENV_PATH + "/bin/activate", "cd " + REPO));
        out.add(withText(tpl.get("n"), "아래 명령어를 입력하여 시험 데이터 셋과 학습 완료 모델이 저장 경로에 존재하는지 확인하고, 출력된 SHA-256 값이 ‘시험조건’의 Hash 표와 일치하는지 확인 (마지막 줄에 ‘ALL 4376 IMAGES OK’가 출력되면 영상 4,376장의 Hash 값이 모두 일치)"));
        out.add(box(tpl.get("code"), "ls " + NIH + " " + CKPT,
                "sha256sum performance_targets/data/*.csv nih_subset_sha256.txt " + CKPT + "/fair.pt " + CKPT + "/nofair.pt",
                "(cd " + NIH + " && sha256sum -c --quiet $OLDPWD/nih_subset_sha256.txt) && echo \"ALL 4376 IMAGES OK\""));
        out.add(withText(tpl.get("n"), "아래 명령어를 입력하여 시험 데이터 셋(테스트 영상 438장)에 대하여 학습 완료 모델 2개(fair.pt, nofair.pt)로 기흉 분류를 수행 (학습 없이 평가만 수행, 약 " + RUN_MIN + "분 소요)"));
        out.add(box(tpl.get("code"), "CUDA_VISIBLE_DEVICES=5 NIH_ROOT=" + NIH + " HF_HUB_OFFLINE=1 python algorithms/preprocessing/SubgroupBalancedResampling.py --backbone raddino --ckpt_dir " + CKPT + " 2>&1 | tee test_run.log"));
        out.add(withText(tpl.get("t72"), "5. 기흉 분류를 수행하면서 아래 중간 과정과 결과가 화면에 출력되는지 확인"));
        for (String s : new String[]{
                " - 데이터 분할 결과: [SPLIT] train=3938 test=438 test_pneu_pos=30 (F16/M14)",
                " - 학습 완료 모델 로딩: [FAIR] load checkpoint …/fair.pt sha256=…, [NOFAIR] load checkpoint …/nofair.pt sha256=… (3단계의 Hash 값과 같은지 확인)",
                " - 모델별 테스트 결과: [FAIR] FINAL acc=… DEO=…, [NOFAIR] FINAL acc=… DEO=… (소수값으로 개별 출력)",
                " - 최종 결과: accuracy(fair) / accuracy(nofair), DEO(fair) (TPR_f, TPR_m), policy-drop을 %로 출력하고, TARGET CHECK 줄에 세 지표의 기준 충족 여부(True/False) 출력",
                " - 결과 파일: performance_targets/results/target_push_raddino_e15_strong_cw_tta_s42_ckpteval.json, 화면 로그: test_run.log"})
            out.add(withText(tpl.get("t19"), s));
        for (String s : it[2].split("\\|")) out.add(withText(tpl.get("t19"), s));
        out.add(withText(tpl.get("t72"), it[3]));
        m.deleteAllParagraphs();
        for (Paragraph q : out) m.insertParagraph(m.getParagraphCount(), q);

        setText(C(t, 7, 1).getParagraph(0), it[4]);
        setLines(C(t, 8, 1), it[5].split("\\|"));
    }

    public static void main(String[] a) throws Exception {
        HWPFile h = HWPReader.fromFile(a[0]);
        Section s = h.getBodyText().getSectionList().get(0);

        // ---- 머리 표
        ControlTable hd = T(s, "p0/c3");
        setText(C(hd, 1, 2).getParagraph(0), "[입력: 담당자 이름]");
        cellLines(hd, 2, 4, "[입력: 010-0000-0000]");
        cellLines(hd, 4, 4, "hmkang@kaist.ac.kr");
        cellLines(hd, 5, 2, "ALINMed V1.0");
        cellLines(hd, 5, 4, "3 건");
        cellLines(hd, 6, 2, "[협의 후 기재]");
        cellLines(hd, 6, 4, "[협의 후 기재]");

        // ---- 1. 시험구성
        h.getBinData().getEmbeddedBinaryDataList().get(0).setData(Files.readAllBytes(Paths.get(a[1])));
        ControlTable g = T(s, "p3/c0");
        setLines(C(g, 1, 0),
                "‘ALINMed V1.0’은 python 기반 SW로, 단일소프트웨어로 구성되어 있다.",
                "‘ALINMed V1.0’은 시험용 서버(연구실 GPU 서버)에 설치되어 있으며, 필요 조건이 충족된 Python 가상환경(venv)에서 실행된다.",
                "‘ALINMed V1.0’은 서버 내에 저장된 학습 완료 모델 2개(fair.pt, nofair.pt), 흉부 X선 영상(NIH ChestX-ray14, 4,376장)과 전문가 판독 라벨(CSV 2개)을 입력으로 사용한다.",
                "‘ALINMed V1.0’은 실행 시 학습 완료 모델 2개(그룹 균형 리샘플링 적용 / 미적용)를 불러와, 학습에 쓰지 않은 테스트 영상 438장으로 평가한다(학습은 수행하지 않음).",
                "‘ALINMed V1.0’은 시험용 PC에서 SSH(TCP/IP, 22번 포트)로 접속하여 실행 및 출력 결과 확인이 가능하다.");

        // ---- 2. 신청제품 정보
        ControlTable info = T(s, "p10/c0");
        setText(C(info, 1, 0).getParagraph(0), "ALINMed V1.0");
        cellLines(info, 1, 3, "흉부 X선 영상의 기흉 진단 AI에서 성별 간 진단 성능 격차를 줄이는 공정성 알고리즘(그룹 균형 리샘플링) 적용 모델");

        ControlTable ai = T(s, "p11/c0");
        only(C(ai, 1, 1), "관련 있음");
        only(C(ai, 2, 1), "Classification Task");
        setText(P(s, "p11/c0/r2c1/p1/c2/r0c0/p0"), "");
        only(C(ai, 3, 1), "지도학습");
        only(C(ai, 4, 1), "Etc");
        setText(P(s, "p11/c0/r4c1/p7/c2/r0c0/p0"), "Transformer (ViT, rad-DINO)");
        only(C(ai, 5, 1), "Linux");
        only(C(ai, 6, 1), "Pytorch");
        only(C(ai, 7, 1), "0 ~ 4 hour");
        only(C(ai, 8, 1), "정확도(Accuracy)", "그외 기타 정량적 목표");

        ControlTable dt = T(s, "p12/c0");
        only(C(dt, 1, 1), "신뢰된 출처");
        String srcTxt = "NIH Clinical Center (NIH ChestX-ray14), Google Health (Four-Findings 전문가 판독 라벨)";
        setText(P(s, "p12/c0/r1c1/p4/c1/r0c0/p0"), srcTxt);
        only(C(dt, 2, 1), "이미지");
        setText(P(s, "p12/c0/r3c2/p0/c0/r0c0/p0"), "3,938 개");
        only(C(dt, 4, 1), "이미지");
        only(C(dt, 5, 1), "테스트 데이터 제공 가능");
        only(C(dt, 6, 1), "미사용");
        only(C(dt, 7, 1), "신뢰된 출처");
        setText(P(s, "p12/c0/r7c1/p4/c1/r0c0/p0"), srcTxt);
        only(C(dt, 8, 1), "이미지");
        setText(P(s, "p12/c0/r9c2/p0/c0/r0c0/p0"), "438 개");

        // ---- 3. 구성, 5. 시험환경
        setText(P(s, "p15"), "- ALINMed V1.0은 단일소프트웨어로 구성");
        setText(P(s, "p21/c0/r2c1/p0"), " 시험 1회 실행(학습 완료 모델 2개 평가)에 약 " + RUN_MIN + "분 소요 (RTX 2080 Ti 1장 기준)");
        setText(P(s, "p23"), "  5.1 시험용 서버 (ALINMed V1.0)");
        ControlTable sv = T(s, "p24/c0");
        cellLines(sv, 1, 1, "Intel Xeon Silver 4214 @ 2.20GHz × 2 (총 24코어 / 48스레드)", "GPU: NVIDIA GeForce RTX 2080 Ti 11 GB × 1 (시험에 1장 사용, 드라이버 525.147.05)");
        cellLines(sv, 2, 1, "128 GB");
        cellLines(sv, 3, 1, "1 TB");
        cellLines(sv, 4, 1, "Intel I350 Gigabit Network Connection, 10/100/1000 BASE-T Ethernet, 1 Port");
        cellLines(sv, 5, 1, "Ubuntu 22.04.4 LTS (커널 5.15.0-101-generic)");
        cellLines(sv, 6, 1, "python v3.10", "CUDA v12.1", "torch v2.3.0+cu121", "torchvision v0.18.0+cu121",
                "transformers v4.44.2", "scikit-learn v1.7.2", "numpy v2.2.6", "pandas v2.3.3", "aif360 v0.6.1",
                "Pillow v12.3.0", "tqdm v4.67.3", "requests v2.25.1",
                "사전학습 모델: microsoft/rad-dino (revision 110cbc18d5133582e320b43d53bf5c44e410c936)",
                "[가상환경 전체 패키지 목록 (uv pip freeze)]", "aif360==0.6.1", "certifi==2026.10.23", "chardet==4.0.0", "cloudpickle==3.1.2", "contourpy==1.3.2", "cycler==0.12.1", "filelock==4.1.0", "fonttools==4.65.0", "fsspec==2026.9.0", "hf-xet==1.8.0", "huggingface-hub==0.36.2", "idna==2.10", "jinja2==3.1.6", "joblib==1.6.0", "kiwisolver==1.5.1", "markupsafe==3.0.4", "matplotlib==3.10.9", "mpmath==1.3.0", "networkx==3.4.2", "numpy==2.2.6", "nvidia-cublas-cu12==12.1.3.1", "nvidia-cuda-cupti-cu12==12.1.105", "nvidia-cuda-nvrtc-cu12==12.1.105", "nvidia-cuda-runtime-cu12==12.1.105", "nvidia-cudnn-cu12==8.9.2.26", "nvidia-cufft-cu12==11.0.2.54", "nvidia-curand-cu12==10.3.2.106", "nvidia-cusolver-cu12==11.4.5.107", "nvidia-cusparse-cu12==12.1.0.106", "nvidia-nccl-cu12==2.20.5", "nvidia-nvjitlink-cu12==12.9.86", "nvidia-nvtx-cu12==12.1.105", "packaging==26.3", "pandas==2.3.3", "pillow==12.3.0", "pyparsing==3.3.3", "python-dateutil==3.0.0", "pytz==2026.6", "pyyaml==6.0.3", "regex==2026.9.29", "requests==2.25.1", "safetensors==0.8.0", "scikit-learn==1.7.2", "scipy==1.15.3", "six==1.17.0", "sympy==1.14.0", "threadpoolctl==3.8.0", "tokenizers==0.19.1", "torch==2.3.0+cu121", "torchvision==0.18.0+cu121", "tqdm==4.67.3", "transformers==4.44.2", "triton==2.3.0", "typing-extensions==4.16.0", "tzdata==2026.6", "urllib3==1.26.20");
        ControlTable pc = T(s, "p27/c0");
        cellLines(pc, 1, 1, "Apple M4 Pro (14코어: 성능 10 + 효율 4), MacBook Pro (Mac16,7)");
        cellLines(pc, 2, 1, "24 GB");
        cellLines(pc, 3, 1, "500 GB (SSD)");
        cellLines(pc, 4, 1, "내장 무선 NIC, Wi-Fi 802.11 a/b/g/n/ac/ax");
        cellLines(pc, 5, 1, "macOS 26.5.1 (빌드 25F80)");
        cellLines(pc, 6, 1, "Visual Studio Code 1.139.0", "Remote - SSH 확장 0.128.0");
        Paragraph h52 = P(s, "p26");
        Paragraph mobile = withText(h52, "  5.2 모바일 단말기 : 해당 없음");
        setText(h52, "  5.3 시험용 PC");

        // ---- 6. 시험항목: 6.1 표를 틀로 6.1~6.3 을 만든다
        Paragraph t61 = P(s, "p33");
        Map<String, Paragraph> tpl = new HashMap<>();
        tpl.put("n", plain(P(s, "p33/c0/r6c1/p0")));
        tpl.put("access", P(s, "p33/c0/r6c1/p1").clone());
        tpl.put("code", P(s, "p33/c0/r6c1/p10").clone());
        tpl.put("t72", plain(P(s, "p33/c0/r6c1/p12")));
        tpl.put("t19", plain(P(s, "p33/c0/r6c1/p13")));
        Paragraph tableTpl = t61.clone();
        Paragraph head62 = P(s, "p38"), blank = P(s, "p34").clone();
        Paragraph t62 = tableTpl.clone(), t63 = tableTpl.clone();
        Paragraph head63 = withText(head62, "  6.3 시험항목 3");
        fillItem(t61, ITEMS[0], tpl);
        fillItem(t62, ITEMS[1], tpl);
        fillItem(t63, ITEMS[2], tpl);

        // ---- 서명
        setText(P(s, "p42/c0/r0c0/p4"), "                       2026 년   11 월   [입력]  일");
        setText(P(s, "p42/c0/r0c0/p6"), "                                    의뢰자      [입력: 이름]  (서명 또는 날인)");

        // ---- 구조 변경 (뒤에서부터: 인덱스가 밀리지 않게)
        s.deleteParagraph(39);                          // 6.2 표 -> 6.1 틀 사본으로 교체
        s.insertParagraph(39, t62);
        s.insertParagraph(40, blank);
        s.insertParagraph(41, head63);
        s.insertParagraph(42, t63);
        s.insertParagraph(26, mobile);                  // 5.2 모바일 단말기 (5.3 시험용 PC 앞)

        HWPWriter.toFile(h, a[2]);
        System.out.println("saved " + a[2]);
    }
}
