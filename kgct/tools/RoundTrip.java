import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.writer.HWPWriter;

/** 읽고 그대로 저장만 한다 (hwplib 왕복 검사용). */
public class RoundTrip {
    public static void main(String[] a) throws Exception {
        HWPFile h = HWPReader.fromFile(a[0]);
        HWPWriter.toFile(h, a[1]);
        System.out.println("saved " + a[1]);
    }
}
