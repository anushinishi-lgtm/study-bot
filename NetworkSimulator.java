import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;


public class NetworkSimulator {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}

class MainWindow extends JFrame {
    GraphCanvas canvas;
    DefaultListModel<String> routerListModel = new DefaultListModel<>();
    JList<String> routerList;
    JTextArea logBox;
    JComboBox<String> algoSelect;
    JComboBox<String> sourceSelect;

    MainWindow() {
        setTitle("Network Routing Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(new EmptyBorder(10, 10, 10, 10));
        left.setPreferredSize(new Dimension(180, 0));

        JButton addRouter = new JButton("Add Router");
        JButton addLink = new JButton("Add Link");
        JButton run = new JButton("Run Algorithm");
        JButton clear = new JButton("Clear Network");

        left.add(addRouter);
        left.add(Box.createRigidArea(new Dimension(0, 6)));
        left.add(addLink);
        left.add(Box.createRigidArea(new Dimension(0, 6)));
        left.add(run);
        left.add(Box.createRigidArea(new Dimension(0, 6)));
        left.add(clear);
        left.add(Box.createRigidArea(new Dimension(0, 12)));

        algoSelect = new JComboBox<>(new String[]{"Distance Vector", "Link State"});
        left.add(new JLabel("Algorithm:"));
        left.add(algoSelect);
        left.add(Box.createRigidArea(new Dimension(0, 12)));

        left.add(new JLabel("Routers in network:"));
        routerList = new JList<>(routerListModel);
        routerList.setVisibleRowCount(8);
        JScrollPane sp = new JScrollPane(routerList);
        sp.setPreferredSize(new Dimension(160, 160));
        left.add(sp);

        canvas = new GraphCanvas(this);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(10, 10, 10, 10));
        right.setPreferredSize(new Dimension(340, 0));

        right.add(new JLabel("Select source node (for Link State):"));
        sourceSelect = new JComboBox<>();
        right.add(sourceSelect);
        right.add(Box.createRigidArea(new Dimension(0, 12)));

        right.add(new JLabel("Routing tables (select node tab):"));
        JTabbedPane tableTabs = canvas.tableTabs;
        tableTabs.setPreferredSize(new Dimension(320, 300));
        right.add(tableTabs);
        right.add(Box.createRigidArea(new Dimension(0, 12)));

        right.add(new JLabel("Event log:"));
        logBox = new JTextArea(10, 30);
        logBox.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logBox);
        right.add(logScroll);

        getContentPane().add(left, BorderLayout.WEST);
        getContentPane().add(canvas, BorderLayout.CENTER);
        getContentPane().add(right, BorderLayout.EAST);

        addRouter.addActionListener(e -> canvas.enableAddRouter());
        addLink.addActionListener(e -> canvas.enableAddLink());
        clear.addActionListener(e -> {
            canvas.clearAll();
            log("Network cleared");
        });
        run.addActionListener(e -> runAlgorithm());
    }

    void log(String msg) {
        logBox.append(msg + "\n");
        logBox.setCaretPosition(logBox.getDocument().getLength());
    }

    void updateLists(List<String> names) {
        routerListModel.clear();
        sourceSelect.removeAllItems();
        for (String n : names) {
            routerListModel.addElement(n);
            sourceSelect.addItem(n);
        }
    }

    void runAlgorithm() {
        String algo = (String) algoSelect.getSelectedItem();
        String source = (String) sourceSelect.getSelectedItem();
        if ("Link State".equals(algo) && source == null) {
            JOptionPane.showMessageDialog(this, "Please select a source node for Link State.", "No source", JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("Running " + algo + (source == null ? "" : (" from " + source)));
        if ("Distance Vector".equals(algo)) {
            new DVSimulator(canvas).run();
        } else {
            new LSSimulator(canvas).run(source);
        }
    }
}

class GraphCanvas extends JPanel {
    MainWindow window;
    List<RouterNode> nodes = new ArrayList<>();
    List<LinkEdge> links = new ArrayList<>();

    enum Mode {NONE, ADD_ROUTER, ADD_LINK}
    Mode mode = Mode.NONE;

    RouterNode dragNode = null;
    Point dragOffset = null;
    RouterNode firstLinkNode = null;

    JTabbedPane tableTabs = new JTabbedPane();

    GraphCanvas(MainWindow w) {
        this.window = w;
        setBackground(Color.WHITE);

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                if (mode == Mode.ADD_ROUTER) {
                    RouterNode r = new RouterNode(nextId(), p.x, p.y);
                    nodes.add(r);
                    // initialize its routing table (self)
                    r.table.clear();
                    r.table.put(r.id, new RouteItem(null, 0));
                    window.log("Added router " + r.id);
                    mode = Mode.NONE;
                    refresh();
                    return;
                } else if (mode == Mode.ADD_LINK) {
                    RouterNode clicked = findNode(p);
                    if (clicked != null) {
                        if (firstLinkNode == null) {
                            firstLinkNode = clicked;
                            window.log("Selected first router for link: " + clicked.id);
                        } else if (firstLinkNode == clicked) {
                            window.log("Select a different router for the link.");
                        } else {
                            String costStr = JOptionPane.showInputDialog(GraphCanvas.this, "Enter link cost (integer):", "1");
                            if (costStr != null) {
                                try {
                                    int cost = Integer.parseInt(costStr.trim());
                                    LinkEdge edge = new LinkEdge(firstLinkNode, clicked, cost);
                                    links.add(edge);
                                    // update immediate neighbor entries in routing tables
                                    ensureNodeTablesExist(firstLinkNode);
                                    ensureNodeTablesExist(clicked);
                                    firstLinkNode.table.put(clicked.id, new RouteItem(clicked.id, cost));
                                    clicked.table.put(firstLinkNode.id, new RouteItem(firstLinkNode.id, cost));
                                    window.log("Added link " + firstLinkNode.id + " <-> " + clicked.id + " cost=" + cost);
                                    refresh();
                                } catch (NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(GraphCanvas.this, "Invalid number", "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                            firstLinkNode = null;
                            mode = Mode.NONE;
                        }
                    }
                    return;
                }

                RouterNode n = findNode(p);
                if (n != null) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        dragNode = n;
                        dragOffset = new Point(p.x - n.x, p.y - n.y);
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                dragNode = null;
                dragOffset = null;
            }

            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    Point p = e.getPoint();
                    RouterNode n = findNode(p);
                    if (n != null) {
                        int confirm = JOptionPane.showConfirmDialog(GraphCanvas.this, "Remove router " + n.id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            removeNode(n);
                            window.log("Removed router " + n.id);
                            refresh();
                        }
                        return;
                    }
                    LinkEdge edge = findLink(p);
                    if (edge != null) {
                        int confirm = JOptionPane.showConfirmDialog(GraphCanvas.this, "Remove link " + edge.a.id + "-" + edge.b.id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            links.remove(edge);
                            // remove neighbor entries from tables
                            if (edge.a != null && edge.b != null) {
                                if (edge.a.table != null) edge.a.table.remove(edge.b.id);
                                if (edge.b.table != null) edge.b.table.remove(edge.a.id);
                            }
                            window.log("Removed link " + edge.a.id + "-" + edge.b.id);
                            refresh();
                        }
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragNode != null && dragOffset != null) {
                    dragNode.x = e.getX() - dragOffset.x;
                    dragNode.y = e.getY() - dragOffset.y;
                    refresh();
                }
            }
        });

        updateTables();
    }

    void enableAddRouter() {
        mode = Mode.ADD_ROUTER;
        window.log("Click on canvas to place new router");
    }

    void enableAddLink() {
        mode = Mode.ADD_LINK;
        firstLinkNode = null;
        window.log("Click two routers to connect them with a link");
    }

    void clearAll() {
        nodes.clear();
        links.clear();
        updateTables();
        window.updateLists(getNodeIds());
        repaint();
    }

    String nextId() {
        for (char c = 'A'; c <= 'Z'; c++) {
            String id = String.valueOf(c);
            boolean used = false;
            for (RouterNode n : nodes) if (n.id.equals(id)) used = true;
            if (!used) return id;
        }
        return "R" + (nodes.size() + 1);
    }

    RouterNode findNode(Point p) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            RouterNode n = nodes.get(i);
            if (Math.hypot(p.x - n.x, p.y - n.y) <= RouterNode.RADIUS) return n;
        }
        return null;
    }

    LinkEdge findLink(Point p) {
        for (LinkEdge e : links) {
            if (e.containsPoint(p)) return e;
        }
        return null;
    }

    void removeNode(RouterNode n) {
        nodes.remove(n);
        links.removeIf(e -> e.a == n || e.b == n);
        // remove references in other node tables
        for (RouterNode r : nodes) {
            if (r.table != null) r.table.remove(n.id);
        }
    }

    List<String> getNodeIds() {
        List<String> ids = new ArrayList<>();
        for (RouterNode n : nodes) ids.add(n.id);
        return ids;
    }

    void ensureNodeTablesExist(RouterNode n) {
        if (n.table == null) n.table = new HashMap<>();
        if (!n.table.containsKey(n.id)) n.table.put(n.id, new RouteItem(null, 0));
    }

    void updateTables() {
        // ensure every node's table contains entries for all nodes (INF if unknown),
        // and direct neighbors' costs where available
        for (RouterNode r : nodes) {
            if (r.table == null) r.table = new HashMap<>();
            // self entry
            r.table.put(r.id, new RouteItem(null, 0));
        }
        // fill direct neighbor costs from links
        for (LinkEdge l : links) {
            if (l.a.table == null) l.a.table = new HashMap<>();
            if (l.b.table == null) l.b.table = new HashMap<>();
            l.a.table.put(l.b.id, new RouteItem(l.b.id, l.cost));
            l.b.table.put(l.a.id, new RouteItem(l.a.id, l.cost));
        }
        // ensure all destinations exist (INF if unreachable)
        for (RouterNode r : nodes) {
            for (RouterNode dest : nodes) {
                if (!r.table.containsKey(dest.id)) {
                    r.table.put(dest.id, new RouteItem(null, RouteItem.INF));
                }
            }
        }

        tableTabs.removeAll();
        for (RouterNode n : nodes) {
            JTextArea ta = new JTextArea(n.tableString());
            ta.setEditable(false);
            tableTabs.addTab(n.id, new JScrollPane(ta));
        }
    }

    void refresh() {
        updateTables();
        repaint();
        window.updateLists(getNodeIds());
    }

    public Dimension getPreferredSize() {
        return new Dimension(700, 600);
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // draw edges first
        for (LinkEdge e : links) e.draw(g2);
        // draw nodes
        for (RouterNode n : nodes) n.draw(g2);
    }

    static double ptSegDist(double x1, double y1, double x2, double y2, double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            dx = px - x1;
            dy = py - y1;
            return Math.hypot(dx, dy);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        double cx = x1 + t * dx;
        double cy = y1 + t * dy;
        return Math.hypot(px - cx, py - cy);
    }
}

class RouterNode {
    static final int RADIUS = 20;
    String id;
    int x, y;
    Map<String, RouteItem> table = new HashMap<>();

    RouterNode(String id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        table.put(id, new RouteItem(null, 0));
    }

    void draw(Graphics2D g) {
        g.setColor(Color.CYAN);
        g.fillOval(x - RADIUS, y - RADIUS, RADIUS * 2, RADIUS * 2);
        g.setColor(Color.BLACK);
        g.drawOval(x - RADIUS, y - RADIUS, RADIUS * 2, RADIUS * 2);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(id);
        g.drawString(id, x - w / 2, y + fm.getAscent() / 2 - 2);
    }

    boolean contains(Point p) {
        return Math.hypot(p.x - x, p.y - y) <= RADIUS;
    }

    String tableString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Router ").append(id).append("\n\n");
        sb.append(String.format("%-10s %-10s %-10s\n", "Dest", "NextHop", "Cost"));
        List<String> dests = new ArrayList<>(table.keySet());
        Collections.sort(dests);
        for (String d : dests) {
            RouteItem ri = table.get(d);
            String hop = ri.hop == null ? "-" : ri.hop;
            String cost = ri.cost >= RouteItem.INF ? "INF" : String.valueOf(ri.cost);
            sb.append(String.format("%-10s %-10s %-10s\n", d, hop, cost));
        }
        return sb.toString();
    }
}

class LinkEdge {
    RouterNode a, b;
    int cost;

    LinkEdge(RouterNode a, RouterNode b, int cost) {
        this.a = a;
        this.b = b;
        this.cost = cost;
    }

    void draw(Graphics2D g) {
        g.setColor(Color.GRAY);
        g.setStroke(new BasicStroke(2));
        g.drawLine(a.x, a.y, b.x, b.y);
        int mx = (a.x + b.x) / 2;
        int my = (a.y + b.y) / 2;
        g.setColor(Color.RED);
        FontMetrics fm = g.getFontMetrics();
        String s = String.valueOf(cost);
        g.drawString(s, mx - fm.stringWidth(s) / 2, my - 4);
    }

    boolean containsPoint(Point p) {
        return GraphCanvas.ptSegDist(a.x, a.y, b.x, b.y, p.x, p.y) < 6.0;
    }
}

class RouteItem {
    static final int INF = 1_000_000;
    String hop;
    int cost;

    RouteItem(String hop, int cost) {
        this.hop = hop;
        this.cost = cost;
    }
}

class DVSimulator {
    GraphCanvas canvas;
    MainWindow window;
    Map<String, RouterNode> nodeMap = new HashMap<>();
    Map<String, List<Neighbor>> neighbors = new HashMap<>();

    static class Neighbor {
        String id;
        int cost;

        Neighbor(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }
    }

    DVSimulator(GraphCanvas c) {
        this.canvas = c;
        this.window = c.window;
        for (RouterNode r : c.nodes) nodeMap.put(r.id, r);
        for (RouterNode r : c.nodes) neighbors.put(r.id, new ArrayList<>());
        for (LinkEdge e : c.links) {
            neighbors.get(e.a.id).add(new Neighbor(e.b.id, e.cost));
            neighbors.get(e.b.id).add(new Neighbor(e.a.id, e.cost));
        }
    }

    void run() {
        // initialize tables
        for (RouterNode r : canvas.nodes) {
            r.table.clear();
            for (RouterNode d : canvas.nodes) {
                if (d == r) r.table.put(d.id, new RouteItem(null, 0));
                else r.table.put(d.id, new RouteItem(null, RouteItem.INF));
            }
        }
        for (LinkEdge e : canvas.links) {
            e.a.table.put(e.b.id, new RouteItem(e.b.id, e.cost));
            e.b.table.put(e.a.id, new RouteItem(e.a.id, e.cost));
        }
        canvas.refresh();

        new SwingWorker<Void, String>() {
            protected Void doInBackground() throws Exception {
                boolean changed = true;
                int rounds = 0;
                while (changed && rounds < 30) {
                    rounds++;
                    changed = false;
                    publish("--- Round " + rounds + " ---");
                    Map<String, Map<String, RouteItem>> sent = new HashMap<>();
                    for (RouterNode n : canvas.nodes) {
                        Map<String, RouteItem> copy = new HashMap<>();
                        for (Map.Entry<String, RouteItem> e : n.table.entrySet()) {
                            copy.put(e.getKey(), new RouteItem(e.getValue().hop, e.getValue().cost));
                        }
                        sent.put(n.id, copy);
                    }
                    for (RouterNode receiver : canvas.nodes) {
                        List<Neighbor> nbs = neighbors.get(receiver.id);
                        if (nbs == null) continue;
                        for (Neighbor nb : nbs) {
                            Map<String, RouteItem> vec = sent.get(nb.id);
                            if (vec == null) continue;
                            for (Map.Entry<String, RouteItem> adv : vec.entrySet()) {
                                String dest = adv.getKey();
                                int advCost = adv.getValue().cost >= RouteItem.INF ? RouteItem.INF : adv.getValue().cost + nb.cost;
                                RouteItem cur = receiver.table.get(dest);
                                if (cur == null) {
                                    cur = new RouteItem(null, RouteItem.INF);
                                    receiver.table.put(dest, cur);
                                }
                                if (advCost < cur.cost) {
                                    cur.cost = advCost;
                                    cur.hop = nb.id;
                                    changed = true;
                                    publish("Node " + receiver.id + " updated dest " + dest + " via " + nb.id + " cost=" + advCost);
                                }
                            }
                        }
                    }
                    canvas.refresh();
                    Thread.sleep(600);
                }
                publish("Converged after " + rounds + " rounds (or reached limit).");
                return null;
            }

            protected void process(List<String> chunks) {
                for (String s : chunks) window.log(s);
            }

            protected void done() {
                canvas.refresh();
            }
        }.execute();
    }
}

class LSSimulator {
    GraphCanvas canvas;
    MainWindow window;
    Map<String, RouterNode> nodeMap = new HashMap<>();

    LSSimulator(GraphCanvas c) {
        this.canvas = c;
        this.window = c.window;
        for (RouterNode r : c.nodes) nodeMap.put(r.id, r);
    }

    void run(String source) {
        Map<String, List<Adj>> adj = new HashMap<>();
        for (RouterNode r : canvas.nodes) adj.put(r.id, new ArrayList<>());
        for (LinkEdge e : canvas.links) {
            adj.get(e.a.id).add(new Adj(e.b.id, e.cost));
            adj.get(e.b.id).add(new Adj(e.a.id, e.cost));
        }

        window.log("Flooding LS information (simulated) ...");

        new SwingWorker<Void, String>() {
            protected Void doInBackground() throws Exception {
                for (String s : new ArrayList<>(adj.keySet())) {
                    // Dijkstra
                    Map<String, Integer> dist = new HashMap<>();
                    Map<String, String> prev = new HashMap<>();
                    for (String v : adj.keySet()) dist.put(v, RouteItem.INF);
                    dist.put(s, 0);
                    PriorityQueue<NodeDist> pq = new PriorityQueue<>(Comparator.comparingInt(nd -> nd.d));
                    pq.add(new NodeDist(s, 0));
                    while (!pq.isEmpty()) {
                        NodeDist cur = pq.poll();
                        if (!Objects.equals(cur.d, dist.get(cur.node))) continue;
                        List<Adj> outs = adj.get(cur.node);
                        if (outs == null) continue;
                        for (Adj a : outs) {
                            int nd = cur.d + a.cost;
                            if (nd < dist.get(a.to)) {
                                dist.put(a.to, nd);
                                prev.put(a.to, cur.node);
                                pq.add(new NodeDist(a.to, nd));
                            }
                        }
                    }
                    RouterNode rn = nodeMap.get(s);
                    if (rn == null) continue;
                    rn.table.clear();
                    for (String d : dist.keySet()) {
                        if (d.equals(s)) {
                            rn.table.put(d, new RouteItem(null, 0));
                            continue;
                        }
                        int cost = dist.get(d);
                        if (cost >= RouteItem.INF) {
                            rn.table.put(d, new RouteItem(null, RouteItem.INF));
                        } else {
                            // compute next-hop from s to d
                            String next = d;
                            String p = prev.get(next);
                            while (p != null && !p.equals(s)) {
                                next = p;
                                p = prev.get(next);
                            }
                            rn.table.put(d, new RouteItem(next, cost));
                        }
                    }
                    publish("Computed LS routes for " + s);
                    canvas.refresh();
                    Thread.sleep(250);
                }
                publish("Link State computation complete");
                return null;
            }

            protected void process(List<String> chunks) {
                for (String s : chunks) window.log(s);
            }
        }.execute();
    }

    static class Adj {
        String to;
        int cost;

        Adj(String to, int cost) {
            this.to = to;
            this.cost = cost;
        }
    }

    static class NodeDist {
        String node;
        int d;

        NodeDist(String node, int d) { this.node = node; this.d = d; }
    }
}
