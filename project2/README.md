The main entry point of the program is `./src/main/JourneyPlianer.java`, and executing this file will run the program.
Program data is stored in the `./data` directory, which contains two files: one for the route map and another for walking times.

When the program runs, a window will pop up. Users can select the departure station and arrival station.
The third field is for closed stations, where users can input station names separated by commas (e.g., `Manchester, Altrincham, Bury`), indicating these stations are closed.
The fourth field is for delay information. In the `delayField` text box, users should enter station names and their corresponding delay times separated by commas (e.g., `Altrincham:5.0, Bury:3.0, Manchester:10.0`), indicating delays for these stations.

 **Here's a concise explanation of the program:** 

**Core Functionality**

- A GUI-based journey planner for Manchester Metrolink system
- Supports two search modes:
  • Fastest route (Dijkstra's algorithm)
  • Fewest transfers (BFS-inspired with change count priority)

**Data Handling**

- Loads transportation data from:
  • `./data/Metrolink_times_linecolour.csv` (rail connections)
  • `./data/walktimes.csv` (walking times between stations)
- Maintains data structures for:
  • Rail network graph (adjacency list)
  • Walking time matrix
  • Real-time closures and delays

**Key Features**

- Station selection via dropdown menus
- Handles special conditions:
  • Closed stations (completely excluded from routes)
  • Station delays (adds penalty time)
- Calculates transfer penalties (2 minutes) and walking times
- Provides both text display and console visualization of routes

**Algorithm Implementation**

- **Fastest Path**:
  • Priority queue sorted by total travel time
  • Considers line transfers, walking times, and delays
- **Fewest Changes**:
  • Prioritizes transfer count first, then time
  • Tracks line changes through path nodes

**Output Formatting**

- Detailed route breakdown showing:
  • Line changes
  • Station sequences with line information
  • Total time and transfer count
- Clear error handling for invalid inputs/no routes

**Technical Components**

- Swing-based GUI with input validation
- Immutable path nodes tracking:
  • Current station
  • Accumulated time
  • Line usage
  • Transfer count
  • Path history