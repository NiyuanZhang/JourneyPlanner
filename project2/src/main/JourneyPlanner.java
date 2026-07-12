import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.io.*;

public class JourneyPlanner {
    // Graph representing the connections between stations
    private static Map<String, List<Connection>> graph = new HashMap<>();
    // All station names, sorted
    private static Set<String> stations = new TreeSet<>();
    // Walking time between stations
    private static Map<String, Map<String, Double>> walkTimes = new HashMap<>();
    // Stations that are temporarily closed
    private static Set<String> closedStations = new HashSet<>();
    // Delay times for specific stations
    private static Map<String, Double> delays = new HashMap<>();

    public static void main(String[] args) {
        loadGraph("./data/Metrolink_times_linecolour.csv");
        loadWalkTimes("./data/walktimes.csv");

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Manchester Metrolink Journey Planner");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(700, 500);
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout(10, 10));

            JPanel inputPanel = new JPanel(new GridLayout(6, 2, 10, 10));

            JComboBox<String> startBox = new JComboBox<>(stations.toArray(new String[0]));
            JComboBox<String> endBox = new JComboBox<>(stations.toArray(new String[0]));

            String[] modeOptions = { "Fastest Time", "FewestsChanges" };
            JComboBox<String> modeBox = new JComboBox<>(modeOptions);

            JTextField closedStationsField = new JTextField();
            JTextField delayField = new JTextField();

            JButton searchButton = new JButton("Search");

            inputPanel.add(new JLabel("Start Station:"));
            inputPanel.add(startBox);
            inputPanel.add(new JLabel("End Station:"));
            inputPanel.add(endBox);
            inputPanel.add(new JLabel("Search Mode:"));
            inputPanel.add(modeBox);
            inputPanel.add(new JLabel("Closed Stations (comma separated):"));
            inputPanel.add(closedStationsField);
            inputPanel.add(new JLabel("Delays (station:time in minutes)"));
            inputPanel.add(delayField);
            inputPanel.add(new JLabel()); 
            inputPanel.add(searchButton);

            panel.add(inputPanel, BorderLayout.NORTH);

            JTextArea outputArea = new JTextArea();
            outputArea.setEditable(false);
            outputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
            JScrollPane scrollPane = new JScrollPane(outputArea);

            panel.add(scrollPane, BorderLayout.CENTER);

            frame.getContentPane().add(panel);
            frame.setVisible(true);

            searchButton.addActionListener(e -> {
                String start = (String) startBox.getSelectedItem();
                String end = (String) endBox.getSelectedItem();
                String mode = (String) modeBox.getSelectedItem();

                String[] closedInput = closedStationsField.getText().split(",");
                closedStations.clear();
                for (String station : closedInput) {
                    closedStations.add(station.trim());
                }

                String[] delayInput = delayField.getText().split(",");
                delays.clear();
                for (String delay : delayInput) {
                    String[] parts = delay.split(":");
                    if (parts.length == 2) {
                        String stationName = parts[0].trim();
                        double delayTime = Double.parseDouble(parts[1].trim());
                        delays.put(stationName, delayTime);
                    }
                }

                if (start.equals(end)) {
                    outputArea.setText("Start and end stations must be different.");
                    return;
                }

                List<String> result;
                if (mode.equals("Fastest Time")) {
                    result = findShortestTimePath(start, end);
                } else {
                    result = findFewestChangePath(start, end);
                }

                if (result == null || result.isEmpty()) {
                    outputArea.setText("No route found.");
                } else {
                    outputArea.setText(String.join("\n", result));
                    printRouteVisualization(result);
                }
            });
        });
    }

    // Load Metrolink graph from file
    private static void loadGraph(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 4)
                    continue;

                String from = parts[0].trim();
                String to = parts[1].trim();
                String lineName = parts[2].trim().toLowerCase();
                double time = Double.parseDouble(parts[3].trim());

                graph.computeIfAbsent(from, k -> new ArrayList<>()).add(new Connection(to, lineName, time));
                graph.computeIfAbsent(to, k -> new ArrayList<>()).add(new Connection(from, lineName, time));
                stations.add(from);
                stations.add(to);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load walking times between stations from file
    private static void loadWalkTimes(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            List<String> stationNames = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (stationNames.isEmpty()) {
                    for (int i = 1; i < parts.length; i++) {
                        stationNames.add(parts[i].trim());
                    }
                    continue;
                }

                String from = parts[0].trim();
                Map<String, Double> walkMap = new HashMap<>();
                for (int i = 1; i < parts.length; i++) {
                    double time = Double.parseDouble(parts[i].trim());
                    walkMap.put(stationNames.get(i - 1), time);
                }
                walkTimes.put(from, walkMap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Get walking time between two stations
    private static double getWalkingTime(String from, String to) {
        return walkTimes.getOrDefault(from, new HashMap<>()).getOrDefault(to, Double.MAX_VALUE);
    }

    // Find the path with the shortest time
    private static List<String> findShortestTimePath(String start, String end) {
        PriorityQueue<PathNode> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> n.totalTime));
        Set<String> visited = new HashSet<>();

        List<String> initialPath = new ArrayList<>();
        initialPath.add(start);
        List<String> initialLines = new ArrayList<>();
        pq.add(new PathNode(start, 0.0, "", initialPath, initialLines, 0));

        while (!pq.isEmpty()) {
            PathNode current = pq.poll();

            if (current.station.equals(end)) {
                return formatDetailedRoute(current.path, current.lines, current.totalTime, current.changes);
            }

            String visitKey = current.station + "_" + current.line;
            if (visited.contains(visitKey))
                continue;
            visited.add(visitKey);

            List<Connection> neighbors = graph.getOrDefault(current.station, new ArrayList<>());
            for (Connection conn : neighbors) {
                String nextVisitKey = conn.to + "_" + conn.line;
                if (!visited.contains(nextVisitKey) && !closedStations.contains(conn.to)) {
                    List<String> newPath = new ArrayList<>(current.path);
                    newPath.add(conn.to);

                    List<String> newLines = new ArrayList<>(current.lines);
                    newLines.add(conn.line);

                    boolean isChange = !current.line.isEmpty() && !current.line.equals(conn.line);
                    double changePenalty = isChange ? 2.0 : 0.0;
                    int nesChanges = current.changes + (isChange ? 1 : 0);
                    double newTotalTime = current.totalTime + conn.time + changePenalty;

                    if (delays.containsKey(conn.to)) {
                        newTotalTime += delays.get(conn.to);
                    }

                    if (isChange) {
                        double walkTime = getWalkingTime(current.station, conn.to);
                        if (walkTime < Double.MAX_VALUE) {
                            newTotalTime += walkTime;
                        }
                    }

                    pq.add(new PathNode(conn.to, newTotalTime, conn.line, newPath, newLines, nesChanges));
                }
            }
        }
        return null;
    }

    // Find the path with fewest line changes
    private static List<String> findFewestChangePath(String start, String end) {
        PriorityQueue<PathNode> pq = new PriorityQueue<>(
                Comparator.comparingInt((PathNode n) -> n.changes).thenComparingDouble(n -> n.totalTime));
        Map<String, Integer> bestChanges = new HashMap<>();

        List<String> initialPath = new ArrayList<>();
        initialPath.add(start);
        List<String> initialLines = new ArrayList<>();
        pq.add(new PathNode(start, 0.0, "", initialPath, initialLines, 0));

        while (!pq.isEmpty()) {
            PathNode current = pq.poll();

            if (bestChanges.containsKey(current.station) && current.changes > bestChanges.get(current.station)) {
                continue;
            }

            bestChanges.put(current.station, current.changes);

            if (current.station.equals(end)) {
                return formatDetailedRouteFewestChanges(current.path, current.lines, current.totalTime,
                        current.changes);
            }

            List<Connection> neighbors = graph.getOrDefault(current.station, new ArrayList<>());
            for (Connection conn : neighbors) {
                List<String> newPath = new ArrayList<>(current.path);
                newPath.add(conn.to);

                List<String> newLines = new ArrayList<>(current.lines);
                newLines.add(conn.line);

                boolean isChange = !current.line.isEmpty() && !current.line.equals(conn.line);
                int nesChanges = current.changes + (isChange ? 1 : 0);
                double newTotalTime = current.totalTime + conn.time + (isChange ? 2.0 : 0.0);

                if (delays.containsKey(conn.to)) {
                    newTotalTime += delays.get(conn.to);
                }

                if (isChange) {
                    double walkTime = getWalkingTime(current.station, conn.to);
                    if (walkTime < Double.MAX_VALUE) {
                        newTotalTime += walkTime;
                    }
                }

                pq.add(new PathNode(conn.to, newTotalTime, conn.line, newPath, newLines, nesChanges));
            }
        }
        return null;
    }

    // Format the route with fewest changes
    private static List<String> formatDetailedRouteFewestChanges(List<String> path, List<String> lines,
            double totalTime, int changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("*** Route with Fewest Changes ***\n");
        String currentLine = "";
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String lineUsed = lines.get(i);

            if (!currentLine.isEmpty() && !currentLine.equals(lineUsed)) {
                sb.append("\n*** Change to ").append(lineUsed).append(" Line ***\n");
            }

            currentLine = lineUsed;
            sb.append(from).append(" (").append(currentLine).append(" line)\n");
        }

        sb.append(path.get(path.size() - 1)).append(" (").append(currentLine).append(" line)\n");
        sb.append("\nTotal Time: ").append(totalTime).append(" mins\n");
        sb.append("Total Changes: ").append(changes).append("\n");

        return Arrays.asList(sb.toString().split("\n"));
    }

    // Format the route with fastest travel time
    private static List<String> formatDetailedRoute(List<String> path, List<String> lines, double totalTime,
            int changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("*** Fastest Time Route ***\n");

        String currentLine = "";

        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String lineUsed = lines.get(i);

            if (!currentLine.isEmpty() && !currentLine.equals(lineUsed)) {
                sb.append("\n*** Change to ").append(lineUsed).append(" Line ***\n\n");
            }

            currentLine = lineUsed;
            sb.append(from).append(" (").append(currentLine).append(" line)\n");
        }

        sb.append(path.get(path.size() - 1)).append(" (").append(currentLine).append(" line)\n");
        sb.append("\nTotal Time: ").append(totalTime).append(" mins\n");
        sb.append("TotalsChanges: ").append(changes).append("\n");

        return Arrays.asList(sb.toString().split("\n"));
    }

    // Print route to console
    private static void printRouteVisualization(List<String> result) {
        System.out.println("\n--- Route Visualization ---");
        for (String step : result) {
            System.out.println(step);
        }
    }

    // Represents a connection between stations
    static class Connection {
        String to;
        String line;
        double time;

        Connection(String to, String line, double time) {
            this.to = to;
            this.line = line;
            this.time = time;
        }
    }

    // Node used in pathfinding algorithms
    static class PathNode implements Comparable<PathNode> {
        String station;
        double totalTime;
        String line;
        List<String> path;
        List<String> lines;
        int changes;

        PathNode(String station, double totalTime, String line, List<String> path, List<String> lines, int changes) {
            this.station = station;
            this.totalTime = totalTime;
            this.line = line;
            this.path = path;
            this.lines = lines;
            this.changes = changes;
        }

        @Override
        public int compareTo(PathNode other) {
            return Double.compare(this.totalTime, other.totalTime);
        }
    }
}