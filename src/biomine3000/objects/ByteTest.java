package biomine3000.objects;

public class ByteTest {
    
    public static void main(String[] args) throws Exception {
        String foo = "foo";
        System.out.println("getBytes(): ");
        System.out.write(foo.getBytes());
        System.out.println("");
        
        BusinessObjectMetadata meta = new BusinessObjectMetadata(Biomine3000Mimetype.PLAINTEXT);
        System.out.println("meta:");
        System.out.write(meta.toString().getBytes("UTF-8"));
        System.out.println("");
        
        
//        foo
       //  CharsetEncoder encoder = new CharsetEncoder() {
            
        
            
        
    }
}
