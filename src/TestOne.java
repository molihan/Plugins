import com.sio.plugin.Terminal;


public class TestOne extends Terminal {

	public TestOne() {
	}

	@Override
	public void start() {
		
		System.out.println(new TestTwo().getTxT());
	}

	@Override
	public void onEvent() {

	}

	@Override
	public void stop() {

	}

}
