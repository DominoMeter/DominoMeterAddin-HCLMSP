import java.util.HashMap;
import net.prominic.gja_v20220510.Event;
import net.prominic.gja_v20220510.GLogger;

public class EventMain extends Event {
	private DominoMeter m_dominoMeter = null;
	
	public EventMain(String name, long seconds, boolean fireOnStart, HashMap<String, Object> params, GLogger logger) {
		super(name, seconds, fireOnStart, params, logger);
		
		m_dominoMeter = (DominoMeter) params.get("dominometer");
	}

	@Override
	public void run() {
		System.out.println(this.getName());
		
		if (!m_dominoMeter.checkConnection()) return;

		m_dominoMeter.loadConfig();
		m_dominoMeter.sendReport(false);
		m_dominoMeter.updateVersion();
	}

}
