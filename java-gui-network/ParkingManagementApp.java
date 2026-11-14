import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ParkingManagementApp {

    enum SpaceType { MOTORCYCLE, COMPACT, LARGE }

    public static abstract class vehicle {
        protected String plate;
        protected int typeRank;
        protected int level = -1, row = -1, startBay = -1;

        public vehicle(String plate, int typeRank) {
            this.plate = plate;
            this.typeRank = typeRank;
        }

        public String getPlate() { return plate; }
        public int getTypeRank() { return typeRank; }
        public abstract String getTypeName();
        public abstract int getRequiredSpaces();
        public abstract boolean canFit(SpaceType spaceType);

        public void setLocation(int lvl, int r, int bay) {
            this.level = lvl;
            this.row = r;
            this.startBay = bay;
        }

        public String getLocation() {
            return (level == -1) ? "Unparked" : "L" + level + "-R" + row + "-Bay" + startBay;
        }

        public static class motorcycles extends vehicle {
            public motorcycles(String plate) { super(plate, 1); }
            @Override public String getTypeName() { return "Motorcycle"; }
            @Override public int getRequiredSpaces() { return 1; }
            @Override public boolean canFit(SpaceType s) { return true; }
        }

        public static class cars extends vehicle {
            public cars(String plate) { super(plate, 2); }
            @Override public String getTypeName() { return "Car"; }
            @Override public int getRequiredSpaces() { return 1; }
            @Override public boolean canFit(SpaceType s) {
                return s == SpaceType.COMPACT || s == SpaceType.LARGE;
            }
        }

        public static class buses extends vehicle {
            public buses(String plate) { super(plate, 3); }
            @Override public String getTypeName() { return "Bus"; }
            @Override public int getRequiredSpaces() { return 5; }
            @Override public boolean canFit(SpaceType s) {
                return s == SpaceType.LARGE;
            }
        }
    }

    static class BayNode {
        int bayId;
        SpaceType type;
        boolean occupied = false;
        String plate = "";
        BayNode prev, next;

        BayNode(int id, SpaceType type) {
            this.bayId = id;
            this.type = type;
        }
    }

    static class RowDoublyLinkedList {
        BayNode head, tail;

        void addBay(int id, SpaceType type) {
            BayNode node = new BayNode(id, type);
            if (head == null) head = tail = node;
            else {
                tail.next = node;
                node.prev = tail;
                tail = node;
            }
        }

        int parkVehicle(vehicle v) {
            BayNode curr = head;
            int needed = v.getRequiredSpaces();
            while (curr != null) {
                if (!curr.occupied && v.canFit(curr.type)) {
                    if (needed == 1) {
                        curr.occupied = true;
                        curr.plate = v.getPlate();
                        return curr.bayId;
                    } else if (needed == 5) {
                        BayNode scan = curr;
                        int count = 0;
                        while (scan != null && count < 5 && !scan.occupied && scan.type == SpaceType.LARGE) {
                            count++;
                            scan = scan.next;
                        }
                        if (count == 5) {
                            BayNode fill = curr;
                            for (int i = 0; i < 5; i++) {
                                fill.occupied = true;
                                fill.plate = v.getPlate();
                                fill = fill.next;
                            }
                            return curr.bayId;
                        }
                    }
                }
                curr = curr.next;
            }
            return -1;
        }
    }

    static class BayBinaryTree {
        static class Node {
            int bayCode;
            String plate;
            Node left, right;
            Node(int code, String plate) { this.bayCode = code; this.plate = plate; }
        }
        Node root;
        void insert(int code, String plate) { root = insertRec(root, code, plate); }
        private Node insertRec(Node r, int code, String plate) {
            if (r == null) return new Node(code, plate);
            if (code < r.bayCode) r.left = insertRec(r.left, code, plate);
            else if (code > r.bayCode) r.right = insertRec(r.right, code, plate);
            return r;
        }
    }

    static class CustomHashTable {
        private final LinkedList<vehicle>[] table;
        @SuppressWarnings("unchecked")
        CustomHashTable(int size) {
            table = new LinkedList[size];
            for (int i = 0; i < size; i++) table[i] = new LinkedList<>();
        }
        private int hash(String key) {
            return Math.abs(key.hashCode()) % table.length;
        }
        void put(vehicle v) { table[hash(v.getPlate())].add(v); }
        vehicle get(String plate) {
            for (vehicle v : table[hash(plate)]) {
                if (v.getPlate().equals(plate)) return v;
            }
            return null;
        }
    }

    static class BinarySearch {
        public static vehicle findByPlate(List<vehicle> sortedList, String plate) {
            int low = 0, high = sortedList.size() - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int cmp = sortedList.get(mid).getPlate().compareToIgnoreCase(plate);
                if (cmp == 0) return sortedList.get(mid);
                if (cmp < 0) low = mid + 1;
                else high = mid - 1;
            }
            return null;
        }
    }

    static class MergeSort {
        public static Comparator<vehicle> TYPE_COMPARATOR = Comparator
                .comparingInt(vehicle::getTypeRank)
                .thenComparing(vehicle::getPlate);

        public static List<vehicle> sort(List<vehicle> list) {
            if (list.size() <= 1) return list;
            int mid = list.size() / 2;
            List<vehicle> left = sort(new ArrayList<>(list.subList(0, mid)));
            List<vehicle> right = sort(new ArrayList<>(list.subList(mid, list.size())));
            return merge(left, right);
        }

        private static List<vehicle> merge(List<vehicle> left, List<vehicle> right) {
            List<vehicle> merged = new ArrayList<>();
            int i = 0, j = 0;
            while (i < left.size() && j < right.size()) {
                if (TYPE_COMPARATOR.compare(left.get(i), right.get(j)) <= 0) merged.add(left.get(i++));
                else merged.add(right.get(j++));
            }
            while (i < left.size()) merged.add(left.get(i++));
            while (j < right.size()) merged.add(right.get(j++));
            return merged;
        }
    }

    public static void runIPCDemo() throws Exception {
        BlockingQueue<String> msgQueue = new ArrayBlockingQueue<>(5);
        Path shmPath = Files.createTempFile("parking_shm", ".bin");
        try (FileChannel channel = FileChannel.open(shmPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MappedByteBuffer shmBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 64);
            shmBuffer.putInt(0, 0);

            Thread producer = new Thread(() -> {
                try {
                    msgQueue.put("GATE-IN: CAR-888");
                    msgQueue.put("GATE-IN: MOTO-22");
                } catch (InterruptedException ignored) {}
            });

            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        String msg = msgQueue.take();
                        int count = shmBuffer.getInt(0) + 1;
                        shmBuffer.putInt(0, count);
                        System.out.println("[Java IPC Consumer] Received: " + msg + " | SharedMem Count: " + count);
                    }
                } catch (InterruptedException ignored) {}
            });

            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
        }
        Files.deleteIfExists(shmPath);
    }

    public static void runOneWaySocketDemo() throws Exception {
        int port = 5055;
        CountDownLatch serverReady = new CountDownLatch(1);

        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverReady.countDown();
                try (Socket client = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                    String received = in.readLine();
                    System.out.println("[Java Socket Server] Displayed Message from Client: " + received);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        serverThread.start();
        serverReady.await();

        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("Gate Sensor Alert: Bus BUS-901 entered Level 1 Row A.");
        }
        serverThread.join();
    }

    static class ParkingLot2DPanel extends JPanel {
        private final RowDoublyLinkedList row;

        ParkingLot2DPanel(RowDoublyLinkedList row) {
            this.row = row;
            setPreferredSize(new Dimension(680, 180));
            setBackground(new Color(40, 44, 52));

            setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.stringFlavor);
                }

                @Override
                public boolean importData(TransferSupport support) {
                    try {
                        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        String[] parts = data.split(":");
                        String type = parts[0];
                        String plate = parts[1];

                        vehicle v = switch (type) {
                            case "Motorcycle" -> new vehicle.motorcycles(plate);
                            case "Car" -> new vehicle.cars(plate);
                            default -> new vehicle.buses(plate);
                        };

                        int bay = row.parkVehicle(v);
                        if (bay != -1) {
                            repaint();
                            JOptionPane.showMessageDialog(ParkingLot2DPanel.this,
                                    "Parked " + type + " (" + plate + ") starting at Bay " + bay);
                            return true;
                        } else {
                            JOptionPane.showMessageDialog(ParkingLot2DPanel.this,
                                    "No valid space available for " + type + " (" + plate + ")!",
                                    "Allocation Error", JOptionPane.WARNING_MESSAGE);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    return false;
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Level 1 - Row A (2D Parking Map - Drag Vehicles Here)", 20, 25);

            BayNode curr = row.head;
            int x = 20;
            while (curr != null) {
                g2.setColor(curr.occupied ? new Color(220, 76, 70) : new Color(76, 175, 80));
                g2.fillRoundRect(x, 45, 58, 95, 10, 10);
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(x, 45, 58, 95, 10, 10);

                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.drawString("Bay " + curr.bayId, x + 10, 65);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.drawString(curr.type.name().substring(0, 4), x + 12, 82);
                if (curr.occupied) {
                    g2.drawString(curr.plate, x + 5, 115);
                } else {
                    g2.drawString("FREE", x + 14, 115);
                }
                x += 64;
                curr = curr.next;
            }
        }
    }

    public static void launchGUI(RowDoublyLinkedList row) {
        JFrame frame = new JFrame("Vehicle Parking Management System (Java 2D GUI)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Help");
        JMenuItem guideItem = new JMenuItem("User Guide & Rules");
        guideItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "HOW TO USE:\n" +
                "1. Drag a vehicle chip from the top bar and drop it onto the 2D parking lot.\n" +
                "2. Rules: Motorcycles fit anywhere; Cars need Compact/Large; Buses need 5 consecutive Large bays.\n" +
                "3. Green bays = Free | Red bays = Occupied.",
                "System Help File", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(guideItem);
        menuBar.add(helpMenu);
        frame.setJMenuBar(menuBar);

        JPanel dragPanel = new JPanel();
        String[] tokens = {"Motorcycle:MOTO-77", "Car:CAR-404", "Bus:BUS-901"};
        for (String token : tokens) {
            JLabel lbl = new JLabel(" [Drag: " + token + "] ");
            lbl.setOpaque(true);
            lbl.setBackground(new Color(230, 236, 245));
            lbl.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            lbl.setTransferHandler(new TransferHandler("text"));
            lbl.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent e) {
                    JComponent c = (JComponent) e.getSource();
                    c.getTransferHandler().exportAsDrag(c, e, TransferHandler.COPY);
                }
            });
            dragPanel.add(lbl);
        }

        frame.add(dragPanel, BorderLayout.NORTH);
        frame.add(new ParkingLot2DPanel(row), BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        RowDoublyLinkedList level1RowA = new RowDoublyLinkedList();
        for (int i = 1; i <= 2; i++) level1RowA.addBay(i, SpaceType.MOTORCYCLE);
        for (int i = 3; i <= 5; i++) level1RowA.addBay(i, SpaceType.COMPACT);
        for (int i = 6; i <= 10; i++) level1RowA.addBay(i, SpaceType.LARGE);

        BayBinaryTree bst = new BayBinaryTree();
        CustomHashTable hashTable = new CustomHashTable(17);

        vehicle c1 = new vehicle.cars("CAR-101");
        int bay = level1RowA.parkVehicle(c1);
        c1.setLocation(1, 1, bay);
        bst.insert(100 + bay, c1.getPlate());
        hashTable.put(c1);

        List<vehicle> list = new ArrayList<>(List.of(
                new vehicle.buses("BUS-202"),
                c1,
                new vehicle.motorcycles("MOTO-05")
        ));
        list = MergeSort.sort(list);
        System.out.println("Sorted Vehicles by Type (Java MergeSort):");
        list.forEach(v -> System.out.println(" - " + v.getTypeName() + ": " + v.getPlate()));

        runIPCDemo();
        runOneWaySocketDemo();

        SwingUtilities.invokeLater(() -> launchGUI(level1RowA));
    }
}
