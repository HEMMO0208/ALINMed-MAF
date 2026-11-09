import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.control.*;
import kr.dogfoot.hwplib.object.bodytext.control.table.*;
import kr.dogfoot.hwplib.object.bodytext.control.gso.*;
import kr.dogfoot.hwplib.reader.HWPReader;

/** HWP 문단 트리를 경로와 함께 출력: s0/p3/c1:tbl/r0c2/p0 "텍스트" */
public class Dump {
    static void list(ParagraphListInterface pl, String path) throws Exception {
        for (int i = 0; i < pl.getParagraphCount(); i++) {
            Paragraph p = pl.getParagraph(i);
            String pp = path + "/p" + i;
            String t = p.getText() == null ? "" : p.getNormalString();
            System.out.println(pp + " cs=" + (p.getCharShape() == null ? "-" : p.getCharShape().getPositonShapeIdPairList().size())
                    + " ps=" + p.getHeader().getParaShapeId() + " \"" + t.replace("\n", "\\n") + "\"");
            if (p.getControlList() == null) continue;
            for (int k = 0; k < p.getControlList().size(); k++) {
                Control c = p.getControlList().get(k);
                String cp = pp + "/c" + k;
                if (c instanceof ControlTable) {
                    ControlTable tb = (ControlTable) c;
                    System.out.println(cp + ":TABLE rows=" + tb.getRowList().size() + " w=" + tb.getHeader().getWidth());
                    for (Row r : tb.getRowList())
                        for (Cell cell : r.getCellList()) {
                            ListHeaderForCell h = cell.getListHeader();
                            list(cell.getParagraphList(), cp + "/r" + h.getRowIndex() + "c" + h.getColIndex()
                                    + "[" + h.getRowSpan() + "x" + h.getColSpan() + "]");
                        }
                } else if (c instanceof ControlRectangle && ((ControlRectangle) c).getTextBox() != null) {
                    System.out.println(cp + ":RECT");
                    list(((ControlRectangle) c).getTextBox().getParagraphList(), cp);
                } else if (c instanceof ControlPicture) {
                    ControlPicture pic = (ControlPicture) c;
                    System.out.println(cp + ":PICTURE bin=" + pic.getShapeComponentPicture().getPictureInfo().getBinItemID()
                            + " w=" + pic.getHeader().getWidth() + " h=" + pic.getHeader().getHeight());
                } else if (c instanceof ControlForm) {
                    ControlForm f = (ControlForm) c;
                    System.out.println(cp + ":FORM " + f.getFormObject().getType() + " " + f.getFormObject().getProperties().toString().replace("\n", " "));
                } else if (c instanceof GsoControl) {
                    System.out.println(cp + ":GSO " + ((GsoControl) c).getGsoType());
                } else {
                    System.out.println(cp + ":" + c.getType());
                }
            }
        }
    }

    public static void main(String[] a) throws Exception {
        HWPFile h = HWPReader.fromFile(a[0]);
        int s = 0;
        for (Section sec : h.getBodyText().getSectionList()) list(sec, "s" + (s++));
    }
}
