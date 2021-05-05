package ch.ethz.matsim.baseline_scenario.analysis.ptOperator.readers;

import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.VehicleDepartureItem;
import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.listeners.VehicleDepartureListener;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsReaderXMLv1;
import org.matsim.core.events.EventsUtils;

import java.util.Collection;

public class EventsVehicleDepartureReader {
    final private VehicleDepartureListener depListener;

    public EventsVehicleDepartureReader(VehicleDepartureListener depListener) {
        this.depListener = depListener;
    }

    public Collection<VehicleDepartureItem> readTrips(String eventsPath) {
        EventsManager eventsManager = EventsUtils.createEventsManager();
        eventsManager.addHandler(depListener);

        EventsReaderXMLv1 reader = new EventsReaderXMLv1(eventsManager);
        reader.readFile(eventsPath);

        return depListener.getVehicleDepartureItems();
    }
}

