import javax.tools.*;
/** java Compile.java <classpath> <outdir> <src...> */
public class Compile {
    public static void main(String[] a) {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        String[] args = new String[a.length + 2];
        args[0] = "-cp"; args[1] = a[0]; args[2] = "-d"; args[3] = a[1];
        System.arraycopy(a, 2, args, 4, a.length - 2);
        System.exit(jc.run(null, null, null, args));
    }
}
