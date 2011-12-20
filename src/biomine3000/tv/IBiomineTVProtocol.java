package biomine3000.tv;

import java.util.Collection;

public interface IBiomineTVProtocol {
	public void announce(Collection<String> content);
	public void upload(Collection<String> content);
	public void uploadCodeSample(Collection<String> content);
	public void zombiAlert(String text);	
}
