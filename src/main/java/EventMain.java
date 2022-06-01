import net.prominic.gja_v20220524.Event;
import net.prominic.gja_v20220524.GLogger;

public class EventMain extends Event {
	public DominoMeter dominoMeter = null;
	
	public EventMain(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		if (!dominoMeter.checkConnection()) return;

		dominoMeter.loadConfig();
		dominoMeter.sendReport(false);
		dominoMeter.updateVersion();
	}
}
