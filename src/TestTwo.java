import java.util.ArrayList;
import java.util.List;


public class TestTwo {
	private static final String TEXT = "hello iam 2";
	
	public TestTwo() {
		// TODO Auto-generated constructor stub
	}

	public String getTxT(){
		return TEXT;
	}
	
	public static void main(String[] args) {
		List<String> strings = new ArrayList<>();
		strings.add("a0a0");
		if(strings.contains("a0a0")){
			System.out.println("yes");
		} else {
			System.out.println("no");
		}
	}
}
