package ch.ethz.matsim.baseline_scenario.analysis.ptOperator;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

public class CSVVehicleDepartureWriter {
    final private Collection<VehicleDepartureItem> departures;
    final private String delimiter;

    public CSVVehicleDepartureWriter(Collection<VehicleDepartureItem> departures) {
        this(departures, ";");
    }

    public CSVVehicleDepartureWriter(Collection<VehicleDepartureItem> departures, String delimiter) {
        this.departures = departures;
        this.delimiter = delimiter;
    }

    public void write(String outputPath) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath)));

        writer.write(formatHeader() + "\n");
        writer.flush();

        for (VehicleDepartureItem dep : departures) {
            writer.write(formatTrip(dep) + "\n");
            writer.flush();
        }

        writer.flush();
        writer.close();
    }

    private String formatHeader() {
        return String.join(delimiter,
                new String[] { "line_id", "route_id", "departure_id", "n_stops", "dep_time", "vehicle_time", "vehicle_dist",
                "boardings", "pax_dist", "tot_no_stops", "network_length", "capacity"  });
    }

    private String formatTrip(VehicleDepartureItem dep) {
        return String.join(delimiter, new String[] { dep.lineId.toString(), dep.routeId.toString(),
                dep.departureId.toString(), String.valueOf(dep.noStops), String.valueOf(dep.departureTime),
                String.valueOf(dep.vehicleTime),
                String.valueOf(dep.vehDist), String.valueOf(dep.passengerBoardings), String.valueOf(dep.paxDist),
                String.valueOf(dep.totStops), String.valueOf(dep.networkLength), String.valueOf(dep.capacity)	});
    }
}
