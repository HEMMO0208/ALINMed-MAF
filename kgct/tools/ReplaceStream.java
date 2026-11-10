import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.Deflater;

/**
 * HWP(OLE) 안의 스트림 하나만 바꿔 넣는다. 새 내용은 압축 전 바이트로 받아 raw deflate 로 압축한다.
 * java -cp hwplib-1.1.11.jar ReplaceStream.java <in.hwp> <BodyText/Section0> <raw.bin> <out.hwp>
 */
public class ReplaceStream {
    public static void main(String[] a) throws Exception {
        byte[] raw = Files.readAllBytes(Paths.get(a[2]));
        Deflater df = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        df.setInput(raw); df.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        while (!df.finished()) bo.write(buf, 0, df.deflate(buf));
        { POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(a[0]));
            DirectoryEntry dir = fs.getRoot();
            String[] parts = a[1].split("/");
            for (int i = 0; i < parts.length - 1; i++) dir = (DirectoryEntry) dir.getEntry(parts[i]);
            dir.getEntry(parts[parts.length - 1]).delete();
            dir.createDocument(parts[parts.length - 1], new ByteArrayInputStream(bo.toByteArray()));
            try (FileOutputStream out = new FileOutputStream(a[3])) { fs.writeFilesystem(out); }
        }
        System.out.println(a[1] + ": " + raw.length + " bytes -> " + bo.size() + " compressed -> " + a[3]);
    }
}
