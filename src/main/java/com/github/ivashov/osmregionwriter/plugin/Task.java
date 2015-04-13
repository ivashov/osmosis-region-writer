package com.github.ivashov.osmregionwriter.plugin;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

public class Task implements Sink {
    private static final Logger log = Logger.getLogger(Task.class.getName());

    private PrintWriter writer;
    private String file;

    private int count;

    private Tag tagLevel = new Tag("admin_level", "6");
    private Tag tagAdmin = new Tag("boundary", "administrative");

    private HashMap<Long, NodeDesc> nodes = new HashMap<>();
    private HashMap<Long, WayDesc> ways = new HashMap<>();

    private List<Segment> segments = new ArrayList<>();

    public Task(String file) {
        this.file = file;
    }

    @Override
    public void process(EntityContainer entityContainer) {
        Entity entity = entityContainer.getEntity();

        if (entity.getType() == EntityType.Relation) {
            Relation relation = (Relation) entity;
            Collection<Tag> tags = entity.getTags();

            String name = null;
            int found = 0;

            for (Tag tag : tags) {
                if (tag.getKey().equals("boundary") && tag.getValue().equals("administrative")) {
                    found++;
                } else if (tag.getKey().equals("admin_level") && tag.getValue().equals("6")) {
                    found++;
                } else if (tag.getKey().equals("name")) {
                    name = tag.getValue();
                }
            }

            if (found == 2) {

                if (name != null && name.contains("Петрозаводс")) {
                    log.info("Relation found");

                    List<RelationMember> ptzMembers = relation.getMembers();
                    for (RelationMember relationMember : ptzMembers) {
                        if (relationMember.getMemberType() == EntityType.Way) {
                            WayDesc wayDesc = ways.get(relationMember.getMemberId());
                            addWay(wayDesc.nodes);
                        }
                    }
                }
            }
        } else if (entity.getType() == EntityType.Node) {
            Node node = (Node) entity;
            nodes.put(node.getId(), new NodeDesc(node));
        } else if (entity.getType() == EntityType.Way) {
            Way way = (Way) entity;
            ways.put(way.getId(), new WayDesc(way));
        }
    }

    @Override
    public void initialize(Map<String, Object> metaData) {
        try {
            writer = new PrintWriter(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Can't open output file " + file);
        }
    }

    @Override
    public void complete() {
        writer.println("polygons");

        int c = 0;
        for (Segment segment : segments) {
            writer.println("" + ++c);

            for (Long node : segment.nodes) {
                NodeDesc nodeDesc = nodes.get(node);
                writer.println(nodeDesc.lon + " " + nodeDesc.lat);
            }

            writer.println("END");
        }
        writer.println("END");
    }

    @Override
    public void release() {
        writer.close();
    }

    private void addWay(long[] nodes) {
        log.info("Adding way");
        Segment newSegment = new Segment();
        newSegment.nodes = new ArrayDeque<>(nodes.length);
        for (long node : nodes) {
            newSegment.nodes.addLast(node);
        }

        for (Iterator<Segment> iterator = segments.iterator(); iterator.hasNext(); ) {
            Segment segment = iterator.next();

            if (newSegment.addSegment(segment)) {
                iterator.remove();
            }
        }

        segments.add(newSegment);
    }

    private static class Segment {
        ArrayDeque<Long> nodes;

        boolean addSegment(Segment other) {
            long first = nodes.peekFirst();
            long last = nodes.peekLast();

            long oFirst = other.nodes.peekFirst();
            long oLast = other.nodes.peekLast();

            if (first == oFirst) {
                while (!other.nodes.isEmpty()) {
                    nodes.addFirst(other.nodes.pollFirst());
                }
            } else if (last == oLast) {
                while (!other.nodes.isEmpty()) {
                    nodes.addLast(other.nodes.pollLast());
                }
            } else if (last == oFirst) {
                while (!other.nodes.isEmpty()) {
                    nodes.addLast(other.nodes.pollFirst());
                }
            } else if (first == oLast) {
                while (!other.nodes.isEmpty()) {
                    nodes.addFirst(other.nodes.pollLast());
                }
            } else {
                return false;
            }

            return true;
        }
    }

    private static class NodeDesc {
        double lat, lon;

        public NodeDesc(Node node) {
            lat = node.getLatitude();
            lon = node.getLongitude();
        }
    }

    private static class WayDesc {
        private long[] nodes;

        public WayDesc(Way way) {
            nodes = new long[way.getWayNodes().size()];
            nodes = way.getWayNodes().stream().mapToLong(WayNode::getNodeId).toArray();
        }
    }
}
