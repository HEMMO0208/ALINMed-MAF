import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.control.*;
import kr.dogfoot.hwplib.object.bodytext.control.table.*;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.writer.HWPWriter;
import java.util.*;

/**
 * 공개 저장소에 올리기 전에 [첨부2] 템플릿의 개인정보를 가린다. 문단 구조(인덱스)는 바꾸지 않으므로 Fill.java 가 그대로 동작한다.
 * Fill.java 와 같은 클래스패스로:  java ... Scrub.java <in.hwp> <out.hwp> <replacements.tsv (커밋 금지)>
 */
public class Scrub {
    /** 바꿀 문자열 쌍: 커밋하지 않는 로컬 파일(한 줄에 "원문<TAB>대체문")에서 읽는다. */
    static String[][] REPL;
    static void loadRepl(String path) throws Exception {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        for (String ln : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(path)))
            if (ln.contains("\t")) l.add(ln.split("\t", 2));
        REPL = l.toArray(new String[0][]);
    }
    static int n = 0;

    static void walk(ParagraphListInterface pl) throws Exception {
        for (Paragraph p : pl.getParagraphs()) {
            if (p.getText() != null) {
                String t = p.getNormalString(), u = t;
                for (String[] r : REPL) u = u.replace(r[0], r[1]);
                if (!u.equals(t)) { Fill.setText(p, u); n++; }
            }
            if (p.getControlList() == null) continue;
            for (Control c : p.getControlList())
                if (c instanceof ControlTable)
                    for (Row r : ((ControlTable) c).getRowList())
                        for (Cell cl : r.getCellList()) walk(cl.getParagraphList());
        }
    }

    public static void main(String[] a) throws Exception {
        loadRepl(a[2]);
        HWPFile h = HWPReader.fromFile(a[0]);
        walk(h.getBodyText().getSectionList().get(0));
        if (h.getSummaryInformation() != null) {
            h.getSummaryInformation().setAuthor("");
            h.getSummaryInformation().setLastAuthor("");
        }
        HWPWriter.toFile(h, a[1]);
        System.out.println("scrubbed paragraphs: " + n + " -> " + a[1]);
    }
}
